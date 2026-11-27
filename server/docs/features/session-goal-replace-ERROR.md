# 개발 오류 보고서 — 세션 중 할 일 교체 (#320)

## 1. 교체한 적 없는 같은 버전 요청을 재시도로 보고 200

- 발견: CodeRabbit 리뷰(PR #346, 2026-11-26).
- 증상: 할 일 `[11,12]`, 버전 1에서 `{goalId:"12", replaceGoalId:"13", goalVersion:1}`처럼 교체 대상은 없고 새 할 일은 이미 있는 요청이 400이 아니라 200(이미 반영된 재시도)으로 처리됐다.
- 원인: 재시도 판정이 `goalVersion <= 현재 버전`이었다. 교체에 성공하면 버전이 오르므로 진짜 재시도는 더 낮은 버전을 들고 온다.
- 조치: 재시도 판정을 `goalVersion < 현재 버전`으로 바꿨다. 같은 버전이면 일반 검증(교체 대상 없음 → 400)으로 간다.
- 검증: `SessionApiTests.replaceSessionGoalOverwritesSlotAndReleasesOldGoal`에 해당 사례 추가.
