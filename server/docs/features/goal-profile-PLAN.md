# Backend 기능 기획서 — 목표 세분화(GoalProfile) 저장과 세션 고정

## 기본 정보

- 기능명: 목표 세분화 저장과 세션 시작 시 고정
- 기능 ID: goal-profile
- 작성자: 박진욱
- 작성일: 2026-11-21
- 우선순위: MVP - 핵심
- 관련 Issue: #298
- 예정 Branch: `feat/298-server-goal-profile`
- 관련 Client·AI 문서: 재설계 A-1(목표 자동 확장, 채택), 「재설계 수정 사안」 목표 세분화 논의안, `AI/src/api/schemas.py` `GoalProfileDraft`

## 기능 목적

- 해결할 문제: Goal AI는 CLEAR 목표에 `goalProfileDraft`(핵심 주제·보조 주제·예상 활동)를 돌려주지만, 서버 공개 응답에 칸이 없어 버려진다. 목표·세션에도 저장할 곳이 없다.
- Backend가 담당할 역할: 세분화를 검증해 목표마다 저장하고, 세션 시작 때 고정한다. 판정 AI 전달은 범위 밖이다.

## API 정의

- 공개 API 변경 없음. `POST /goals/clarify` 응답에는 세분화를 싣지 않는다(1차는 사용자에게 숨김).

## 데이터 설계

- V25: `goals.profile_json JSONB NULL`, `learning_sessions.goal_profile_snapshot_json JSONB NULL`. 기존 행은 NULL.
- 저장 필드: `mainTopic`, `purpose`(AI `GoalPurpose` 7개 값), `coreTopics`·`supportingTopics`·`expectedActivities`(각 최대 5개, 항목 100자 이하, 대소문자 무시 중복 금지).
- 저장하지 않을 데이터: 목표 다듬기 질문·답변 원문, AI 원본 출력(`originalText`·`interpretedGoal` 포함), 화면 내용.
- 저장 구조를 「목표마다 profile, 세션에는 시작 당시 사본」으로 잡아 논의안 ①·②·③ 어느 쪽으로 가도 다시 마이그레이션하지 않게 한다.

## 처리 흐름

1. `POST /goals/clarify`: AI 응답이 CLEAR면 `goalProfileDraft`를 검증해 서버 대화 상태에만 보관한다. 형식이 어긋나면 버리고 응답은 그대로 준다.
2. `POST /goals`(`AI_CONFIRMED`): 확정 응답과 같은 대화 상태에 있는 세분화를 목표에 저장한다. 직접 입력(`MANUAL_CONFIRMED`)은 저장하지 않는다.
3. `PATCH /goals/{id}`: 문구가 바뀌면 이전 세분화를 지운다.
4. 세션 시작: 목표의 세분화를 세션에 복사해 고정한다.

## 범위 밖

- 판정 요청에 세분화 전달(API 계약 + 판정 AI 항목별 비교 필요).
- 사용자 노출(논의안 ②), 작업 바구니(③), 직접 입력 목표의 세분화 생성: 팀 미정.

## 완료 조건

- [x] CLEAR 목표 확정 시 세분화가 저장되고 세션 시작 때 고정된다.
- [x] 형식이 어긋난 세분화는 저장하지 않고 목표 생성은 성공한다.
- [x] 공개 응답에 세분화가 나오지 않는다.
- [x] 문구 수정 시 세분화가 지워진다.
- [x] 테스트·문서 갱신.
