const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { createLogger, initLogger, sanitize } = require('../../dist-electron/main/logger.cjs')
const { AuthApiClient } = require('../../dist-electron/main/auth-api.cjs')
const { AuthService } = require('../../dist-electron/main/auth-service.cjs')
const { AuthSessionStore } = require('../../dist-electron/main/auth-ipc.cjs')
const { SessionRuntime } = require('../../dist-electron/main/session-runtime.cjs')

// 로그를 파일 대신 배열에 모은다.
function capture() {
  const lines = []
  initLogger((level, event, fields = {}) => { lines.push({ level, event, ...sanitize(fields) }) })
  return lines
}

test('제목·본문은 글자 수로만, 토큰·URL·모르는 필드는 버리고 경로의 Query는 지운다', () => {
  assert.deepEqual(sanitize({
    title: '카카오톡 - 김철수와의 대화', text: '비밀 메모 내용', accessToken: 'secret', url: 'https://mail.example/inbox?q=1',
    nickname: '포커스', appName: 'KakaoTalk', bundleId: 'com.kakao', path: '/api/v1/goals?draft=비밀', domain: 'https://youtube.com/watch?v=1', status: 200,
  }), { titleLen: 15, textLen: 8, path: '/api/v1/goals', domain: 'youtube.com', status: 200 })
})

test('파일에는 공통 필드가 있는 JSON 한 줄씩 쌓이고, 1MB를 넘으면 .1.log로 넘긴다', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'focuson-log-'))
  const file = path.join(dir, 'logs', 'focuson.log')
  const log = createLogger(file, { now: () => new Date('2026-10-29T00:00:00Z') })
  log('INFO', 'session.start', { component: 'session', sessionId: '901', windowTitle: '은행 로그인' })
  const line = JSON.parse(fs.readFileSync(file, 'utf8').trim())
  assert.deepEqual(line, { ts: '2026-10-29T00:00:00.000Z', level: 'INFO', module: 'desktop', component: 'session', event: 'session.start', traceId: null, durMs: 0, sessionId: '901', titleLen: 6 })

  fs.appendFileSync(file, 'x'.repeat(1024 * 1024 + 1))
  log('INFO', 'session.end', {})
  assert.equal(fs.existsSync(file.replace('.log', '.1.log')), true)
  assert.equal(fs.readFileSync(file, 'utf8').trim().split('\n').length, 1)
  fs.rmSync(dir, { recursive: true })
})

test('서버 요청은 X-Trace-Id를 보내고 OUT·IN을 같은 traceId로 남긴다', async () => {
  const lines = capture()
  let headers
  const api = new AuthApiClient('http://127.0.0.1:8080', async (_url, options) => { headers = options.headers; return new Response(JSON.stringify({ data: { ok: true } }), { status: 200 }) })
  await api.call('/api/v1/users/me/profile?x=1', 'GET', 200, (value) => value)
  const traceId = headers['X-Trace-Id']
  assert.match(traceId, /^[0-9a-f-]{36}$/)
  assert.deepEqual(lines.map(({ event, traceId: id, path: p }) => ({ event, id, p })), [
    { event: 'api.request.out', id: traceId, p: '/api/v1/users/me/profile' },
    { event: 'api.response.in', id: traceId, p: '/api/v1/users/me/profile' },
  ])
})

test('분석 요청은 decision.* 이름과 정한 traceId를 쓰고, 응답 결과값만 남긴다', async () => {
  const lines = capture()
  let headers
  const api = new AuthApiClient('http://127.0.0.1:8080', async (_url, options) => { headers = options.headers; return new Response(JSON.stringify({ data: { relation: 'UNRELATED', reasonCode: 'SEMANTIC_GOAL_MISMATCH' } }), { status: 200 }) })
  await api.call('/api/v1/sessions/901/analysis-runs', 'POST', 200, (value) => value, {}, 'token', { 'X-Trace-Id': 'run-1' }, { contentSource: 'APP_META', title: '목표와 관계없는 창' })
  assert.equal(headers['X-Trace-Id'], 'run-1')
  assert.deepEqual(lines.map(({ event, traceId, relation, titleLen }) => ({ event, traceId, relation, titleLen })), [
    { event: 'decision.request.out', traceId: 'run-1', relation: undefined, titleLen: 10 },
    { event: 'decision.response.in', traceId: 'run-1', relation: 'UNRELATED', titleLen: 10 },
  ])
})

test('실패는 단계와 errorClass로 나눠 남긴다', async () => {
  const lines = capture()
  const timeout = Object.assign(new Error('late'), { name: 'TimeoutError' })
  await new AuthApiClient('http://x', async () => { throw timeout }).call('/a', 'GET', 200, (v) => v)
  await new AuthApiClient('http://x', async () => { throw new TypeError('fetch failed') }).call('/b', 'GET', 200, (v) => v)
  await new AuthApiClient('http://x', async () => new Response(JSON.stringify({ code: 'AUTH_ACCESS_TOKEN_EXPIRED' }), { status: 401 })).call('/c', 'GET', 200, (v) => v)
  await new AuthApiClient('http://x', async () => new Response('{}', { status: 200 })).call('/d', 'GET', 200, () => null)
  assert.deepEqual(lines.filter((line) => line.event === 'api.request.fail').map(({ level, errorClass, code }) => ({ level, errorClass, code })), [
    { level: 'ERROR', errorClass: 'timeout', code: 'LOCAL_NETWORK_UNAVAILABLE' },
    { level: 'ERROR', errorClass: 'network', code: 'LOCAL_NETWORK_UNAVAILABLE' },
    { level: 'WARN', errorClass: 'auth', code: 'AUTH_ACCESS_TOKEN_EXPIRED' },
    { level: 'ERROR', errorClass: 'bad_response', code: 'INVALID_RESPONSE' },
  ])
})

test('GET 재시도와 토큰 갱신 뒤 재전송은 같은 traceId를 쓴다', async () => {
  const sent = []
  let saved = null
  const pair = { user: { userId: 'u', nickname: null }, device: { deviceId: 'd', deviceType: 'DESKTOP' }, onboardingStatus: 'INCOMPLETE', accessToken: 'a', refreshToken: 'r', accessTokenExpiresAt: new Date(Date.now() + 900_000).toISOString() }
  const replies = [{ code: 'SERVICE_UNAVAILABLE' }, { code: 'AUTH_ACCESS_TOKEN_EXPIRED' }, null]
  const api = {
    exchange: async () => ({ success: true, data: pair }),
    refresh: async () => ({ success: true, data: pair }),
    call: async (...args) => {
      sent.push(args[6]['X-Trace-Id'])
      const reply = replies.shift()
      return reply ? { success: false, error: { ...reply, retryable: false } } : { success: true, data: {} }
    },
  }
  const service = new AuthService(new AuthSessionStore(() => {}), api, { read: async () => saved, write: async (v) => { saved = v }, clear: async () => { saved = null } }, async () => ({ installationId: 'i', deviceName: 'Mac', platform: 'MACOS' }), async () => {})
  await service.handleOAuthOutcome({ type: 'code', data: { authorizationCode: 'c', codeVerifier: 'v'.repeat(43), state: 's', redirectUri: 'http://127.0.0.1:1' } })
  await service.authorizedRequest({ path: '/p', method: 'GET', expectedStatus: 200, parse: (v) => v })
  assert.equal(sent.length, 3)
  assert.equal(new Set(sent).size, 1)
})

test('세션 상태가 바뀔 때마다 from·to·trigger와 머문 시간을 남기고, 두 시계가 벌어지면 절전으로 표시한다', async () => {
  const lines = capture()
  let mono = 0
  let wall = Date.parse('2026-10-29T00:00:00Z')
  const auth = { authorizedRequest: async (request) => ({ success: true, data: request.parse({ sessionId: '901', status: 'PAUSED', version: 2, accumulatedActiveMs: 1000 }) }) }
  const runtime = new SessionRuntime(auth, () => {}, { screen: () => true, notification: () => true }, () => mono, () => new Date(wall))
  runtime.begin({ sessionId: '901', status: 'RUNNING', version: 1, targetDurationMs: 600_000, accumulatedActiveMs: 0 }, '목표')
  // 덮개를 닫은 것처럼 벽시계만 10분 흐른다.
  mono += 1000
  wall += 600_000
  await runtime.pause()
  assert.deepEqual(lines.filter((line) => line.event === 'state.transition').map(({ from, to, trigger, heldMono, heldWall, sleepSuspected }) => ({ from, to, trigger, heldMono, heldWall, sleepSuspected })), [
    { from: null, to: 'RUNNING', trigger: 'start', heldMono: 0, heldWall: 0, sleepSuspected: false },
    { from: 'RUNNING', to: 'PAUSED', trigger: 'pause:USER', heldMono: 1000, heldWall: 600_000, sleepSuspected: true },
  ])
})

test('직렬화할 수 없는 값이 와도 로거가 예외를 밖으로 내보내지 않는다', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'focuson-log-'))
  const log = createLogger(path.join(dir, 'focuson.log'))
  assert.doesNotThrow(() => log('INFO', 'session.start', { sessionId: 10n }))
  fs.rmSync(dir, { recursive: true })
})

test('로그아웃으로 세션이 사라지면 to: null 전이를 남긴다', () => {
  const lines = capture()
  const runtime = new SessionRuntime({ authorizedRequest: async () => ({ success: false, error: { code: 'X', retryable: false } }) }, () => {}, { screen: () => true, notification: () => true }, () => 0, () => new Date(0))
  runtime.begin({ sessionId: '901', status: 'RUNNING', version: 1, targetDurationMs: 600_000, accumulatedActiveMs: 0 }, '목표')
  runtime.reset()
  const last = lines.filter((line) => line.event === 'state.transition').at(-1)
  assert.deepEqual({ from: last.from, to: last.to, trigger: last.trigger }, { from: 'RUNNING', to: null, trigger: 'logout' })
})

test('모르는 알림 채널만 오면 알림도 notify.fire도 남기지 않는다', async () => {
  const lines = capture()
  const { InterventionWatcher } = require('../../dist-electron/main/intervention-watcher.cjs')
  const shown = []
  const auth = { authorizedRequest: async (request) => ({ success: true, data: request.parse([{ interventionId: 'i-1', type: 'OFF_TASK', status: 'PENDING', channels: ['<script>'] }]) }) }
  const watcher = new InterventionWatcher({ auth, show: (p) => shown.push(p), notify: (p) => shown.push(p), pause: async () => ({ success: true }), isCurrent: () => true })
  await watcher.poll('901', '목표', { interventionLevel: 'NORMAL', notificationChannels: ['MOLE_UI'], cameraAnalysisEnabled: false })
  assert.deepEqual(shown, [])
  assert.equal(lines.some((line) => line.event === 'notify.fire'), false)
})
