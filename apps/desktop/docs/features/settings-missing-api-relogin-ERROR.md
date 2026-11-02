# Desktop 개발 중 오류 보고서

## 오류 기본 정보

- 오류 번호: settings-04
- 오류 식별명: missing-api-relogin
- 기능명: 홈 대시보드 → 설정 이동 (실제 서버 QA)
- 담당 영역: Frontend(`apps`)
- 대상 앱: Desktop
- 발생 일시: 2026-10-31
- 작성자: jae1
- 심각도: 높음 (로그인 직후 홈에서 로그아웃, 설정 화면에 갈 수 없음)
- 상태: 보류 (서버 수정 대기)
- 관련 Issue·PR: #80

## 오류 제목

- 온보딩을 마치고 홈으로 가면 바로 로그인 화면으로 돌아간다.

## 실행 환경

- macOS 버전: 26.6.1
- Mac 모델·칩: Apple M4
- Electron 버전: 44.1.0
- Node.js·pnpm: 24.14.1 · 11.19.0
- 화면 기록 권한: 허용
- 카메라 권한: 해당 없음

## 재현 방법

1. 로컬 Docker 서버(2026-10-30 dev)로 로그인하고 온보딩을 마친다.
2. 홈 화면이 열린다.

## 기대 결과

- 홈이 열리고, 서버에 없는 달력 API는 달력 칸에만 실패로 보인다.

## 실제 결과

- `요청을 마치지 못했어요. / 로그인 정보가 만료됐어요. Google로 다시 로그인해 주세요.`가 뜨고 로그인 화면으로 이동한다. 다시 로그인해도 반복된다.

## 오류 메시지

```text
desktop api.request.fail GET /api/v1/reports/calendar status=401 code=AUTH_RELOGIN_REQUIRED errorClass=auth
같은 요청의 다른 API(onboarding-readiness·consents·excluded-domains)는 같은 토큰으로 200
```

## 원인 및 해결

- 추정 원인: Access Token이 만료됐거나 Refresh Token 회전이 깨졌다.
- 실제 원인: 서버에 `/api/v1/reports/*`·`/sessions/*`·`/interventions/*`·`/goals/clarify`가 아직 없다. Spring은 없는 주소를 `/error`로 넘기는데 보안 설정 `anyRequest().denyAll()`에 막혀 `401 AUTH_RELOGIN_REQUIRED`로 응답한다(토큰 없이 불러도 같음). Desktop은 계약대로 이 코드를 받으면 저장된 로그인을 지우고 로그인 화면으로 보낸다.
- 해결 방법: 백엔드에 ① 없는 주소는 404로 응답 ② 위 API 구현을 요청했다. 이번 QA는 커밋하지 않은 임시 수정(일반 요청의 재로그인 응답에서 로그인을 지우지 않음)으로 진행했고 QA 뒤 되돌렸다. Desktop 코드는 바꾸지 않는다(계약대로 동작).
- 변경 파일: 없음

## 확인 결과

- [ ] 같은 방법으로 재현되지 않는다. (서버 수정 대기)
- [ ] 권한 허용·거부 양쪽을 확인했다. (해당 없음)
- [ ] 앱 재실행 후 정상 동작한다.
- [x] 관련 기능의 회귀가 없다. (코드 변경 없음)

## 재발 방지 및 남은 작업

- 서버 수정 뒤 홈 진입 시 로그아웃되지 않는지 다시 확인한다.
