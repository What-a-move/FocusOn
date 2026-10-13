# Access JWT 작업 결과

- Issue: [#25](https://github.com/What-a-move/FocusOn/issues/25)
- 구현: 15분 Access JWT 발급·검증 서비스, 환경별 서명 키 주입, Docker Compose 전달
- 설정: 로컬 실행 전 `JWT_SECRET_BASE64`를 32바이트 이상 난수의 Base64 값으로 설정한다. `server/.env.example`에 생성 방법을 적었다.
- 확인: `./gradlew compileJava checkstyleMain` 통과. 요청받지 않은 테스트는 추가하거나 실행하지 않았다.
- 후속 작업: 사용자·기기·토큰 패밀리 저장 모델 및 Google 로그인 API가 구현되면 이 서비스를 연결한다. 실제 요청 인증에는 활성 기기·패밀리 확인이 필요하다.
