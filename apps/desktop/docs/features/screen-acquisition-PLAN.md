# Desktop 화면 수집 기능 기획서

## 기본 정보

- 기능명: macOS 활성 창 선택 및 Apple Vision OCR
- 기능 ID: screen-acquisition
- 담당 영역: Frontend(apps), Desktop
- 작성일: 2026-10-19
- 우선순위: MVP 핵심
- 현재 Branch: `feat/screen-acquisition`
- 근거: 사용자가 제공한 허재원 화면 분석 기획서 3·4·6절

## 기능 목적과 사용자 흐름

1. Main의 수집 모듈이 최신 RUNNING 세션과 제외 정책을 확인한다. Server 동기화는 후속 3번 작업이다.
2. NSWorkspace·Accessibility로 현재 foreground 앱과 focused window 하나를 확인한다.
3. FocusOn 자체 UI·제외 앱·민감 앱을 차단하고 APP_META를 반환한다. Chrome은 Extension으로 위임한다.
4. APP_META가 내용 부족으로 UNCERTAIN일 때만 같은 창을 ScreenCaptureKit으로 캡처하고 Apple Vision 로컬 OCR을 실행한다.
5. 시작·캡처·OCR 완료 후 같은 창·세션·정책인지 확인하고 변경 시 원문을 폐기한다.

## macOS 권한 및 시스템 기능

- macOS 14 이상을 대상으로 Objective-C helper를 빌드한다. 초기 Swift 시도는 로컬 CLT의 SDK 충돌로 빌드되지 않아 Apple Framework를 직접 호출하는 clang 경로로 변경했다.
- Accessibility: foreground window 제목·위치·크기·민감 입력 역할 확인. 권한이 없으면 추측해서 다른 창을 선택하지 않는다.
- Screen Recording: OCR 실행 직전에 확인하며 권한을 자동 요청하거나 설정을 변경하지 않는다.
- 캡처는 desktopIndependentWindow 필터로 대상 창 하나만 포함한다. 다른 창과 Floating Poki를 합성하지 않는다.
- 화면 밖·다른 디스플레이에 걸친 창은 보이는 한 화면 영역만 사용하며 불완전하면 보류한다.

## 데이터 및 IPC/API

- Node/Electron Main에서 사용할 전용 모듈과 native stdin/stdout JSON 프로토콜을 구현한다. Renderer에 원본 이미지·본문을 노출하지 않는다.
- 임의 shell 명령·파일 경로·URL을 실행하지 않는다. 고정된 helper만 execFile로 호출한다.
- 세션·정책을 Main에 공급하고 결과를 Server에 전송하는 연결, Electron 앱 전체 생명주기는 3번 후속 작업에서 연결한다.
- APP_META→DESKTOP_APP_OCR 출처를 유지하며 Desktop 텍스트를 CHROME_DOM으로 표시하지 않는다.

## 예외 처리와 개인정보

- 미확인·만료된 정책, 비활성 세션, 권한 없음, 대상 불명은 UNCERTAIN.
- FocusOn·Chrome·제외 앱은 Desktop OCR 대상에서 제외한다. 도메인을 확인할 수 없는 다른 브라우저는 캡처하지 않는다.
- 알려진 민감 앱·창 제목·SecureTextField를 사전 차단하고 OCR 텍스트는 로컬에서 개인정보를 재검사한다.
- 원본 화면과 OCR 본문은 메모리에서 처리한 뒤 폐기한다. 파일·로그·ChromaDB에 저장하지 않는다.

## 완료 조건

- native helper 빌드, 합성 입력으로 대상 선택·단계 전환·정책 변경·원문 비저장 검증.
- 실제 macOS 권한 승인·사용자 화면 수집·학교 모델 평가 결과는 구현 검증과 구분해 기록한다. 실제 화면·모델 평가는 사용자가 보류한 4번 작업이다.
- CONTEXT·NEXT_TASK·결과 리포트를 갱신한다.
