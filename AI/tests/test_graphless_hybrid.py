"""Graph-free experiment behavior; synthetic vectors do not measure model accuracy."""
import json

import pytest

from AI.evaluation.graphless_hybrid import GraphlessConfig, extract_goal_keywords, score_passages
from AI.evaluation.streamlit_runner import GraphlessSession, compare_graphless, parse_inputs
from src.analysis.passage_grouping import CodeContextGroup
from src.preprocessing.models import CleanedPassage, PassageKind


def passage(pid, text, kind=PassageKind.BODY, order=0):
    return CleanedPassage(pid, kind, text, order)


def test_keyword_canonicalization_does_not_count_suffix_forms_twice():
    assert extract_goal_keywords("React useState로 객체 상태 변경하기 공부를") == (
        "react", "usestate", "객체", "상태", "변경")


def test_querydsl_goal_removes_particles_and_function_words():
    assert extract_goal_keywords("QueryDSL로 검색 조건에 따라 JPA 동적 조회 쿼리 구현하기") == (
        "querydsl", "검색", "조건", "jpa", "동적", "조회", "쿼리")


def test_particle_forms_match_the_same_body_concept_without_harming_noun_endings():
    assert extract_goal_keywords("조건에 조건이 조건을 조건에서 조건으로") == ("조건",)
    assert extract_goal_keywords("사과를 사서 과일의 가격을 비교하기") == ("사과", "과일", "가격")
    result = score_passages("조건에 따라 조회하기", (passage("body", "조건이 있으면 조회 결과를 반환합니다."),),
                            (1, 0), ((1, 0),))
    assert result["matches"][0]["keywordScore"] == 1
    assert result["matchedKeywords"] == ["조건", "조회"]


def test_technical_identifiers_keep_symbols_case_normalization_and_korean_particles():
    assert extract_goal_keywords("C++과 C#으로 Node.js useState set_state를 학습하기") == (
        "c++", "c#", "node.js", "usestate", "set_state")
    assert extract_goal_keywords("ＱｕｅｒｙＤＳＬ로 JPA를 공부하기") == ("querydsl", "jpa")


def test_keyword_matching_uses_complete_terms_and_does_not_generate_aliases():
    result = score_passages("Java SQL data", (passage("body", "JavaScript NoSQL database BooleanBuilder"),),
                            (1, 0), ((1, 0),))
    assert result["matchedKeywords"] == []
    assert result["matches"][0]["keywordScore"] == 0


def test_semantic_match_can_pass_without_literal_goal_keywords():
    result = score_passages("Transformer decoder", (passage("body", "미래 위치의 정보를 가리고 다음 단어를 예측합니다."),),
                            (1, 0), ((1, 0),))
    assert result["matchedKeywords"] == []
    assert result["relevanceLabel"] == "RELATED"


def test_tcpudp_game_selection_is_not_goal_evidence():
    import math
    from pathlib import Path
    fixture = json.loads((Path(__file__).resolve().parents[1] / "evaluation" / "tcp-udp-1h-cases.json").read_text())
    case = next(case for case in fixture["cases"] if case["caseId"] == "tcpudp-35m")
    # Fixed reproduction of the observed cosine, not a fresh model measurement.
    result = score_passages(fixture["goal"]["text"], (passage("game", case["text"]),),
                            (1., 0.), ((.2357, math.sqrt(1 - .2357 ** 2)),))
    match = result["matches"][0]
    assert result["keywordTerms"] == ["tcp", "udp", "전송", "프로토콜"]
    assert "선택" in result["generalTerms"]
    assert "프로토콜 선택" in result["goalPhrases"]
    assert match["keywordHits"] == match["goalPhraseHits"] == []
    assert match["ignoredGeneralHits"] == ["선택"]
    assert match["keywordScore"] == 0
    assert match["vectorBase"] == pytest.approx(.2357)
    assert match["keywordBoost"] == 0
    assert match["hybridScore"] == pytest.approx(.2357)
    assert result["unrelatedCoverage"] == 1
    assert result["relevanceLabel"] == "UNRELATED"


def test_meaningful_phrase_keeps_general_word_without_double_scoring():
    result = score_passages("전송 프로토콜 선택하기", (passage("body", "전송 프로토콜을 선택합니다."),),
                            (1., 0.), ((1., 0.),))
    match = result["matches"][0]
    assert match["goalPhraseHits"] == ["프로토콜 선택"]
    assert match["generalKeywordHits"] == ["선택"]
    assert match["ignoredGeneralHits"] == []
    assert match["keywordScore"] == match["hybridScore"] == 1
    assert result["relevanceLabel"] == "RELATED"


@pytest.mark.parametrize("body", ["프로토콜을 공부합니다. 장비를 선택합니다.", "프로토콜\n선택", "선택 프로토콜"])
def test_goal_phrase_does_not_cross_sentence_line_or_reverse_order(body):
    result = score_passages("프로토콜 선택", (passage("body", body),), (1., 0.), ((1., 0.),))
    assert result["matches"][0]["goalPhraseHits"] == []


def test_selection_sort_is_preserved_as_a_phrase():
    result = score_passages("선택 정렬 공부하기", (passage("body", "선택 정렬은 최솟값을 찾는 정렬 알고리즘입니다."),),
                            (1., 0.), ((1., 0.),))
    assert result["keywordTerms"] == ["정렬"]
    assert result["goalPhrases"] == result["matches"][0]["goalPhraseHits"] == ["선택 정렬"]
    assert result["relevanceLabel"] == "RELATED"


def test_general_only_goal_never_forces_unrelated_from_low_cosine():
    result = score_passages("차이 비교 정리 확인하기", (passage("body", "아무 주제를 설명하는 본문입니다. " * 10),),
                            (1., 0.), ((0., 1.),))
    assert result["keywordTerms"] == []
    assert result["relevanceLabel"] == "UNCERTAIN"
    assert result["reasonCode"] == "INSUFFICIENT_GOAL_KEYWORDS"


def test_coverage_excludes_title_and_heading_and_counts_original_chars_once():
    body = passage("body", "React 객체 상태 변경 방법입니다.")
    code = passage("code", "setState(value)", PassageKind.CODE, 1)
    group = CodeContextGroup(passage("group", body.text + "\n\n" + code.text, PassageKind.CODE), ("body", "code"))
    passages = (passage("title", "요리" * 100, PassageKind.TITLE),
                passage("heading", "식단" * 100, PassageKind.HEADING), body, code)
    result = score_passages("React 상태", passages, (1, 0), ((0, 1), (0, 1), (1, 0), (1, 0)),
                            context_groups=(group,), context_vectors=((1, 0),))
    assert result["relevanceLabel"] == "RELATED"
    assert result["relatedCoverage"] == 1
    assert result["totalBodyChars"] == result["relatedBodyChars"] == len(body.text) + len(code.text)
    assert result["evidenceIds"] == ["body", "code"]


def test_mixed_evidence_is_uncertain_even_if_related_coverage_passes():
    result = score_passages("React 상태", (passage("a", "React 상태 " * 30), passage("b", "요리 레시피")),
                            (1, 0), ((1, 0), (0, 1)))
    assert result["relatedCoverage"] > .6
    assert result["reasonCode"] == "CONFLICTING_EVIDENCE"
    assert result["relevanceLabel"] == "UNCERTAIN"


def test_related_coverage_is_diagnostic_even_below_sixty_percent():
    import math
    related = passage("a", "React 상태 업데이트")
    neutral = passage("b", "중간 단계의 코드와 출력 내용을 살펴봅니다. " * 20)
    result = score_passages("React 상태", (related, neutral), (1., 0.),
                            ((1., 0.), (.4, math.sqrt(1 - .4 ** 2))))
    assert 0 < result["relatedCoverage"] < .6
    assert result["relatedBodyChars"] == len(related.text)
    assert result["unrelatedCoverage"] == 0
    assert result["reasonCode"] == "RELATED_EVIDENCE_FOUND"
    assert result["relevanceLabel"] == "RELATED"


def test_unrelated_requires_enough_body_and_complete_extraction():
    for text, complete, label in [("요리 레시피", True, "UNCERTAIN"),
                                  ("요리 레시피 재료 준비 " * 20, False, "UNCERTAIN"),
                                  ("요리 레시피 재료 준비 " * 20, True, "UNRELATED")]:
        result = score_passages("React 상태", (passage("body", text),), (1, 0), ((0, 1),), allow_unrelated=complete)
        assert result["relevanceLabel"] == label


@pytest.mark.parametrize("kwargs", [
    {"keyword_boost": float("nan")}, {"keyword_boost": True}, {"keyword_boost": 1.1},
    {"unrelated_coverage_threshold": 0}, {"unrelated_cosine_threshold": .55},
    {"unrelated_min_body_chars": 0},
])
def test_invalid_experiment_settings_are_rejected(kwargs):
    with pytest.raises(ValueError):
        GraphlessConfig(**kwargs)


def test_semantic_score_is_not_discounted_when_keywords_are_missing():
    import math
    result = score_passages("React 상태", (passage("body", "새로운 값으로 화면에 표시할 정보를 갱신합니다."),),
                            (1., 0.), ((.7, math.sqrt(1 - .7 ** 2)),))
    match = result["matches"][0]
    assert match["keywordScore"] == match["keywordBoost"] == 0
    assert match["hybridScore"] == pytest.approx(.7)
    assert result["relevanceLabel"] == "RELATED"


def test_keyword_boost_is_visible_and_cannot_bypass_minimum_cosine():
    import math
    result = score_passages("React 상태", (passage("body", "React 상태를 새로운 값으로 갱신합니다."),),
                            (1., 0.), ((.54, math.sqrt(1 - .54 ** 2)),))
    match = result["matches"][0]
    assert match["keywordScore"] == 1
    assert match["keywordBoost"] == pytest.approx(.092)
    assert match["hybridScore"] == pytest.approx(.632)
    assert result["relevanceLabel"] == "UNCERTAIN"


@pytest.mark.parametrize("cosine,expected", [(.2499, "UNRELATED"), (.2501, "UNCERTAIN")])
def test_unrelated_cosine_boundary_is_preserved_without_keyword_hits(cosine, expected):
    import math
    result = score_passages("React 상태", (passage("body", "요리 레시피 재료 준비와 조리 순서를 살펴봅니다. " * 8),),
                            (1., 0.), ((cosine, math.sqrt(1 - cosine ** 2)),))
    assert result["relevanceLabel"] == expected


@pytest.mark.parametrize("cosine", [-.4, 0., .6, 1.])
@pytest.mark.parametrize("text", ["요리 재료를 살펴봅니다.", "React 렌더링을 살펴봅니다.", "React 상태를 살펴봅니다."])
def test_keyword_evidence_only_increases_bounded_vector_base(cosine, text):
    import math
    result = score_passages("React 상태", (passage("body", text),), (1., 0.),
                            ((cosine, math.sqrt(1 - cosine ** 2)),))
    match = result["matches"][0]
    assert 0 <= match["vectorBase"] <= match["hybridScore"] <= 1
    assert 0 <= match["keywordBoost"] <= .2
    if not match["keywordHits"] or cosine == 1:
        assert match["keywordBoost"] == 0


class Client:
    fingerprint = "synthetic-only"
    dimensions = 2

    def __init__(self):
        self.calls = 0
        self.inputs = []

    def embed(self, texts):
        self.calls += 1
        self.inputs.extend(texts)
        return tuple((1., 0.) if "Instruct:" in text or "React" in text else (0., 1.) for text in texts)


def inputs(payload):
    return parse_inputs("React 객체 상태 변경", json.dumps(payload, ensure_ascii=False), "CHROME_DOM")


def test_expectations_do_not_enter_embedding_and_goal_vectors_are_reused():
    payload = {"text": "React 객체 상태 변경을 위해 새로운 객체를 상태 함수에 전달합니다.", "expectedLabel": "RELATED"}
    goal, cases = inputs(payload)
    client = Client()
    runtime = GraphlessSession(client)
    first = compare_graphless(goal, cases, client, session=runtime)
    assert first["rows"][0]["relevanceLabel"] == "RELATED"
    assert first["embeddingCalls"] == 1
    assert first["graph"] is None
    assert first["openaiCalls"] == first["openaiMs"] == 0
    assert first["rows"][0]["openaiCalls"] == first["rows"][0]["openaiMs"] == 0
    assert len(client.inputs) == 2  # Goal + body in one request.
    payload["expectedLabel"] = "UNRELATED"
    goal, cases = inputs(payload)
    second = compare_graphless(goal, cases, client, session=runtime)
    assert second["embeddingCalls"] == 0
    assert second["rows"][0]["cacheHit"]
    assert second["rows"][0]["relevanceLabel"] == "RELATED"
    assert second["summary"][0]["기대 일치율 %"] == 0
    goal, cases = inputs({"text": "React 객체 상태 변경은 기존 객체를 수정하지 않고 새로운 객체로 처리합니다."})
    compare_graphless(goal, cases, client, session=runtime)
    assert len([text for text in client.inputs if text.startswith("Instruct:")]) == 1


def test_keyword_normalization_does_not_rewrite_embedding_input():
    goal_text = "QueryDSL로 검색 조건에 따라 JPA 동적 조회 쿼리 구현하기"
    body = "검색 조건이 있으면 QueryDSL로 JPA 조회 쿼리를 동적으로 구성합니다."
    goal, cases = parse_inputs(goal_text, json.dumps({"text": body}, ensure_ascii=False), "CHROME_DOM")
    client = Client()
    report = compare_graphless(goal, cases, client)
    assert report["rows"][0]["status"] == "COMPLETED"
    assert any(text.endswith("Query: " + goal_text) for text in client.inputs)
    assert body in client.inputs


@pytest.mark.parametrize("extra,reason", [
    ({"isSensitive": True}, "PRIVACY_BLOCKED"),
    ({"isExcluded": True}, "EXCLUDED"),
    ({"text": "비공개 연락처 test@example.com을 기록한 문서입니다."}, "PRIVACY_BLOCKED"),
    ({"isActiveTarget": False}, "INACTIVE_TARGET"),
    ({"sessionStatus": "PAUSED"}, "STALE_EVENT"),
    ({"contentSource": "DESKTOP_APP_OCR"}, "OCR_METADATA_REQUIRED"),
    ({"contentSource": "DESKTOP_APP_OCR", "ocr": {"confidence": .9, "captureStatus": "BLACK_SCREEN"}}, "BLACK_SCREEN"),
    ({"text": "React setter 오류", "contentSource": "DESKTOP_APP_OCR",
      "ocr": {"confidence": .1, "captureStatus": "SUCCESS"}}, "LOW_CONFIDENCE"),
])
def test_policy_and_failed_ocr_never_send_model_requests(extra, reason):
    goal, cases = inputs({"text": "React 객체 상태 변경을 위해 새로운 객체를 상태 함수에 전달합니다.", **extra})
    client = Client()
    result = compare_graphless(goal, cases, client)
    assert result["embeddingCalls"] == 0
    assert result["rows"][0]["reasonCode"] == reason
    assert result["rows"][0]["relevanceLabel"] != "UNRELATED"


def test_model_failure_is_not_counted_as_correct_uncertain():
    class Broken(Client):
        def embed(self, texts):
            raise RuntimeError("private provider message")
    goal, cases = inputs({"text": "React 객체 상태 변경을 위해 새로운 객체를 상태 함수에 전달합니다.", "expectedLabel": "UNCERTAIN"})
    report = compare_graphless(goal, cases, Broken())
    assert report["rows"][0]["status"] == "FAILED"
    assert report["summary"][0]["기대 일치율 %"] == 0
    assert "private provider message" not in json.dumps(report)


def test_title_only_and_goal_mismatch_do_not_embed():
    goal, cases = inputs({"pageTitle": "React 객체 상태 변경", "text": ""})
    client = Client()
    report = compare_graphless(goal, cases, client)
    assert report["rows"][0]["reasonCode"] == "INSUFFICIENT_BODY"
    assert client.calls == 0
    goal, cases = inputs({"text": "React 객체 상태 변경을 위해 새로운 객체를 상태 함수에 전달합니다."})
    report = compare_graphless(goal.model_copy(update={"goal_id": "different"}), cases, client)
    assert report["rows"][0]["reasonCode"] == "GOAL_VERSION_MISMATCH"
    assert client.calls == 0


def test_long_related_title_cannot_promote_short_body_without_related_evidence():
    goal, cases = inputs({"pageTitle": "React 객체 상태 변경 학습 자료" * 10, "text": "출력 결과"})
    client = Client()
    report = compare_graphless(goal, cases, client)
    assert report["rows"][0]["reasonCode"] == "INSUFFICIENT_EVIDENCE"
    assert report["rows"][0]["relevanceLabel"] == "UNCERTAIN"
    assert report["rows"][0]["status"] == "COMPLETED"
    assert client.calls == 1


@pytest.mark.parametrize("text", ["React setter 오류", "```javascript\nReact.setState(next)\n```"])
def test_short_error_or_code_can_be_related_without_character_minimum(text):
    goal, cases = inputs({"text": text})
    client = Client()
    row = compare_graphless(goal, cases, client)["rows"][0]
    assert row["status"] == "COMPLETED"
    assert row["relevanceLabel"] == "RELATED"
    assert row["reasonCode"] == "RELATED_EVIDENCE_FOUND"
    assert client.calls == 1


@pytest.mark.parametrize("text", ["", "닫기", "!!!"])
def test_missing_or_unusable_body_still_abstains_without_embedding(text):
    goal, cases = inputs({"text": text})
    client = Client()
    row = compare_graphless(goal, cases, client)["rows"][0]
    assert row["relevanceLabel"] == "UNCERTAIN"
    assert client.calls == 0


def test_generic_status_word_no_longer_vetoes_unrelated():
    import math
    text = '게임 캐릭터의 상태 창에서 체력과 장비를 확인하고 보스를 공격합니다. ' * 5
    result = score_passages('React 상태', (passage('b', text),), (1., 0.),
                            ((.2, math.sqrt(1 - .2 ** 2)),))
    row = result['matches'][0]
    assert row['keywordHits'] == ['상태']
    assert row['keywordScore'] == pytest.approx(.25 / 1.25)
    assert result['keywordWeights'] == {'react': 1., '상태': .25}
    assert result['relevanceLabel'] == 'UNRELATED'


def test_screen_context_cannot_upgrade_unrelated_body_or_add_goal_keywords():
    from AI.evaluation.graphless_hybrid import build_screen_contexts
    body = passage('body', '요리 레시피로 재료를 준비하고 팬에서 볶는 순서를 확인합니다. ' * 5)
    items = (passage('title', 'React 상태 업데이트', PassageKind.TITLE), body)
    groups = build_screen_contexts(items)
    result = score_passages('React 상태', items, (1., 0.), ((1., 0.), (0., 1.)),
                            screen_contexts=groups, screen_vectors=((1., 0.),))
    ctx = result['matches'][-1]
    assert ctx['unitType'] == 'SCREEN_CONTEXT'
    assert ctx['keywordHits'] == []
    assert not ctx['relatedEvidence']
    assert result['relevanceLabel'] == 'UNRELATED'
    assert result['relatedCoverage'] == 0


def test_context_can_support_body_only_above_its_raw_cosine_floor():
    import math
    from AI.evaluation.graphless_hybrid import build_screen_contexts
    body = passage('b', '새 값을 만드는 예제입니다.')
    groups = build_screen_contexts((body,), window_title='React 상태 업데이트')
    for raw_cosine, label in [(.54, 'UNCERTAIN'), (.56, 'RELATED')]:
        result = score_passages('React 상태', (body,), (1., 0.),
                                ((raw_cosine, math.sqrt(1 - raw_cosine ** 2)),),
                                screen_contexts=groups, screen_vectors=((1., 0.),))
        assert result['relevanceLabel'] == label
        assert result['matches'][0]['cosine'] == pytest.approx(raw_cosine)
        assert result['matches'][-1]['keywordScore'] == 0
    assert result['relatedBodyChars'] == len(body.text)
    assert result['relatedCoverage'] == 1


def test_actual_heading_scope_resets_and_plain_ocr_does_not_invent_headings():
    from AI.evaluation.graphless_hybrid import build_screen_contexts
    items = (passage('h1', '# 대주제', PassageKind.HEADING),
             passage('h2', '## 첫 절', PassageKind.HEADING),
             passage('h3', '### 하위 설명', PassageKind.HEADING),
             passage('a', '첫 본문'), passage('new-h2', '## 다음 절', PassageKind.HEADING),
             passage('b', '다음 본문'))
    groups = build_screen_contexts(items)
    assert '하위 설명' in groups[0].passage.text
    assert '첫 절' not in groups[1].passage.text
    assert '하위 설명' not in groups[1].passage.text
    assert '대주제' in groups[1].passage.text
    assert build_screen_contexts((passage('plain', '제목처럼 보이는 평문\n일반 OCR 본문'),)) == ()
    assert build_screen_contexts(items, max_bytes=1) == ()


def test_valid_body_with_menu_fragments_reaches_embedding_in_streamlit():
    goal, cases = inputs({'text': 'React 객체 상태 변경은 새 객체를 setter에 전달하는 방식입니다.\n닫기\n공유\n확인'})
    client = Client()
    row = compare_graphless(goal, cases, client)['rows'][0]
    assert row['status'] == 'COMPLETED'
    assert row['relevanceLabel'] == 'RELATED'
    assert client.calls == 1


def test_desktop_window_title_context_is_embedded_as_data_not_query():
    goal, cases = inputs({'text': '기존 객체의 속성을 복사하여 다음 값을 만듭니다.',
                         'contentSource': 'DESKTOP_APP_OCR', 'windowTitle': 'React useState 자료',
                         'ocr': {'confidence': .95, 'captureStatus': 'SUCCESS'}})
    client = Client()
    row = compare_graphless(goal, cases, client)['rows'][0]
    assert any('[page title]\nReact useState 자료\n\n[body]' in text for text in client.inputs)
    assert len([text for text in client.inputs if text.startswith('Instruct:')]) == 1
    assert row['bodyScore'] == pytest.approx(.02)  # Body-only weak 객체 hit; title terms add no K.
    assert row['contextScore'] == 1
    assert row['decisionScore'] is None
    assert row['relevanceLabel'] == 'UNCERTAIN'  # Short body cannot meet the existing UNRELATED length guard.



def test_calibration_loader_rejects_stale_or_unsafe_settings(tmp_path):
    import hashlib
    from dataclasses import asdict
    from AI.evaluation.graphless_hybrid import load_calibrated_config, SCORING_VERSION, KEYWORD_EXTRACTOR_VERSION
    from src.analysis.relevance_analyzer import DEFAULT_INSTRUCTION
    target = tmp_path / 'config.json'
    cfg, meta = load_calibrated_config(target)
    assert meta['status'] == 'UNCALIBRATED'
    value = {'scoringVersion': SCORING_VERSION, 'keywordExtractorVersion': KEYWORD_EXTRACTOR_VERSION,
             'constraintSatisfied': True, 'config': asdict(GraphlessConfig()), 'selectedUsing': 'development-only',
             'embedding': {'model': 'qwen3-embedding:4b-fp16', 'dimensions': 2560, 'configuredRevision': 'test-pin',
                           'instructionSha256': hashlib.sha256(DEFAULT_INSTRUCTION.encode()).hexdigest()}}
    target.write_text(json.dumps(value))
    assert load_calibrated_config(target)[0] == GraphlessConfig()
    for patch in [{'scoringVersion': 'old'}, {'keywordExtractorVersion': 'old'}, {'constraintSatisfied': False}]:
        target.write_text(json.dumps({**value, **patch}))
        with pytest.raises(ValueError):
            load_calibrated_config(target)
    target.write_text(json.dumps({**value, 'embedding': {**value['embedding'], 'instructionSha256': 'old'}}))
    with pytest.raises(ValueError):
        load_calibrated_config(target)


def test_intro_candidate_is_deferred_without_becoming_related_evidence():
    from AI.evaluation.graphless_hybrid import decide_matches
    config = GraphlessConfig(min_related_cosine=.30, related_passage_threshold=.35)
    rows = [dict(passageId='intro', sourceIds=['intro'], kind='BODY', text='문자 누락 현상',
                 cosine=.22, hybridScore=.22, followingBodyId='explanation', followingSimilarity=.52),
            dict(passageId='explanation', sourceIds=['explanation'], kind='BODY', text='설명',
                 cosine=.26, hybridScore=.26),
            dict(passageId='related', sourceIds=['related'], kind='BODY', text='수신 인터럽트 처리',
                 cosine=.40, hybridScore=.43)]
    result = decide_matches(rows, config)
    assert result['relevanceLabel'] == 'RELATED'
    intro = result['matches'][0]
    assert intro['unrelatedCandidate'] and not intro['unrelatedEvidence'] and not intro['relatedEvidence']
    assert intro['negativeEvidenceReason'] == 'INTRO_CONTEXT_DEFERRED'
    assert result['relatedBodyChars'] == len('수신 인터럽트 처리')
    assert result['unrelatedBodyChars'] == 0
    # No independent positive evidence means abstain, not an automatic RELATED.
    assert decide_matches(rows[:2], config)['relevanceLabel'] == 'UNCERTAIN'
    # Abrupt topic change and two consistently low-goal passages still conflict.
    for change in ({'followingSimilarity': .49}, {'followingBodyId': 'missing'}):
        assert decide_matches([dict(rows[0], **change), *rows[1:]], config)['relevanceLabel'] == 'UNCERTAIN'
    assert decide_matches([rows[0], dict(rows[1], cosine=.25), rows[2]], config)['relevanceLabel'] == 'UNCERTAIN'


def test_intro_requires_original_adjacency_and_non_sentence_structure():
    import math
    v = (.22, math.sqrt(1 - .22 ** 2))
    w = (.26, math.sqrt(1 - .26 ** 2))
    config = GraphlessConfig(min_related_cosine=.30, related_passage_threshold=.35)
    for kind in (PassageKind.BODY, PassageKind.OCR):
        a, b = passage('a', '문자 누락 현상', kind, 1), passage('b', '문자를 수신한 다음 처리합니다.', kind, 2)
        result = score_passages('UART 공부', (a, b), (1, 0), (v, w), config,
                                intro_pairs=((a.text, b.text),))
        assert result['matches'][0]['negativeEvidenceReason'] == 'INTRO_CONTEXT_DEFERRED'
        assert 'followingBodyId' not in score_passages('UART 공부', (a, b), (1, 0), (v, w), config)['matches'][0]
        for first in (passage('a', '문자를 놓친다.', kind, 1), passage('a', a.text, kind, 0),
                      passage('a', a.text, PassageKind.HEADING, 1)):
            output = score_passages('UART 공부', (first, b), (1, 0), (v, w), config,
                                    intro_pairs=((first.text, b.text),))
            assert 'followingBodyId' not in output['matches'][0]


@pytest.mark.parametrize('text', [
    '앞에서 본 동작을 다른 입력으로 반복해 보고 차이를 설명해 보세요.',
    '방금 설명한 항목을 순서에 맞게 요약해 둔다.',
    '위 내용을 참고해 결과를 단계별로 요약하고 비교합니다.',
    '실습 결과를 예상과 비교하고 잘못된 부분이 있으면 해당 동작을 다시 추적하여 수정한다.',
])
def test_dependent_followup_recognizes_paraphrases(text):
    from AI.evaluation.graphless_hybrid import is_dependent_followup
    assert is_dependent_followup(text)


@pytest.mark.parametrize('text', [
    '위 내용을 참고해 친구들과 게임을 플레이한다.',
    '예제의 결과를 확인한 다음 RTX4090 공동구매 가격을 비교한다.',
    '앞의 설명처럼 토마토와 마늘을 넣고 조리한다.',
    '위 결과를 참고해 주문한다.',
    '예제의 결과를 보며 논다.',
    '그 예제의 실행 순서',
    'Compare the prices of the examples on a shopping website.',
])
def test_followup_heuristic_leaves_new_topics_actions_and_incomplete_text_unchanged(text):
    from AI.evaluation.graphless_hybrid import is_dependent_followup
    assert not is_dependent_followup(text)


def test_followup_requires_original_same_source_adjacency_and_body_boundaries():
    from AI.evaluation.graphless_hybrid import build_followup_contexts
    a = passage('a', 'JWT 서명을 검증하고 토큰을 검사합니다.', order=1)
    b = passage('b', '위 내용을 참고해 결과를 정리한다.', order=2)
    pairs = ((a.text, b.text),)
    assert len(build_followup_contexts((a,b), source_pairs=pairs)) == 1
    assert not build_followup_contexts((a,b), source_pairs=())
    assert not build_followup_contexts((a,b), source_pairs=pairs, max_bytes=1)
    for first in (passage('a',a.text,PassageKind.TITLE,1), passage('a',a.text,order=0),
                  passage('a',a.text,PassageKind.OCR,1), passage('a',a.text,PassageKind.CODE,1)):
        assert not build_followup_contexts((first,b), source_pairs=pairs)
    assert not build_followup_contexts((a,passage('h','# 다른 주제',PassageKind.HEADING,2),
                                        passage('b',b.text,order=3)), source_pairs=pairs)


def test_followup_deferral_needs_raw_related_anchor_and_context_without_promoting_or_double_counting():
    import math
    from AI.evaluation.graphless_hybrid import build_followup_contexts
    def vector(v): return (v, math.sqrt(1-v*v))
    a = passage('a','JWT 토큰의 서명을 검증합니다.',order=1)
    b = passage('b','앞의 설명과 결과를 비교하여 정리한다.',order=2)
    contexts = build_followup_contexts((a,b), source_pairs=((a.text,b.text),))
    cfg = GraphlessConfig(min_related_cosine=.30,related_passage_threshold=.35)
    def run(anchor=.6,context=.5):
        return score_passages('JWT 토큰 검증',(a,b),(1,0),(vector(anchor),vector(.1)),cfg,
                              followup_contexts=contexts,followup_vectors=(vector(context),))
    result=run()
    assert result['relevanceLabel']=='RELATED'
    assert result['totalBodyChars']==len(a.text)+len(b.text)
    assert result['relatedBodyChars']==len(a.text) and result['unrelatedBodyChars']==0
    followup=result['matches'][1]
    assert followup['unrelatedCandidate'] and not followup['unrelatedEvidence']
    assert not followup['relatedEvidence'] and followup['cosine']==pytest.approx(.1)
    assert followup['negativeEvidenceReason']=='FOLLOWUP_CONTEXT_DEFERRED'
    assert not result['matches'][2]['relatedEvidence']  # Combined context adds no coverage.
    assert run(anchor=.29)['relevanceLabel']=='UNCERTAIN'
    assert run(context=.29)['relevanceLabel']=='UNCERTAIN'
    with pytest.raises(ValueError,match='VECTOR_COUNT_INVALID'):
        score_passages('JWT 토큰',(a,b),(1,0),(vector(.6),vector(.1)),cfg,followup_contexts=contexts)


def test_followup_does_not_propagate_relevance_through_neutral_paragraphs():
    import math
    from AI.evaluation.graphless_hybrid import build_followup_contexts
    ps=(passage('a','JWT 토큰 서명을 검증합니다.',order=1),
        passage('b','앞의 설명과 결과를 비교한다.',order=2),
        passage('c','위 결과와 예상을 비교해 정리한다.',order=3))
    contexts=build_followup_contexts(ps,source_pairs=tuple((a.text,b.text) for a,b in zip(ps,ps[1:])))
    low=(.1, math.sqrt(.99))
    result=score_passages('JWT 토큰',ps,(1,0),((1,0),low,low),
                          followup_contexts=contexts,followup_vectors=((1,0),(1,0)))
    assert result['relevanceLabel']=='UNCERTAIN'
    assert result['matches'][1]['negativeEvidenceReason']=='FOLLOWUP_CONTEXT_DEFERRED'
    assert result['matches'][2]['unrelatedEvidence']
