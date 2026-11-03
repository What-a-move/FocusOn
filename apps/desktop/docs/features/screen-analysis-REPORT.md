# Desktop 기능 결과 리포트

## 기본 정보

- 기능명: 화면 분석 Desktop 흐름 (1단계 관측·제외 앱, 2단계 서버 전송, 4단계 화면 위 포키, 5단계 OCR)
- 기능 ID: F04·F05 (화면 위 포키)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-11-03
- 관련 기획서: `screen-observation-PLAN.md`, `analysis-sending-PLAN.md`, `floating-poki-PLAN.md`, `desktop-ocr-PLAN.md`
- 관련 Issue·PR: #54(#55), #56(#57), #63(#64), #71(#72), QA 중 발견한 도우미 수정 #140(#141)
- 관련 Branch: `feat/54-desktop-screen-observation`, `feat/56-desktop-analysis-sending`, `feat/63-desktop-floating-poki`, `feat/71-desktop-ocr`

네 단계가 한 흐름(관측 → 전송 → 다시 보내기 → 알림 → 반응 기록)이라 실제 앱 QA를 한 번에 진행했고, 리포트도 하나로 쓴다.

## 구현 결과

- 기획한 내용 중 구현한 범위:
  - 1단계: Swift 도우미로 맨 앞 앱·창 제목·설치된 앱 목록 읽기, 15초 유지 관측, 제외 앱 `EXCLUDED`·민감 앱 `PRIVACY_BLOCKED`, 제외 앱 기기 저장, 세션 중 화면 기록 권한 철회 시 `AUTO_PAUSED` + `local-events`
  - 2단계: `AnalysisSender`(clientRunId·세션별 sequence, 429면 60초 쉼, 다시 보내거나 쌓지 않음)
  - 4단계: `InterventionWatcher` 10초 조회, 화면 위 포키 창, `조금 더 보기`·`잠시 쉬기`·닫기 acknowledgements
  - 5단계: 도우미 `ocr`(맨 앞 창 하나만 캡처 → Vision, 이미지는 도우미 안에서 버림), `APP_META` 결과가 관련 없음·판단 보류이면 `DESKTOP_APP_OCR`로 다시 보냄
- 기획과 달라진 내용: 알림 종류를 `OFF_TASK`·`BREAK_SUGGESTION` 두 가지로 줄였다(결정 035).
- 구현하지 못한 내용: 포키 F06(분석 제외·민감 화면)·F07(연결 끊김 말풍선)·위치 옮기기는 다음 작업.

## 변경 파일

| 파일 경로 | 변경 내용 |
| --- | --- |
| `native/observer/main.swift`, `scripts/build-native.cjs` | 관측 도우미(맨 앞 앱·앱 목록·OCR) |
| `electron/main/observer-helper.cts`·`screen-observer.cts`·`screen-analysis.cts`·`excluded-apps.cts`·`ocr-text.cts` | 관측 상태 기계, 제외 앱 저장, OCR 글자 정리 |
| `electron/main/analysis-sender.cts`·`analysis-ipc.cts` | `analysis-runs` 전송, 제외 앱 IPC |
| `electron/main/intervention-watcher.cts`, `app/floating/`, `features/floating/` | 알림 조회와 화면 위 포키 창 |
| `features/onboarding/ExcludedAppsField.tsx`·`ExcludedSitesStep.tsx` | 온보딩 제외 앱 고르기 |
| `tests/electron/screen-observer`·`analysis-sender`·`desktop-ocr`·`intervention-watcher.test.cjs` | 단위 테스트 |

(전체 목록은 각 PR)

## 사용자 흐름 테스트

실제 Google 계정 + 로컬 Docker 서버(dev 최신, #139 포함)로 실제 Electron 앱을 띄워 확인했다. 서버에 아직 없는 `analysis-runs`·`interventions`·`acknowledgements`·`heartbeats`·`local-events`만 QA용 중계 서버가 가짜로 답했고, 로그인·목표·세션 시작·일시정지·재개는 실제 서버로 그대로 보냈다. 중계 서버와 QA 앱은 QA가 끝난 뒤 껐다.

- [x] 앱 실행: 도우미가 맨 앞 앱·창 제목·설치 앱 113개를 읽음
- [x] 정상 흐름
  - 관련 앱(텍스트 편집기) 15초 → `APP_META` 전송(창 제목 18자, `X-Device-Id` 있음) → `RELATED`
  - 관련 없는 문서 → `APP_META` `UNRELATED` → OCR 글자 88자로 `DESKTOP_APP_OCR` 다시 보냄(순서 번호 따로) → 알림 생성
  - 화면 위 포키 표시, `조금 더 보기`·`잠시 쉬기`·닫기 모두 acknowledgements 기록
- [x] 일시정지·재개: `잠시 쉬기` → 실제 서버 일시정지 200, `다시 집중하기` → 실제 서버 재개 200. 종료는 서버 API가 없어 미확인
- [x] 권한 허용: 화면 기록 허용 상태에서 창 캡처·OCR 정상(한글·영어, 0.8~1.5초)
- [ ] 권한 거부: 세션 중 권한 철회는 단위 테스트로만 확인
- [x] 분석 제외 앱: Discord를 제외 앱으로 두면 앱 이름·제목 없이 `EXCLUDED`만 전송
- [x] 앱 재실행: 진행 중 세션을 이어받아 `APP_RESTART` 일시정지(실제 서버 200), 홈에 진행 중 안내

## 테스트 환경

- macOS: 26.6.1
- Mac 모델·칩: Apple M4
- Electron: 44.1.0 (개발 실행)
- 테스트 일시: 2026-11-03

## 오류 기록

- 관련 `기능명-오류명-ERROR.md` 목록: `desktop-ocr-observer-cgs-init-crash-ERROR.md`, `screen-observation-transparent-window-ERROR.md`, `screen-observation-dialog-margin-preflight-ERROR.md`
- 해결된 오류: OCR 요청 시 도우미 종료(`CGS_REQUIRE_INIT`), Electron 앱의 투명 창을 맨 앞 창으로 고름(#141)
- 남은 오류: 없음. 포키가 뜬 직후 맨 앞 앱이 FocusOn으로 바뀐 순간이 한 번 있었으나, 사용자가 포키를 눌러서인지 구분하지 못해 확인 중이다.

## 성능 및 제한사항

- CPU·메모리 확인: 미측정
- 화면 캡처 제한: 맨 앞 창 하나만, OCR이 필요할 때만 캡처. 이미지는 도우미 밖으로 나가지 않음
- 카메라 분석 제한: 해당 없음
- 네트워크 끊김 처리: 전송 실패는 다시 보내지 않음(단위 테스트)

## 다음 작업

- 서버 `analysis-runs`·`interventions`·`acknowledgements`·`local-events`가 생기면 중계 서버 없이 연동 QA
- 포키 F06·F07, 위치 옮기기

## 문서 갱신 확인

- [x] `CONTEXT.md` 갱신
- [x] `NEXT_TASK.md` 갱신
- [x] `DECISION_RECORD.md` 갱신 필요 여부 확인 (결정 035 이미 기록)
- [x] PR에 이 리포트 연결
