# Access JWT 구현 계획

- Issue: [#25](https://github.com/What-a-move/FocusOn/issues/25)
- 기준: Notion `공통 정책 · 기술 계약 > 인증·토큰 정책`, `FocusOn API 명세서`
- 범위: 백엔드 내부 Access JWT 발급·검증. 이번 작업에 HTTP 로그인 엔드포인트는 없다.

## 계약

- 인증을 마친 사용자와 기기의 `userId`, `deviceId`, `tokenFamilyId`를 문자열 클레임으로 넣는다. 세 ID는 현재 ERD의 BIGINT 식별자다.
- 서버가 발급한 HS256 Access JWT의 수명은 15분이다. `iss=focuson`, `aud=focuson-api`, `tokenType=ACCESS`, `iat`, `exp`, `jti`를 포함한다.
- 검증 시 서명 알고리즘, 발급자, 대상, 만료, 필수 식별자와 토큰 종류를 확인한다. 만료 허용 오차는 0초다.
- 원문 토큰과 서명 키를 저장하거나 로그에 남기지 않는다. 서명 키는 실행 환경의 `JWT_SECRET_BASE64`로 제공한다.
- 기기·토큰 패밀리의 활성 상태와 폐기 여부는 이후 인증 저장 모델을 구현할 때 조회한다. JWT 검증만으로 그 상태를 보증하지 않는다.

## 제외 범위

- Google OAuth/ID token 교환, 로그인 Controller, Refresh Token, DB 스키마, 인증 필터

## 완료 기준

- [x] 발급·검증 서비스와 설정을 추가한다.
- [x] 서명 키 누락·형식 오류·짧은 키에서 시작을 중단한다.
- [x] Java 컴파일과 Checkstyle을 통과한다.
- [ ] 인증 저장 모델과 로그인 API 연결은 후속 Issue에서 진행한다.
