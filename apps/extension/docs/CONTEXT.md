# Extension 작업 컨텍스트

> Chrome Extension 담당자가 작업을 시작할 때 가장 먼저 읽는 문서다.
> 작업이 끝나면 현재 상태와 다음 담당자가 알아야 할 내용을 갱신한다.

## 담당 영역

- Chrome Extension Manifest V3
- 현재 탭·URL·페이지 제목 확인
- 페이지 이동 흐름 기록
- Content Script와 Service Worker
- 분석 제외 도메인 처리
- Desktop 앱과 상태 연결

## 현재 상태

### 완료된 작업

- Vite 기본 화면을 FocusOn 브라우저 분석 화면으로 교체했다.
- Manifest V3 기본 파일을 `public/manifest.json`으로 정리했다.
- Content Script와 Service Worker 진입 파일을 추가했다.
- Extension production build 결과물에 manifest와 스크립트가 포함되는 것을 확인했다.

### 진행 중인 작업

- 실제 탭 정보와 FocusOn Desktop 연결 방식을 구체화해야 한다.
- 페이지 본문 분석의 최소 수집 범위를 확정해야 한다.

### 아직 진행하지 않은 작업

- 현재 탭 정보 수집 API 연결
- 페이지 이동 이벤트 저장
- 15초 체류 후 재분석
- 분석 제외 도메인 적용
- Desktop 연결 및 상태 동기화
- Server API 연결

## 현재 기술

- 프레임워크: React, Vite
- 언어: TypeScript
- 확장 규격: Chrome Extension Manifest V3
- 주요 구성: Popup, Content Script, Service Worker
- 실행: `pnpm --filter @focuson/extension dev`
- 빌드: `pnpm --filter @focuson/extension build`

## 현재 데이터 흐름

```text
Chrome 탭
  → Content Script / tabs API
  → Service Worker
  → Desktop 또는 Server
  → 관련성 판단·집중 상태·리포트
```

## 반드시 지켜야 하는 조건

- Manifest 권한은 필요한 범위만 요청한다.
- 사용자가 지정한 제외 도메인에서는 본문 분석과 전송을 중지한다.
- 비밀번호, 결제 정보, 개인 메시지 등 민감한 본문을 기본 수집하지 않는다.
- 페이지 이동이나 탭 변경이 발생해도 중복 이벤트를 최소화한다.
- Desktop 연결 실패가 Chrome 페이지 동작을 막지 않아야 한다.
- 기능 추가 전 `features/` 안에 기획서를 먼저 만든다.

## 참고 문서

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

