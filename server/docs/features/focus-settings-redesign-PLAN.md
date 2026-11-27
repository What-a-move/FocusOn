# Backend 기능 기획서 — 집중 설정 새 필드 5개

## 기본 정보

- 기능명: 집중 설정 재설계 1차 필드
- 기능 ID: focus-settings-redesign
- 작성자: 박진욱
- 작성일: 2026-11-27
- 우선순위: MVP - 기본
- 관련 Issue: #373
- 예정 Branch: `feat/373-server-focus-settings-fields`
- 관련 문서: Notion `PUT/GET /api/v1/users/me/focus-settings`(VERIFIED, 재설계 1차 호환 규칙)

## 기능 목적

- 해결할 문제: 리포트 진행 요약(하루·주간 목표), 회고 표시, 미리 알림, 학습 메신저 설정을 저장할 곳이 없다.
- Backend가 담당할 역할: 새 필드를 하위 호환으로 받고 돌려준다.

## API 정의

- PUT·GET 응답에 `learningMessengers`, `preDistractionReminderEnabled`, `dailyTargetMs`, `weeklyTargetMs`, `retrospectiveEnabled`.
- PUT에서 선택 필드. 없으면 저장값 유지, 저장값이 없으면 기본값. `weeklyTargetMs: null`은 미설정으로 지운다.
- 범위 밖은 400 `VALIDATION_ERROR`.

## 데이터 설계

- V35 `user_focus_settings` 컬럼 5개(기본값 포함) + 하루·주간 목표 CHECK(`NOT VALID`).

## 범위 밖

- 메신저 보호 판정, 미리 알림 발송, 회고 화면 표시는 각 기능에서 쓴다.

## 완료 조건

- [x] 저장·유지·기본값·범위 검증 테스트
- [x] 마이그레이션·문서 갱신
