from concurrent.futures import ThreadPoolExecutor

import pytest

from src.api.schemas import ConfirmedGoal, ContentInput, SafeInputError, VerifiedContext
from src.analysis.relevance_analyzer import RelevanceAnalyzer
from src.models.embedding_client import EmbeddingError
from src.preprocessing.adapter import adapt_content, preprocessing_context
from src.preprocessing.validator import validate_and_preprocess
from src.utils.cache import TTLCache


class ScriptedClient:
    fingerprint = "fake-v1"
    dimensions = 6

    def __init__(self):
        self.calls = 0
        self.requests = []
        self.after_embed = None
        self.error = None
        self.invalid_vector = None

    def embed(self, texts):
        self.calls += 1
        self.requests.append(tuple(texts))
        if self.after_embed:
            self.after_embed(self.calls)
        if self.error:
            raise self.error
        if self.invalid_vector is not None:
            return tuple(self.invalid_vector for _ in texts)
        if texts[0].startswith("Instruct:"):
            return tuple(tuple(float(column == index) for column in range(6)) for index in range(len(texts)))
        return tuple((1, 0, 0, 0, 0, 0) for _ in texts)


def inputs(**changes):
    observation = dict(sessionId="session-1", runId="run-1", goalId="goal-1", goalVersion=1,
                       clientRunId="event-1", sequence=1, navigationId="nav-1",
                       observedAt="2026-10-07T01:00:00Z", contentSource="CHROME_DOM",
                       isActiveTarget=True, sessionStatus="ACTIVE", exclusionMode="NONE")
    content = ContentInput.parse_safe({**observation, "contentType": "DOCUMENT",
                                      "text": "useState를 사용하여 객체 상태 업데이트를 수행하는 상태 변경 함수 예제입니다.",
                                      "extractionStatus": "SUCCESS", "isSensitive": False, "isExcluded": False, **changes})
    content_data = content.model_dump(mode="json", by_alias=True)
    context_observation = {key: content_data[key] for key in observation}
    context = VerifiedContext.parse_safe({**context_observation, "userId": "user-1", "resumeRequired": False,
                                         "policyVersion": "policy-1", "extractorVersion": "extractor-1", "stateVersion": 1})
    goal = ConfirmedGoal.parse_safe({"goalId": "goal-1", "goalVersion": 1, "text": "React useState로 객체 상태 변경하기", "confirmed": True, "templateId": "react-object-state"})
    return content, goal, context


def test_explanation_code_group_is_embedded_and_searched_with_original_evidence_ids():
    client = ScriptedClient()
    content, goal, context = inputs(text=(
        "useState 상태 업데이트는 객체를 복사하여 수행합니다.\n\n"
        "```javascript\nconst next = {...prev, count: 1};\nupdate(next);\n```"
    ))
    prepared = validate_and_preprocess(adapt_content(content, context), preprocessing_context(context))
    body, code = prepared.passages
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == "RELATED"
    assert result.evidence_ids == (body.id, code.id)
    assert client.requests[1] == (body.text, code.text, body.text + "\n\n" + code.text)
    assert client.calls == 2


def test_duplicate_heading_removed_by_preprocessing_still_blocks_code_grouping():
    client = ScriptedClient()
    content, goal, context = inputs(text=(
        "## Example\n\nuseState 상태 업데이트를 설명하는 첫 번째 문단입니다.\n\n"
        "## Example\n\n```javascript\nconst next = {...prev, count: 1};\nupdate(next);\n```"
    ))
    prepared = validate_and_preprocess(adapt_content(content, context), preprocessing_context(context))
    RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert client.requests[1] == tuple(p.text for p in prepared.passages)


def test_direct_result_quality_attribution_and_no_raw_cache():
    content, goal, context = inputs(qualityScore=0.2)
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    result = analyzer.analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == "RELATED"
    assert result.quality_score == 1.0  # Client-supplied final quality is ignored.
    assert result.client_run_id == content.client_run_id and result.sequence == content.sequence
    assert result.observed_at == content.observed_at.isoformat()
    assert result.evidence_ids == ("passage-001",)
    assert client.calls == 2
    for cache in (analyzer._events, analyzer._results, analyzer._vectors):
        assert content.text not in repr(cache._items)
        assert goal.text not in repr(cache._items)
    assert content.text not in repr(result)


def test_arbitrary_confirmed_goal_uses_generated_minimal_graph_without_template():
    goal_text = "Rust ownership과 borrowing 학습"
    content, _, context = inputs(
        text=f"{goal_text}에서는 소유권 이동과 참조 규칙을 예제로 반복해서 확인합니다."
    )
    goal = ConfirmedGoal.parse_safe({
        "goalId": "goal-1",
        "goalVersion": 1,
        "text": goal_text,
        "confirmed": True,
    })
    client = ScriptedClient()

    result = RelevanceAnalyzer(client).analyze(
        content, goal, context, current_context=lambda: context
    )

    assert result.relevance_label == "RELATED"
    assert result.reason_code == "GOAL_CONNECTED"
    assert client.calls == 2


@pytest.mark.parametrize("changes,label", [({"isExcluded": True}, "EXCLUDED"), ({"isSensitive": True}, "PRIVACY_BLOCKED"), ({"extractionStatus": "EXCLUDED"}, "EXCLUDED"), ({"extractionStatus": "FAILED"}, "UNCERTAIN"), ({"extractionStatus": "UNSUPPORTED"}, "UNCERTAIN"), ({"text": "synthetic@example.invalid"}, "PRIVACY_BLOCKED"), ({"text": "닫기\n공유\n확인"}, "UNCERTAIN"), ({"text": "\ufffd" * 40}, "UNCERTAIN"), ({"text": "", "pageTitle": "React useState"}, "UNCERTAIN")])
def test_model_not_called_on_rejected_content(changes, label):
    content, goal, context = inputs(**changes)
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == label
    assert not result.evidence_ids and result.content_hash is None
    if changes.get("pageTitle"):
        # Titles alone are rejected by the analyzer before embedding.
        assert result.reason_code == "INSUFFICIENT_BODY"
    assert client.calls == 0


def test_clear_tab_metadata_is_decided_without_embedding():
    content, goal, context = inputs(
        contentSource="TAB_META",
        text="",
        appName="Chrome",
        windowTitle="React useState 객체 상태 업데이트",
        pageTitle="useState 상태 변경 함수",
        urlHost="react.dev",
    )
    client = ScriptedClient()

    result = RelevanceAnalyzer(client).analyze(
        content, goal, context, current_context=lambda: context
    )

    assert result.relevance_label == "RELATED"
    assert result.reason_code == "METADATA_GOAL_CONNECTED"
    assert result.evidence_ids == ("windowTitle", "pageTitle")
    assert result.next_content_source is None
    assert client.calls == 0


def test_ambiguous_or_conflicting_metadata_is_uncertain_without_embedding():
    cases = (
        ({"pageTitle": "useState 참고", "windowTitle": ""}, "METADATA_INSUFFICIENT"),
        ({"pageTitle": "useState 객체 상태 노래 가사", "windowTitle": "상태 업데이트 앨범"}, "CONFLICTING_METADATA"),
    )
    for metadata, reason in cases:
        content, goal, context = inputs(contentSource="TAB_META", text="", **metadata)
        client = ScriptedClient()

        result = RelevanceAnalyzer(client).analyze(
            content, goal, context, current_context=lambda: context
        )

        assert result.relevance_label == "UNCERTAIN"
        assert result.reason_code == reason
        assert result.next_content_source == "CHROME_DOM"
        assert client.calls == 0


def test_content_fallback_order_stops_after_dom_in_mvp():
    metadata, goal, metadata_context = inputs(
        contentSource="TAB_META",
        text="",
        pageTitle="useState 참고",
        windowTitle="",
    )
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)

    metadata_result = analyzer.analyze(
        metadata, goal, metadata_context, current_context=lambda: metadata_context
    )
    assert metadata_result.next_content_source == "CHROME_DOM"

    dom, _, dom_context = inputs(
        clientRunId="event-2",
        sequence=2,
        contentSource="CHROME_DOM",
        text="닫기\n공유\n확인",
    )
    dom_result = analyzer.analyze(
        dom, goal, dom_context, current_context=lambda: dom_context
    )
    assert dom_result.relevance_label == "UNCERTAIN"
    assert dom_result.next_content_source is None

    ocr, _, ocr_context = inputs(
        clientRunId="event-3",
        sequence=3,
        contentSource="CHROME_VIEWPORT_OCR",
        text="닫기\n공유\n확인",
        ocr={"confidence": 0.95, "captureStatus": "SUCCESS"},
    )
    ocr_result = analyzer.analyze(
        ocr, goal, ocr_context, current_context=lambda: ocr_context
    )
    assert ocr_result.relevance_label == "UNCERTAIN"
    assert ocr_result.next_content_source is None


def test_desktop_metadata_falls_back_directly_to_desktop_ocr():
    content, goal, context = inputs(
        contentSource="APP_META",
        text="",
        appName="Preview",
        windowTitle="학습 자료",
        pageTitle="",
    )

    result = RelevanceAnalyzer(ScriptedClient()).analyze(
        content, goal, context, current_context=lambda: context
    )

    assert result.relevance_label == "UNCERTAIN"
    assert result.next_content_source == "DESKTOP_APP_OCR"


def test_unrelated_metadata_cannot_finish_without_body_confirmation():
    content, goal, context = inputs(contentSource="TAB_META", text="", pageTitle="노래 가사 앨범")
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == "UNCERTAIN"
    assert result.reason_code == "METADATA_UNRELATED_REQUIRES_BODY"
    assert result.next_content_source == "CHROME_DOM"
    assert client.calls == 0


def test_staged_analysis_requests_only_needed_chrome_content():
    metadata, goal, metadata_context = inputs(
        contentSource="TAB_META", text="", pageTitle="useState 참고",
    )
    dom, _, dom_context = inputs(
        clientRunId="event-2", contentSource="CHROME_DOM",
        text="닫기\n공유\n확인",
    )
    ocr, _, ocr_context = inputs(
        clientRunId="event-3", contentSource="CHROME_VIEWPORT_OCR",
        text="useState를 사용하여 객체 상태 업데이트를 수행하는 상태 변경 함수 예제입니다.",
        ocr={"confidence": 0.95, "captureStatus": "SUCCESS"},
    )
    live = [metadata_context]
    requested = []

    def acquire(source):
        requested.append(source.value)
        content, context = {
            "CHROME_DOM": (dom, dom_context),
            "CHROME_VIEWPORT_OCR": (ocr, ocr_context),
        }[source.value]
        live[0] = context
        return content, context

    result = RelevanceAnalyzer(ScriptedClient()).analyze_staged(
        metadata, goal, metadata_context,
        current_context=lambda: live[0], acquire_next=acquire,
    )
    assert requested == ["CHROME_DOM"]
    assert result.content_source == "CHROME_DOM"
    assert result.relevance_label == "UNCERTAIN"


def test_staged_analysis_stops_after_decisive_metadata():
    metadata, goal, context = inputs(
        contentSource="TAB_META", text="", appName="Chrome",
        windowTitle="React useState 객체 상태 업데이트",
        pageTitle="useState 상태 변경 함수",
    )
    result = RelevanceAnalyzer(ScriptedClient()).analyze_staged(
        metadata, goal, context, current_context=lambda: context,
        acquire_next=lambda source: pytest.fail(f"Unexpected capture: {source}"),
    )
    assert result.relevance_label == "RELATED"
    assert result.next_content_source is None


def test_staged_analysis_requests_desktop_ocr_without_chrome_dom():
    metadata, goal, metadata_context = inputs(
        contentSource="APP_META", text="", appName="Preview", windowTitle="학습 자료",
    )
    ocr, _, ocr_context = inputs(
        clientRunId="event-2", contentSource="DESKTOP_APP_OCR",
        text="useState를 사용하여 객체 상태 업데이트를 수행하는 상태 변경 함수 예제입니다.",
        ocr={"confidence": 0.95, "captureStatus": "SUCCESS"},
    )
    live = [metadata_context]
    requested = []

    def acquire(source):
        requested.append(source.value)
        live[0] = ocr_context
        return ocr, ocr_context

    result = RelevanceAnalyzer(ScriptedClient()).analyze_staged(
        metadata, goal, metadata_context,
        current_context=lambda: live[0], acquire_next=acquire,
    )
    assert requested == ["DESKTOP_APP_OCR"]
    assert result.relevance_label == "RELATED"


def test_staged_analysis_does_not_capture_excluded_screen():
    metadata, goal, context = inputs(
        contentSource="TAB_META", text="", isExcluded=True,
    )
    result = RelevanceAnalyzer(ScriptedClient()).analyze_staged(
        metadata, goal, context, current_context=lambda: context,
        acquire_next=lambda source: pytest.fail(f"Captured excluded screen: {source}"),
    )
    assert result.relevance_label == "EXCLUDED"
    assert result.next_content_source is None


def test_staged_analysis_refuses_changed_target_before_capture():
    metadata, goal, context = inputs(
        contentSource="TAB_META", text="", pageTitle="useState 참고",
    )
    live = [context]

    def after_metadata(source):
        pytest.fail(f"Captured stale target: {source}")

    analyzer = RelevanceAnalyzer(ScriptedClient())
    original_analyze = analyzer.analyze

    def analyze_then_navigate(*args, **kwargs):
        result = original_analyze(*args, **kwargs)
        live[0] = VerifiedContext.parse_safe({
            **context.model_dump(mode="json", by_alias=True),
            "navigationId": "nav-2",
        })
        return result

    analyzer.analyze = analyze_then_navigate
    result = analyzer.analyze_staged(
        metadata, goal, context, current_context=lambda: live[0],
        acquire_next=after_metadata,
    )
    assert result.analysis_status == "DISCARDED"
    assert result.relevance_label == "UNCERTAIN"
    assert result.reason_code == "STALE_EVENT"


def test_staged_analysis_rejects_unrequested_stage():
    metadata, goal, context = inputs(
        contentSource="TAB_META", text="", pageTitle="useState 참고",
    )
    ocr, _, ocr_context = inputs(
        clientRunId="event-2", contentSource="CHROME_VIEWPORT_OCR",
        ocr={"confidence": 0.95, "captureStatus": "SUCCESS"},
    )
    result = RelevanceAnalyzer(ScriptedClient()).analyze_staged(
        metadata, goal, context, current_context=lambda: context,
        acquire_next=lambda source: (ocr, ocr_context),
    )
    assert result.analysis_status == "DISCARDED"
    assert result.relevance_label == "UNCERTAIN"
    assert result.reason_code == "STAGE_MISMATCH"


@pytest.mark.parametrize(
    "changes,expected_reason",
    [
        ({"isActiveTarget": False}, "INACTIVE_TARGET"),
        ({"isSensitive": True}, "PRIVACY_BLOCKED"),
        ({"isExcluded": True}, "EXCLUDED"),
    ],
)
def test_policy_or_target_rejection_never_requests_more_content(changes, expected_reason):
    content, goal, context = inputs(contentSource="TAB_META", text="", **changes)

    result = RelevanceAnalyzer(ScriptedClient()).analyze(
        content, goal, context, current_context=lambda: context
    )

    assert result.reason_code == expected_reason
    assert result.next_content_source is None


def test_failed_metadata_extraction_cannot_create_a_relevance_label():
    content, goal, context = inputs(
        contentSource="TAB_META",
        extractionStatus="FAILED",
        text="",
        pageTitle="useState 객체 상태 업데이트",
    )
    client = ScriptedClient()

    result = RelevanceAnalyzer(client).analyze(
        content, goal, context, current_context=lambda: context
    )

    assert result.analysis_status == "FAILED"
    assert result.relevance_label == "UNCERTAIN"
    assert result.reason_code == "EXTRACTION_FAILED"
    assert client.calls == 0


def test_inactive_target_is_uncertain_and_never_embedded():
    content, goal, context = inputs(isActiveTarget=False)
    client = ScriptedClient()

    result = RelevanceAnalyzer(client).analyze(
        content, goal, context, current_context=lambda: context
    )

    assert result.relevance_label == "UNCERTAIN"
    assert result.reason_code == "INACTIVE_TARGET"
    assert client.calls == 0


def test_older_sequence_is_discarded_before_embedding():
    content, goal, context = inputs()
    latest = VerifiedContext.parse_safe({
        **context.model_dump(mode="json", by_alias=True),
        "clientRunId": "event-2",
        "sequence": 2,
    })
    client = ScriptedClient()

    result = RelevanceAnalyzer(client).analyze(
        content, goal, context, current_context=lambda: latest
    )

    assert result.analysis_status == "DISCARDED"
    assert result.relevance_label == "UNCERTAIN"
    assert result.reason_code == "STALE_EVENT"
    assert client.calls == 0


def test_stale_observation_does_not_flush_reusable_analysis_cache():
    content, goal, context = inputs()
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    analyzer.analyze(content, goal, context, current_context=lambda: context)

    stale_content = ContentInput.parse_safe({
        **content.model_dump(mode="json", by_alias=True),
        "clientRunId": "event-2",
        "sequence": 2,
    })
    latest = VerifiedContext.parse_safe({
        **context.model_dump(mode="json", by_alias=True),
        "clientRunId": "event-3",
        "sequence": 3,
    })
    stale_context = VerifiedContext.parse_safe({
        **context.model_dump(mode="json", by_alias=True),
        "clientRunId": "event-2",
        "sequence": 2,
    })
    assert analyzer.analyze(
        stale_content, goal, stale_context, current_context=lambda: latest
    ).reason_code == "STALE_EVENT"

    latest_content = ContentInput.parse_safe({
        **content.model_dump(mode="json", by_alias=True),
        "clientRunId": "event-3",
        "sequence": 3,
    })
    reused = analyzer.analyze(
        latest_content, goal, latest, current_context=lambda: latest
    )

    assert reused.cache_hit
    assert client.calls == 2


@pytest.mark.parametrize("capture", ["BLACK_SCREEN", "EMPTY_CAPTURE", "OCR_FAILED", "DUPLICATE", "LOW_CONFIDENCE"])
def test_capture_failure_detail(capture):
    content, goal, context = inputs(contentSource="DESKTOP_APP_OCR", ocr={"confidence": 0.99, "captureStatus": capture})
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.reason_code == capture and result.relevance_label == "UNCERTAIN"
    assert client.calls == 0


@pytest.mark.parametrize("update", [{"goal_version": 2}, {"navigation_id": "nav-2"}, {"client_run_id": "event-2"}, {"sequence": 2}, {"run_id": "run-2"}, {"goal_id": "goal-2"}, {"session_id": "session-2"}, {"session_status": "PAUSED"}, {"resume_required": True}, {"policy_version": "policy-2"}])
def test_current_state_mismatch_skips_model(update):
    content, goal, context = inputs()
    data = context.model_dump(mode="json", by_alias=True)
    aliases = {name: field.alias for name, field in VerifiedContext.model_fields.items()}
    data.update({aliases[key]: value for key, value in update.items()})
    live = VerifiedContext.parse_safe(data)
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: live)
    assert result.analysis_status == "DISCARDED" and client.calls == 0


@pytest.mark.parametrize("change_after,label", [(1, "UNCERTAIN"), (2, "UNCERTAIN"), (2, "EXCLUDED")])
def test_state_changes_during_embeddings_are_discarded(change_after, label):
    content, goal, context = inputs()
    live = [context]
    client = ScriptedClient()
    def change(calls):
        if calls == change_after:
            data = context.model_dump(mode="json", by_alias=True)
            data.update({"exclusionMode": "ANALYSIS_ONLY"} if label == "EXCLUDED" else {"navigationId": "nav-2"})
            live[0] = VerifiedContext.parse_safe(data)
    client.after_embed = change
    analyzer = RelevanceAnalyzer(client)
    result = analyzer.analyze(content, goal, context, current_context=lambda: live[0])
    assert result.relevance_label == label and not result.evidence_ids
    assert client.calls == change_after
    assert not len(analyzer._events) and not len(analyzer._results) and not len(analyzer._vectors)


def test_duplicate_event_replay_and_content_conflict():
    content, goal, context = inputs()
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    first = analyzer.analyze(content, goal, context, current_context=lambda: context)
    assert analyzer.analyze(content, goal, context, current_context=lambda: context).cache_hit
    changed, _, _ = inputs(text="useState 객체 상태 업데이트에서 다른 속성을 변경하는 예제입니다.")
    conflict = analyzer.analyze(changed, goal, context, current_context=lambda: context)
    assert conflict.reason_code == "IDEMPOTENCY_CONFLICT"
    assert first.relevance_label == "RELATED" and client.calls == 2


def test_parallel_duplicate_events_are_single_flight():
    content, goal, context = inputs()
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda _: analyzer.analyze(content, goal, context, current_context=lambda: context), range(4)))
    assert all(result.relevance_label == "RELATED" for result in results)
    assert client.calls == 2


@pytest.mark.parametrize("change", ["userId", "goalId", "goalVersion", "policyVersion", "extractorVersion", "runId"])
def test_cache_isolation(change):
    content, goal, context = inputs()
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    analyzer.analyze(content, goal, context, current_context=lambda: context)
    data = context.model_dump(mode="json", by_alias=True)
    data[change] = 2 if change == "goalVersion" else data[change] + "-2"
    data["clientRunId"] = "event-2"
    data["sequence"] = 2
    new_context = VerifiedContext.parse_safe(data)
    new_content = ContentInput.parse_safe({**content.model_dump(mode="json", by_alias=True), **{key: data[key] for key in ("goalId", "goalVersion", "runId", "clientRunId", "sequence")}})
    new_goal = ConfirmedGoal.parse_safe({**goal.model_dump(mode="json", by_alias=True), "goalId": data["goalId"], "goalVersion": data["goalVersion"]})
    result = analyzer.analyze(new_content, new_goal, new_context, current_context=lambda: new_context)
    assert not result.cache_hit and result.relevance_label == "RELATED"
    assert client.calls == 4


@pytest.mark.parametrize("bad_vector", [[0]*6, [float("nan")]*6, [1, 0], [False]*6])
def test_injected_client_vectors_are_revalidated(bad_vector):
    content, goal, context = inputs()
    client = ScriptedClient()
    client.invalid_vector = bad_vector
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.analysis_status == "FAILED" and result.relevance_label == "UNCERTAIN"


def test_error_never_echoes_provider_secret_or_uses_previous_result():
    content, goal, context = inputs()
    client = ScriptedClient()
    client.error = EmbeddingError("provider-secret-must-not-appear")
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.reason_code == "EMBEDDING_FAILED" and result.relevance_label == "UNCERTAIN"
    assert "provider-secret" not in repr(result)


@pytest.mark.parametrize("changes", [{"goalVersion": True}, {"clientRunId": "secret with spaces"}, {"sequence": True}, {"urlHost": "https://example.com/?secret=1"}, {"unknown": "private"}, {"observedAt": "2026-10-07T01:00:00"}, {"ocr": {"confidence": float("nan"), "captureStatus": "SUCCESS"}}, {"isSensitive": "false"}])
def test_safe_schema_errors(changes):
    content, _, _ = inputs()
    with pytest.raises(SafeInputError) as error:
        ContentInput.parse_safe({**content.model_dump(mode="json", by_alias=True), **changes})
    assert str(error.value) == "INVALID_INPUT"
    assert error.value.__context__ is None


def test_fenced_code_preserves_indentation_and_short_error_is_valid():
    content, _, context = inputs(text="공개 설명\n\n```python\nif token:\n    verify(token)\n```", contentType="DOCUMENT")
    prepared = validate_and_preprocess(adapt_content(content, context), preprocessing_context(context))
    assert "    verify(token)" in prepared.passages[-1].text
    short, _, _ = inputs(text="403 Forbidden", contentType="ERROR")
    assert validate_and_preprocess(adapt_content(short, context), preprocessing_context(context)).should_analyze


def test_ttl_lru_and_clear():
    now = [0.0]
    cache = TTLCache(2, 10, clock=lambda: now[0])
    cache.put("a", 1); cache.put("b", 2)
    assert cache.get("a") == 1
    cache.put("c", 3)
    assert cache.get("b") is None
    now[0] = 11
    assert len(cache) == 0
    cache.put("d", 4)
    cache.clear()
    assert len(cache) == 0


@pytest.mark.parametrize("confirmed", [False, 1, "true"])
def test_goal_confirmation_is_strict(confirmed):
    _, goal, _ = inputs()
    with pytest.raises(SafeInputError):
        ConfirmedGoal.parse_safe({**goal.model_dump(mode="json", by_alias=True), "confirmed": confirmed})


def test_cached_result_is_rechecked_before_return():
    content, goal, context = inputs()
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    analyzer.analyze(content, goal, context, current_context=lambda: context)
    checks = []
    def live():
        checks.append(1)
        if len(checks) == 1:
            return context
        return VerifiedContext.parse_safe({**context.model_dump(mode="json", by_alias=True), "stateVersion": 2})
    result = analyzer.analyze(content, goal, context, current_context=live)
    assert result.analysis_status == "DISCARDED" and not result.evidence_ids
    assert client.calls == 2 and len(analyzer._events) == 1


def test_invalid_or_unavailable_current_context_is_safe():
    content, goal, context = inputs()
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    result = analyzer.analyze(content, goal, context, current_context=lambda: None)
    assert result.reason_code == "CONTEXT_UNAVAILABLE" and client.calls == 0


def test_new_observation_uses_content_cache_and_rebinds_event():
    content, goal, context = inputs()
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    first = analyzer.analyze(content, goal, context, current_context=lambda: context)
    content2 = ContentInput.parse_safe({**content.model_dump(mode="json", by_alias=True), "clientRunId": "event-2", "sequence": 2, "navigationId": "nav-2"})
    context2 = VerifiedContext.parse_safe({**context.model_dump(mode="json", by_alias=True), "clientRunId": "event-2", "sequence": 2, "navigationId": "nav-2"})
    second = analyzer.analyze(content2, goal, context2, current_context=lambda: context2)
    assert first.content_hash == second.content_hash and second.cache_hit
    assert second.client_run_id == "event-2" and second.sequence == 2 and second.navigation_id == "nav-2"
    assert client.calls == 2


def test_no_goal_graph_does_not_guess_from_page():
    content, goal, context = inputs()
    goal2 = ConfirmedGoal.parse_safe({**goal.model_dump(mode="json", by_alias=True), "text": "수학 학습"})
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal2, context, current_context=lambda: context)
    assert result.reason_code == "GOAL_GRAPH_UNAVAILABLE" and client.calls == 0


def test_truncated_major_body_is_not_classified_from_first_chunks():
    content, goal, context = inputs(text="\n\n".join(f"useState 객체 상태 업데이트 고유 예제 {i}에 대한 설명입니다." for i in range(10)))
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.reason_code == "INCOMPLETE_REPRESENTATION" and client.calls == 0


@pytest.mark.parametrize("text", ["synthetic&amp;#64;example.invalid", "synthetic\u200b@example.invalid"])
def test_encoded_or_zero_width_sensitive_text_is_not_embedded(text):
    content, goal, context = inputs(text=text)
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == "PRIVACY_BLOCKED" and client.calls == 0


def test_title_heading_and_body_reach_embedding_and_evidence():
    content, goal, context = inputs(
        pageTitle="useState 상태 업데이트 자료", appName="Chrome", urlHost="react.dev",
        text="## useState 객체 상태\n\nuseState를 사용하여 객체 상태 업데이트를 수행하는 상태 변경 함수 예제입니다.")
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    result = analyzer.analyze(content, goal, context, current_context=lambda: context)
    assert client.requests[1] == (content.page_title, "## useState 객체 상태", content.text.split("\n\n")[1])
    assert result.relevance_label == "RELATED"
    assert result.evidence_ids == ("passage-001", "passage-002", "passage-003")
    assert all("react.dev" not in text and "Chrome" not in text for batch in client.requests for text in batch)
    for cache in (analyzer._events, analyzer._results, analyzer._vectors):
        assert content.page_title not in repr(cache._items)


def test_unrelated_title_conflicts_with_related_body_through_pipeline():
    class TopicClient(ScriptedClient):
        def embed(self, texts):
            vectors = super().embed(texts)
            if texts[0].startswith("Instruct:"):
                return vectors
            return tuple((0, 0, 0, 0, 1, 0) if text == "노래 가사 앨범" else vector
                         for text, vector in zip(texts, vectors))

    content, goal, context = inputs(pageTitle="노래 가사 앨범")
    result = RelevanceAnalyzer(TopicClient()).analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == "UNCERTAIN" and result.reason_code == "CONFLICTING_EVIDENCE"


@pytest.mark.parametrize("field,initial,changed", [
    ("pageTitle", "useState 객체 상태", "useState 상태 업데이트"),
    ("appName", "Chrome", "Preview"),
    ("urlHost", "react.dev", "example.org"),
])
def test_changed_metadata_recomputes_decision_but_reuses_goal_vectors(field, initial, changed):
    content, goal, context = inputs(**{field: initial})
    client = ScriptedClient()
    analyzer = RelevanceAnalyzer(client)
    first = analyzer.analyze(content, goal, context, current_context=lambda: context)
    content2 = ContentInput.parse_safe({**content.model_dump(mode="json", by_alias=True), field: changed, "clientRunId": "event-2", "sequence": 2})
    context2 = VerifiedContext.parse_safe({**context.model_dump(mode="json", by_alias=True), "clientRunId": "event-2", "sequence": 2})
    second = analyzer.analyze(content2, goal, context2, current_context=lambda: context2)
    assert first.relevance_label == second.relevance_label == "RELATED"
    assert not second.cache_hit and client.calls == 3
    assert (first.content_hash != second.content_hash) is (field == "pageTitle")


@pytest.mark.parametrize("changes", [
    {"pageTitle": "synthetic@example.invalid"},
    {"appName": "synthetic@example.invalid"},
    {"text": "# synthetic@example.invalid\n\n공개 본문 설명입니다."},
    {"text": '```python\naddress = "synthetic&amp;#64;example.invalid"\n```'},
])
def test_sensitive_context_or_code_is_rejected_before_embedding(changes):
    content, goal, context = inputs(**changes)
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == "PRIVACY_BLOCKED" and client.calls == 0


def test_heading_only_input_has_no_body_evidence():
    content, goal, context = inputs(pageTitle="useState 자료", text="# useState 객체 상태\n\n## 상태 업데이트")
    client = ScriptedClient()
    result = RelevanceAnalyzer(client).analyze(content, goal, context, current_context=lambda: context)
    assert result.relevance_label == "UNCERTAIN" and result.reason_code == "INSUFFICIENT_BODY"
    assert client.calls == 0
