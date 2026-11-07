# 분석 요청 개발 오류 기록

## AI가 헤더 후 본문 전송을 멈추면 timeout을 초과함

- 일시: 2026-11-08 / #171
- 심각도: High(외부 서비스 지연으로 요청 처리 자원 장기 점유)
- 재현: 루프백 AI stub이 HTTP200 헤더와 `{` 한 글자만 보내고 본문을 10초 동안 닫지 않는다. 응답 본문 크기는8KB 제한보다 작다.
- 최초 결과: `HttpRequest.timeout(5초)` + 동기 send/body subscriber 구성에서 8초 이내 종료 assertion이 실패했다. 헤더를 수신한 뒤 본문이 멈추는 경우까지 해당 timeout이 제한한다고 가정한 것이 원인이다.
- 수정: sendAsync의 본문 수신 완료 future를 get(5초)로 기다리고, timeout/중단 시 미완료 future를 cancel(true)한다. connect2초, header/request5초, body8KB 제한도 유지한다. 실패 시 원문 없이 WARN 후200 UNCERTAIN/ANALYSIS_UNAVAILABLE로 저장한다.
- 회귀: AnalysisAiClientTests.oversizedAndStalledResponsesCannotHoldRequestIndefinitely. 9KB 응답과 헤더 이후 본문 stall을 모두 검사한다. 검증 결과는 QA 문서에 기록한다.
- 수치는 로컬 합성 stub의 제한시간 검증이며 운영 AI 응답시간이나 처리량 개선 수치가 아니다.

## 개발 검증 중 정리한 항목

- 잘못된 MockMvc servletPath를 넣은 테스트 요청1건을 URL 명시로 수정했다. 제품 코드 실패와 구분한다.
- 새 Java 파일의100자 스타일 경고를 정리했다.
