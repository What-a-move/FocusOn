# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: 화면 관측 결과 서버 전송 (화면 분석 2단계)
- 기능 ID: 화면 분석 4.1·4.3·7.3, `POST /api/v1/sessions/{sessionId}/analysis-runs`
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-25
- 우선순위: MVP - 핵심
- 관련 Issue: #56
- 예정 Branch: `feat/56-desktop-analysis-sending`
- 관련 Notion·Figma: 화면 분석 4.1·4.3·7.3, AI 분석 계약 2.2·8·9, API 명세 `POST /sessions/{id}/analysis-runs`, 결정 024
- 디자인 원본: 화면 변경 없음

## 기능 목적

- 사용자의 문제: 1단계 관측은 Desktop 안에만 있어 서버가 이탈을 판단할 수 없다.
- Desktop에서 제공할 가치: 관측 1건을 바로 서버로 보내 AI 판정·Policy Engine 개입 판단으로 이어지게 한다. 메인 흐름(관측 → 판정 → 개입)의 Desktop 쪽 연결이다.

## 순서 변경 (2026-10-25)

메인 흐름을 먼저 잇고 OCR은 정확도 보강으로 뒤에 둔다: 2단계 Desktop 전송 → 3단계 Extension 탭·DOM → 4단계 개입 표시 → 5단계 Desktop OCR.

## 사용자 흐름

1. 세션 `RUNNING` 중 1단계 관측(같은 화면 15초 유지)이 만들어진다.
2. Main `AnalysisSender`가 바로 `analysis-runs`로 보낸다. 사용자 화면에는 변화가 없다.

## Desktop 상세 동작

- 요청: `POST /api/v1/sessions/{sessionId}/analysis-runs`, 헤더 `Authorization`·`X-Device-Id`.
  - 일반: `{ clientRunId, contentSource: 'APP_META', observedAt, sequence, guardStatus: null, metadata: { appName, windowTitle } }`
  - 제외·민감: `{ clientRunId, contentSource: 'APP_META', observedAt, sequence, guardStatus: 'EXCLUDED' | 'PRIVACY_BLOCKED' }` (metadata 없음)
- `clientRunId`: 관측마다 UUID v4. `sequence`: 같은 세션·`APP_META` 안에서 1부터 증가. 세션이 바뀌면 1부터. 서버는 더 작은 sequence를 옛날 결과로 보므로, 앱을 다시 켜도 같은 세션이면 이어 세도록 마지막 값을 `userData/analysis-sequence.json`에 둔다.
- 응답 200: `{ analysisRunId, relation, confidence, reasonCode, observedAt, policyVersion }`을 검사만 한다. 결과가 `UNRELATED`·`UNCERTAIN`이면 OCR로 다시 보내는 것은 5단계다(2026-10-26 기준, 결정 024).
- 실패 처리:
  - 자동 재전송하지 않는다(POST, 공통 규칙). 다음 화면 변경부터 새로 보낸다.
  - `TOO_MANY_REQUESTS`: 60초 동안 보내지 않는다. ponytail: Retry-After 헤더 대신 요청 제한 단위(1분)로 쉰다. 공통 요청 함수가 헤더를 넘기게 되면 그 값을 쓴다.
  - `ANALYSIS_SERVICE_UNAVAILABLE`·네트워크 오류: 서버가 `UNCERTAIN`으로 기록하거나 기록이 없을 뿐이라 알림으로 이어지지 않는다. 쌓아 두지 않는다(화면 분석 7.3).
- 요청 수: 관측은 화면이 15초 유지돼야 만들어져 Desktop 한 대는 분당 최대 4건이다(서버 제한 분당 8회 안).

## macOS 권한 및 시스템 기능

- 새 권한 없음. 1단계 관측을 그대로 쓴다.

## 화면 및 사용자 경험

- 화면 변경 없음. 판정·개입 표시는 4단계다.

## 데이터 및 IPC/API

- Renderer 입력: 없음. IPC 변경 없음.
- Server 요청: 위 `analysis-runs`.
- 저장 데이터: `userData/analysis-sequence.json` `{ sessionId, sequence }` (마지막 순서 번호만, 화면 내용 없음).

## 예외 처리

| 상황 | 사용자에게 보여줄 내용 | Desktop 처리 |
| --- | --- | --- |
| 네트워크 끊김 | 없음 | 보내지 않고 버림, 복구 뒤 새 관측부터 |
| 요청 제한(429) | 없음 | 60초 동안 보내지 않음 |
| AI 장애(503) | 없음 | 다시 보내지 않음(서버가 UNCERTAIN 처리) |
| 응답 형식 오류 | 없음 | 무시 |

## 보안·개인정보

- 화면 원본 저장 여부: 없음(캡처하지 않음).
- Server로 전송하는 최소 데이터: 앱 이름·창 제목(일반) 또는 제외·민감 상태만. 창 제목은 로그에 남기지 않는다.

## 완료 조건

- [x] 기획 내용 확인
- [x] 정상 흐름 구현 (요청 본문·순서 번호·세션 변경)
- [x] 예외 상황 확인 (429·503·네트워크·형식 오류)
- [x] 테스트 완료 (단위·가짜 bridge 화면 확인, 실제 API QA는 백엔드 병합 뒤)
- [x] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성 (실제 오류 없음)
- [ ] `*-REPORT.md` 작성 (백엔드 `analysis-runs` 병합 뒤 실제 QA 포함)
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신
