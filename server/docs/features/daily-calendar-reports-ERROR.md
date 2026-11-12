# 일간·달력 리포트 개발 오류

## 기존 미구현 경로 HTTP 테스트 충돌

- 전체 build 최초 실행에서 `MissingEndpointHttpTests.authenticatedMissingEndpointIsNotRelogin`이404 대신400을 받아 실패했다.
- 원인: 테스트가 `/api/v1/reports/calendar`를 미구현 경로 fixture로 사용했으나 이번에 실제 API가 되어 필수 기기 헤더 누락400이 정상 반환됐다.
- 수정: 해당 테스트 하나의 요청 경로를 `/api/v1/missing-endpoint-fixture`로 변경했다. 404 공통 오류·인증 재요청 오판 방지 검증은 유지했다. 나머지 인증 실패 테스트의 달력 경로는 그대로다.
- 보완 후 전체 build/Checkstyle에서 실패0을 확인했다. 최종 수치는 QA 문서를 따른다.

## 개발 도구·스타일

- Gradle 기본 실행은 사용자 캐시 lock 접근 권한으로 실패해 승인된 확장 권한으로 실행했다. Docker socket 조회도 동일하게 확장 권한으로 수행했다. 사용자 서비스는 중지하지 않았다.
- Checkstyle의 지역 변수 사용 거리 및 import 정렬 지적을 보완하고 Main/Test 검사를 통과했다.
- Notion 두 정본 fetch는404였다. 조회 실패를 실제 확인으로 취급하지 않고 사용자에게 구현 기준을 승인받았다. Chrome은 실행하지 않았다.
