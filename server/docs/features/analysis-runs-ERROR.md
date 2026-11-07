# 분석 요청 개발 오류 기록

## AI가 헤더 후 본문 전송을 멈추면 timeout을 초과함

- 일시: 2026-11-08 / #171
- 심각도: High(외부 서비스 지연으로 요청 처리 자원 장기 점유)
- 재현: 루프백 AI stub이 HTTP200 헤더와 `{` 한 글자만 보내고 본문을 10초 동안 닫지 않는다. 응답 본문 크기는8KB 제한보다 작다.
- 최초 결과: `HttpRequest.timeout(5초)` + 동기 send/body subscriber 구성에서 8초 이내 종료 assertion이 실패했다. 헤더를 수신한 뒤 본문이 멈추는 경우까지 해당 timeout이 제한한다고 가정한 것이 원인이다.
- 수정: sendAsync의 본문 수신 완료 future를 get(5초)로 기다리고, timeout/중단 시 미완료 future를 cancel(true)한다. connect2초, header/request5초, body8KB 제한도 유지한다. 실패 시 원문 없이 WARN 후200 UNCERTAIN/ANALYSIS_UNAVAILABLE로 저장한다.
- 회귀: AnalysisAiClientTests.stalledRequestClosesConnectionBeforeStubCleanup / oversizedValidDecisionIsRejectedByByteLimit. 9KB 응답과 헤더 이후 본문 stall을 모두 검사한다. 검증 결과는 QA 문서에 기록한다.
- 수치는 로컬 합성 stub의 제한시간 검증이며 운영 AI 응답시간이나 처리량 개선 수치가 아니다.

## 개발 검증 중 정리한 항목

- 잘못된 MockMvc servletPath를 넣은 테스트 요청1건을 URL 명시로 수정했다. 제품 코드 실패와 구분한다.
- 새 Java 파일의100자 스타일 경고를 정리했다.

## Chrome DOM host 누락으로 제외 도메인 검사가 생략됨

- PR #172 CodeRabbit Major 지적: guard 없는 CHROME_DOM 본문에서 metadata 또는 urlHost가 누락/빈 값이면 host가 빈 문자열이 돼 제외 목록 검사를 건너뛰었다. AI가 설정돼 있으면 제외 사이트의 본문도 외부로 보낼 수 있었다.
- 수정: CHROME_DOM+contentPayload+guard 없음 조합은 유효한 host를 요구한다. 누락/빈 값은 저장·AI 호출 전에400 VALIDATION_ERROR다. 보호 상태만 보내는 요청과 metadata source의 기존 처리는 유지한다.
- 회귀: AnalysisApiTests.chromeDomBodyRequiresHostBeforeStorageOrAi. metadata 없음/host null/host 빈 값3종을 거절하고 저장 증가0·AI 호출0을 확인한다. host 없는 EXCLUDED/PRIVACY_BLOCKED는200이다.

## AI 크기 제한·취소 테스트가 보호 코드 제거를 잡지 못함

- CodeRabbit 테스트 지적2건: 과대 응답이 잘못된 JSON이면 byte limit이 없어도 schema 오류로 실패하며, timeout fallback만 검사하면 실제 취소가 없어도 통과할 수 있었다.
- 수정: 정상 판정 JSON 앞에 공백9,000개를 넣어8KB 상한만 제거했을 때 정상 판정으로 통과할 수 있게 만든다. raw loopback socket에서 헤더+본문1글자 후 멈추고 cleanup 전에 client의 EOF/reset을 확인한다.
- 실제 보호 코드 제거 실험과 최종 회귀 결과는 QA에 기록한다. 프로덕션 구현은 실험 뒤 복원한다.
