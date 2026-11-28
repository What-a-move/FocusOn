# 학습 메신저 보호와 이탈 집계 연결

## 기본 정보

- 기능 ID `learning-messenger-analysis`
- 작성일 2026-11-28, 우선순위 MVP 기본
- 선행 작업 [#373 집중 설정](https://github.com/What-a-move/FocusOn/issues/373), [#381 딴짓 종류](https://github.com/What-a-move/FocusOn/issues/381)
- 기준 `origin/dev`의 `bfe39b0`
- [이슈 #383](https://github.com/What-a-move/FocusOn/issues/383), `feat/383-server-learning-messenger`

## 기능 목적

Backend가 보호된 메신저 관측을 사용자의 집중 설정과 연결한다. 학습 메신저는 이탈 구간을 끊고 이탈 비율의 분자와 분모에서 제외한다. 미등록 메신저는 관측 시각을 기준으로 이탈 시간을 계산한다.

## API 정의

Notion의 [화면 분석 6.2](https://app.notion.com/p/3de0b6e5e64c80c1a9e3d69db89f2ab8), [DR-025·DR-026](https://app.notion.com/p/DECISION_RECORD-3de0b6e5e64c80bda58dffad654f31d3), [분석 POST 명세](https://app.notion.com/p/POST-api-v1-sessions-sessionId-analysis-runs-58cf7aade6614fc9a00e248affb052f9)의 재설계 조항을 대조했다. DR-026은 ACCEPTED이고 API 행은 VERIFIED다. 기존 요청 예시에는 두 보호 상태만 적혀 있으나 같은 API 행의 재설계 비고에 `MESSENGER` 입력이 명시돼 있다.

- 기존 `POST /api/v1/sessions/{sessionId}/analysis-runs`에서 인증·기기·관측 계약에 더해 `guardStatus: MESSENGER`와 `metadata: {"messengerId":"DISCORD"}`를 받는다. 값은 `DISCORD`, `SLACK`, `KAKAOTALK`, `TEAMS`, `OTHER` 중 하나다.
- 잘못된 ID, ID 누락, 메신저 보호 요청에 실린 본문·제목·앱 이름·호스트는 기존 400 `VALIDATION_ERROR`로 처리한다. 다른 보호 상태에서 메신저 ID를 사용할 수 없다.
- 정상 입력은 기존 공개 응답의 `PRIVACY_BLOCKED / SKIPPED`를 사용한다. POST와 이력 GET의 응답 스키마는 기존 필드를 쓴다.

## 데이터 설계와 처리 흐름

1. 기존 사용자 잠금 아래에서 인증·동의, 소유 세션, 관측 순서와 현재 전경을 확인한다. 동의가 없는 요청은 기존 보호 흐름을 적용한다.
2. 새 메신저 관측마다 `learningMessengers`의 현재 설정과 ID를 비교한다. 설정이 없거나 목록이 비어 있으면 미등록으로 계산한다. AI 호출과 학습 정리 근거 생성 없이 보호 판정을 확정한다.
3. [분석 실행 ERD](https://app.notion.com/p/analysis_runs-3de0b6e5e64c8190bbdff71f9739615b)에 따라 `analysis_runs.messenger_scope`에 `LEARNING` 또는 `OTHER`만 저장한다. 기존 행은 null이다. 메신저 ID, 제목, 원문, 앱 이름, 호스트의 저장·로그·응답 경로를 만들지 않는다.
4. 기존 정책의 관측 바인딩을 통과한 실행만 이탈 집계에 반영한다. 학습용은 현재 이탈 구간을 닫는다. 미등록 관측은 내부 정책 입력에서만 이탈로 취급한다. 공개 판정, 기존 제목 기반 이탈 보류, 이력의 `offTaskCategory` 조건은 기존 계약을 적용한다.

## 연동 범위

- Desktop과 Extension은 방어 규칙에서 메신저를 확인하고 원문 없이 보호 신호를 보낸다. 현재 shared-types와 두 클라이언트가 지원하는 보호 상태는 `EXCLUDED`와 `PRIVACY_BLOCKED`다. 새 요청 전송과 로컬 수집 차단의 실제 앱 검증은 각 담당자에게 인계한다.
- Backend 변경은 분석과 개입 경로, 기존 분석 테이블에 한정한다. 집중 설정의 저장·조회와 학습 정리 Worker는 기존 경로를 사용한다.

## 완료 조건

- [ ] 등록·미등록·설정 없음에서 동일한 공개 보호 응답과 서로 다른 내부 집계를 검증한다.
- [ ] 본문 혼입, 허용 밖·누락된 ID, 동의 철회, 오래된 관측 및 기존 보호 요청을 검증한다.
- [ ] AI 미호출과 원문·식별자 비노출, PostgreSQL 제약과 기존 행 호환을 확인한다.
- [ ] 백엔드 빌드·Checkstyle 및 격리 테스트와 연동 인계 범위를 기록한다.
