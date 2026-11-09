# 세션 요약 오류 기록

- Notion 원본 접근404. 사용자 제공 세션 정책과 로컬 확정 계약/소비자 파서를 기준으로 했으며 최신 원문 대조는 미완료다.
- 전체 build와 PostgreSQL test를 같은 디렉터리에서 동시에 실행해 결과 파일 EOF 및 in-progress-results-generic.bin 누락, 일부 컨텍스트 초기화 실패가 발생했다. 애플리케이션 수정 없이 테스트를 순차 실행으로 전환했다. PostgreSQL27개 모두 재통과했고 전체 build 재검증을 진행했다.
- 순차 전체 build·Checkstyle도 성공해 위 실행 충돌을 해소했다.
