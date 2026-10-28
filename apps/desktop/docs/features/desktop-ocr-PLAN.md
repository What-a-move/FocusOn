# Desktop 기능 기획서

> Desktop 기능 개발 전에 작성하고, 기획 확인 후 개발을 시작한다.
> ERROR와 REPORT 파일은 미리 만들지 않는다. 오류가 실제 발생하면 오류별 ERROR를 만들고, 기능이 완료되면 REPORT를 작성한다.

## 기본 정보

- 기능명: Desktop 화면 OCR (화면 분석 5단계)
- 기능 ID: 화면 분석 3.3·4.1, AI 분석 계약 4.1·4.4·8, `contentSource: DESKTOP_APP_OCR`
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 작성자: jae1
- 작성일: 2026-10-26
- 우선순위: MVP - 핵심
- 관련 Issue: #71
- 예정 Branch: `feat/71-desktop-ocr`
- 관련 Notion·Figma: 화면 분석 3.3, AI 분석 계약 4.1(2026-10-26: 메타 결과 UNRELATED·UNCERTAIN이면 항상 OCR), 4.4(메타 근거 UNRELATED는 이탈로 세지 않음), 8(늦은 응답), 결정 024
- 디자인 원본: 화면 변경 없음

## 기능 목적

- 사용자의 문제: 서버는 앱 이름·창 제목만으로 나온 `UNRELATED`를 이탈로 세지 않는다. 그래서 OCR이 없으면 Chrome이 아닌 앱에서의 딴짓을 잡지 못한다.
- Desktop에서 제공할 가치: 판정이 애매할 때만 지금 보는 창 하나의 글자를 읽어, 원본 이미지는 남기지 않고 최소 텍스트로 다시 판정받는다.

## 사용자 흐름

1. 세션 중 같은 앱 화면이 15초 유지되면 `APP_META`를 보낸다(1·2단계).
2. 결과가 `UNRELATED`·`UNCERTAIN`이고 아직 같은 화면이면, 그 창만 캡처해 Vision OCR로 글자를 읽는다.
3. 정리한 글자를 `DESKTOP_APP_OCR`로 보낸다. 민감한 글자면 `PRIVACY_BLOCKED`만 보낸다. 사용자 화면에는 변화가 없다.

## Desktop 상세 동작

- Swift 도우미
  - `frontmost`가 맨 앞 창 번호(`windowId`, CGWindowID)도 돌려준다.
  - 새 명령 `ocr { windowId }`: `SCShareableContent`에서 그 창을 찾아 `SCContentFilter(desktopIndependentWindow:)` + `SCScreenshotManager.captureImage`로 그 창 하나만 캡처한다(최대 가로 2,560px). `VNRecognizeTextRequest`(정확도 우선, `ko-KR`·`en-US`, 언어 보정)로 읽고 위→아래 순서로 이어 최대 4,000자를 돌려준다.
  - 캡처 이미지는 도우미 메모리에서만 쓰고 바로 버린다. 화면 기록 권한이 없거나 창을 못 찾으면 실패로 답한다.
- Main
  - `analyzeObservation`: 보낸 관측의 결과가 `UNRELATED`·`UNCERTAIN`이고, 일반 `APP_META`이며, 응답·OCR 뒤에도 같은 화면·같은 RUNNING 세션일 때만 OCR로 다시 보낸다. 다른 화면으로 옮겼으면 늦은 결과로 다음 단계를 보내지 않는다(계약 8).
  - `cleanOcrText`: 민감 단어·카드 번호 모양이면 sensitive → `PRIVACY_BLOCKED`. 아니면 주소·이메일·전화번호·긴 토큰을 지우고 공백을 줄여 2,000자.
  - `AnalysisSender`: 순서 번호를 `contentSource`별로 센다(`APP_META`, `DESKTOP_APP_OCR`). 예전 저장 형식(`{ sessionId, sequence }`)은 `APP_META`로 읽는다.
  - OCR 대기 시간은 최대 5초. 실패하면 보내지 않는다(서버는 메타 근거 UNRELATED를 이탈로 세지 않아 알림 없음).
- 같은 창에서 글자만 바뀌는 경우(스크롤 등) 30초 재전송은 이번에 하지 않는다(ponytail: 창 제목이 바뀔 때만). 필요하면 OCR 텍스트 hash 비교로 추가한다.

## macOS 권한 및 시스템 기능

- 필요한 권한: 화면 기록(이미 온보딩 필수). 없으면 OCR 실패 → 보내지 않음. 세션 중 철회는 1단계 흐름(AUTO_PAUSED).
- 지원 macOS 14 이상(`SCScreenshotManager`).

## 보안·개인정보

- 화면 원본 저장 여부: 저장하지 않는다. 이미지는 Swift 도우미 밖으로 나오지 않는다.
- 메모리에서 데이터가 유지되는 시간: OCR 텍스트는 전송까지 한 번 쓰고 버린다. 로그에 남기지 않는다.
- Server로 전송하는 최소 데이터: 정리한 텍스트 최대 2,000자 + 앱 이름·창 제목.
- 제외 앱·민감 앱·FocusOn·Chrome·지원하지 않는 브라우저는 OCR하지 않는다(1단계에서 이미 걸러짐).

## 완료 조건

- [x] 기획 내용 확인
- [x] 정상 흐름 구현 (도우미 OCR, 다시 보내기, 글자 정리, 순서 번호)
- [x] 예외 상황 확인 (화면 변경·세션 변경·OCR 실패·민감 글자)
- [x] 테스트 완료 (단위 테스트, Vision 한국어 인식은 저장된 캡처로 확인. 창 캡처는 화면 기록 권한이 있는 실제 앱에서 확인 필요)
- [x] 실제 오류가 발생했다면 오류별 `기능명-오류명-ERROR.md` 작성 (실제 오류 없음. 개발 셸에 화면 기록 권한이 없어 캡처 실패는 예상된 동작)
- [ ] `*-REPORT.md` 작성 (백엔드 병합 뒤 실제 QA 포함)
- [x] `CONTEXT.md`, `NEXT_TASK.md` 갱신
