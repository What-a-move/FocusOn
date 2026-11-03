# 목표 AI 명확성 확인·저장 연동 계획

- Issue: #99 / Branch: `feat/99-goal-ai-clarification` / 기준 dev: `5fa2053`
- 공개 정본: Notion `POST /api/v1/goals/clarify`, 목표 저장, AI 분석 계약 및 ERD.
- AI 구현: `feat/12-ai-goal-assistance`의 `56c6ac219e78`, 내부 `POST /internal/v1/goals/clarify`. 제공된 비교 구간은 AI 구현 자체보다 충돌 해소 문서 위주다.
- 사용자 인증, 목표·대화 ID 소유권, 질문 최대 2회 및 이력 위변조를 서버에서 검증한다. AI 장애 시 수동 확정 경로를 보존한다.
- 내부 AI 6종 상태와 공개 4종 decision의 대응은 사용자 확인 중이다. 내부 질문 전용 결과는 추천 목록을 포함하지 않을 수 있지만 공개 명세는 AMBIGUOUS 추천 2~3개를 기재한다. 결과를 임의로 발명하지 않는다.
- 외부 호출 재시도는 1초·3초 후 최대 2회이며, AI 작업 중 DB 잠금을 오래 잡지 않도록 이력 확정과 동시성 경계를 설계한다.
- AI 실제 배포·서비스 간 인증은 AI 팀 REPORT에서 미완료로 기재되어 있어 로컬 검증과 실제 서비스 연동을 구분한다.
- 대화 이력의 보존 기간, 수동 확정과 AI 확정 시 목표 연결은 Notion ERD·팀 계약 확인 전 확정하지 않는다.
- 사용자 Swagger API 테스트 이후 동시성 테스트 및 문서 결과 반영; PR·푸시는 사용자 지시에 따른다.
