# Codex PR 자동 리뷰와 답변 초안

Issue: [#212](https://github.com/What-a-move/FocusOn/issues/212) · [계획](plans/codex-pr-automation.md)

## 현재 구현과 활성화 상태

- AI 리뷰: `dev` 대상 PR의 변경 목록에 `AI/**`가 있으면 생성·업데이트·재오픈·초안 공개 시 Codex Action으로 분석하고 PR 전체 댓글 하나를 게시한다. 혼합 PR도 실행하되 finding은 AI 변경에 원인이 있는 문제로 제한한다.
- 답변 초안: `kshi3430` 작성의 열린 일반 `dev` PR에 다른 사람의 일반 댓글·인라인 댓글·리뷰 본문이 새로 등록되면 한국어 답변 초안을 만든다. 해당 PR은 AI 변경이 없어도 처리한다.
- 두 기능 모두 같은 저장소의 write 권한 사용자 이벤트만 처리한다. fork, 초안/닫힌 PR, 본인/봇 댓글은 제외한다. 편집·삭제 이벤트는 처리하지 않는다.
- 현재 상태는 코드 구현 및 로컬 fixture 검증이다. 원격 모델 실행·PR 댓글 게시·실제 이벤트 수신까지 성공했다고 간주하지 않는다.

## 운영 결정

2026-11-10: 사용자 요청으로 공식 `openai/codex-action@v1`을 사용한다. Cloud 봇에 `@codex review`를 대신 남기는 방법은 사용하지 않는다. GitHub 읽기만 가능한 분석 job과 리뷰 게시 job을 분리한다. 답변은 사람이 검토 후 직접 게시하며 초안 workflow에는 쓰기 권한을 부여하지 않는다.

답변 초안은 비공개 개인 메시지가 아니다. 공개 저장소의 Actions Summary는 공개 열람이 가능하며, artifact도 저장소 접근 권한을 따른다. 민감한 논의에는 사용하지 않는다. 자동으로 코드를 수정하거나 댓글 스레드를 해결 처리하지 않는다.

## 활성화 절차와 다음 작업

1. 저장소 `Settings → Secrets and variables → Actions → New repository secret`에 `OPENAI_API_KEY`를 등록한다. 키를 코드·Issue·PR·채팅에 붙이지 않는다. OpenAI API 프로젝트에 사용량·지출 정책을 설정한다. 이 Action은 ChatGPT 구독의 Cloud 리뷰 한도를 사용하지 않는다.
2. 이 변경을 `dev`에 반영한다. PR 리뷰 workflow는 신뢰된 base `dev`의 helper/prompt를 로드하므로 base에 파일이 없는 도입 PR 자체는 실행 대상이 아니다. 도입 PR에는 `AI/**` 변경도 없다.
3. 일반 PR 댓글 이벤트(`issue_comment`)는 GitHub 기본 브랜치에 workflow가 있어야 실행된다. 현재 기본 브랜치는 `main`이다. 기존 최종 통합 절차대로 `dev → main` 반영을 마쳐야 일반 댓글 초안이 활성화된다. 기본 브랜치를 이 기능 때문에 임의로 변경하지 않는다.
4. 같은 저장소의 팀원이 실제 `AI/` 변경 PR을 `dev` 대상으로 열고, Actions의 `AI Codex review`에서 분석·게시 job 성공과 커밋 SHA를 확인한다. 새 커밋이 올라오면 새 리뷰가 생성되는지 확인한다.
5. `kshi3430` 작성 PR에 write 권한 팀원이 일반/인라인 댓글을 각각 남긴다. Actions의 `Codex reply draft → save_draft`에서 Summary 또는 `codex-reply-pr-…-comment-…` artifact의 `reply.md`를 확인하고, PR에 답글이 자동 게시되지 않았는지 확인한다. 초안의 분석 SHA와 현재 PR head가 다르면 새 코드와 대조한다.
6. API 리뷰가 실제 동작한 다음 기존 Codex Cloud 저장소 자동 리뷰를 꺼야 AI 외 PR의 리뷰와 AI PR의 중복 리뷰를 막을 수 있다. 이 코드 변경은 Cloud 설정을 대신 변경하지 않는다. Cloud의 수동 `@codex review`는 별도로 사용할 수 있다.

실제 Secret 등록 및 원격 실행은 운영자 확인이 남아 있다. PR 병합·기본 브랜치 반영·Cloud 설정 변경은 자동으로 하지 않는다.

## 처리 흐름과 제한

`ai-codex-review.yml`: AI PR 이벤트 → 같은 저장소/초안 검사 → base 코드 checkout → 권한/크기 검사 → head 별도 checkout → Codex 읽기 전용 분석 → 별도 job이 현재 head/상태 재확인 → 중복 없는 PR 댓글.

`codex-reply-draft.yml`: 사람 댓글 이벤트 → PR 작성자/권한/상태 검사 → 댓글과 root 대화 수집 → head 읽기 → Codex 답변 생성 → 별도 job이 Summary와 7일 artifact 저장. 초안은 수정이나 테스트를 수행했다고 주장하지 않도록 지침을 둔다.

- Codex job은 12분, 결과 저장/게시 job은 3분 제한이다. 자동 재시도는 하지 않는다. 재실행은 Actions에서 수동으로 요청한다.
- 최대 변경 파일 150개·변경 30,000줄. 문맥은 최대 100,000자, 현재/root 댓글은 각각 최대 12,000자, 대화는 최대 40개·개당 1,000자다. 일반 대화는 첫 40개, 인라인은 최근 100개 중 해당 스레드 최대 40개로 제한하며 현재/root는 별도로 포함한다. 누락 범위를 모델에 알린다.
- 모델 결과가 없거나 40,000자를 넘으면 실패한다. API Secret 누락도 명시적 실패로 표시하며 '문제 없음' 댓글로 대체하지 않는다.
- PR head가 분석 중 바뀌거나 닫히면 결과를 게시하지 않는다. 같은 head에 이미 남긴 Actions 리뷰 댓글은 다시 게시하지 않는다. PR별 concurrency로 오래된 실행을 취소한다.
- 댓글 ID별 초안 실행은 독립적이다. 리뷰 본문과 인라인 댓글이 함께 제출되면 각각 초안이 생길 수 있다. 삭제/편집 후 이전 초안은 자동 삭제되지 않는다.
- 금액에 대한 강제 상한은 구현하지 않았다. 타임아웃과 입력 제한은 요금 상한을 보장하지 않는다.
- 신뢰된 branch의 스크립트·prompt만 실행하고 PR 소스는 읽기만 한다. 모델에게 PR 코드/댓글에 포함된 명령을 실행하지 않도록 지시한다. 제품 테스트는 모델이 실행하지 않는다.

## 검증 명령

```bash
node --test .github/codex/automation.test.cjs
python3 .github/codex/check_workflows.py
actionlint -shellcheck= .github/workflows/ai-codex-review.yml .github/workflows/codex-reply-draft.yml
```

첫 명령은 이벤트·작성자·권한·파일·예산·SHA 변화·중복 게시·초안 미게시를 fixture로 검사한다. 두 번째는 PyYAML이 필요하며 workflow 트리거와 권한 계약을 파싱해 검사한다. 세 번째는 actionlint 1.7.7로 YAML과 GitHub 식을 검사한다. shellcheck는 이 환경에 없어 비활성화하며, 두 셸 step은 Secret 존재 여부만 검사한다.

2026-11-10 로컬 검증: Node fixture 22/22, YAML 계약 검사, actionlint 1.7.7, tooling baseline 6/6 통과. 독립 Verifier는 3회차 PASS를 보고했다. 발견·수정 기록은 계획 문서에 남겼다. 실제 API·Actions 이벤트·Secret 설정은 별도의 원격 검증이 필요하다.

## 문제 확인

- workflow가 없음: 일반 댓글 이벤트에서는 기본 브랜치 `main`에 파일이 있는지 먼저 확인한다.
- skipped: Actions 로그의 `Codex skipped: 사유`를 확인한다. `no_ai_changes`, `other_author`, `own_or_bot_comment`, `untrusted_actor`, `fork`, `draft`, `closed`, `other_base`, `input_limit`, `context_limit`은 의도한 제외 조건이다. 로그에는 댓글 본문/URL/Token을 출력하지 않는다.
- 키 누락/API 실패: Secret 이름·프로젝트 모델 사용 권한·사용량을 확인한다. 키를 로그에 출력하지 않는다.
- 리뷰 미게시: 분석 SHA와 현재 head가 달라졌거나 PR이 닫혔는지 확인한다. 새 PR head 실행에서 다시 검토한다.
- 초안 확인: Actions Summary의 코드는 그대로 복사해 검토한다. Summary에는 Markdown을 fence 안에 표시하여 생성된 외부 링크/이미지가 자동 실행되지 않도록 한다.

## 근거

- [공식 Codex Action](https://learn.chatgpt.com/docs/github-action)
- [공식 Action 소스와 인증 요건](https://github.com/openai/codex-action)
- [GitHub 경로 필터](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onpushpull_requestpull_request_targetpathspaths-ignore)
- [GitHub 댓글 이벤트](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)
