# Chrome 활성 탭·단계별 수집 구현 결과

## 기본 정보

- 기능 ID: screen-acquisition
- 담당 영역: Extension, 작성일: 2026-10-19
- 기획서: [PLAN](screen-acquisition-PLAN.md)
- Branch: `feat/screen-acquisition`

## 구현 결과

- focused Chrome 창의 active tab 하나만 선택하며 Background·Incognito·내부 URL·정책 불명은 수집하지 않는다.
- 메타데이터부터 시작하고 내용 부족/충돌 UNCERTAIN만 DOM→현재 viewport OCR로 진행한다. DOM·OCR 본문은 15초 활성 체류 조건 이후에만 수집한다.
- DOM은 숨긴/편집/폼/메뉴 내용을 제거하고 제목·소제목·본문·코드·표 순서를 보존한다. 6,000자 초과는 원문을 임의로 잘라 대표 근거로 제출하지 않는다.
- OCR은 optional 권한 승인 뒤 captureVisibleTab→로컬 offscreen Tesseract.js(한국어·영어)로 실행한다. Worker·코어·모델은 패키지 안에 포함한다.
- 페이지의 민감 폼 구조를 값 읽기 없이 검사하고, 내용을 검사할 수 없는 iframe은 OCR로 우회하지 않는다. 개인정보 후보는 수집 전후 차단한다.
- 정책·탭·문서 변경은 진행 중 OCR을 중지하고 결과를 폐기한다. screenshot·본문은 storage·로그·오프라인 큐에 저장하지 않는다.
- `observedAt`은 수집 시작 시각이다. 새 clientRunId와 출처별 sequence를 발급한다. 내부 probe/정책 lease는 공개 API 필드로 추가하지 않았다.

## 변경 파일

| 파일 | 역할 |
| --- | --- |
| `public/capture/controller.js`, `policy.js` | 대상 선택·정책·단계·최신 관측 검사 |
| `public/content-script.js`, `public/capture/dom.js` | DOM·민감 구조 검사·15초 체류 |
| `public/capture/offscreen.html`, `ocr-document.js` | 로컬 OCR, 종료·이미지 메모리 해제 |
| `public/capture/permissions.*`, `app/page.tsx` | optional OCR 권한 설정 진입 |
| `public/service-worker.js`, `public/manifest.json` | Event 무효화·내부 Message·로컬 WASM 권한 |
| `scripts/prepare-ocr.mjs`, Package·lockfile | 로컬 OCR assets 준비 |
| `tests/screen-acquisition.test.mjs` | 합성 Chrome API·DOM 검사 |

## 검증 결과

- Node 22.14.0·pnpm 11.19.0·jsdom 26.1.0, Chrome API 대역 테스트 **14개 통과**.
- TypeScript·Lint·Extension production build 통과. [정리 순서 오류](screen-acquisition-cleanup-ERROR.md)를 해결했다.
- 실제 Chrome 설치·optional 권한 승인·offscreen WASM 실행·사용자 화면 OCR·학교 모델은 아직 검증하지 않았다. 합성 검사를 실제 브라우저 동작 검증으로 표현하지 않는다.

## 제한사항과 다음 작업

- Server 검증 세션·제외 목록을 setContext에 공급하고 관측을 실제 분석 API에 보내는 연결은 후속 3번이다. 연결 전에는 기본적으로 수집이 차단된다.
- Popup의 기존 학습 시작 UI는 Server 세션과 아직 연결되지 않았으며 이 UI 토글만으로 수집 정책을 허용하지 않는다.
- DWELL_PENDING 이후 호출 재개/주기 분석과 Browser 재시작 후 sequence 복구도 3번에서 연결한다. DOM/viewport OCR 데이터는 재전송 대기용으로 저장하지 않는다.
- 민감 화면 후보 탐지의 실제 누락·오탐, Chrome 권한 철회·Worker 재시작·페이지별 DOM과 OCR 품질은 사용자가 보류한 4번에서 확인한다.
- CONTEXT·NEXT_TASK·DECISION_RECORD·권한/메시지 문서를 갱신했다.
