const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { InterventionWatcher, parsePending } = require('../../dist-electron/main/intervention-watcher.cjs')
const { isActInput } = require('../../dist-electron/shared/intervention-contract.cjs')

const OPTIONS = { interventionLevel: 'NORMAL', notificationChannels: ['MOLE_UI'], cameraAnalysisEnabled: false }
// 서버 알림 1건. 기본은 지금 전경 context(c-1)에서 만들어졌고 30초 남았다.
const pending = (id, extra = {}) => ({ interventionId: id, type: 'OFF_TASK', status: 'PENDING', channels: [], observationContextId: 'c-1', remainingTtlMs: 30000, ...extra })
const tempJournal = () => path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'fo-receipts-')), 'receipts.json')

function setup(lists, { current = true, pauseOk = true, context = 'c-1', journalFile = tempJournal(), deliveries = () => ({ success: true, data: true }), clock = { t: 0 }, showOk = true } = {}) {
  const calls = []; const shown = []; const notified = []; let paused = 0
  const auth = {
    authorizedRequest: async (request) => {
      calls.push(request)
      if (request.method === 'GET') { const data = request.parse(lists.shift() ?? []); return data ? { success: true, data } : { success: false, error: { code: 'INVALID_RESPONSE', retryable: false } } }
      if (request.path.endsWith('/deliveries')) return deliveries(request)
      return { success: true, data: true }
    },
  }
  let n = 0
  const watcher = new InterventionWatcher({
    auth, show: (p) => { shown.push(p); return showOk }, notify: (p, sound) => { notified.push([p.interventionId, sound]); return true },
    pause: async () => { paused += 1; return { success: pauseOk } }, isCurrent: () => current, currentContext: () => context,
    journalFile, now: () => clock.t, uuid: () => `d-${++n}`,
  })
  const receipts = () => calls.filter((c) => c.path.endsWith('/deliveries')).map((c) => [c.path, c.body])
  return { watcher, calls, shown, notified, receipts, journalFile, paused: () => paused }
}

test('처음 보는 PENDING 알림 하나만 강도별 문구로 보여 주고, 띄운 결과를 SENT로 보낸다', async () => {
  const item = pending('i-1')
  const t = setup([[item, { ...item, interventionId: 'i-0', status: 'ACKNOWLEDGED' }], { interventions: [item] }])
  await t.watcher.poll('901', '미적분 풀기', OPTIONS)
  await t.watcher.poll('901', '미적분 풀기', OPTIONS)
  assert.equal(t.calls[0].path, '/api/v1/sessions/901/interventions')
  assert.deepEqual(t.shown, [{ interventionId: 'i-1', kind: 'OFF_TASK', level: 'NORMAL', goalText: '미적분 풀기', say: '어? 또 딴 데 보고 있네 👀', sub: '목표로 돌아가자!' }])
  assert.deepEqual(t.receipts(), [['/api/v1/interventions/i-1/deliveries', { clientDeliveryId: 'd-1', dispatchStatus: 'SENT', channels: ['MOLE_UI'] }]])
  assert.equal(t.calls.find((c) => c.path.endsWith('/deliveries')).withDeviceId, true)
})

test('지금 전경과 다른 context·남은 시간이 지난 알림·예전 이력(context 없음)은 띄우지 않는다', async () => {
  const clock = { t: 0 }
  const t = setup([[pending('x-1', { observationContextId: 'c-0' }), pending('x-2', { observationContextId: null }), pending('x-3', { remainingTtlMs: 0 })]], { clock })
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.shown, [])
  // 응답이 늦게 와서 남은 시간을 다 썼으면 버린다(요청 시작 뒤 monotonic 경과 차감).
  const late = setup([[pending('x-4', { remainingTtlMs: 1000 })]], { clock })
  const auth = late.watcher.deps.auth
  const original = auth.authorizedRequest
  auth.authorizedRequest = async (request) => { clock.t += 1500; return original(request) }
  await late.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(late.shown, [])
  // 전경을 모르면(context 없음) 어떤 알림도 띄우지 않는다.
  const none = setup([[pending('x-5')]], { context: null })
  await none.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(none.shown, [])
})

test('receipt를 못 보냈으면 같은 nonce로 다시 보내고, 그동안 새 알림을 띄우지 않는다', async () => {
  let fail = true
  const t = setup([[pending('r-1')], [pending('r-2')]], { deliveries: () => fail ? { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } } : { success: true, data: true } })
  await t.watcher.poll('901', '목표', OPTIONS)
  fail = false
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.shown.map((p) => p.interventionId), ['r-1'], '밀린 receipt를 보내는 주기에는 새 후보를 띄우지 않음')
  assert.deepEqual(t.receipts().map(([, body]) => body.clientDeliveryId), ['d-1', 'd-1'])
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.shown.map((p) => p.interventionId), ['r-1', 'r-2'])
})

test('앱을 다시 켰을 때 띄우는 중이던 시도는 UNKNOWN, 보내지 못한 SENT는 그대로 이어 보낸다', async () => {
  const journalFile = tempJournal()
  fs.writeFileSync(journalFile, JSON.stringify([
    { interventionId: 'k-1', clientDeliveryId: 'n-1', dispatchStatus: 'DISPATCHING', channels: [] },
    { interventionId: 'k-2', clientDeliveryId: 'n-2', dispatchStatus: 'SENT', channels: ['MOLE_UI'] },
  ]))
  const t = setup([], { journalFile })
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.receipts(), [
    ['/api/v1/interventions/k-1/deliveries', { clientDeliveryId: 'n-1', dispatchStatus: 'UNKNOWN', channels: [] }],
    ['/api/v1/interventions/k-2/deliveries', { clientDeliveryId: 'n-2', dispatchStatus: 'SENT', channels: ['MOLE_UI'] }],
  ])
  assert.deepEqual(JSON.parse(fs.readFileSync(journalFile, 'utf8')), [])
})

test('채널에 따라 포키·macOS 알림·소리를 고른다. 휴식 제안은 휴식 문구다. 띄우지 못했으면 NOT_SENT', async () => {
  const t = setup([[pending('b-1', { type: 'BREAK_SUGGESTION', channels: ['MACOS_NOTIFICATION', 'SOUND'] })]])
  await t.watcher.poll('901', '미적분 풀기', { ...OPTIONS, interventionLevel: 'ACTIVE' })
  assert.deepEqual(t.shown, [], 'MOLE_UI가 없으면 포키 창은 띄우지 않음')
  assert.deepEqual(t.notified, [['b-1', true]])
  assert.deepEqual(t.receipts()[0][1].channels, ['MACOS_NOTIFICATION', 'SOUND'])
  const g = setup([[pending('g-1')]])
  await g.watcher.poll('901', '목표', { ...OPTIONS, interventionLevel: 'GENTLE' })
  assert.equal(g.shown[0].say, '어, 잠깐 샜네?')
  const failed = setup([[pending('f-1')]], { showOk: false })
  await failed.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(failed.receipts()[0][1], { clientDeliveryId: 'd-1', dispatchStatus: 'NOT_SENT', channels: [] })
})

test('확인했어·잠시 쉬기는 확인, 닫기는 해제로 기록하고 잠시 쉬기는 세션을 멈춘다. 알겠어는 기록하지 않는다', async () => {
  const t = setup([])
  await t.watcher.act('i-1', 'RECENTER')
  await t.watcher.act('i-1', 'ACK')
  await t.watcher.act('i-2', 'CLOSE')
  await t.watcher.act('i-3', 'REST')
  assert.deepEqual(t.calls.map((call) => [call.path, call.body.action, call.withDeviceId]), [
    ['/api/v1/interventions/i-1/acknowledgements', 'ACKNOWLEDGED', true],
    ['/api/v1/interventions/i-2/acknowledgements', 'DISMISSED', true],
    ['/api/v1/interventions/i-3/acknowledgements', 'ACKNOWLEDGED', true],
  ])
  assert.equal(t.paused(), 1)
})

test('응답 형식이 다르면 보여 주지 않고, 포키 창 입력은 허용한 값만 받는다', async () => {
  assert.equal(parsePending({ items: [] }), null)
  assert.deepEqual(parsePending([{ interventionId: 1, type: 'OFF_TASK', status: 'PENDING' }]), [])
  assert.deepEqual(parsePending([{ interventionId: 'e-1', type: 'END_CONFIRMATION', status: 'PENDING' }]), [], '정하지 않은 type은 보이지 않음')
  assert.deepEqual(parsePending([pending('p-1', { remainingTtlMs: -1 })])[0].remainingTtlMs, null, '음수 남은 시간은 모르는 값')
  assert.equal(isActInput({ interventionId: 'i-1', action: 'RECENTER' }), true)
  assert.equal(isActInput({ interventionId: 'i-1', action: 'DELETE' }), false)
  assert.equal(isActInput({ interventionId: '../x', action: 'ACK' }), false)
  assert.equal(isActInput({ interventionId: 'i-1', action: 'ACK', extra: 1 }), false)
})

test('소리만 골라도 소리 있는 알림을 띄우고, 이미 끝난 세션의 조회 결과는 보이지 않는다', async () => {
  const sound = setup([[pending('s-1', { channels: ['SOUND'] })]])
  await sound.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(sound.notified, [['s-1', true]])
  const stale = setup([[pending('o-1')]], { current: false })
  await stale.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(stale.shown, [])
})

test('잠시 쉬기에서 일시정지에 실패하면 실패로 알린다', async () => {
  const t = setup([], { pauseOk: false })
  assert.equal(await t.watcher.act('i-1', 'REST'), false)
  assert.equal(await t.watcher.act('i-1', 'CLOSE'), true)
})
