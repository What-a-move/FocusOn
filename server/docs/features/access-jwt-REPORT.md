# Access JWT 작업 결과

- Issue: [#25](https://github.com/What-a-move/FocusOn/issues/25)
- 구현: 15분 Access JWT 발급·검증 서비스, 환경별 서명 키 주입, Docker Compose 전달
- 설정: 로컬 실행 전 `JWT_SECRET_BASE64`를 32바이트 이상 난수의 Base64 값으로 설정한다. `server/.env.example`에 생성 방법을 적었다.
- 확인: 로컬 `./gradlew test checkstyleMain checkstyleTest` 통과. PR #26의 기존 Backend CI `Build and test server` 통과.
- 리뷰 반영: JWT 관련 주석을 한글로 바꾸고 최신 `dev`를 병합해 충돌을 해결했다. 후속 검토에서 발급·검증의 저장소 책임 경계를 Javadoc에 명시하고 JWT 단위 테스트를 추가했다.
- 연결 지점: 로그인·갱신 흐름은 인증된 `userId`, `deviceId`, `tokenFamilyId`로 `issue()`를 호출한다. 요청 인증 흐름은 `verify()`로 토큰을 확인한 뒤 저장소에서 세 식별자의 현재 활성·폐기 상태를 조회한다. `verify()`만으로 권한을 확정하지 않는다.
- 후속 작업: 사용자·기기·토큰 패밀리 저장 모델 및 Google 로그인 API가 구현되면 이 서비스를 연결한다. 실제 요청 인증에는 활성 기기·패밀리 확인이 필요하다.
