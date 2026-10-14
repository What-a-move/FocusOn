import pytest

from src.api.schemas import ContentInput, VerifiedContext
from src.preprocessing.adapter import adapt_content, preprocessing_context
from src.preprocessing.content_parser import parse_content
from src.preprocessing.models import PassageKind, RejectionReason
from src.preprocessing.validator import validate_and_preprocess


def prepare(text, **changes):
    observation = dict(sessionId="session-1", runId="run-1", goalId="goal-1", goalVersion=1,
                       eventId="event-1", navigationId="nav-1", observedAt="2026-10-11T01:00:00Z",
                       sessionStatus="ACTIVE", exclusionMode="NONE")
    content = ContentInput.parse_safe({
        **observation, "contentSource": "CHROME_DOM", "contentType": "DOCUMENT",
        "text": text, "extractionStatus": "SUCCESS", "isSensitive": False,
        "isExcluded": False, **changes,
    })
    context = VerifiedContext.parse_safe({
        **observation, "userId": "user-1", "resumeRequired": False,
        "policyVersion": "policy-1", "extractorVersion": "extractor-1", "stateVersion": 1,
    })
    return adapt_content(content, context), context


def test_preserves_title_heading_body_and_code_reading_order():
    code = '```javascript title="state.js"\nif (state) {\n    setState({...state});\n}\n```'
    request, _ = prepare("## 객체 복사\n전개 구문으로 복사합니다.\n\n" + code +
                         "\n### 상태 확인\n결과를 확인합니다.", pageTitle="React 학습 자료")

    passages = parse_content(request).passages

    assert [p.kind for p in passages] == [PassageKind.TITLE, PassageKind.HEADING,
        PassageKind.BODY, PassageKind.CODE, PassageKind.HEADING, PassageKind.BODY]
    assert [p.order for p in passages] == list(range(6))
    assert passages[1].text == "## 객체 복사"
    assert passages[3].text == code
    assert passages[4].text == "### 상태 확인"


def test_only_explicit_atx_h1_h2_h3_are_headings():
    request, _ = prepare("# 첫째\n## 둘째\n   ### 셋째\n#### 넷째\n#공백없음\n일반 제목")

    passages = parse_content(request).passages

    assert [p.kind for p in passages] == [PassageKind.HEADING] * 3 + [PassageKind.BODY]
    assert passages[-1].text == "#### 넷째\n#공백없음\n일반 제목"


@pytest.mark.parametrize("fence", ["```", "~~~~"])
def test_fence_info_and_code_indentation_are_preserved(fence):
    source = fence + 'python title="example.py"\nif value:\n    print("&lt;")\n' + fence
    request, _ = prepare(source)

    passages = parse_content(request).passages

    assert len(passages) == 1 and passages[0].kind is PassageKind.CODE
    assert passages[0].text == source


def test_only_a_matching_complete_fence_closes_code():
    code = "````python\n```\n~~~\n```` not-a-close\n# still code\nprint(1)\n`````"
    request, _ = prepare(code + "\n다음 본문입니다.")

    passages = parse_content(request).passages

    assert [p.kind for p in passages] == [PassageKind.CODE, PassageKind.BODY]
    assert passages[0].text == code
    assert passages[1].text == "다음 본문입니다."


def test_unclosed_code_fence_does_not_turn_code_comments_into_headings():
    source = "```python\n# code comment\nprint(1)"
    request, _ = prepare(source)

    passages = parse_content(request).passages

    assert len(passages) == 1 and passages[0].kind is PassageKind.CODE
    assert passages[0].text == source


@pytest.mark.parametrize("content_type", ["CODE", "TABLE", "ERROR"])
def test_explicit_structured_content_is_not_reinterpreted_as_markdown(content_type):
    source = "# source line\n~~~python\n    value\n~~~"
    request, _ = prepare(source, contentType=content_type)

    passages = parse_content(request).passages

    assert len(passages) == 1
    assert passages[0].kind is (PassageKind.TABLE if content_type == "TABLE" else PassageKind.CODE)
    assert passages[0].text == source


def test_plain_ocr_title_is_not_inferred_as_a_heading():
    request, _ = prepare("화면의 제목\n화면에 표시된 본문입니다.", contentSource="DESKTOP_APP_OCR",
                         ocr={"confidence": 0.99, "captureStatus": "SUCCESS"})

    passages = parse_content(request).passages

    assert len(passages) == 1 and passages[0].kind is PassageKind.OCR


@pytest.mark.parametrize("source", ["```python\n\n```", "```python"])
def test_empty_code_fence_does_not_create_body_evidence(source):
    request, _ = prepare(source)

    assert not parse_content(request).passages


def test_entity_preservation_does_not_bypass_code_privacy_check():
    request, context = prepare('```python\ncontact = "synthetic&#64;example.invalid"\n```')

    result = validate_and_preprocess(request, preprocessing_context(context))

    assert not result.should_analyze
    assert result.reasons == (RejectionReason.PRIVACY_BLOCKED,)
    assert not result.passages
