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
- 제외 Domain은 `chrome.storage.local`, Navigation 임시 상태는 `chrome.storage.session`, 세션·연결 기준은 Server로 확정했다.
- Content Script Timer와 Service Worker 재검증을 조합한 15초 체류 흐름과 Message 계약을 확정했다.
- 최소 Chrome 120과 HTTP Page 분석 지원을 확정했다.

### 진행 중인 작업

- 확정한 현재 Tab 자동 추적과 Message 구조를 실제 코드로 구현해야 한다.
- 페이지 본문 분석의 최소 수집 범위를 확정해야 한다.
- 전체 HTTP·HTTPS Host 권한을 필수 또는 Optional 권한으로 요청할지 확정해야 한다.
- 일회용 연결 코드 pairing과 token 저장 정책을 구현해야 하며 Server 실시간 동기화 방식은 추가 확정이 필요하다.

### 아직 진행하지 않은 작업

- 현재 탭 정보 수집 API 연결
- 페이지 이동 이벤트 저장
- 15초 체류 후 재분석
- 분석 제외 도메인 적용
- Desktop 연결 및 상태 동기화
- Server API 연결
- 일회용 연결 코드 기반 Extension pairing
- Popup 타이머 조회·제어 UI

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
- 사용자가 지정한 제외 도메인에서는 본문 분석과 전송을 중지하고 Desktop의 화면·OCR·AI·Camera 분석도 중지한다.
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
