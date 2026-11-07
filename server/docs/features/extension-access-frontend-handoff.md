# Frontend 전달: CORS·확장 연결 1개 (#151)

- `CORS_ALLOWED_ORIGINS`에 정확한 `chrome-extension://<확장ID>`를 콤마로 지정한다. local은 생략하면 `chrome-extension://fbhlmioghblalhmcokcpoapjpnjccbde`를 허용한다. docker는 생략하면 전체 불허이므로 배포 ID를 명시해야 한다.
- API 필드 변경 없음. 허용 메서드는 GET/POST/PUT/DELETE/OPTIONS이며 요청 헤더는 Authorization/Content-Type/Idempotency-Key/If-Match-Version/X-Device-Id/X-Trace-Id, 응답 노출 헤더는 Retry-After다.
- 같은 계정의 다른 설치가 연결되면 이전 확장은 다음 요청에서401 LINK_TOKEN_REVOKED를 받는다. 기존 토큰을 지우고 재연결 화면으로 진입한다. 같은 설치 재연결은 deviceId를 재사용한다.
- 권한 철회 후 서버 OPTIONS가 CORS 허용을 응답해도 Chrome이 host 권한 때문에 요청 자체를 차단하는지는 실제 Chrome QA가 필요하다. 요청이 서버에 도달하지 않는 문제와 서버403/401을 구분해 traceId로 확인한다.
- local-events에 따른 세션 자동 멈춤은 5번 담당 구현과 함께 별도 QA한다. 이번 변경만으로 자동 멈춤 완료를 주장하지 않는다.
- 기존 여러 확장 중 인증 가능한 연결을 우선하고 그 안에서 최신1개를 남기는 V11이 있어 적용 시 이전 브라우저 연결이 끊길 수 있다. Backend 배포·재시작과 프론트 QA 일정을 맞춘다.
