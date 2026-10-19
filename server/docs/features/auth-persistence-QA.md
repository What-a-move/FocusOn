# 인증 저장 모델 QA

- 검증일: 2026-10-19
- 관련 Issue: [#32](https://github.com/What-a-move/FocusOn/issues/32)
- 환경: Java 25.0.2, Spring Boot 4.1.1, PostgreSQL 17.11 Docker, H2 테스트 프로필
- 범위: 인증 저장 모델. 이번 Issue에는 HTTP 엔드포인트가 없어 API 정상·인증·오류 응답 검증은 해당하지 않는다.

## 실행 결과

| 항목 | 결과 |
| --- | --- |
| `./gradlew build test --no-daemon` | 통과 |
| Backend local 기동과 Hibernate `ddl-auto=validate` | 통과 |
| Flyway V1·V2 | `flyway_schema_history`에 `1:true`, `2:true` 확인 |
| V2 복합 FK | 사용자 A의 family와 사용자 B의 기기 조합은 `fk_token_families_device_owner` 위반으로 거부 |
| 정상 소유 관계 | 같은 사용자의 family·기기 조합은 INSERT 허용 |
| QA용 데이터 | 두 시나리오 모두 transaction rollback; 사용자·기기·family 행 0건 확인 |
| Repository 테스트 | 해제된 Google identity가 로그인 조회에서 제외되고 Refresh Token `issuedAt`이 생성됨 |

## 심각도별 발견 사항

- Critical: 없음.
- High: 없음. 코드 리뷰에서 발견한 소유 관계 결함은 V2 복합 FK로 수정 후 검증했다.
- Medium: 없음. 연결 해제된 Google identity 조회와 `issuedAt` 누락을 수정 후 검증했다.
- Low: 없음.

## 남은 확인

- PR 생성 뒤 Backend CI 통과 여부를 확인한다. PR 전에는 CI 통과라고 기록하지 않는다.
- Google OAuth 교환과 Refresh Token 회전의 HTTP 정상·오류 흐름은 후속 인증 API Issue에서 검증한다.
