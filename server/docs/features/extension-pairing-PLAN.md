# Extension 연결 기능 기획서

## 기본 정보

- 기능명: Desktop–Extension 일회용 코드 연결 / Issue #58
- 작성·확정: 2026-10-28, 사용자 정책3개·Notion 정본 반영 승인
- Branch: feat/#58-extension-pairing / 기준 origin/dev b293bf1
- 범위: server/만. 취소 API를 같은 연결 사용 사례에 포함한다.
- 제외: Frontend·닉네임 unique·프로필·readiness(#38)·기기 관리·Extension unlink·AI.

## 목적과 API 설계 판단

Google 로그인한 현재 Desktop만5분 일회용 코드를 발급하고, 공개 Extension 요청이 별도 기기·token family를 얻는다. 원본 계약 준수, 사용자·호출 기기 소유권 검증, UTC/string ID·오류 envelope, 원문 비저장을 유지한다. 발급/연결/취소는 각각 하나의 사용 사례다. 세션용 idempotency/version 헤더는 요구하지 않는다.

## API 계약

### POST /api/v1/pairing-codes

- 인증: 현재 활성 Desktop Bearer, 온보딩 INCOMPLETE 허용. body 없음.
- 201: data.{pairingCodeId:string,codeDisplay:"ABC-123",expiresAt:UTC ISO8601,maxAttempts:3}.
- 표시 코드 대문자 영문·숫자6자리,5분,1회용. 기존 ACTIVE는 재발급/로그아웃/같은 설치 재로그인에서 취소한다.
- maxAttempts는 Extension 설치별 오입력 한도다. 코드별 실패로 다른 Desktop 코드를 폐기하지 않는다.
- 한도: user+Desktop3회/10분, 일반 인증60회/분도 공유한다. 인증401, 한도429+Retry-After.

### POST /api/v1/extension-pairings

- 인증 전 공개 요청. Bearer는 사용하지 않는다. 요청: {pairingCode,installationId}.
- 코드 원문6자리/표시3-3, 양끝 공백 제거·대문자 정규화. 설치 ID 필수·최대128자.
- 201: data.{accessToken,refreshToken,accessTokenExpiresAt,device:{deviceId:string,deviceType:"EXTENSION"}}.
- 내부 device_type=CHROME_EXTENSION, 기본 display_name=Chrome Extension.
- Access15분, 독립 family/refresh30일. 갱신 성공마다 token+family30일 연장은 기존 refresh 서비스 계약이다.
- IP+installation10회/10분. JSON 바인딩 전 별도 익명 공간의 IP300회/10분 보호. 기존 인증 사용자 bucket을 소모하지 않는다. 단일 서버 fixed-window 한도이며 proxy 헤더는 신뢰하지 않는다.

| 상황 | HTTP / code | 부가 처리 |
| --- | --- | --- |
| 코드 형식 | 400 LINK_001_INVALID_FORMAT | DTO 일반 validation으로 바꾸지 않음 |
| 만료 | 410 LINK_002_EXPIRED | EXPIRED commit, terminal_at=expires_at |
| 이미 소비 | 409 LINK_003_ALREADY_USED | 재소비 불가 |
| 설치별3회 실패/잠금 중 | 410 LINK_004_ATTEMPTS_EXCEEDED | remainingAttempts0·retryAfterSeconds·Retry-After, retryable=false |
| 취소/발급 Desktop 비활성 | 410 LINK_005_CANCELLED | 필요 시 CANCELLED commit |
| 형식 정상·존재하지 않음1~2회 | 400 LINK_006_CODE_MISMATCH | remainingAttempts2/1, retryable=false |
| 다른 계정 활성 설치 | 409 LINK_007_INSTALLATION_ALREADY_LINKED | 코드 미소비·소유자 정보 미노출 |

- 실패는 installationId 해시별 누적. 3회 후180초 잠금이며 유효 코드로도 우회하지 못한다. 성공/잠금 만료 시 reset. 미완료1~2회는 마지막 실패 후24시간이 지나면 초기화하고 정리한다. 새 실패가 생기면 기준 시각을 갱신한다.
- 같은 사용자 설치는 deviceId 재사용·이전 family와 ACTIVE refresh 폐기 뒤 새 Desktop 연결/새 family 발급. 다른 사용자 활성 설치는 먼저 unlink해야 한다.

### DELETE /api/v1/pairing-codes/{pairingCodeId}

- 현재 활성 Desktop Bearer, body 없음. 본인 발급 ACTIVE에만 효과가 있다.
- 204 body 없음. 없는 ID·타 Desktop 소유·이미 terminal도 동일한 no-op. 인증 자체가 잘못됐으면401.
- 소비가 먼저 확정됐다면 취소 요청은 token/연결을 폐기하지 않는다.
- 정상 화면 닫기·앱 종료는 이 요청으로 즉시 취소한다. 강제 종료·충돌·전원 차단·통신 미전달은 발급 후5분 만료다.

## 저장 모델

- V1/V2 유지, 신규 V5__add_device_pairing.sql.
- pairing_codes: BIGINT id/user_id/issuer_desktop_device_id, CHAR64 code_hash UNIQUE, ACTIVE/CONSUMED/EXPIRED/CANCELLED, expires_at/created_at, consumed_by_extension_device_id/consumed_at/cancelled_at/terminal_at nullable. UTC는 PostgreSQL TIMESTAMPTZ다.
- user_id 및 발급·소비 기기 (id,user_id) 복합 FK. 상태별 필수·NULL timestamp CHECK, Desktop당 ACTIVE코드1개 partial UNIQUE, expiry/terminal 정리 인덱스.
- pairing_installation_attempts: CHAR64 installation_id_hash PK, failed_attempts SMALLINT0~3, locked_until nullable, updated_at NOT NULL. count3과 잠금시각의 동시 유무 CHECK. 원문 설치 ID는 미저장.
- user_devices: 기존 사용자별 설치 UNIQUE 유지, ACTIVE Extension installation hash 전역 partial UNIQUE와 연결 Desktop 소유 복합 FK 추가. 대상 DESKTOP 타입은 서비스 사용자 잠금 안에서 검증한다.
- 코드 원문 대신 별도 고정 HMAC-SHA256 키를 PAIRING_SECRET_BASE64로 설정한다. JWT 키와 분리하며 배포 간 유지한다. 무중단 키 교체는 이번 범위가 아니며 데이터/코드 전환 절차로 따로 검토한다.
- 원문 코드·설치 ID·JWT/refresh·key는 DB/로그/DTO문자열/오류details에 남기지 않는다. 응답에서만 원문 제공.

## 트랜잭션·동시성

1. 발급/취소: 사용자→현재 family→코드. 현재 principal의 user/device/family·활성/만료를 다시 검사한다.
2. claim: 설치 실패 행→사용자→코드. 기존 auth의 family 변경도 사용자 잠금을 선행하므로 같은 사용자의 family 변경과 직렬화된다. 잠금 전 코드는 ID projection만 읽고 상태 Entity는 잠금 뒤 읽는다.
3. 최초 설치 행은 REQUIRES_NEW INSERT, PK 경쟁만 실패 transaction 바깥에서 재조회한다. assigned-ID merge로 기존 카운터를 덮지 않는다. cleanup과 행 생성 경쟁은 제한3회 재시도한다.
4. 실패/만료/취소 결과는 transaction outcome으로 반환해 commit 후 ApiException을 던진다.
5. 재연결은 이전 ACTIVE family·refresh 폐기/flush 후 새 family·refresh를 발급한다. 코드 소비까지 한 transaction이다.
6. HMAC hash UNIQUE 충돌은 해당 제약만 실패 transaction 바깥에서 새 코드로 최대3회 생성 시도한다.
7. cleanup은 코드 만료/30일 삭제와 설치 행 정리를 서로 다른 transaction으로 실행해 claim의 설치→코드 순서를 역전하지 않는다. EXPIRED의 terminal_at은 실제 expires_at이며 보관 기간을 worker 실행 시각으로 늘리지 않는다.
8. terminal 코드30일 보관 후 삭제. reset 완료 행은30일 후·잠금 행은 만료 후 정리할 수 있다. 미완료1~2회는 마지막 실패 후24시간이 지나면 삭제한다. 경계에서 cleanup 실행 여부와 관계없이 최초 재입력 때 초기화한다.

## 리뷰·검증·완료 조건

- [x] 코드 작성 전 독립 설계 리뷰7개 항목과 사용자 승인 반영
- [x] Notion API/인증 정책/오류/ERD 반영·저장 내용 확인
- [x] 세 API·저장 모델·기존 auth 연동 구현
- [x] 독립 diff 리뷰·P2 수정 후 재리뷰
- [x] 정상/형식/만료/재사용/취소/실패 commit/180초/성공 reset
- [x] 같은 코드1승자·다른 계정 같은 설치1승자·cancel/logout 경쟁·재연결 이전 token 폐기
- [x] PostgreSQL migration/validate·FK/CHECK/UNIQUE 거절·실제HTTP
- [x] 서버 문서·Frontend handoff·Issue 진행 상태 갱신
- [ ] 실제 Frontend UI 연동 및 PR/CI (별도 사용자 승인 후)

## 정본

- API DB: https://app.notion.com/p/3e00b6e5e64c80e89eb1e64017029210
- 취소: https://app.notion.com/p/4060b6e5e64c80e3accdc21737395c52
- 인증: https://app.notion.com/p/3de0b6e5e64c80c89c6cfe0b703fd964
- 오류: https://app.notion.com/p/3de0b6e5e64c80f6bac6d980ba0c1df5
- ERD: https://app.notion.com/p/3de0b6e5e64c80a4bf5ef7ff3ac01f17
