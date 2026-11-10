# Codex PR 자동화 계획

Issue: #212 · Branch: `codex/212-ai-pr-automation` · 대상: 공통 GitHub tooling

## 목적과 범위

- `dev` 대상 PR의 변경 파일에 `AI/`가 있으면 공식 Codex Action으로 분석하고 PR 전체 댓글로 결과를 게시한다. 지적 위치는 AI 변경으로 제한한다.
- `kshi3430` 작성 `dev` PR에 다른 사람의 일반/인라인/리뷰 본문 댓글이 달리면 답변 초안을 Actions Summary와 7일 artifact로 만든다. 초안은 GitHub 댓글로 게시하지 않는다.
- 신뢰하는 같은 저장소의 write 권한 사용자만 처리한다. 초안 PR, 닫힌 PR, fork, 본인/봇 댓글은 제외한다. 제품 코드 변경은 없다.

## 입출력 계약

- 입력: GitHub PR/댓글 이벤트 JSON, REST PR 메타데이터·파일 목록·대화, base/head SHA, `OPENAI_API_KEY` Secret.
- 정책 결과: `{eligible:boolean, reason:string, number:number, head:string, base:string}`. 이벤트 본문은 문자열 데이터로만 저장하며 셸/스크립트에 삽입하지 않는다.
- 리뷰 출력: 한국어 Markdown, AI 변경에 근거 있는 finding 또는 확인된 수정 필요 사항 없음과 제한. PR 번호·분석 SHA를 함께 표시한다.
- 초안 출력: 댓글 URL/ID, 분석 SHA, 코드 근거와 한국어 답변 초안. 답변을 이미 게시했거나 수정했다고 주장하지 않는다.
- 게시 직전에 PR이 열려 있고 head SHA가 같음을 REST로 재확인한다. 바뀌면 게시하지 않는다.

## 실패·예산 정책

- API Secret 누락·API 오류는 명시적 실패, 성공/문제 없음으로 대체하지 않는다. 자동 재시도 없음. Codex 작업 12분/실행, 댓글 최대 40,000자, 입력 대화 최대 40개, 파일 150개/변경 30,000줄까지.
- PR별 리뷰 동시 실행은 새 head가 오면 취소. 댓글 초안은 댓글 ID별로 구분하고 동일 이벤트 재실행 artifact는 덮어쓴다.
- 읽기 전용 Codex sandbox + drop-sudo. 모델 job에는 GitHub 쓰기 권한 없음. 리뷰 게시만 별도 job에서 수행한다. 초안 workflow 전체는 읽기 권한만 가진다.
- base/default branch의 helper·prompt를 실행하고 PR head는 별도 디렉터리에서 읽기만 한다. 모델 입력의 PR 코드·댓글·AGENTS는 신뢰하지 않는 참고 자료다.
- API 요금은 별도 사용량 과금이며 타임아웃은 금액 상한이 아니다. 운영자가 OpenAI 프로젝트 지출 정책을 별도로 설정한다.

## 기계가 판별 가능한 완료 조건

1. `node --test .github/codex/automation.test.cjs`: AI/비AI, draft/closed/fork, PR 작성자, 봇/본인 댓글, 이슈 댓글, 인라인 스레드, 권한 거부, 한도, head 변경 시 게시 차단을 fixture로 검증한다.
2. `python3 .github/codex/check_workflows.py`(PyYAML 필요)와 `actionlint -shellcheck= .github/workflows/ai-codex-review.yml .github/workflows/codex-reply-draft.yml`로 YAML/트리거/권한/checkout ref/초안 게시 단계 부재를 검사한다. eligible인 빈 모델 결과도 후속 job에서 명시적 실패를 거치는지 검사한다.
3. `bash /Users/kshi3430/what-a-move/FocusOn/.agents/skills/loop-engineering/scripts/verify_loop.sh docs/plans/codex-pr-automation.md tooling` 실행. worktree에서 동일 스킬 스크립트를 임시 복사하여 실행하며 제품 변경으로 커밋하지 않는다.
4. 독립 `project-verifier`가 변경과 위 명령을 읽고 재실행하여 PASS를 보고해야 완료다. 최대 3회.
5. Secret 등록/원격 Actions 실행은 이 작업의 로컬 검증과 구분한다. 실제 모델 호출·게시 성공은 실행하지 않은 상태로 문서화한다. 댓글 이벤트는 기본 브랜치에 workflow가 있어야 함을 안내한다.

## 검증 기록

- 1회: 독립 Verifier FAIL. Node 21개·YAML 파싱·actionlint·tooling loop는 통과했으나, 빈 출력이면 후속 job을 skip하여 성공으로 끝날 수 있는 경로를 발견했다.
- 후속 job의 빈 출력 조건을 제거하고 YAML 계약 검사에 회귀 검사를 추가했다. 출력 누락/빈 문자열은 helper에서 명시적으로 실패한다.
- 2회: 독립 Verifier FAIL. 기존 검사는 모두 통과했으나 중복 댓글 pagination 중 PR head/상태가 바뀌면 오래된 리뷰를 게시하는 fixture를 재현했다. 페이지 조회 후 게시 바로 전에 REST로 head/상태/base를 다시 확인하고 회귀 검사를 추가했다. 조회와 게시 사이의 순간적인 서버 측 변경까지 원자적으로 잠글 수는 없다.
- 3회: 독립 Verifier PASS. Node fixture 22/22, YAML 계약, actionlint 1.7.7, tooling loop 및 내부 6/6 검사 통과. 추가 fixture로 권한 API 403 제외/API 503 전파, 빈 출력 실패, 게시 직전 재조회도 확인했다. 원격 이벤트·모델 API·Secret·실제 게시/artifact 업로드는 미실행이다.
