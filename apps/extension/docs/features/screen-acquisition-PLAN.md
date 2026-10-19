# Chrome 화면 수집 기능 기획서

## 기본 정보

- 기능명: 활성 탭 선택 및 단계별 화면 수집
- 기능 ID: screen-acquisition
- 담당 영역: Frontend(apps), Chrome Extension
- 작성일: 2026-10-19
- 우선순위: MVP 핵심
- 현재 Branch: `feat/screen-acquisition`
- 근거: 사용자가 제공한 허재원 화면 분석 기획서 3·4·6절, Extension Message·권한·개인정보 문서

## 기능 목적과 사용자 흐름

1. 세션·제외 정책의 최신 상태를 확인한다. API 동기화는 후속 3번 작업이다.
2. 현재 focused Chrome 창의 활성 탭 하나만 선택한다. Background·Incognito·내부 URL은 보류한다.
3. 민감 화면 구조를 값 수집 없이 검사한 후 TAB_META를 만든다.
4. 이전 결과가 내용 부족·충돌인 UNCERTAIN일 때만 CHROME_DOM, 이후 필요한 경우 CHROME_VIEWPORT_OCR을 수집한다.
5. 매 단계 전후 최신 탭·문서·Navigation·세션·정책을 재검증하고 달라지면 폐기한다.

## 상세 동작과 데이터

- Service Worker가 출처별 sequence와 navigationId를 발급하고 수집 순서를 관리한다. 각 단계는 새 clientRunId를 사용한다.
- 메타데이터에는 appName·pageTitle·urlHost만 사용하며 경로·검색어는 반환하거나 저장하지 않는다.
- DOM은 h1~h3·주요 본문·코드·표를 읽고 입력 폼·개인 메시지·메뉴·숨긴 요소는 제외한다. 본문 수집은 활성 체류 15초 이후에만 허용한다.
- 6,000자 상한을 넘으면 일부 본문으로 전체를 대표하지 않고 내용 부족 상태로 반환한다.
- Chrome OCR은 captureVisibleTab과 Extension offscreen document의 Tesseract.js를 사용한다. 한국어·영어 모델과 WASM·Worker는 빌드 결과 안에 포함하며 런타임 CDN 호출을 금지한다.
- Screenshot은 현재 viewport만 처리하고 FocusOn 페이지 overlay 영역을 마스킹한다. OCR 작업 뒤 Worker·offscreen document를 닫는다.
- raw 이미지·DOM/OCR 본문은 storage·로그·오프라인 큐에 넣지 않는다. storage.session에는 최소 세션 상태·식별자·시간·순서만 저장한다.
- 공개 API·새 공개 JSON 컬럼·모델 호출·이탈 및 알림 정책은 이 작업에서 구현하지 않는다. 수집 결과의 내부 형식을 API 어댑터에서 매핑한다.

## 권한과 구현 선택

- 기존 HTTP·HTTPS host 권한은 유지한다. 자동 viewport 캡처에 필요한 `<all_urls>`는 optional 권한으로 선언하고 사용자 동작에서 승인한 경우에만 OCR을 허용한다.
- `offscreen`은 웹 페이지 CSP와 분리된 로컬 OCR Worker 실행에 필요하다. 외부 서버·Native Messaging OCR 우회를 만들지 않는다.
- 현재 public/ 정적 Script 구조를 유지하고 ESM Service Worker와 로컬 모듈을 사용한다.

## 예외 처리와 완료 조건

- 정책 미확인·만료, 비활성 세션·탭·창, 민감 화면, 제외 도메인에서는 원문 수집 금지.
- 교차 출처 iframe·권한 없는 페이지에서 안전한 개인정보 사전 검사가 불가능하면 OCR로 우회하지 않는다.
- 잘못된 다음 단계·중복 요청·오래된 결과는 현재 관측에 적용하지 않는다.
- 합성 DOM·Chrome API 대역으로 선택·순서·개인정보·취소를 검사하고 Build·Lint를 확인한다.
- 실제 사용자 화면 및 학교 모델 정확도·속도 평가는 사용자가 보류한 4번 작업이다.
