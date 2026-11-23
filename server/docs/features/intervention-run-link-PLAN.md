# Backend 기능 기획서 — 개입 이벤트의 근거 판정 id

## 기본 정보

- 기능명: 개입 이벤트에 근거 판정 기록 id 저장
- 기능 ID: intervention-run-link
- 작성자: 박진욱
- 작성일: 2026-11-21
- 우선순위: MVP - 핵심
- 관련 Issue: #297 (후속 #300 NOT_OFF_TASK)
- 예정 Branch: `feat/297-server-intervention-run-link` (#296 위에 쌓음, #296 먼저 머지)
- 관련 Client·AI 문서: 재설계 B-4 「이것도 공부야」, 「딴짓 판단 (AI 판정)」 6장 ③

## 기능 목적

- 해결할 문제: 오판 신고(NOT_OFF_TASK)는 판정 기록 id와만 잇기로 했지만, 지금 이벤트의 `cause`는 정책 상태 revision이라 어떤 판정 때문에 알림이 생겼는지 알 수 없다.
- Backend가 담당할 역할: 알림 생성 시 근거 판정 id를 이벤트에 저장한다.

## API 정의

- 공개 API 변경 없음.

## 데이터 설계

- V24: `intervention_events.analysis_run_id BIGINT NULL REFERENCES analysis_runs(id)`. 기존 이벤트는 NULL.
- 타임라인 snapshot에 `offTaskRun`(열린 딴짓 구간을 마지막으로 이어 준 무관 판정 id)을 추가한다. 이전 snapshot에는 없어 null로 읽힌다.
- 저장하지 않을 민감 데이터: 제목·URL·본문.

## 처리 흐름

1. 무관 판정이 구간을 열거나 이으면 `offTaskRun`을 그 판정 id로 바꾼다.
2. 판단 보류로 멈춘 동안(v2)에는 유지하고, 구간이 끝나면 지운다.
3. 알림 후보를 만들 때 `offTaskRun`을 이벤트의 `analysis_run_id`로 저장한다.

`latestRun`(마지막 판정)을 쓰지 않는 이유: v2에서 판단 보류로 멈춘 뒤 cooldown이 풀린 heartbeat에서 알림이 생기면 마지막 판정이 보류 판정이 된다.

## 완료 조건

- [x] 새 OFF_TASK 이벤트에 근거 무관 판정 id가 저장된다.
- [x] 판단 보류로 멈춰도 근거가 보류 판정으로 바뀌지 않는다.
- [x] 이전 snapshot을 그대로 읽는다. 공개 API 응답은 그대로다.
- [x] 테스트·문서 갱신.
