# Backend 기능 기획서 — 세션 데이터 종료 후 1년 보관 정리

## 기본 정보

- 기능명: 세션 데이터 보관 기간 정리
- 작성일: 2026-11-28
- 우선순위: MVP - 기본
- 관련 Issue: #389
- 예정 Branch: `feat/389-session-retention`

## 기능 목적

- 해결할 문제: `docs/DATA_PRIVACY.md`는 세션 이력·요약·일간 리포트·복습 노트·일시정지 구간·회고를 "세션 종료 후 1년"만 보관한다고 정했지만, 정상 계정의 세션 데이터는 지금 지워지지 않는다(#317 후속).
- Backend 역할: 종료(ENDED·ABANDONED) 후 1년이 지난 세션을 하위 기록과 함께 주기적으로 지운다.

## API 정의

- 변경 없음. 일간 리포트·달력·진행 요약은 세션에서 계산하므로 세션이 지워지면 해당 날짜에서 함께 빠진다.

## 처리 규칙

- 대상: `status IN (ENDED, ABANDONED) AND ended_at <= now - 365일`. 정확히 1년이 된 순간부터 지운다.
- 지우는 순서(자식 → 부모, 탈퇴 계정 정리와 같은 순서): 개입 전달 → 개입 → 정책 상태 → 학습 근거 → 학습 정리 → 분석 실행 → 로컬 이벤트 → 연장 → 관측 맥락 → 세션 할 일 → 회고 → 일시정지 → 세션.
- 남기는 것: 목표(`goals`), 동의 이력, 집중 설정, 계정·기기. 세션 데이터가 아니거나 따로 보관 기준이 있다.
- 동시성: 사용자 행 `FOR UPDATE SKIP LOCKED` → 세션 행 `FOR UPDATE` 뒤 상태·기한을 다시 확인한다. 세션 하나가 한 transaction이다.
- 주기: 기본 1시간(`focuson.session.retention-delay-ms`), 100건씩 id 순으로 끝까지 처리한다.
- 로그: 실패 시 `component=session-retention`, 예외 종류만 WARN으로 남긴다.

## DB

- V39: `ix_learning_sessions_retention (ended_at, id) WHERE status IN ('ENDED','ABANDONED')` 부분 Index.

## 테스트

- 1년 1초 전에는 지우지 않고, 1년이 되면 하위 13개 테이블 기록이 모두 사라진다.
- 1년 미만 종료 세션·진행 중 세션·목표는 남는다. 다시 실행하면 0건이다.
