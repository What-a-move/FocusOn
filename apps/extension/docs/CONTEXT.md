# Extension 작업 컨텍스트

> Frontend(`apps`) 담당자가 Chrome Extension 작업을 시작할 때 읽는 플랫폼 전용 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 담당 영역

- Frontend 공통 담당 범위: `apps/`
- 이 문서의 대상 앱: `apps/extension/`
- Chrome Extension Manifest V3
- 현재 탭·URL·페이지 제목 확인
- 페이지 이동 흐름 기록
- Content Script와 Service Worker
- 분석 제외 도메인 처리
- Desktop 앱과 학습 세션·타이머 상태 연결
- 연결 코드 기반 기기 연동과 인증 상태

## 현재 상태

### 완료된 작업

- Next.js 기본 화면을 FocusOn 브라우저 분석 화면으로 교체했다.
- Manifest V3 기본 파일을 `public/manifest.json`으로 정리했다.
- Content Script와 Service Worker 진입 파일을 추가했다.
- Extension production build 결과물에 manifest와 스크립트가 포함되는 것을 확인했다.
- Next.js App Router와 Tailwind CSS v4 기반 화면 구조를 확인했다.
- 활성 학습 세션 중 현재 Tab을 자동 추적하고 `tabs`와 `webNavigation`을 함께 사용하는 기준을 확정했다.
- 제외 Domain은 Server의 사용자별 정규화 host 목록이 정본이고 `chrome.storage.local`은 캐시다. Navigation 임시 상태는 `chrome.storage.session`, 세션·연결 기준은 Server로 확정했다.
- 15초 체류는 Service Worker 타이머와 저장된 시작 시각·알람 복구로 처리한다(결정 014, 결정 007 변경). Worker 재시작 복구는 구현했고 실제 Chrome QA가 남아 있다.
- 최소 Chrome 120과 HTTP Page 분석 지원을 확정했다.
- 일회용 연결 코드 pairing(2026-10-22): 코드 입력·형식 검증, `POST /api/v1/extension-pairings` 제출, 오류 안내, token 저장(결정 011), 연결 완료 화면. 실제 Backend 왕복 QA는 남아 있다.
- 팝업 세션 표시(Issue #52, 2026-10-25): 팝업을 연결 전(E01)·세션 없음(E02)·세션 진행 중(E03, 표시 전용)·서버 연결 불가(E06)로 나눴다. `features/session/api-client.ts`가 Access Token 갱신(만료 30초 전·서버 만료 응답 시 1회, single-flight, Refresh Token rotation 저장)과 재연결 처리(토큰 삭제 후 연결 화면)를 맡는다. 테스트는 `pnpm --filter @focuson/extension test`(Node 24 TypeScript 실행). 세션 API가 dev에 없어 실제 왕복은 미검증이며, 빌드 결과물에 가짜 저장소·서버 응답을 넣어 화면을 확인했다.

- 탭·페이지 분석(Issue #61, 2026-10-26): Service Worker가 집중 중 활성 탭을 15초 본 뒤 제외·민감·Chrome 내부 판정 → `TAB_META` → 관련 없음·판단 보류면 정리한 본문으로 `CHROME_DOM`을 `analysis-runs`에 보낸다. 15초는 Worker 타이머+저장된 시작 시각+알람으로 복구하고 페이지는 필요할 때만 읽는다(결정 014). 사이트 권한은 optional로 바꾸고 팝업 E01b·E06을 추가했다. 집중 중 권한을 끄면 `local-events`. 가짜 서버로 실제 Chromium에서 문서·로그인·제외 사이트 흐름을 확인했고, 실제 백엔드 왕복은 미검증이다.

### 진행 중인 작업

- Issue #105 모듈 연동 로거(2026-10-29): 공용 `log()`가 Desktop과 같은 JSON 형식으로 기록하고 INFO 이상은 `chrome.storage.local`의 `focuson.logs`에 최근 500건만 둔다. 모든 서버 요청·연결 코드 요청에 `X-Trace-Id`와 `api.*`/`decision.*` OUT·IN·fail, Worker 시작 때 `sw.restart`. 팝업 하단 `로그 복사`로 JSON을 클립보드에 넣는다(결정 016).
- 세션·pairing API가 dev에 병합되면 실제 계정으로 pairing → 세션 표시 → 토큰 갱신을 확인하고 REPORT를 작성한다.
- 탭 분석(Issue #61): 실제 Chrome에서 사이트 권한 창 허용·거부, Service Worker 종료 뒤 복구(구현됨) QA와 `analysis-runs` 실제 왕복이 남아 있다.
- 페이지 본문 최소 수집 범위: 제목·h1~h3·문단·목록·figcaption 최대 2,000자, 코드 내용·입력값 제외(결정 013, 구현 완료).
- Desktop 온보딩에서 Extension 연결은 필수 단계다. Extension pairing UI는 코드 입력과 연결 결과 표시까지만 담당하며, 온보딩 완료 뒤의 목표·시간 설정은 Desktop 대시보드에서 진행한다.

### 아직 진행하지 않은 작업

- 팝업에서 세션 일시정지·재개·종료 (P1, 결정 012)
- 같은 페이지 안 내용 변경(스크롤 등) 재전송 (결정 013, 미룸)

## 현재 기술

- 프레임워크: Next.js, React, Tailwind CSS v4
- 언어: TypeScript
- 확장 규격: Chrome Extension Manifest V3
- 최소 지원: Chrome 120
- 주요 구성: Popup, Content Script, Service Worker
- 작업 실행: Turborepo
- 실행: `pnpm dev:extension` 또는 `pnpm turbo run dev --filter=@focuson/extension`
- 빌드: `pnpm build:extension` 또는 `pnpm turbo run build --filter=@focuson/extension`

## 현재 데이터 흐름

```text
Chrome 탭
  → Content Script / tabs API
  → Service Worker
  → Server의 인증·학습 세션·분석 API
  → Desktop과 상태 동기화
  → 관련성 판단·집중 상태·리포트
```

## 반드시 지켜야 하는 조건

- Manifest 권한은 필요한 범위만 요청한다.
- 사용자가 지정한 제외 도메인에서는 본문 분석과 전송 및 Desktop의 화면·OCR·AI 콘텐츠 분석을 중지한다. 타이머와 동의된 Desktop Camera 분석은 유지하며 `PRIVACY_BLOCKED`에서는 Camera 분석도 중지한다.
- 비밀번호, 결제 정보, 개인 메시지 등 민감한 본문을 기본 수집하지 않는다.
- 페이지 이동이나 탭 변경이 발생해도 중복 이벤트를 최소화한다.
- Desktop 연결 실패가 Chrome 페이지 동작을 막지 않아야 한다.
- Popup은 타이머의 기준이 아니며 Server의 세션 상태를 조회해 표시한다.
- Extension에서 일시정지·재개·종료를 요청하면 서버 응답을 확인한 뒤 UI를 갱신한다.
- 활성 학습 세션 중 현재 활성 Tab만 자동 추적하고 Background Tab과 Incognito Window는 기본 분석하지 않는다.
- HTTP Page 분석은 허용하지만 FocusOn Server와 인증 통신은 HTTPS만 사용한다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 만든다.

## 참고 문서

- Extension: `apps/extension/docs/EXTENSION_ARCHITECTURE.md`
- Extension: `apps/extension/docs/MESSAGE_CONTRACT.md`
- Extension: `apps/extension/docs/MANIFEST_PERMISSION_POLICY.md`
- Extension: `apps/extension/docs/SERVICE_WORKER_LIFECYCLE.md`
- 루트: `docs/ARCHITECTURE.md`
- 루트: `docs/API_CONTRACT.md`
- 루트: `docs/DATA_PRIVACY.md`
- 루트: `docs/WORKFLOW.md`
- Desktop: `apps/desktop/docs/README.md`

## 작업 종료 시 갱신

- 완료·진행 중·미착수 작업
- 변경된 권한과 수집 범위
- 발생한 오류 보고서
- 다음 작업과 선행 조건
