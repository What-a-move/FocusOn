# Backend 기능 기획서 — 반복 이탈 기반 휴식 제안 생성

## 기본 정보

- 기능명: 휴식 제안(BREAK_SUGGESTION) 생성
- 기능 ID: break-suggestion
- 작성자: 박진욱
- 작성일: 2026-11-21
- 우선순위: MVP - 핵심 (기능 카탈로그 P0 2.5)
- 관련 Issue: #301
- 예정 Branch: `feat/301-server-break-suggestion` (#300 위에 쌓음)
- 관련 Client·AI 문서: Notion 「학습 세션 · 타이머 · 휴식 제안」 8장(REVIEW), Desktop `intervention-watcher.cts`(이미 BREAK_SUGGESTION 표시)

## 기능 목적

- 해결할 문제: 개입 종류에 `BREAK_SUGGESTION`은 있지만 서버가 만들지 않아, Desktop의 휴식 제안 화면이 동작하지 않는다.
- Backend가 담당할 역할: 반복 이탈을 판단해 휴식 제안을 만들고 민감값 없는 근거를 내려 준다.

## API 정의

- 새 엔드포인트 없음. `GET /sessions/{id}/interventions` 항목에 `breakEvidence: { repeatCount, windowMinutes } | null` 추가(하위 호환).
- 계약: Notion 명세에 근거 필드가 없어 서버 제안으로 `docs/API_CONTRACT.md`에 적었다. 문서 오너 확인 필요.

## 데이터 설계

- V27: `intervention_events.break_repeat_count`(≥3), `break_window_minutes`(≥1), 둘 다 NULL 허용.
- 저장하지 않을 데이터: 화면 원문·도메인·카메라 신호.

## 처리 흐름

1. 알림 평가(분석 완료·heartbeat) 때 기존 대기·미확인 전송 검사를 먼저 통과해야 한다.
2. 설정(`user_focus_settings.breakSuggestionEnabled`, 행이 없으면 켜짐)이 켜져 있고 이번 실행 구간에 휴식 제안이 없을 때 근거를 계산한다.
3. 근거: 강도별 반복 기준을 넘긴 딴짓 구간을 이탈 1회로 보고, 시작 시각이 5분 이내로 이어진 마지막 사슬이 3회 이상이며, 지금 딴짓 중이고, 알림 간격이 지났을 것.
4. 근거가 있으면 이탈 알림 대신 휴식 제안을 만든다(수명 30초·대기 슬롯·전송 규칙은 이탈 알림과 같음). 근거 판정 id도 남긴다.

## 범위 밖

- 「잠시 쉬기」 선택 시 일시정지(Desktop이 기존 pause API로 처리).
- 개인화 휴식 추천(P1).

## 완료 조건

- [x] 조건 충족 시 한 번만 생성되고 설정 OFF면 이탈 알림만 만든다.
- [x] 응답에 최소 근거만 포함된다.
- [x] 테스트·문서 갱신.
