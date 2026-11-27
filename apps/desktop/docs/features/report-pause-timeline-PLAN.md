# Desktop 집중 리포트 일시정지 구간 기획

## 기본 정보

- 담당 영역과 대상 앱: Frontend, Desktop
- 우선순위: MVP 기본
- 관련 이슈: [#370](https://github.com/What-a-move/FocusOn/issues/370)
- 작업 브랜치: `codex/370-report-pause-timeline`
- 관련 계약: [서버 #317](https://github.com/What-a-move/FocusOn/issues/317), [서버 PR #356](https://github.com/What-a-move/FocusOn/pull/356)
- 디자인: `FocusOn-design/session-report.html`의 집중 시간 축과 표시 방식

## 기능 목적과 사용자 흐름

집중 리포트에서 언제 멈추고 다시 시작했는지 확인한다. 활동 유형별 합계를 보여 주는 기존 흐름 막대 아래에 별도의 집중 시간 축을 둔다. 이 축에서는 일시정지 시간을 빼고 멈춘 지점을 표시하며, 목록에는 실제 시작·재개 시각을 제공한다. 재개 전에 세션이 끝난 기록은 종료 시각까지 표시한다.

## 데이터 및 IPC

Renderer에서 세션 ID로 `report:session-pauses:get`을 호출한다. Main이 인증된 `GET /api/v1/sessions/{sessionId}/pauses`를 요청하고 시각 순서와 응답 형식을 검증한다. 로컬 저장이나 신규 권한은 필요 없다.

## 예외와 완료 조건

- 빈 목록은 표시를 생략한다. 멈춤 기록 조회에 실패하면 기록과 학습 정리를 그대로 보여 주고 일시정지 조회만 다시 시도할 수 있게 한다.
- 분석 자료가 없는 회차에서는 시각 목록을 계속 표시한다.
- 멈춤 표시는 텍스트 목록으로도 읽을 수 있다.
- 실제 서버 연동 또는 화면 QA는 서버 PR 반영 후 검증한다.
