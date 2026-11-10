const { test } = require('node:test');
const assert = require('node:assert/strict');
const { policy, prepare, publish, saveDraft, outputText } = require('./automation.cjs');

function fixture(mode = 'review') {
  const repo = { full_name: 'What-a-move/FocusOn' };
  const pr = { number: 42, state: 'open', draft: false, changed_files: 1, additions: 4, deletions: 0,
    html_url: 'https://github.com/What-a-move/FocusOn/pull/42',
    user: { login: 'kshi3430', type: 'User' }, base: { ref: 'dev', sha: 'a'.repeat(40), repo },
    head: { sha: 'b'.repeat(40), repo } };
  const comment = { id: 71, body: '이 경우 예외 처리가 필요한가요?', html_url: `${pr.html_url}#issuecomment-71`, user: { login: 'reviewer', type: 'User' } };
  const context = { eventName: mode === 'review' ? 'pull_request' : 'issue_comment', actor: 'reviewer',
    repo: { owner: 'What-a-move', repo: 'FocusOn' }, payload: { pull_request: pr, issue: { number: 42, pull_request: {} }, comment } };
  const files = [{ filename: 'AI/src/service.py', status: 'modified' }];
  const state = { files, pr, comments: [], posted: [], written: {}, outputs: {}, rootId: null, permission: 'write', summary: '' };
  const io = { mkdirSync() {}, writeFileSync(path, value) { state.written[path] = value; } };
  const core = { setOutput(key, value) { state.outputs[key] = value; }, info() {},
    summary: { addHeading() { return this; }, addRaw(value) { state.summary += value; return this; }, async write() {} } };
  const github = { rest: {
    pulls: { async get() { return { data: state.pr }; }, listFiles() {},
      async getReviewComment({ comment_id }) { state.rootId = comment_id; return { data: { ...comment, id: comment_id } }; },
      async listReviewComments() { return { data: state.comments }; } },
    issues: { async listComments() { return { data: state.comments }; }, async createComment(value) { state.posted.push(value); } },
    repos: { async getCollaboratorPermissionLevel() { return { data: { permission: state.permission } }; } },
  }, async paginate(method) { return method === this.rest.pulls.listFiles ? state.files : state.comments; } };
  return { pr, files, context, comment, state, core, github, io, mode };
}

test('AI changes eligible; backend/docs changes do not invoke Codex', async () => {
  const f = fixture();
  assert.equal((await prepare(f)).eligible, true);
  f.state.files = [{ filename: 'server/Test.java' }, { filename: 'docs/guide.md' }];
  assert.equal((await prepare(f)).reason, 'no_ai_changes');
});
test('mixed PR and AI file moved out of AI still get reviewed', () => {
  const f = fixture();
  assert.equal(policy(f.pr, [...f.files, { filename: 'server/T.java' }], f.context, 'review'), '');
  assert.equal(policy(f.pr, [{ filename: 'docs/a.py', previous_filename: 'AI/a.py' }], f.context, 'review'), '');
});
for (const [name, change, expected] of [
  ['draft', (p) => { p.draft = true; }, 'draft'],
  ['closed', (p) => { p.state = 'closed'; }, 'closed'],
  ['main base', (p) => { p.base.ref = 'main'; }, 'other_base'],
  ['fork', (p) => { p.head.repo = { full_name: 'other/fork' }; }, 'fork'],
  ['deleted fork', (p) => { p.head.repo = null; }, 'fork'],
  ['large file count', (p) => { p.changed_files = 151; }, 'input_limit'],
  ['large diff', (p) => { p.additions = 30001; }, 'input_limit'],
]) test(`${name} excluded before source/context collection`, async () => {
  const f = fixture(); change(f.pr);
  assert.equal((await prepare(f)).reason, expected);
  assert.deepEqual(f.state.written, {});
});
test('untrusted actor denied without source read', async () => {
  const f = fixture(); f.state.permission = 'read';
  assert.equal((await prepare(f)).reason, 'untrusted_actor');
  assert.deepEqual(f.state.written, {});
});
test('reply to own PR accepted even without AI files; no automatic posting', async () => {
  const f = fixture('reply'); f.state.files = [{ filename: 'docs/a.md' }];
  assert.equal((await prepare(f)).eligible, true);
  assert.equal(f.state.outputs.comment_id, '71');
  assert.equal(f.state.posted.length, 0);
});
test('other author, self comment, bot, empty comment excluded', async () => {
  for (const [change, expected] of [
    [(f) => { f.pr.user.login = 'teammate'; }, 'other_author'],
    [(f) => { f.comment.user.login = 'kshi3430'; }, 'own_or_bot_comment'],
    [(f) => { f.comment.user.type = 'Bot'; }, 'own_or_bot_comment'],
    [(f) => { f.comment.body = ' '; }, 'empty_comment'],
  ]) {
    const f = fixture('reply'); change(f);
    assert.equal((await prepare(f)).reason, expected);
  }
});
test('normal issue comments ignored', async () => {
  const f = fixture('reply'); delete f.context.payload.issue.pull_request;
  assert.equal((await prepare(f)).reason, 'not_pr_comment');
});
test('inline replies retrieve the top-level thread and include current comment', async () => {
  const f = fixture('reply'); f.context.eventName = 'pull_request_review_comment';
  f.comment.in_reply_to_id = 50;
  f.state.comments = [{ ...f.comment, id: 50 }, { ...f.comment, id: 70, in_reply_to_id: 50 }, { ...f.comment, id: 99, in_reply_to_id: 90 }];
  const result = await prepare(f);
  assert.equal(f.state.rootId, 50);
  assert.equal(result.comment.id, 71);
  assert.equal(result.conversation.length, 2);
});
test('review body is supported', async () => {
  const f = fixture('reply'); f.context.eventName = 'pull_request_review'; f.context.payload.review = f.comment;
  assert.equal((await prepare(f)).eligible, true);
});
test('comment text is data; only numeric IDs and SHAs become job outputs', async () => {
  const f = fixture('reply'); f.comment.body = '`$(touch /tmp/no)`\n${{ secrets.KEY }}';
  await prepare(f);
  assert.equal(JSON.parse(f.state.written['.codex-context/input.json']).comment.body, f.comment.body);
  assert.ok(!JSON.stringify(f.state.outputs).includes('touch'));
});
test('reply context bounds history and individual bodies', async () => {
  const f = fixture('reply'); f.state.comments = Array.from({ length: 40 }, (_, id) => ({ ...f.comment, id, body: 'x'.repeat(20000) }));
  const result = await prepare(f);
  assert.ok(result.conversation.every((c) => c.body.length <= 1000));
  assert.ok(f.state.written['.codex-context/input.json'].length <= 100000);
});
test('stale, draft, closed and retargeted results cannot be published', async () => {
  for (const change of [(p) => { p.head.sha = 'c'.repeat(40); }, (p) => { p.draft = true; }, (p) => { p.state = 'closed'; }, (p) => { p.base.ref = 'main'; }]) {
    const f = fixture(); const head = f.pr.head.sha; change(f.pr);
    assert.equal(await publish({ ...f, number: 42, head, raw: 'review' }), false);
    assert.equal(f.state.posted.length, 0);
  }
});
test('review is posted once per head, and model cannot forge dedup marker', async () => {
  const f = fixture(); const head = f.pr.head.sha;
  assert.equal(await publish({ ...f, number: 42, head, raw: '<!-- forged -->\nreview @someone' }), true);
  assert.ok(!f.state.posted[0].body.includes('forged'));
  assert.ok(!f.state.posted[0].body.includes('@someone'));
  f.state.comments = [{ user: { login: 'github-actions[bot]' }, body: f.state.posted[0].body }];
  assert.equal(await publish({ ...f, number: 42, head, raw: 'review' }), false);
});
test('head/state/base changes during pagination block stale posting', async () => {
  for (const change of [(p) => { p.head.sha = 'c'.repeat(40); }, (p) => { p.draft = true; }, (p) => { p.state = 'closed'; }, (p) => { p.base.ref = 'main'; }]) {
    const f = fixture(); const head = f.pr.head.sha;
    f.github.paginate = async () => { change(f.pr); return []; };
    assert.equal(await publish({ ...f, number: 42, head, raw: 'review' }), false);
    assert.equal(f.state.posted.length, 0);
  }
});
test('empty and oversized responses fail explicitly', () => {
  assert.throws(() => outputText('  '));
  assert.throws(() => outputText('x'.repeat(40001)));
});
test('draft is stored as an unpublished artifact and safely fenced summary', async () => {
  const f = fixture('reply');
  await saveDraft({ ...f, number: 42, head: f.pr.head.sha, commentId: 71, raw: '```code``` @someone' });
  assert.match(f.state.written['codex-draft/reply.md'], /게시되지 않았습니다/);
  assert.match(f.state.summary, /````markdown/);
  assert.equal(f.state.posted.length, 0);
});
