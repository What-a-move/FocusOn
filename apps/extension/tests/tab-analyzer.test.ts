import assert from 'node:assert/strict'
import test from 'node:test'
import { DWELL_MS, TabAnalyzer, type Deps, type RunBody, type Target, type Tracking } from '../extension/background/tab-analyzer.ts'

const docs: Target = { tabId: 7, url: 'https://tanstack.com/query/latest?utm=1', title: 'Important Defaults - TanStack Query' }

function setup(options: { relation?: string; relations?: string[]; page?: { sensitive: boolean; text: string } | null; excluded?: string[] | null; access?: boolean; session?: string | null } = {}) {
  let clock = 1_000_000
  let ids = 0
  let target: Target | null = docs
  let tracking: Tracking | null = null
  let sequences: Awaited<ReturnType<Deps['loadSequences']>> = null
  const sent: RunBody[] = []
  const inspected: number[] = []
  const timers: Array<() => void> = []
  const relations = options.relations ?? [options.relation ?? 'RELATED']
  const deps: Deps = {
    now: () => clock,
    uuid: () => `id-${++ids}`,
    currentTarget: async () => target,
    activeSession: async () => options.session === null ? null : { sessionId: '901', status: options.session ?? 'RUNNING' },
    excludedDomains: async () => options.excluded === undefined ? [] : options.excluded,
    hasSiteAccess: async () => options.access ?? true,
    inspect: async (_tabId, max) => { inspected.push(max); return options.page === undefined ? { sensitive: false, text: 'staleTime 기본값은 0이다' } : options.page },
    post: async (_sessionId, body) => { sent.push(body); return { ok: true, relation: relations.shift() ?? 'RELATED' } },
    loadTracking: async () => tracking,
    saveTracking: async (value) => { tracking = value },
    loadSequences: async () => sequences,
    saveSequences: async (value) => { sequences = value },
    schedule: (_ms, run) => { timers.push(run) },
  }
  const analyzer = new TabAnalyzer(deps)
  return {
    analyzer, sent, inspected,
    advance: (ms: number) => { clock += ms },
    show: (next: Target | null) => { target = next },
    fire: async () => { const run = timers.shift(); run?.(); await new Promise((resolve) => setTimeout(resolve, 0)) },
    tracking: () => tracking,
  }
}

test('같은 탭을 15초 보면 탭 제목·도메인만 보내고, 관련 있으면 본문은 읽지 않는다', async () => {
  const t = setup({ relation: 'RELATED' })
  await t.analyzer.onChange()
  t.advance(DWELL_MS)
  await t.fire()
  assert.equal(t.sent.length, 1)
  assert.deepEqual({ ...t.sent[0], clientRunId: 'x' }, {
    clientRunId: 'x', contentSource: 'TAB_META', observedAt: new Date(1_000_000 + DWELL_MS).toISOString(), sequence: 1, guardStatus: null,
    metadata: { appName: 'Google Chrome', pageTitle: docs.title, urlHost: 'tanstack.com' },
  })
  assert.deepEqual(t.inspected, [0], '민감 입력칸 확인만 하고 본문은 읽지 않음')
})

test('관련 없음·판단 보류면 정리한 본문으로 한 번 더 보낸다', async () => {
  for (const relation of ['UNRELATED', 'UNCERTAIN']) {
    const t = setup({ relation })
    await t.analyzer.onChange()
    t.advance(DWELL_MS)
    await t.fire()
    assert.deepEqual(t.sent.map((body) => [body.contentSource, body.sequence]), [['TAB_META', 1], ['CHROME_DOM', 1]], relation)
    assert.deepEqual(t.sent[1].contentPayload, { text: 'staleTime 기본값은 0이다' })
    assert.equal(t.sent[1].metadata?.urlHost, 'tanstack.com')
  }
})

test('15초가 안 됐거나 그 사이 다른 탭으로 옮기면 보내지 않는다', async () => {
  const t = setup()
  await t.analyzer.onChange()
  t.advance(DWELL_MS - 1)
  await t.analyzer.resume()
  t.show({ ...docs, url: 'https://youtube.com/watch' })
  t.advance(1)
  await t.fire()
  assert.equal(t.sent.length, 0)
})

test('같은 탭·주소는 다시 세지 않고 한 번만 보낸다', async () => {
  const t = setup()
  await t.analyzer.onChange()
  await t.analyzer.onChange()
  t.advance(DWELL_MS)
  await t.fire()
  await t.fire()
  await t.analyzer.resume()
  assert.equal(t.sent.length, 1)
})

test('Worker가 꺼졌다 깨어나면 저장된 시작 시각으로 이어서 판단한다', async () => {
  const t = setup()
  await t.analyzer.onChange()
  t.advance(DWELL_MS + 10_000)
  await t.analyzer.resume()
  assert.equal(t.sent.length, 1)
})

test('제외·민감·지원하지 않는 페이지는 내용 없이 상태만 보낸다', async () => {
  const cases: Array<[Parameters<typeof setup>[0], Target, string]> = [
    [{ excluded: ['tanstack.com'] }, docs, 'EXCLUDED'],
    [{ excluded: null }, docs, 'EXCLUDED'],
    [{}, { ...docs, url: 'chrome://newtab/' }, 'EXCLUDED'],
    [{ access: false }, docs, 'EXCLUDED'],
    [{}, { ...docs, url: 'https://mail.google.com/mail/u/0' }, 'PRIVACY_BLOCKED'],
    [{ page: { sensitive: true, text: '' } }, docs, 'PRIVACY_BLOCKED'],
  ]
  for (const [options, target, guard] of cases) {
    const t = setup(options)
    t.show(target)
    await t.analyzer.onChange()
    t.advance(DWELL_MS)
    await t.fire()
    assert.equal(t.sent.length, 1, `${target.url} ${guard}`)
    assert.equal(t.sent[0].guardStatus, guard)
    assert.equal('metadata' in t.sent[0], false)
  }
})

test('본문에 민감한 단어가 있으면 본문 대신 민감 화면으로 보낸다', async () => {
  const t = setup({ relation: 'UNRELATED', page: { sensitive: false, text: '새 비밀번호 설정' } })
  await t.analyzer.onChange()
  t.advance(DWELL_MS)
  await t.fire()
  assert.deepEqual(t.sent.map((body) => [body.contentSource, body.guardStatus]), [['TAB_META', null], ['CHROME_DOM', 'PRIVACY_BLOCKED']])
  assert.equal('contentPayload' in t.sent[1], false)
})

test('세션이 진행 중이 아니면 보내지 않는다', async () => {
  for (const session of ['PAUSED', null]) {
    const t = setup({ session })
    await t.analyzer.onChange()
    t.advance(DWELL_MS)
    await t.fire()
    assert.equal(t.sent.length, 0)
  }
})

test('Chrome이 뒤로 가면 추적을 멈춘다', async () => {
  const t = setup()
  await t.analyzer.onChange()
  t.show(null)
  await t.analyzer.onChange()
  assert.equal(t.tracking(), null)
  t.advance(DWELL_MS)
  await t.fire()
  assert.equal(t.sent.length, 0)
})
