# Desktop 집중 리포트 딴짓 종류 비율 기획

## 기본 정보

- 담당 영역 · 대상: Frontend, Desktop
- 작성일: 2026-11-28
- 우선순위: MVP 기본
- 관련 이슈: [#387](https://github.com/What-a-move/FocusOn/issues/387)
- 브랜치: `codex/387-desktop-off-task-category`
- 선행 작업: [서버 #381](https://github.com/What-a-move/FocusOn/issues/381), [PR #382](https://github.com/What-a-move/FocusOn/pull/382)
- 기준: [리포트 설계서 B](https://app.notion.com/p/3de0b6e5e64c808683b0e9a4a79fccca), [개인정보 정책](https://app.notion.com/p/3de0b6e5e64c80c0b63cf0514a06f36a), 디자인 `session-report.html` · `onboarding.html`

## 사용자 흐름

1. 세션 종료 후 집중 리포트에서 딴짓 종류별 막대와 비율을 확인한다. 목록의 고정 색·아이콘·이름으로 8가지 종류를 구분한다.
2. 분류된 기록이 없는 회차는 빈 상태를 보여 준다. 조회에 실패하면 이 영역에서 다시 시도한다.
3. 온보딩의 자세히나 설정의 전문 보기에서 종류 저장의 목적을 확인한다.

## Desktop 동작과 데이터

- Main에서 `GET /api/v1/sessions/{id}/analysis-runs`를 조회한다. `UNRELATED`이며 허용된 카테고리가 있는 기록 건수를 모아, 분류된 기록 전체 대비 비율을 계산한다.
- 이전 회차의 null·누락 값은 분모에서 제외한다. 서버가 명시적으로 분류한 `OTHER`는 기타로 표시한다.
- 화면에는 종류와 비율만 보내고 주소·제목·앱 이름을 표시하거나 새로 저장하지 않는다.
- 동의 상세에 정책의 딴짓 종류 저장 문구를 추가한다. 현재 서버 동의 버전을 사용한다.

## 완료 조건

- [x] 분류 비율과 빈 상태·오류 재시도 확인
- [x] 온보딩과 설정의 공용 동의 상세 문구 확인
- [x] 테스트, 결과 리포트, 컨텍스트 갱신
