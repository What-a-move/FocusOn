import assert from 'node:assert/strict'
import test from 'node:test'
import { createApiClient } from '../features/session/api-client.ts'
import { displayActiveMs, formatClock, parseActiveSession } from '../features/session/session-model.ts'

const NOW = Date.parse('2026-10-25T01:00:00Z')
const json = (status: number, body: unknown) => new Response(status === 204 ? null : JSON.stringify(body), { status })

function setup(replies: Array<(url: string, init: RequestInit) => Response>, access: { accessToken: string; expiresAt: string } | null = { accessToken: 'a-1', expiresAt: new Date(NOW + 600_000).toISOString() }) {
  const calls: Array<{ url: string; init: RequestInit }> = []
  const store = {
    access, refresh: 'r-1' as string | null, cleared: false,
    async getAccess() { return this.access },
    async getRefreshToken() { return this.refresh },
    async saveRotation(t: { accessToken: string; refreshToken: string; accessTokenExpiresAt: string }) { this.access = { accessToken: t.accessToken, expiresAt: t.accessTokenExpiresAt }; this.refresh = t.refreshToken },
    async clear() { this.cleared = true; this.access = null; this.refresh = null },
  }
  const waits: number[] = []
  const request = (async (url: string, init: RequestInit) => { calls.push({ url, init }); const reply = replies.shift(); if (!reply) throw new Error('network'); return reply(url, init) }) as typeof fetch
  const client = createApiClient({ baseUrl: 'https://api.test', store, request, sleep: async (ms) => { waits.push(ms) }, now: () => NOW })
  return { client, calls, store, waits }
}
const session = { sessionId: '901', status: 'RUNNING', version: 3, goalTextSnapshot: '미적분 풀기', targetDurationMs: 1500000, accumulatedActiveMs: 300000 }

test('활성 세션 조회: Bearer 토큰으로 보내고, 204면 없음으로 읽는다', async () => {
  const { client, calls } = setup([() => json(200, { data: session }), () => json(204, null)])
  const first = await client.authorized('/api/v1/sessions/active', 'GET', 200, parseActiveSession)
  assert.equal(first.success && 'sessionId' in first.data && first.data.goalText, '미적분 풀기')
  assert.equal((calls[0].init.headers as Record<string, string>).Authorization, 'Bearer a-1')
  const none = await client.authorized('/api/v1/sessions/active', 'GET', 200, parseActiveSession)
  assert.deepEqual(none, { success: true, data: { none: true } })
})

test('만료가 가까우면 먼저 갱신하고, 바뀐 Refresh Token을 저장한다', async () => {
  const { client, calls, store } = setup([
    () => json(200, { data: { accessToken: 'a-2', refreshToken: 'r-2', accessTokenExpiresAt: new Date(NOW + 900_000).toISOString() } }),
    () => json(200, { data: session }),
  ], { accessToken: 'a-1', expiresAt: new Date(NOW + 10_000).toISOString() })
  await client.authorized('/api/v1/sessions/active', 'GET', 200, parseActiveSession)
  assert.equal(calls[0].url, 'https://api.test/api/v1/auth/refresh')
  assert.equal((calls[0].init.headers as Record<string, string>).Authorization, undefined, 'refresh는 Bearer 없이 body로')
  assert.deepEqual(JSON.parse(String(calls[0].init.body)), { refreshToken: 'r-1' })
  assert.equal(store.refresh, 'r-2')
  assert.equal((calls[1].init.headers as Record<string, string>).Authorization, 'Bearer a-2')
})

test('서버가 만료를 알리면 한 번만 갱신하고 원 요청을 한 번만 다시 보낸다', async () => {
  const { client, calls } = setup([
    () => json(401, { code: 'AUTH_ACCESS_TOKEN_EXPIRED' }),
    () => json(200, { data: { accessToken: 'a-2', refreshToken: 'r-2', accessTokenExpiresAt: new Date(NOW + 900_000).toISOString() } }),
    () => json(200, { data: session }),
  ])
  const result = await client.authorized('/api/v1/sessions/active', 'GET', 200, parseActiveSession)
  assert.equal(result.success, true)
  assert.equal(calls.length, 3)
})

test('동시에 갱신이 필요해도 refresh는 한 번만 보낸다', async () => {
  const { client, calls } = setup([
    () => json(200, { data: { accessToken: 'a-2', refreshToken: 'r-2', accessTokenExpiresAt: new Date(NOW + 900_000).toISOString() } }),
    () => json(200, { data: session }),
    () => json(200, { data: session }),
  ], null)
  await Promise.all([client.authorized('/a', 'GET', 200, parseActiveSession), client.authorized('/b', 'GET', 200, parseActiveSession)])
  assert.equal(calls.filter((call) => call.url.endsWith('/auth/refresh')).length, 1)
})

test('재로그인 필요·연결 해제면 로컬 토큰을 지우고 RELINK_REQUIRED', async () => {
  const revoked = setup([() => json(401, { code: 'LINK_TOKEN_REVOKED' })])
  assert.deepEqual(await revoked.client.authorized('/a', 'GET', 200, parseActiveSession), { success: false, error: { code: 'RELINK_REQUIRED', retryable: false } })
  assert.equal(revoked.store.cleared, true)
  const reuse = setup([() => json(401, { code: 'AUTH_TOKEN_REUSE_DETECTED' })], null)
  assert.equal((await reuse.client.authorized('/a', 'GET', 200, parseActiveSession)).success, false)
  assert.equal(reuse.store.cleared, true)
})

test('GET은 네트워크·503에서 1초 → 3초로 다시 보내고, DELETE는 다시 보내지 않는다', async () => {
  const get = setup([() => { throw new Error('offline') }, () => json(503, { code: 'SERVICE_UNAVAILABLE' }), () => json(200, { data: session })])
  assert.equal((await get.client.authorized('/a', 'GET', 200, parseActiveSession)).success, true)
  assert.deepEqual(get.waits, [1000, 3000])
  const del = setup([() => json(503, { code: 'SERVICE_UNAVAILABLE' })])
  assert.equal((await del.client.authorized('/x', 'DELETE', 204, () => ({ ok: true }))).success, false)
  assert.equal(del.calls.length, 1)
})

test('화면용 누적 시간은 실행 중일 때만 받은 뒤 흐른 시간을 더하고 목표를 넘지 않는다', () => {
  const parsed = parseActiveSession(session)
  assert.ok(parsed && 'sessionId' in parsed)
  assert.equal(displayActiveMs(parsed, 0, 60_000), 360_000)
  assert.equal(displayActiveMs({ ...parsed, status: 'PAUSED' }, 0, 60_000), 300_000)
  assert.equal(displayActiveMs(parsed, 0, 9_999_999), 1_500_000)
  assert.equal(formatClock(1_200_000), '20:00')
  assert.equal(parseActiveSession({ ...session, status: 'DONE' }), null)
})
