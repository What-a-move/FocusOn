# 확장 접근 정책 구현 결과

- 기존 #151을 재사용하고 `feat/151-extension-access`에서 사용자 담당 1·2번의 server 코드만 변경했다.
- `CORS_ALLOWED_ORIGINS`에 등록한 정확한 Chrome 확장 origin만 API CORS를 허용한다. local 개발 ID 기본값, docker 기본 빈 값·Compose 전달, 쿠키 비활성·기존 JWT 인증을 적용했다.
- 같은 계정의 새 설치가 연결되면 이전 확장 device/family/refresh를 같은 트랜잭션에서 폐기한다(family 사유 REPLACED). 같은 설치는 deviceId를 재사용한다.
- V11은 기존 복수 연결을 데이터 삭제 없이 정리하고 계정당 ACTIVE 확장 1개를 보장하는 partial unique를 추가한다. 기존 migration은 수정하지 않았다.
- H2 전체 135개 중 실패0·생략4, 분리 PostgreSQL 30개 모두 통과, Checkstyle 통과. 마이그레이션의 기존 데이터 보존과 DB 제약 거절도 별도 SQL fixture로 확인했다. 상세는 extension-access-QA.md를 따른다.
- 실제 Chrome 권한 철회·자동 멈춤 QA, 독립 리뷰와 PR은 남아 있다. #163/PR164 세션 종료 작업은 이번 브랜치에 포함하지 않았다.

## PR #170 리뷰 반영

- 제약 전체 스캔을 V11의 강한 잠금 transaction에서 분리했다. V11 NOT VALID 추가→커밋→V12 VALIDATE 순서다. 기존 정리·index 생성 잠금은 유지된다.
- 연결·refresh POST preflight 테스트2개를 추가해 허용 origin·POST·요청 헤더를 검증했다.
- 수정 후 H2전체137개 중 실패0·skip4, Checkstyle 통과. 분리 PostgreSQL32개 모두 통과 및 Flyway V11/V12 success=true·최종 제약 유효 상태 확인. 주석 부족 경고에 따라 연결 순서와 검증 의도 설명도 보완했다.
