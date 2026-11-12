const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { ObservationContext } = require('../../dist-electron/main/observation-context.cjs')

const tempFile = () => path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'fo-context-')), 'context.json')

// PUT 응답을 손으로 정할 수 있는 가짜 서버. reply가 없으면 요청 순번으로 성공한다.
function setup({ reply, file = tempFile(), clock = { t: 0 } } = {}) {
  const calls = []
  let n = 0
  const auth = {
    authorizedRequest: async (request) => {
      calls.push(request)
      if (reply) return reply(request, calls.length)
      return { success: true, data: request.parse({ observationContextId: `ctx-${request.body.foregroundSequence}`, observer: request.body.observer, foregroundSequence: request.body.foregroundSequence }) }
    },
  }
  const context = new ObservationContext(auth, file, () => clock.t, () => `nonce-${++n}`)
  return { context, calls, file, clock }
}

test('화면이 바뀔 때만 순번을 올려 등록하고, 앱 이름·창 제목은 보내지 않는다', async () => {
  const t = setup()
  await t.context.update('901', 'com.apple.Notes\n강의 노트', 'DESKTOP')
  await t.context.update('901', 'com.apple.Notes\n강의 노트', 'DESKTOP')
  assert.equal(t.context.currentId(), 'ctx-1')
  await t.context.update('901', 'com.google.Chrome\nYouTube', 'EXTENSION')
  assert.deepEqual(t.calls.map((c) => [c.method, c.path, c.body, c.withDeviceId]), [
    ['PUT', '/api/v1/sessions/901/observation-context', { clientContextId: 'nonce-1', foregroundSequence: 1, observer: 'DESKTOP' }, true],
    ['PUT', '/api/v1/sessions/901/observation-context', { clientContextId: 'nonce-2', foregroundSequence: 2, observer: 'EXTENSION' }, true],
  ])
  assert.equal(t.context.currentId(), 'ctx-2')
  assert.deepEqual(JSON.parse(fs.readFileSync(t.file, 'utf8')), { sessionId: '901', sequence: 2 })
})

test('실패하면 같은 순번·nonce로 다시 보내고, 앱을 다시 켜도 순번을 이어 센다', async () => {
  let fail = true
  const t = setup({ reply: (request) => fail
    ? { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } }
    : { success: true, data: request.parse({ observationContextId: 'ctx-a', foregroundSequence: request.body.foregroundSequence }) } })
  await t.context.update('901', 'app\nA', 'DESKTOP')
  assert.equal(t.context.currentId(), null, '확정 전에는 붙이지 않음')
  fail = false
  await t.context.update('901', 'app\nA', 'DESKTOP')
  assert.deepEqual(t.calls.map((c) => c.body), [
    { clientContextId: 'nonce-1', foregroundSequence: 1, observer: 'DESKTOP' },
    { clientContextId: 'nonce-1', foregroundSequence: 1, observer: 'DESKTOP' },
  ])
  const restarted = setup({ file: t.file })
  await restarted.context.update('901', 'app\nA', 'DESKTOP')
  assert.equal(restarted.calls[0].body.foregroundSequence, 2)
})

test('기다리는 사이 화면이 또 바뀌면 늦게 온 응답을 쓰지 않는다', async () => {
  let release
  const t = setup({ reply: (request, count) => count === 1
    ? new Promise((resolve) => { release = () => resolve({ success: true, data: request.parse({ observationContextId: 'old', foregroundSequence: 1 }) }) })
    : { success: true, data: request.parse({ observationContextId: 'new', foregroundSequence: request.body.foregroundSequence }) } })
  const first = t.context.update('901', 'app\nA', 'DESKTOP')
  await t.context.update('901', 'app\nB', 'DESKTOP')
  release()
  await first
  assert.equal(t.context.currentId(), null, '지난 화면 응답은 버림')
  await t.context.update('901', 'app\nB', 'DESKTOP')
  assert.equal(t.context.currentId(), 'new')
})

test('heartbeat가 25초 넘게 이어 주지 못하면 새 순번으로 다시 등록한다. 확인 불가(NONE)는 붙이지 않는다', async () => {
  const t = setup()
  await t.context.update('901', 'app\nA', 'DESKTOP')
  t.clock.t = 20_000
  t.context.renewed('ctx-1')
  t.clock.t = 40_000
  await t.context.update('901', 'app\nA', 'DESKTOP')
  assert.equal(t.calls.length, 1, '갱신 뒤 25초 안이면 그대로')
  t.clock.t = 46_000
  await t.context.update('901', 'app\nA', 'DESKTOP')
  assert.equal(t.calls[1].body.foregroundSequence, 2)
  await t.context.update('901', null, 'NONE')
  assert.equal(t.calls[2].body.observer, 'NONE')
  assert.equal(t.context.currentId(), null)
})

test('서버가 작은 순번이라 거절하면 순번을 올려 새 전환으로 보낸다', async () => {
  const t = setup({ reply: (request, count) => count === 1
    ? { success: false, error: { code: 'IDEMPOTENCY_CONFLICT', retryable: false } }
    : { success: true, data: request.parse({ observationContextId: 'ctx-ok', foregroundSequence: request.body.foregroundSequence }) } })
  await t.context.update('901', 'app\nA', 'DESKTOP')
  await t.context.update('901', 'app\nA', 'DESKTOP')
  assert.deepEqual(t.calls.map((c) => [c.body.foregroundSequence, c.body.clientContextId]), [[1, 'nonce-1'], [2, 'nonce-2']])
  assert.equal(t.context.currentId(), 'ctx-ok')
})
