import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from '../features/session/api-client.ts'
import { createLogger, MAX_LINES, sanitize } from '../features/session/logger.ts'

const NOW = Date.parse('2026-10-29T00:00:00Z')
type Line = Record<string, unknown>

function memoryStore() {
  const saved: { lines: unknown[] } = { lines: [] }
  return { saved, store: { async read() { return [...saved.lines] }, async write(lines: unknown[]) { saved.lines = lines } } }
}
const settle = () => new Promise((resolve) => setTimeout(resolve, 0))

function client(replies: Array<() => Response>, access: { accessToken: string; expiresAt: string } | null = { accessToken: 'a-1', expiresAt: new Date(NOW + 600_000).toISOString() }) {
  const lines: Line[] = []
  const headers: Array<Record<string, string>> = []
  const store = {
    access, async getAccess() { return this.access }, async getRefreshToken() { return 'r-1' },
    async saveRotation(t: { accessToken: string; accessTokenExpiresAt: string }) { this.access = { accessToken: t.accessToken, expiresAt: t.accessTokenExpiresAt } }, async clear() {},
  }
  const request = (async (_url: string, init: RequestInit) => {
    headers.push(init.headers as Record<string, string>)
    const reply = replies.shift()
    if (!reply) throw new TypeError('fetch failed')
    return reply()
  }) as typeof fetch
  const api = createApiClient({ baseUrl: 'https://api.test', store, request, sleep: async () => {}, now: () => NOW, log: (level, event, fields = {}) => { lines.push({ level, event, ...sanitize(fields) }) } })
  return { api, lines, headers }
}
const json = (status: number, body: unknown) => () => new Response(JSON.stringify(body), { status })

test('제목·본문은 글자 수로만, 토큰·URL·모르는 필드는 버리고 경로의 Query는 지운다', () => {
  assert.deepEqual(sanitize({
    pageTitle: '받은편지함 - 메일', text: '비밀 본문', accessToken: 'secret', url: 'https://a.test/x?q=1', email: 'a@b.c',
    path: '/api/v1/sessions/active?x=1', domain: 'https://youtube.com/watch?v=1', status: 200,
  }), { titleLen: 10, textLen: 5, path: '/api/v1/sessions/active', domain: 'youtube.com', status: 200 })
})

test('INFO 이상만 저장하고, 500건을 넘으면 오래된 것부터 버린다', async () => {
  const { saved, store } = memoryStore()
  const log = createLogger('service-worker', store, { now: () => new Date(NOW) })
  log('DEBUG', 'sw.restart')
  for (let i = 0; i < MAX_LINES + 5; i += 1) log('INFO', 'api.request.out', { durMs: i })
  await settle()
  assert.equal(saved.lines.length, MAX_LINES)
  assert.deepEqual(saved.lines[0], { ts: '2026-10-29T00:00:00.000Z', level: 'INFO', module: 'extension', component: 'service-worker', event: 'api.request.out', traceId: null, durMs: 5 })
})

test('저장소가 실패하거나 직렬화할 수 없는 값이 와도 예외를 내보내지 않는다', async () => {
  const log = createLogger('popup', { async read() { throw new Error('storage') }, async write() {} })
  assert.doesNotThrow(() => log('INFO', 'sw.restart', { durMs: BigInt(10) }))
  assert.doesNotThrow(() => log('INFO', 'sw.restart'))
  await settle()
})

test('서버 요청은 X-Trace-Id를 보내고 OUT·IN을 같은 traceId로 남긴다', async () => {
  const { api, lines, headers } = client([json(200, { data: { sessionId: '901' } })])
  await api.authorized('/api/v1/sessions/active?x=1', 'GET', 200, (data) => data)
  const traceId = headers[0]['X-Trace-Id']
  assert.match(traceId, /^[0-9a-f-]{36}$/)
  assert.deepEqual(lines.map(({ event, traceId: id, path }) => ({ event, id, path })), [
    { event: 'api.request.out', id: traceId, path: '/api/v1/sessions/active' },
    { event: 'api.response.in', id: traceId, path: '/api/v1/sessions/active' },
  ])
})

test('분석 요청은 decision.* 이름과 정한 traceId를 쓰고 응답 결과값만 남긴다', async () => {
  const { api, lines, headers } = client([json(200, { data: { relation: 'RELATED', reasonCode: 'SEMANTIC_GOAL_MATCH' } })])
  await api.authorized('/api/v1/sessions/901/analysis-runs', 'POST', 200, (data) => data, { body: {}, traceId: 'run-1', logFields: { contentSource: 'TAB_META', pageTitle: 'TanStack Query', domain: 'tanstack.com' } })
  assert.equal(headers[0]['X-Trace-Id'], 'run-1')
  assert.deepEqual(lines.map(({ event, traceId, relation, titleLen, domain }) => ({ event, traceId, relation, titleLen, domain })), [
    { event: 'decision.request.out', traceId: 'run-1', relation: undefined, titleLen: 14, domain: 'tanstack.com' },
    { event: 'decision.response.in', traceId: 'run-1', relation: 'RELATED', titleLen: 14, domain: 'tanstack.com' },
  ])
})

test('GET 재시도와 토큰 갱신 뒤 재전송은 같은 traceId를 쓴다', async () => {
  const { api, headers } = client([
    json(503, { code: 'SERVICE_UNAVAILABLE' }),
    json(401, { code: 'AUTH_ACCESS_TOKEN_EXPIRED' }),
    json(200, { data: { accessToken: 'a-2', refreshToken: 'r-2', accessTokenExpiresAt: new Date(NOW + 900_000).toISOString() } }),
    json(200, { data: { ok: true } }),
  ])
  const result = await api.authorized('/api/v1/sessions/active', 'GET', 200, (data) => data)
  assert.equal(result.success, true)
  const original = headers.filter((_, index) => index !== 2).map((h) => h['X-Trace-Id'])
  assert.equal(new Set(original).size, 1, '원 요청 3번은 같은 traceId')
  assert.notEqual(headers[2]['X-Trace-Id'], original[0], 'refresh는 별도 요청')
})

test('실패는 단계와 errorClass로 나눠 남긴다', async () => {
  const timeout = Object.assign(new Error('late'), { name: 'TimeoutError' })
  const { api, lines } = client([
    () => { throw timeout },
    json(429, { code: 'TOO_MANY_REQUESTS' }),
    json(200, { data: null }),
  ])
  await api.authorized('/a', 'POST', 200, (data) => data)
  await api.authorized('/b', 'POST', 200, (data) => data)
  await api.authorized('/c', 'POST', 200, () => null)
  assert.deepEqual(lines.filter((line) => line.event === 'api.request.fail').map(({ level, errorClass, code }) => ({ level, errorClass, code })), [
    { level: 'ERROR', errorClass: 'timeout', code: 'LOCAL_NETWORK_UNAVAILABLE' },
    { level: 'WARN', errorClass: 'rate_limit', code: 'TOO_MANY_REQUESTS' },
    { level: 'ERROR', errorClass: 'bad_response', code: 'INVALID_RESPONSE' },
  ])
})
