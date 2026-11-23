# Backend 기능 기획서 — 개입 반응 NOT_OFF_TASK(「이것도 공부야」)

## 기본 정보

- 기능명: 오판 신고 반응 NOT_OFF_TASK
- 기능 ID: not-off-task
- 작성자: 박진욱
- 작성일: 2026-11-21
- 우선순위: MVP - 핵심
- 관련 Issue: #300 (선행 #297 근거 판정 id)
- 예정 Branch: `feat/300-server-not-off-task` (#297 위에 쌓음)
- 관련 Client·AI 문서: 재설계 B-4, 「딴짓 판단 (AI 판정)」 6장 ③

## 기능 목적

- 해결할 문제: 오알림에 「돌아가기」를 눌러도 기록은 "확인"과 같아 오판인지 알 수 없다.
- Backend가 담당할 역할: 「이것도 공부야」를 별도 반응으로 받고 근거 판정 id와 잇는다. 재보정 정답 데이터의 원천이 된다.

## API 정의

- `POST /api/v1/interventions/{interventionId}/acknowledgements`
- Request: `{ "action": "NOT_OFF_TASK" }` (기존 `ACKNOWLEDGED`·`DISMISSED` 유지)
- Success 200: `{ "data": { "status": "NOT_OFF_TASK", "actedAt": "..." } }`
- Failure: `BREAK_SUGGESTION`에 보내면 400 `VALIDATION_ERROR`. 그 밖의 인증·소유·만료 규칙은 기존과 같다.
- 계약: Notion API 명세에 없어 서버 제안으로 `docs/API_CONTRACT.md`에 적었다. 문서 오너 확인 필요.

## 데이터 설계

- V26: `intervention_events` 상태 CHECK에 `NOT_OFF_TASK` 추가, `ck_intervention_ack`에서 `NOT_OFF_TASK`도 `acted_at` 필수.
- 근거는 V24 `analysis_run_id`. 제목·URL·본문은 저장하지 않는다.

## 처리 흐름

1. 기존 반응과 같은 인증·소유·만료 확인.
2. `NOT_OFF_TASK`인데 알림 종류가 `OFF_TASK`가 아니면 400.
3. PENDING이면 상태를 `NOT_OFF_TASK`, `actedAt`을 기록한다. 이후 반응은 첫 결정을 바꾸지 않는다.

## 범위 밖

- 오판 신고가 현재 딴짓 구간·알림 간격에 미치는 영향(즉시 구간 종료 등): 팀 미정.
- 오판 집계 조회 API.
- Desktop 「이것도 공부야」 버튼 연결.

## 완료 조건

- [x] 새 반응이 저장되고 근거 판정 id와 이어진다.
- [x] 휴식 제안에는 쓸 수 없다.
- [x] 기존 반응 동작은 그대로다.
- [x] 테스트·문서 갱신.
