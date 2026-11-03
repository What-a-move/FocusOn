# Backend Issue 본문 템플릿

<!--
이 템플릿은 Server 작업에만 사용한다.
Frontend, AI, Shared 작업은 루트 .github/ISSUE_TEMPLATE.md를 따른다.
본문 작성 후 안내 주석은 삭제한다.

권장 Label:
- 영역: area:server
- 유형: type:feature / type:bug / type:refactor / type:docs / type:test / type:chore
- 우선순위: priority:mvp-core / priority:mvp-basic / priority:enhancement
-->

## 배경

<!-- 작업이 필요한 이유와 현재 문제를 설명한다. -->


<!-- 관련 기획서, API, Entity와 참고 문서를 작성한다. -->

관련 문서와 구현 대상은 다음과 같다.

- 기획서: `server/docs/features/{기능명}-PLAN.md`
- 관련 API: `METHOD /api/v1/...`
- 관련 Entity: `{EntityName}`
- 참고 문서: `server/docs/...`

## 작업 범위

<!-- Issue 하나에 엔드포인트 1~2개 또는 기존 로직 수정 1건만 포함한다. -->

- [ ] `{Entity}` 및 Flyway migration 추가·수정
  - [ ] 제약 조건과 Index 반영
  - [ ] 기존 데이터와 migration 호환성 확인
- [ ] `METHOD /api/v1/...` 구현
  - [ ] 요청 DTO와 검증
  - [ ] Service·Repository 로직
  - [ ] 성공·오류 응답
- [ ] 정상·예외 테스트
  - [ ] 정상 흐름
  - [ ] 인증·권한·검증 실패
  - [ ] 충돌·중복 요청

## 완료 조건 (Definition of Done)

- [ ] 로컬 Build·Test·Checkstyle 통과
- [ ] 정상·예외·권한·충돌 흐름 검증
- [ ] 민감 정보가 로그와 오류 응답에 포함되지 않음
- [ ] API 구현이 Notion `FocusOn API 명세서`와 일치함
- [ ] 관련 Backend 문서와 결과 보고서 갱신

## 진행 상태

<!-- 기존 branch-workflow.md 규칙을 바꾸지 않고 Issue 화면에서 확인하기 좋게 세부 작업을 0~17번으로 나눈 표시 형식이다. -->

- [ ] 0. 사전 준비 — 기존 코드와 관련 문서 읽기
- [ ] 1. Issue 생성 및 Label 적용
- [ ] 2. 기능 기획서 생성
- [ ] 3. 기획서 검토 요청
- [ ] 4. 기획서 검토
- [ ] 5. 검토 내용 수정
- [ ] 6. 기획서 승인
- [ ] 7. `dev` 최신화 및 Issue Branch 생성
- [ ] 8. 기능 구현
- [ ] 9. 격리된 컨텍스트로 코드 리뷰
- [ ] 10. Build·Test·Checkstyle 및 API QA
- [ ] 11. 코드 리뷰·QA 문제 보고
- [ ] 12. 문제 내용 검토
- [ ] 13. 수정 반영 및 재검증
- [ ] 14. 작업 완료 보고서와 컨텍스트 문서 갱신
- [ ] 15. Swagger UI 또는 `curl` 검증 자료 정리
- [ ] 16. `dev` 대상 PR 생성·승인·Merge
- [ ] 17. Notion API 명세서와 로컬 계약 최종 동기화
