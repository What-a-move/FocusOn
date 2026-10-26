# Desktop 인증 경쟁 수정 QA

- 날짜: 2026-10-27 / Issue #74 / `fix/74-desktop-auth-races`
- 기준: 최신 dev `65278a9`; Server·API·DB schema는 변경하지 않았다.
- 상태: 로컬 검증 완료. 원격CI·CodeRabbit은 연결 PR의 각 head 기준 결과로 확인한다.

## 수정 전후

같은27개 회귀를 수정 전 `65278a9`와 수정 후 컴파일 결과에 실행했다.

| 지표 | 수정 전 | 수정 후 | 조건 |
| --- | --- | --- | --- |
| 새 회귀 통과 | 7/27 | 27/27 | 동일 fixture·모의 API/vault, 각1회 |
| 새 회귀 실패 | 20/27 | 0/27 | 운영 장애율 아님 |
| restore+refresh의 같은 token 소비 | 2회 | 1회 | 응답 지연을 제어한 중첩1회 |
| logout 뒤 저장 token | 1개 | 0개 | 잔여20초·지연 refresh |
| 계정 전환 뒤 다른 계정 token으로 이전 PUT 재전송 | 1회 | 0회 | 공개 IPC 경계의 모의 만료 응답 |

기존 테스트만 통과하던 결함을 안전한 결과를 기대하는 회귀로 전환했다. 과거103개에 새27개를 추가했으며, 합성 재현을 실제 Google 인증 성공이나 운영 통계로 표현하지 않는다.

## 실행 결과

| 범위 | 결과·제한 |
| --- | --- |
| Desktop | 전체130개 pass, failure/error/skip0; Electron TS·preload·앱 TS·관련 lint 통과 |
| Backend | clean build·Checkstyle·전체58개 pass; source 변경 없음 |
| PostgreSQL17.11 | 전용DB28개 pass, Flyway V1/V2 success·Hibernate validate |
| 실제 jar HTTP·Desktop Keychain | 45건 pass. 임시DB·무작위loopback origin·합성 계정/기기 |
| 독립 리뷰 | Keychain 저장 실패 뒤 SIGNED_IN 고착 회귀1건 수정. 재검토에서 남은 actionable 구현 지적 없음 |

전체 Backend 58개와 PG 28개는 중복 검증을 포함하므로 독립 표본86개로 합산하지 않는다. 단위 회귀27개와 실제HTTP45건도 서로 다른 검증 범위다.

## 실제 연동 범위

- Google token endpoint의 실제 무효 code 거절401·사용자 미생성, 기존 rotation·reuse·logout·기기/연결Extension폐기·슬롯 반환·429/Retry-After/requestId를 재검증했다.
- 실제 refresh HTTP200 응답을 Desktop에 전달하기 전에 지연시킨 상태에서 logout을 시작했다. 저장 완료 후 logout204가 실행되고 Keychain null·family REVOKED임을 확인했다. 이전 일반 요청 추가 전송0회.
- 실제 refresh 응답 지연 중 restore를 호출해 HTTP refresh1회, 실제 auth/me 복구·회전 token 저장·logout정리를 확인했다.
- 폐기된 실제family로 auth/me401을 받아 SIGNED_OUT·Keychain삭제를 확인했다.
- 새로운 HTTP 경쟁 사례는 합성 세션으로 client를 시작했다. Google exchange 성공을 실제 호출한 것으로 표시하지 않는다.
- QA 자원은 생성한 임시 서버·DB·Keychain 항목만 정리했다. 기존 개발DB·사용자 .env·미커밋 파일은 보존했다.

## 한계와 다음 확인

- 일부 계정 전환/profile·restore 공개IPC 순서는 일반화면 클릭으로 재현한 것이 아니다. 기본30초 선갱신·10초요청 timeout은 특정 만료 경쟁의 발생 빈도를 낮추지만 계정 세대 검사를 대체하지 않는다.
- 실제 Google 계정 성공 → callback → exchange201 → Keychain → 재실행 → logout과 실제앱 UI는 본인 인증 후 별도 확인해야 한다.
- 전체 Desktop native 빌드의 기존 SwiftBridging 환경 복구·운영proxy·부하/침투/패키징 검증은 이번 통과 범위가 아니다.
- PR #60은 이미 dev 병합/Issue59종료다. 이번 수정은 새 Issue74이며 merge·종료는 별도 승인받는다.
