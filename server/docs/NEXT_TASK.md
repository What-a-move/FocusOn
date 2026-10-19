# Backend 다음 작업

## 작업명

Issue #32 로그인용 PostgreSQL 저장 모델 구현

- 우선순위: 높음
- 상태: `feat/#32-auth-persistence`에서 구현·독립 코드 리뷰·QA 완료. PR 생성과 CI 확인이 남음.

## 목적

확정된 FocusOn API 명세를 기준으로 도메인 API를 안전하게 추가할 수 있는 서버 기반을 만든다.

## 순서

1. Issue #32의 PR을 `dev` 대상으로 생성하고 Backend CI를 확인한다.
2. 후속 Issue에서 Google Authorization Code + PKCE 교환과 Refresh Token 정책을 구현하고 기존 Access JWT 서비스에 연결한다. Spring Security 필터의 401·403도 공통 오류 응답에 연결한다.
3. 목표·세션·분석 제외 도메인·heartbeat·개입 정책 버전·`studyDate`·학습 정리는 각 도메인 작업의 migration과 Entity로 분리한다.
4. 목표와 세션 MVP API를 구현한다.
5. 분석·개입 정책과 일간 리포트·집중 달력·학습 정리 API를 연결한다.
6. P1인 세션 목록과 주간 리포트는 MVP 안정화 뒤 구현한다.

## 완료 조건

- [ ] 관련 `*-PLAN.md`가 있다.
- [ ] 구현 경로·필드·권한이 Notion 명세와 일치한다.
- [ ] migration이 빈 DB와 기존 개발 DB에 적용된다.
- [ ] 정상·검증·인증·충돌 응답을 확인했다.
- [ ] 토큰과 민감 데이터가 로그에 없다.
- [ ] `*-REPORT.md`에 결과와 남은 위험을 기록했다.
