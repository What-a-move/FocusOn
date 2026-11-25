# Extension 개발 중 오류 보고서

> Extension 오류는 Chrome 버전, 권한, 탭 상태를 포함해 재현 가능한 정보로 기록한다.
> URL의 토큰, 개인 데이터, 페이지 본문 원문은 기록하지 않는다.

## 오류 기본 정보

- 오류 번호: session-popup-004
- 오류 식별명: cors-shown-as-offline
- 기능명: 세션 팝업 서버 연결 상태
- 담당 영역: Frontend(`apps`) · 로컬 서버 설정
- 대상 앱: Chrome Extension
- 발생 일시: 2026-11-24
- 작성자: jae1
- 심각도: 높음 (Extension이 서버와 통신하지 못하는데 원인이 인터넷 끊김으로 보여 찾지 못함)
- 상태: 해결 (팝업 문구) · 로컬 `server/.env` 설정은 각자 확인 필요
- 관련 Issue·PR: extension-host-permission-cors-ERROR.md(2026-11-03)와 같은 뿌리

## 오류 제목

- 서버 CORS가 Extension 요청을 막아도 팝업이 "서버에 연결할 수 없어요 · 인터넷 연결을 확인해 주세요"로 보임

## 실행 환경

- macOS 버전: 26.6.1 (Darwin 25.6.0)
- Chrome 버전: 사용자 Chrome
- Extension 버전: 0.1.0 (`out/`, API origin `http://127.0.0.1:8080`)
- Desktop 연결 상태: 연결 (Desktop은 같은 로컬 서버와 정상 통신)
- 권한 상태: 필수 host 권한 `http://127.0.0.1:8080/*` 있음

## 재현 방법

1. `server/.env`에 `CORS_ALLOWED_ORIGINS` 없이 `docker compose up -d`로 로컬 서버를 띄운다.
2. Extension 팝업을 연다.
3. "다시 연결"을 눌러도 같은 안내가 반복된다.

## 기대 결과

- 서버가 Extension origin을 막았다는 것을 팝업에서 바로 알 수 있다.

## 실제 결과

- 팝업 상단 "서버 연결 끊김", 본문 "서버에 연결할 수 없어요. 인터넷 연결을 확인해 주세요."
- 인터넷·서버는 정상이었다(Desktop 요청 200, `/actuator/health` 200).

## 오류 메시지

```text
backend http.out POST /api/v1/auth/refresh status=403 errorClass=auth   (반복)
curl -X POST /api/v1/auth/refresh -H 'Origin: chrome-extension://fbhlmioghblalhmcokcpoapjpnjccbde' → 403 "Invalid CORS request"
curl -X POST /api/v1/auth/refresh (Origin 없음) → 401 {"code":"AUTH_RELOGIN_REQUIRED"}
backend 컨테이너 CORS_ALLOWED_ORIGINS: 비어 있음
```

## 원인 및 해결

- 추정 원인: 앱을 다시 켜며 새 연결 코드가 발급돼 Extension 토큰이 무효가 됨.
- 실제 원인: 추정과 달랐다. 두 가지가 겹쳤다.
  1. 로컬 백엔드의 `CORS_ALLOWED_ORIGINS`가 비어 있어 Spring이 Extension origin 요청을 본문 `Invalid CORS request`(JSON 아님)와 403으로 막았다. compose는 값이 없으면 빈 값으로 띄운다.
  2. Extension API client는 코드 없는 403을 `REQUEST_FAILED`로 만들고, 팝업은 `RELINK_REQUIRED`가 아닌 모든 실패를 "인터넷 연결 확인"으로 보여 줬다. 그래서 원인이 보이지 않았다.
- 해결 방법:
  - 코드 없는 403을 `ORIGIN_NOT_ALLOWED`로 구분 (`api-client.ts`, `pairing-api.ts`).
  - 팝업이 실패 원인별로 안내: 네트워크·시간 초과 / 서버가 요청을 막음(오류 코드 표시) / 서버 오류 / 그 밖(오류 코드 표시).
  - 로컬 서버: `server/.env`에 `CORS_ALLOWED_ORIGINS=chrome-extension://fbhlmioghblalhmcokcpoapjpnjccbde` (`.env.example`과 같은 값, 고정 Extension ID).
- 변경 파일: `shared/api/api-client.ts`, `shared/api/pairing-api.ts`, `features/session/SessionPopup.tsx`, `tests/session-client.test.ts`

## 확인 결과

- [x] `CORS_ALLOWED_ORIGINS`를 넣어 띄운 백엔드에 같은 origin 요청 → 401 JSON (CORS 통과).
- [x] 단위 테스트: 코드 없는 403 → `ORIGIN_NOT_ALLOWED`, 갱신이 CORS로 막혀도 연결 정보를 지우지 않음.
- [ ] 실제 Chrome 팝업에서 CORS 거절 안내 화면 확인.
- [x] 관련 기능의 회귀가 없다 (`pnpm test` 37개 통과).

## 재발 방지 및 남은 작업

- 팀원 모두 `server/.env`에 `CORS_ALLOWED_ORIGINS`가 있는지 확인한다. 없으면 Extension만 조용히 끊긴다.
- 팝업 "로그 복사"의 `api.request.fail` 줄에 `status`와 `code`가 함께 남는다. 같은 안내가 반복되면 이 두 값으로 먼저 원인을 가른다.
