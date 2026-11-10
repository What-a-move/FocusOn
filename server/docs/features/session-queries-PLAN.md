# 세션 상세·목록 조회 계획

- Issue #195, branch feat/195-session-queries. 최신 dev 3e7f664 기준.
- GET /api/v1/sessions/{sessionId}: 본인 세션 스냅샷을 제공한다. 없는 ID·잘못된 ID·타인 ID는 SESSION_NOT_FOUND 404.
- GET /api/v1/sessions: Desktop 본인의 기록을 from/to 학습 날짜 양 끝 포함 조건으로 조회한다.
- 생성 당시 studyDate를 유지한다. timeZone은 IANA 유효성만 확인하며 기록을 재배정하지 않는다.
- 명세 미기재 구현 기준: 기본20/최대100, ID 내림차순 커서. 추가 1행으로 nextCursor를 결정하고 전체 count는 조회하지 않는다.
- Entity와 기존 migration은 변경하지 않으며 조회는 readOnly transaction이다.
- #169 로컬 브랜치에 상세 API 구현이 없는 것을 확인했다. 제공된 Notion 표 기준이며 원본 상세 규칙은 접근 불가로 미대조다.
- API/보안/DB/동시 조회 검증과 전체 build/Checkstyle 이후 결과 문서를 갱신한다.
