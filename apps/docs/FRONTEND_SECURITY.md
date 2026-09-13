# Frontend 보안 규칙

## 기본 원칙

- Frontend에 포함된 값은 사용자가 확인할 수 있다고 가정한다.
- API Key와 Server Secret을 Frontend 환경변수에 넣지 않는다.
- 권한과 수집 범위는 기능에 필요한 최소 수준으로 제한한다.
- 외부 입력과 Server 응답을 신뢰하지 않고 사용 전에 확인한다.
- 개인정보 기준은 Root `docs/DATA_PRIVACY.md`를 함께 따른다.

## 인증과 데이터

- Token과 Cookie를 Console, 오류 문서, Analytics에 기록하지 않는다.
- 사용자별 Cache는 로그아웃과 계정 변경 시 정리한다.
- URL, Page Text, Screen, Camera 데이터는 목적에 필요한 최소 범위만 처리한다.
- HTML 문자열을 검증 없이 화면에 삽입하지 않는다.
- 외부 URL을 열기 전에 허용된 Protocol과 목적지를 확인한다.

## Electron

- Renderer에서 Node.js 기능을 직접 사용하지 않는다.
- `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`를 사용한다.
- Preload는 필요한 API만 명시적으로 노출한다.
- IPC Channel과 요청 데이터는 허용 목록과 타입으로 제한한다.
- IPC 오류는 Code만 Renderer에 전달하고 사용자 문구는 Renderer에서 안전하게 변환한다.
- BrowserWindow에서 외부 Page Navigation과 새 창 생성을 제한한다.

구체적인 Electron 설정은 Desktop 전용 보안·IPC 문서에서 확정한다.

## Chrome Extension

- Manifest Permission과 Host Permission은 필요한 범위만 선언한다.
- Content Script는 인증 정보와 Extension 내부 Store에 직접 접근하지 않는다.
- Message의 발신자와 데이터 모양을 확인한다.
- 제외 Domain에서는 Page 본문 수집과 전송을 중지하고 Desktop의 화면·OCR·AI·Camera 분석도 중지한다.
- `http://*/*`, `https://*/*` 범위는 필요하지만 필수 또는 Optional Host Permission으로 요청할지는 사용자와 확정한다.

구체적인 권한과 Message 검증은 Extension 전용 문서에서 확정한다.

## Code Review 확인

- 새 환경변수가 공개되어도 안전한 값인지 확인한다.
- 민감 정보가 Source, Build 결과, Log에 포함되지 않았는지 확인한다.
- 새로운 권한·외부 Domain·IPC Channel을 추가한 이유가 문서에 있는지 확인한다.
- 인증 실패와 권한 거부 상황에서도 안전한 기본 동작을 유지하는지 확인한다.
