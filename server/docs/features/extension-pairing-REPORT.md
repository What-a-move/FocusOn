# Extension 연결 구현 결과

- Issue #58 / feat/#58-extension-pairing / 2026-10-28 / 기준 dev b293bf1
- 기획: extension-pairing-PLAN.md. PR 미생성·미병합.

## 구현

- POST pairing-codes / POST extension-pairings / DELETE pairing-codes/{pairingCodeId}.
- V3: pairing_codes·pairing_installation_attempts, parent/issuer/consumer 소유권 FK, 활성 Extension 설치 전역 UNIQUE·코드 상태/정리 인덱스. V1/V2 변경 없음.
- 설치별3회 실패→3분 잠금, 정상 취소·통지 불가5분 만료, 같은 사용자 재연결·다른 사용자 활성 충돌 정책.
- 공개 필터와 익명 한도 분리, 로그인/로그아웃의 이전 코드 취소, 별도 HMAC 키와 보관 worker.
- 이름·token 형식은 기존 인증 API와 공유한다. 닉네임·프로필·readiness·Frontend·AI는 수정하지 않았다.

## 검증

- [x] 정상·형식·누락·인증·권한·없는 ID·중복/기소비
- [x] 저장·조회·상태 변경·보관 정리·트랜잭션·제약
- [x] 전체 build/Checkstyle·JUnit76, PostgreSQL14, 실제 HTTP33, 제약6
- [x] 독립 코드 리뷰·P2 수정 후 재검토
- [ ] 실제 Desktop·Extension UI 통합 QA (handoff 필요)
- [ ] 원격 PR/CI·CodeRabbit (PR 생성 별도 승인)

세부 증거: extension-pairing-QA.md / code-review.md / ERROR.md. 성능 측정은 하지 않았다. 2개 동시 요청 경쟁만 검증했으며 부하 테스트로 표현하지 않는다.

## 문서·정본

API 발급·연결·취소, 인증 정책6절, 공통 오류6절, ERD3.3/3.5에 승인 계약을 반영했다. 취소 API 정본: https://app.notion.com/p/4060b6e5e64c80e3accdc21737395c52

- [x] CONTEXT / NEXT_TASK / DECISION_RECORD / DEVELOPMENT_RULES 갱신
- [x] Frontend 전달 문서 작성
- [ ] PR 연결·dev 병합 뒤 구현 상태 재확인

## 설정

PAIRING_SECRET_BASE64는 JWT와 다른32바이트 이상 Base64 키다. 이 작업의 ignored local .env에는 생성해 설정했고 Git에는 포함하지 않는다. 팀원/배포 환경은 .env.example을 따라 별도 안전한 키를 설정하고 배포 간 유지해야 한다. 기존 실행 명령은 bootRun 그대로다.
