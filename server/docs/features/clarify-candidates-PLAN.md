# Backend 기능 기획서 — 목표 다듬기 NEEDS_SELECTION 폐기와 goalCandidates

## 기본 정보

- 기능명: 목표 후보를 CLEAR로 돌려주기
- 기능 ID: clarify-candidates
- 작성자: 박진욱
- 작성일: 2026-11-23
- 우선순위: MVP - 기본
- 관련 Issue: #315
- 예정 Branch: `feat/315-server-clarify-candidates` (#314 위에 쌓음)
- 관련 Client·AI 문서: 재설계 1차 정리(DR-024~030)

## 기능 목적

- 해결할 문제: 여러 목표가 섞인 입력에 `NEEDS_SELECTION` + 고르기 질문을 돌려줘 대화가 한 번 더 필요했다.
- Backend가 담당할 역할: 후보를 `CLEAR` + `goalCandidates`로 주고, 고른 후보를 바로 AI 확인 목표로 저장하게 한다.

## API 정의

- `POST /goals/clarify`: `NEEDS_SELECTION` 폐기. AI가 후보를 주면 `CLEAR`, `goalCandidates` 2~3개, `question: null`.
- `POST /goals`: `AI_CONFIRMED`는 `normalizedGoalText` 또는 `goalCandidates` 중 하나만 허용(그 밖 400 `VALIDATION_ERROR`).

## 데이터 설계

- 변경 없음. 저장된 대화 상태(최대 24시간)의 `NEEDS_SELECTION`은 `CLEAR`로 읽는다.

## 처리 흐름

1. AI `NEEDS_SELECTION`(dev AI는 아직 이 값을 줌) → 공개 `CLEAR`, 후보 2개 미만이면 AI 응답 오류로 처리.
2. 후보를 고른 저장은 다듬기 세분화를 붙이지 않고(#314) 그 문구로 따로 세분화한다.
3. 후보를 고른 뒤 다시 다듬기 요청하는 기존 흐름도 그대로 동작한다.

## 범위 밖

- AI 쪽 상태 이름 변경.

## 완료 조건

- [x] 후보가 있는 응답이 `CLEAR` + `goalCandidates`로 나온다.
- [x] 잘못된 후보(2개 미만·빈 제목)는 AI 응답 오류로 처리한다.
- [x] 테스트·문서 갱신. Desktop 영향은 아래에 적는다.
