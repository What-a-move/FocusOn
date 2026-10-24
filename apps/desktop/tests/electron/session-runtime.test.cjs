const assert = require('node:assert/strict')
const test = require('node:test')
const { SessionRuntime } = require('../../dist-electron/main/session-runtime.cjs')

const started = { sessionId: '901', status: 'RUNNING', version: 1, targetDurationMs: 10 * 60000, accumulatedActiveMs: 0, startedAt: '2026-10-25T01:00:00Z', interventionPolicyVersion: 'v1' }

// 경로별 응답을 돌려주는 가짜 인증 요청. parse를 그대로 통과시켜 실제 계약 검사도 같이 확인한다.
function setup(routes, ready = { screen: () => true, notification: () => true }) {
  const calls = []
  const events = []
  let clock = 0
  const auth = {
    authorizedRequest: async (request) => {
      calls.push(request)
      const reply = routes[`${request.method} ${request.path}`]
      const result = typeof reply === 'function' ? reply(request, calls) : reply
      if (!result) return { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } }
      if (!result.success) return result
      // raw: auth-api가 이미 parse한 결과(예: 204 → parse(null))를 그대로 돌려준다.
      if ('raw' in result) return { success: true, data: result.raw }
      const data = request.parse(result.data)
      return data ? { success: true, data } : { success: false, error: { code: 'INVALID_RESPONSE', retryable: false } }
    },
  }
  const runtime = new SessionRuntime(auth, (snapshot) => events.push(snapshot), ready, () => clock, () => new Date('2026-10-25T01:00:00Z'))
  return { runtime, calls, events, advance: (ms) => { clock += ms } }
}

test('누적 시간은 주입한 monotonic clock으로만 세고, 목표 시간에 닿으면 멈춰 확인을 기다린다', () => {
  const { runtime, advance } = setup({})
  runtime.begin(started, '미적분 풀기')
  advance(4 * 60000 + 0.7)
  assert.equal(runtime.snapshot().accumulatedActiveMs, 4 * 60000, '정수 ms로 내림')
  advance(7 * 60000)
  runtime.tick()
  assert.equal(runtime.snapshot().status, 'AWAITING_END_CONFIRMATION')
  assert.equal(runtime.snapshot().accumulatedActiveMs, 10 * 60000, '목표 시간을 넘겨 세지 않음')
  advance(60000)
  assert.equal(runtime.snapshot().accumulatedActiveMs, 10 * 60000)
})

test('일시정지는 Idempotency-Key·If-Match-Version을 붙이고, 실패 뒤 다시 누르면 같은 키를 쓴다', async () => {
  let attempt = 0
  const { runtime, calls, advance } = setup({
    'POST /api/v1/sessions/901/pause': () => (++attempt === 1
      ? { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } }
      : { success: true, data: { status: 'PAUSED', version: 2, accumulatedActiveMs: 120000 } }),
  })
  runtime.begin(started, '미적분 풀기')
  advance(120000)
  assert.equal((await runtime.pause()).success, false)
  assert.equal(runtime.snapshot().error, 'LOCAL_NETWORK_UNAVAILABLE')
  assert.equal(runtime.snapshot().status, 'RUNNING', '실패하면 상태를 바꾸지 않음')
  assert.equal((await runtime.pause()).success, true)
  const [first, second] = calls
  assert.equal(first.headers['Idempotency-Key'], second.headers['Idempotency-Key'])
  assert.equal(first.headers['If-Match-Version'], '1')
  assert.deepEqual(first.body, { pauseReason: 'USER', accumulatedActiveMs: 120000 })
  assert.equal(runtime.snapshot().status, 'PAUSED')
  advance(60000)
  assert.equal(runtime.snapshot().accumulatedActiveMs, 120000, '멈춘 동안은 세지 않음')
})

test('버전 충돌이면 서버 최신 상태로 맞추고 로컬 값으로 덮어쓰지 않는다', async () => {
  const { runtime, calls } = setup({
    'POST /api/v1/sessions/901/pause': { success: false, error: { code: 'SESSION_VERSION_CONFLICT', retryable: false } },
    'GET /api/v1/sessions/901': { success: true, data: { sessionId: '901', status: 'PAUSED', version: 5, goalTextSnapshot: '미적분 풀기', targetDurationMs: 600000, accumulatedActiveMs: 300000 } },
  })
  runtime.begin(started, '미적분 풀기')
  await runtime.pause()
  assert.equal(calls[1].path, '/api/v1/sessions/901')
  assert.equal(runtime.snapshot().status, 'PAUSED')
  assert.equal(runtime.snapshot().accumulatedActiveMs, 300000)
  assert.equal(runtime.snapshot().error, 'SESSION_VERSION_CONFLICT')
})

test('heartbeat는 X-Device-Id로 누적 시간을 보내고, 서버가 끝낸 세션은 되살리지 않는다', async () => {
  const { runtime, calls, advance } = setup({
    'POST /api/v1/sessions/901/heartbeats': { success: false, error: { code: 'SESSION_ABANDONED', retryable: false } },
  })
  runtime.begin(started, '미적분 풀기')
  advance(30000)
  await runtime.heartbeat()
  assert.equal(calls[0].withDeviceId, true)
  assert.deepEqual(calls[0].body, { clientObservedAt: '2026-10-25T01:00:00.000Z', accumulatedActiveMs: 30000 })
  assert.equal(runtime.snapshot().status, 'ABANDONED')
  assert.equal((await runtime.resume()).success, false)
})

test('앱 재실행: 실행 중이던 세션은 서버 확정 시간으로 APP_RESTART 일시정지한다', async () => {
  const active = { sessionId: '901', status: 'RUNNING', version: 3, goalTextSnapshot: '미적분 풀기', targetDurationMs: 600000, accumulatedActiveMs: 200000 }
  const { runtime, calls, advance } = setup({
    'GET /api/v1/sessions/active': { success: true, data: active },
    'POST /api/v1/sessions/901/pause': { success: true, data: { status: 'AUTO_PAUSED', version: 4, accumulatedActiveMs: 200000 } },
  })
  advance(999999)
  const restored = await runtime.restore()
  assert.equal(restored.success, true)
  assert.deepEqual(calls[1].body, { pauseReason: 'APP_RESTART', accumulatedActiveMs: 200000 }, '꺼져 있던 시간을 더하지 않음')
  assert.equal(runtime.snapshot().status, 'AUTO_PAUSED')
})

test('활성 세션이 없다는 204 응답은 null로 끝난다', async () => {
  const { runtime } = setup({ 'GET /api/v1/sessions/active': (request) => ({ success: true, raw: request.parse(null) }) })
  const result = await runtime.restore()
  assert.deepEqual(result, { success: true, data: null })
  assert.equal(runtime.snapshot(), null)
})

test('알림 확인 기록이 없으면 재개 요청을 보내지 않는다', async () => {
  const { runtime, calls } = setup({
    'POST /api/v1/sessions/901/pause': { success: true, data: { status: 'PAUSED', version: 2 } },
  }, { screen: () => true, notification: () => false })
  runtime.begin(started, '미적분 풀기')
  await runtime.pause()
  const result = await runtime.resume()
  assert.equal(result.success, false)
  assert.equal(result.error.code, 'LOCAL_NOTIFICATION_NOT_CONFIRMED')
  assert.equal(calls.length, 1)
})

test('시간이 끝난 뒤 연장하면 목표 시간이 늘고 다시 센다', async () => {
  const { runtime, calls, advance } = setup({
    'POST /api/v1/sessions/901/extend': { success: true, data: { targetDurationMs: 25 * 60000, version: 2 } },
  })
  runtime.begin(started, '미적분 풀기')
  advance(10 * 60000)
  runtime.tick()
  await runtime.extend(15)
  assert.deepEqual(calls[0].body, { additionalDurationMs: 15 * 60000 })
  assert.equal(runtime.snapshot().status, 'RUNNING')
  advance(60000)
  assert.equal(runtime.snapshot().accumulatedActiveMs, 11 * 60000)
})

test('같은 의도로 다시 보낼 때는 처음 키와 본문을 그대로 쓰고, 연장 시간이 바뀌면 새 키를 만든다', async () => {
  let pauseTry = 0
  const { runtime, calls, advance } = setup({
    'POST /api/v1/sessions/901/pause': () => (++pauseTry === 1 ? { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } } : { success: true, data: { status: 'PAUSED', version: 2 } }),
    'POST /api/v1/sessions/901/extend': { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } },
  })
  runtime.begin(started, '미적분 풀기')
  advance(60000)
  await runtime.pause()
  advance(30000)
  await runtime.pause()
  assert.deepEqual(calls[1].body, calls[0].body, '시간이 흘러도 재시도 본문은 처음과 같음')
  assert.equal(calls[1].headers['Idempotency-Key'], calls[0].headers['Idempotency-Key'])
  await runtime.extend(10)
  await runtime.extend(10)
  await runtime.extend(25)
  assert.equal(calls[3].headers['Idempotency-Key'], calls[2].headers['Idempotency-Key'])
  assert.notEqual(calls[4].headers['Idempotency-Key'], calls[3].headers['Idempotency-Key'], '연장 시간이 바뀌면 새 키')
  assert.deepEqual(calls[4].body, { additionalDurationMs: 25 * 60000 })
})

test('로그아웃하면 세션 상태를 비우고 빈 상태를 알린다', () => {
  const { runtime, events } = setup({})
  runtime.begin(started, '미적분 풀기')
  runtime.reset()
  assert.equal(runtime.snapshot(), null)
  assert.equal(events.at(-1), null)
})

test('복구 조회 중 새 세션이 시작되면 늦게 온 복구 결과로 덮어쓰지 않는다', async () => {
  let release
  const { runtime } = setup({
    'GET /api/v1/sessions/active': () => new Promise((resolve) => { release = () => resolve({ success: true, data: { sessionId: '700', status: 'PAUSED', version: 9, goalTextSnapshot: '이전 목표', targetDurationMs: 600000, accumulatedActiveMs: 100000 } }) }),
  })
  const restoring = runtime.restore()
  runtime.begin(started, '새 목표')
  release()
  await restoring
  assert.equal(runtime.snapshot().sessionId, '901')
  assert.equal(runtime.snapshot().goalText, '새 목표')
})
