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

function setup(lists, { current = true, pauseOk = true, context = 'c-1', journalFile = tempJournal(), deliveries = () => ({ success: true, data: true }), clock = { t: 0 }, showOk = true, show = () => showOk } = {}) {
  const calls = []; const shown = []; const stages = []; const notified = []; let paused = 0
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
    auth, show: (p, selectStage, canShow) => { shown.push(p); stages.push(selectStage()); return show(canShow, selectStage) }, notify: (p, sound) => { notified.push([p.interventionId, sound]); return true },
    pause: async () => { paused += 1; return { success: pauseOk } }, isCurrent: () => current, currentContext: () => context,
    journalFile, now: () => clock.t, uuid: () => `d-${++n}`,
  })
  const receipts = () => calls.filter((c) => c.path.endsWith('/deliveries')).map((c) => [c.path, c.body])
  return { watcher, calls, shown, stages, notified, receipts, journalFile, paused: () => paused }
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
  const b = setup([[pending('b-2', { type: 'BREAK_SUGGESTION', channels: ['MOLE_UI'], breakEvidence: { repeatCount: 3, windowMinutes: 30 } })]])
  await b.watcher.poll('901', '목표', OPTIONS)
  assert.equal(b.shown[0].say, '최근 30분 동안 3번 흐트러졌어.')
  const noEvidence = setup([[pending('b-3', { type: 'BREAK_SUGGESTION', channels: ['MOLE_UI'], breakEvidence: { repeatCount: 0, windowMinutes: 30 } })]])
  await noEvidence.watcher.poll('901', '목표', OPTIONS)
  assert.equal(noEvidence.shown[0].say, '벌써 세 번째 흐트러졌어.', '근거가 이상하면 기본 문구')
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
  await t.watcher.act('i-4', 'NOT_OFF_TASK')
  assert.deepEqual(t.calls.map((call) => [call.path, call.body.action, call.withDeviceId]), [
    ['/api/v1/interventions/i-1/acknowledgements', 'ACKNOWLEDGED', true],
    ['/api/v1/interventions/i-2/acknowledgements', 'DISMISSED', true],
    ['/api/v1/interventions/i-3/acknowledgements', 'ACKNOWLEDGED', true],
    ['/api/v1/interventions/i-4/acknowledgements', 'NOT_OFF_TASK', true],
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

test('보통은 같은 세션의 중복 없는 딴짓 알림을 1차·2차·3차로 표시한다', async () => {
  const first = pending('normal-1')
  const t = setup([[first], [first], [pending('normal-2')], [pending('normal-3')], [pending('normal-4')], [pending('gentle')], [pending('normal-5')], [pending('normal-6')]])
  for (let i = 0; i < 5; i++) await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.stages, ['peek', 'normal', 'active', 'active'])
  await t.watcher.poll('901', '목표', { ...OPTIONS, interventionLevel: 'GENTLE' })
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.stages.slice(-2), ['gentle', 'peek'])
  t.watcher.reset()
  await t.watcher.poll('902', '새 목표', OPTIONS)
  assert.equal(t.stages.at(-1), 'peek')
})

test('10분 안에 표시한 보통 알림 세 번은 1차·2차·적극 단계로 올라간다', async () => {
  const clock = { t: 0 }
  const t = setup(['one', 'two', 'three'].map((id) => [pending(id)]), { clock })
  for (const minutes of [0, 3, 8]) {
    clock.t = minutes * 60_000
    await t.watcher.poll('901', '목표', OPTIONS)
  }
  assert.deepEqual(t.stages, ['peek', 'normal', 'active'])
})

test('마지막으로 표시한 보통 알림에서 10분이 지나면 다시 1차로 표시한다', async () => {
  const clock = { t: 0 }
  const t = setup(['one', 'two', 'three', 'four'].map((id) => [pending(id)]), { clock })
  for (const ms of [0, 9 * 60_000, 19 * 60_000, 29 * 60_000 + 1]) {
    clock.t = ms
    await t.watcher.poll('901', '목표', OPTIONS)
  }
  assert.deepEqual(t.stages, ['peek', 'normal', 'peek', 'peek'])
})

test('약하게나 적극 알림이 끼면 제한 시간 안이어도 보통 알림을 다시 1차로 표시한다', async () => {
  const clock = { t: 0 }
  const t = setup(['one', 'gentle', 'two', 'active', 'three'].map((id) => [pending(id)]), { clock })
  for (const interventionLevel of ['NORMAL', 'GENTLE', 'NORMAL', 'ACTIVE', 'NORMAL']) {
    await t.watcher.poll('901', '목표', { ...OPTIONS, interventionLevel })
    clock.t += 60_000
  }
  assert.deepEqual(t.stages, ['peek', 'gentle', 'peek', 'active', 'peek'])
})


test('창 로드 결과를 기다리고 실패한 팝업은 전송이나 보통 단계로 세지 않는다', async () => {
  const { promise, resolve } = Promise.withResolvers()
  let attempt = 0
  const t = setup([[pending('failed')], [pending('shown')]], {
    show: () => ++attempt === 1 ? promise : true,
  })
  const polling = t.watcher.poll('901', '목표', OPTIONS)
  await Promise.resolve()
  assert.deepEqual(t.stages, ['peek'])
  assert.deepEqual(t.receipts(), [])
  resolve(false)
  await polling
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.stages, ['peek', 'peek'])
  assert.deepEqual(t.receipts().map(([, body]) => [body.dispatchStatus, body.channels]), [
    ['NOT_SENT', []],
    ['SENT', ['MOLE_UI']],
  ])
})

test('창 로드 도중 표시 가능 시간을 넘기면 팝업과 시스템 알림을 보내지 않는다', async () => {
  const clock = { t: 0 }
  const { promise, resolve } = Promise.withResolvers()
  const t = setup([[pending('expired', { remainingTtlMs: 1000, channels: ['MOLE_UI', 'MACOS_NOTIFICATION'] })]], {
    clock, show: async (canShow) => { await promise; return canShow() },
  })
  const polling = t.watcher.poll('901', '목표', OPTIONS)
  await Promise.resolve()
  clock.t = 1000
  resolve()
  await polling
  assert.deepEqual(t.notified, [])
  assert.equal(t.receipts()[0][1].dispatchStatus, 'NOT_SENT')
})


test('창을 띄우기 전에 10분이 지나면 보통 단계와 횟수를 1차로 바꾼다', async () => {
  const clock = { t: 0 }
  const t = setup(['one', 'two', 'three'].map((id) => [pending(id)]), {
    clock,
    show: (_canShow, selectStage) => {
      if (clock.t === 599_000) {
        clock.t = 601_000
        assert.equal(selectStage(), 'peek')
      }
      return true
    },
  })
  await t.watcher.poll('901', '목표', OPTIONS)
  clock.t = 599_000
  await t.watcher.poll('901', '목표', OPTIONS)
  clock.t = 602_000
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(t.stages, ['peek', 'normal', 'normal'])
})

test('할 일 추가 알림은 접속 중인 세션 ID를 전달하고 이탈 강도를 적용하지 않는다', async () => {
  const t = setup([[pending('goal-1', { type: 'GOAL_ADD_PROMPT' })]])
  await t.watcher.poll('901', '목표', OPTIONS)
  assert.equal(t.shown[0].kind, 'GOAL_ADD')
  assert.equal(t.shown[0].sessionId, '901')
  assert.deepEqual(t.stages, [undefined])
  assert.equal(t.receipts()[0][1].dispatchStatus, 'SENT')
})
