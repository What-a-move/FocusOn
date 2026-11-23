# 개발 오류 보고서 — 목표 후보 CLEAR (#315)

## 1. 폐기 전에 저장된 대화 상태가 새 규칙과 어긋남

- 발견: PR 전 결함 점검(2026-11-23).
- 증상: 배포 전 24시간 안에 만든 `NEEDS_SELECTION` 대화는 ① 후보를 AI 확인 목표로 저장할 수 없었고(저장 검사가 원본 JSON의 `CLEAR`만 허용), ② 같은 요청을 다시 보내면 `CLEAR`인데 고르기 질문이 붙은 응답이 나왔다. 또 `normalizedGoalText`가 JSON null일 때 문자열 "null"과 비교될 수 있었다.
- 조치: 저장 검사는 `CLEAR`면 확정 문구 또는 후보, 옛 `NEEDS_SELECTION`이면 후보만 허용하고, 문자열 값만 비교한다. `ClarifyGoalResponse`는 후보가 있는 `CLEAR`의 질문을 항상 비운다.
- 검증: `GoalProfileExpandTests.legacySelectionStateReplaysAsClearAndAllowsOnlyCandidates`.
