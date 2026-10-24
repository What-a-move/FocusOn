# Backend 다음 작업

## 작업명

Issue #49 로그인 API 마무리 및 후속 인증 준비

- 우선순위: 높음
- 상태: Issue #49의 Google 교환·auth/me·요청 인증 구현 및 검증 완료. PR #51 생성 후 CodeRabbit 지적 반영 중. dev 병합 전이다.

## 목적

확정된 FocusOn API 명세를 기준으로 도메인 API를 안전하게 추가할 수 있는 서버 기반을 만든다.

## 순서

1. PR #51의 CodeRabbit 지적 반영·독립 리뷰·QA를 확인하고 병합 승인을 받는다. 사용자 승인 없이 merge·Issue 종료하지 않는다.
2. 후속 Issue에서 Refresh Token 회전·재사용 감지와 로그아웃을 구현하고, 이어서 기기 목록·폐기 API를 구현한다. Issue #38 사용자·목표 API와는 인증·기기 검증 인터페이스를 공유한다.
3. 목표·세션·분석 제외 도메인·heartbeat·개입 정책 버전·`studyDate`·학습 정리는 각 도메인 작업의 migration과 Entity로 분리한다.
4. 목표와 세션 MVP API를 구현한다.
5. 분석·개입 정책과 일간 리포트·집중 달력·학습 정리 API를 연결한다.
6. P1인 세션 목록과 주간 리포트는 MVP 안정화 뒤 구현한다.

2026-10-25까지의 공유 계약은 `docs/API_CONTRACT.md`와 결정 017~019를 따른다. 알림은 Desktop 요청 전 확인, MVP 목표 시간은 null 거절, 목표 AI 재시도는 Backend 책임이다. pairing·세션·기기 관리 구현 전에는 `docs/NOTION_SYNC_AUDIT.md`의 시도 문맥·4번째 기기 복구 인증·rolling window 경계·토큰 family 만료 기준를 확인한다. 해당 항목을 로그인 교환 Issue의 구현 완료 조건으로 혼합하지 않는다.

## 완료 조건

- [x] 관련 `google-login-PLAN.md` 초안이 있다.
- [x] Issue 생성·기획 승인을 받았다.
- [x] 구현 경로·필드·권한이 Notion 명세와 일치한다.
- [x] 별도 PostgreSQL QA DB에서 기존 V1·V2 적용·Hibernate validate를 확인했다. 새 migration은 없다.
- [x] 정상·검증·인증·충돌 응답을 확인했다.
- [x] 토큰과 민감 데이터가 로그에 없다.
- [x] `*-REPORT.md`에 결과와 남은 위험을 기록했다.
