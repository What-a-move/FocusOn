# Desktop 기능 기획서

## 기본 정보

- 기능명: 포키 알림 새 방식 연결 (전경 context · 전송 receipt)
- 기능 ID: -
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop (+ Extension 분석 요청 1곳, shared-types 선택 필드)
- 작성자: jae1
- 작성일: 2026-11-11
- 우선순위: MVP - 핵심
- 관련 Issue: #218
- 예정 Branch: `feat/218-intervention-context`
- 관련 문서: `docs/API_CONTRACT.md` 실제 알림 연결(#182), `server/docs/features/intervention-policy-handoff.md`, `intervention-policy-integration-contract.md`

## 기능 목적

- 사용자의 문제: 서버(#182, PR #205)가 "지금 앞에 떠 있는 화면" 기준으로만 알림을 만든다. Desktop·Extension이 그 화면(context)을 알려 주지 않으면 공부와 다른 화면을 봐도 포키가 뜨지 않는다.
- Desktop에서 제공할 가치: 실제 서버 판정으로 포키가 뜨고, 이미 지난 화면·오래된 알림은 띄우지 않으며, 띄웠다는 사실을 서버에 남겨 5분 cooldown이 맞게 돌아간다.

## 사용자 흐름

1. 집중 중 앞에 띄운 앱·창이 바뀌면 Desktop이 바로 새 전경 context를 서버에 등록한다(Chrome이면 Extension 담당).
2. 같은 화면을 보는 동안 heartbeat(10초)가 context를 이어 준다(lease 30초).
3. 서버가 이탈 알림을 만들면 Desktop이 조회해, 지금도 같은 화면·진행 중·유효 시간 안일 때만 포키를 띄운다.
4. 띄운 결과(SENT·NOT_SENT·UNKNOWN)를 서버에 보낸다. 확인했어·잠시 쉬기는 기존 acknowledgements 그대로다.

## Desktop 상세 동작

- 진입 조건: 세션 RUNNING. 멈추거나 끝나면 context를 비운다(다시 시작하면 새 context).
- 전경 context (`observation-context.cts`)
  - 1초 관찰 루프의 맨 앞 앱으로 observer를 정한다: Chrome → `EXTENSION`, 확인 불가 → `NONE`, 그 밖 → `DESKTOP`(FocusOn 자기 창·제외·민감 앱도 Desktop이 보호 상태를 보내므로 DESKTOP).
  - 앱·창(bundleId + 창 제목)이 바뀌면 `foregroundSequence`를 1 늘려 파일에 저장하고 새 `clientContextId`로 PUT. 실패하면 같은 번호·nonce로 다시 보낸다.
  - 늦게 온 응답은 지금보다 작은 번호면 버린다. 응답의 `observationContextId`를 현재 context로 확정한다.
  - heartbeat가 context를 이어 주지 못한 채 25초가 지나면(lease 30초 전) 새 번호로 다시 등록한다.
- heartbeat·analysis-runs: 현재 화면이 확정된 context일 때만 `observationContextId`를 붙인다. 바뀌는 중이거나 모르면 붙이지 않는다.
- 알림 표시 (`intervention-watcher.cts`, 단일 coordinator)
  - 미확인 receipt(outbox)가 있으면 먼저 보내고, 이번 주기에는 새 후보를 띄우지 않는다.
  - GET 시작 시각(monotonic)을 잡고, 응답 뒤 `remainingTtlMs - 경과`가 0 이하이거나 context가 현재와 다르거나 RUNNING이 아니면 버린다.
  - 띄우기 전에 시도 기록(journal: interventionId·clientDeliveryId·DISPATCHING)을 파일에 쓰고, 채널별로 띄운 뒤 결과를 SENT(성공 채널)·NOT_SENT로 바꿔 outbox에 둔다. `POST /interventions/{id}/deliveries` 성공 시 지운다.
  - 앱을 다시 켰을 때 DISPATCHING으로 남은 시도는 UNKNOWN으로 보고한다.
- 상태 변화: 세션 상태 전이는 바꾸지 않는다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 기존 화면 기록·알림. 새 권한 없음.
- 활성 앱 처리: 기존 `screen-observer.cts` 규칙(Chrome은 Extension 담당)을 그대로 쓴다.

## 화면 및 사용자 경험

- 화면·문구 변경 없음. 포키 창·확인했어 5분 UI는 그대로.

## 데이터 및 IPC/API

- 서버 요청: `PUT /sessions/{id}/observation-context`, heartbeat·analysis-runs 선택 `observationContextId`, `GET /sessions/{id}/interventions`(observationContextId·remainingTtlMs), `POST /interventions/{id}/deliveries`.
- 보내지 않는 값: 앱 이름·번들 ID·창 제목·URL(context 요청에는 observer만).
- 저장 데이터(userData): 전경 순번 `{sessionId, sequence}`, 알림 journal/outbox `{interventionId, clientDeliveryId, status, channels}`.
- Extension: 분석 직전 `GET observation-context` → `observer=EXTENSION`이고 `observerDeviceId`가 자기 기기일 때만 `observationContextId`를 붙인다. 아니면 기존처럼 보낸다(서버는 정책에 쓰지 않음).

## 완료 조건

- 단위 테스트: 순번 영속·재시도 nonce 유지·늦은 응답·lease 재등록·context 불일치·TTL·receipt 재시도·재시작 UNKNOWN·Extension 담당 확인
- 시연 모드(`pnpm dev:demo`)가 계속 동작한다(가짜 알림에 현재 context를 붙인다).
- 실제 서버(일반 실행)로 전경 등록·deliveries 기록 확인. AI 판정이 필요한 실제 포키 표시는 결과를 그대로 기록한다.

## 남은 일·한계

- 관측 전역 순번(`observationSequence`)은 범위 밖.
- OS 표시와 DB의 exactly-once는 보장하지 않는다(서버 계약과 같음).
