# 학습 메신저 보호와 이탈 집계 결과

- [이슈 #383](https://github.com/What-a-move/FocusOn/issues/383), `feat/383-server-learning-messenger`
- [기획서](learning-messenger-analysis-PLAN.md) · [독립 리뷰](learning-messenger-analysis-code-review.md) · [QA](learning-messenger-analysis-QA.md)

메신저 식별자만 받은 보호 관측은 AI 호출 없이 공개 `PRIVACY_BLOCKED / SKIPPED`로 처리한다. 분석 행에는 현재 집중 설정과 비교한 분류 `LEARNING` 또는 `OTHER`만 남긴다. 검증된 전경의 기존 정책에서 학습용은 이탈 구간을 끊고, 미등록은 이탈 시간으로 계산한다.

Desktop·Extension·shared-types 담당자는 메신저를 로컬에서 감지한 뒤 본문 수집 전에 차단하고 보호 신호를 보내는 경로를 연결한다. 메신저 ID와 내부 분류를 출력에 추가할 필요는 없다. 실앱 검증은 해당 연동 뒤 수행한다.
