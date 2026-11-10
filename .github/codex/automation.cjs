const fs = require('node:fs');

const MAX_FILES = 150;
const MAX_LINES = 30000;
const MAX_OUTPUT = 40000;
const OWNER = 'kshi3430';
const aiPath = (name) => name.startsWith('AI/');
const human = (user) => user?.type === 'User' && !user.login.endsWith('[bot]');
const bounded = (value, limit = 12000) => String(value || '').slice(0, limit);

function subject(context) {
  const p = context.payload;
  if (context.eventName === 'pull_request') return { number: p.pull_request.number };
  if (context.eventName === 'issue_comment' && p.issue?.pull_request) {
    return { number: p.issue.number, comment: p.comment, kind: 'conversation' };
  }
  if (context.eventName === 'pull_request_review_comment') {
    return { number: p.pull_request.number, comment: p.comment, kind: 'inline' };
  }
  if (context.eventName === 'pull_request_review') {
    return { number: p.pull_request.number, comment: p.review, kind: 'review' };
  }
  return null;
}

function policy(pr, files, context, mode) {
  const s = subject(context);
  if (!s) return 'not_pr_comment';
  if (pr.state !== 'open') return 'closed';
  if (pr.draft) return 'draft';
  if (pr.base.ref !== 'dev') return 'other_base';
  if (pr.head.repo?.full_name !== pr.base.repo.full_name) return 'fork';
  if (pr.changed_files > MAX_FILES || pr.additions + pr.deletions > MAX_LINES) return 'input_limit';
  if (mode === 'review') return files.some((f) => aiPath(f.filename) || aiPath(f.previous_filename || '')) ? '' : 'no_ai_changes';
  if (pr.user.login !== OWNER) return 'other_author';
  if (!human(s.comment?.user) || s.comment.user.login === OWNER) return 'own_or_bot_comment';
  if (!s.comment.body?.trim()) return 'empty_comment';
  return '';
}

async function writePermission(github, repo, username) {
  try {
    const { data } = await github.rest.repos.getCollaboratorPermissionLevel({ ...repo, username });
    return ['admin', 'maintain', 'write'].includes(data.permission);
  } catch (error) {
    if ([403, 404].includes(error.status)) return false;
    throw error;
  }
}

async function prepare({ github, context, core, mode, io = fs }) {
  const skip = (reason) => {
    core.setOutput('eligible', 'false');
    core.info(`Codex skipped: ${reason}`);
    return { eligible: false, reason };
  };
  const s = subject(context);
  if (!s) return skip('not_pr_comment');
  const { data: pr } = await github.rest.pulls.get({ ...context.repo, pull_number: s.number });
  // Inspect bounded metadata before fetching the diff or any conversation.
  const early = policy(pr, [], context, mode);
  if (early && early !== 'no_ai_changes') return skip(early);
  if (!await writePermission(github, context.repo, context.actor)) return skip('untrusted_actor');
  const files = await github.paginate(github.rest.pulls.listFiles, { ...context.repo, pull_number: s.number, per_page: 100 });
  const reason = policy(pr, files, context, mode);
  if (reason) return skip(reason);
  let conversation = [];
  let root = null;
  if (mode === 'reply') {
    if (s.kind === 'inline') {
      const rootId = s.comment.in_reply_to_id || s.comment.id;
      const response = await github.rest.pulls.getReviewComment({ ...context.repo, comment_id: rootId });
      root = response.data;
      const { data } = await github.rest.pulls.listReviewComments({ ...context.repo, pull_number: s.number, per_page: 100, sort: 'created', direction: 'desc' });
      conversation = data.filter((c) => c.id === rootId || c.in_reply_to_id === rootId).slice(0, 40).reverse();
    } else {
      const { data } = await github.rest.issues.listComments({ ...context.repo, issue_number: s.number, per_page: 40 });
      conversation = data;
    }
  }
  const commentData = (c) => c && ({ id: c.id, user: c.user.login, body: bounded(c.body), url: c.html_url, path: c.path, line: c.line });
  const input = {
    mode, number: s.number, url: pr.html_url, base: pr.base.sha, head: pr.head.sha,
    files: files.map((f) => ({ path: f.filename, previousPath: f.previous_filename, status: f.status })),
    comment: commentData(s.comment), root: commentData(root),
    conversation: conversation.map(commentData).map((c) => ({ ...c, body: bounded(c.body, 1000) })),
    limitation: 'Conversation is bounded; inspect current comment and root even if older discussion is omitted.',
  };
  if (JSON.stringify(input).length > 100000) return skip('context_limit');
  io.mkdirSync('.codex-context', { recursive: true });
  io.writeFileSync('.codex-context/input.json', JSON.stringify(input, null, 2));
  for (const [key, value] of Object.entries({ eligible: 'true', number: s.number, head: pr.head.sha, base: pr.base.sha, comment_id: s.comment?.id || 0 })) core.setOutput(key, String(value));
  return { eligible: true, ...input };
}

function outputText(raw) {
  const text = String(raw || '').trim();
  if (!text || text.length > MAX_OUTPUT) throw new Error('Codex output missing or exceeds 40000 characters');
  // Keep generated text inert as Markdown and avoid unsolicited user mentions.
  return text.replace(/<!--.*?-->/gs, '').replace(/@/g, '@\u200b');
}

async function publish({ github, context, core, number, head, raw }) {
  const text = outputText(raw);
  const { data: pr } = await github.rest.pulls.get({ ...context.repo, pull_number: number });
  if (pr.state !== 'open' || pr.draft || pr.head.sha !== head || pr.base.ref !== 'dev') {
    core.info('Codex result not posted: PR closed, draft, retargeted, or head changed');
    return false;
  }
  const marker = `<!-- focuson-ai-codex:${head} -->`;
  const comments = await github.paginate(github.rest.issues.listComments, { ...context.repo, issue_number: number, per_page: 100 });
  if (comments.some((c) => c.user.login === 'github-actions[bot]' && c.body.includes(marker))) return false;
  // Pagination can take time. Recheck after it, immediately before posting.
  const { data: latest } = await github.rest.pulls.get({ ...context.repo, pull_number: number });
  if (latest.state !== 'open' || latest.draft || latest.head.sha !== head || latest.base.ref !== 'dev') {
    core.info('Codex result not posted: PR changed during comment lookup');
    return false;
  }
  await github.rest.issues.createComment({ ...context.repo, issue_number: number,
    body: `${marker}\n### AI Codex 자동 리뷰\n분석 커밋: \`${head}\`\n\n${text}` });
  return true;
}

async function saveDraft({ core, number, head, commentId, raw, io = fs }) {
  const text = outputText(raw);
  const body = `# 검토 후 게시할 답변 초안\n\nPR #${number} · 댓글 ID ${commentId} · 분석 커밋 \`${head}\`\n\n이 초안은 PR에 게시되지 않았습니다. 현재 코드를 확인한 후 직접 게시하세요.\n\n${text}\n`;
  io.mkdirSync('codex-draft', { recursive: true });
  io.writeFileSync('codex-draft/reply.md', body);
  // Generated Markdown is kept inside a fenced block in the run summary.
  const fence = '`'.repeat(Math.max(3, ...(body.match(/`+/g) || []).map((s) => s.length + 1)));
  await core.summary.addHeading('Codex 답변 초안').addRaw(`${fence}markdown\n${body}\n${fence}\n`).write();
}

module.exports = { subject, policy, prepare, publish, saveDraft, outputText };
