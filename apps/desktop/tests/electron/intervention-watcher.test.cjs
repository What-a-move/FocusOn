const assert = require('node:assert/strict')
const test = require('node:test')
const { InterventionWatcher, parsePending } = require('../../dist-electron/main/intervention-watcher.cjs')
const { isActInput } = require('../../dist-electron/shared/intervention-contract.cjs')

const OPTIONS = { interventionLevel: 'NORMAL', notificationChannels: ['MOLE_UI'], cameraAnalysisEnabled: false }
function setup(lists, { current = true, pauseOk = true } = {}) {
  const calls = []; const shown = []; const notified = []; let paused = 0
  const auth = {
    authorizedRequest: async (request) => {
      calls.push(request)
      if (request.method === 'GET') { const data = request.parse(lists.shift() ?? []); return data ? { success: true, data } : { success: false, error: { code: 'INVALID_RESPONSE', retryable: false } } }
      return { success: true, data: true }
    },
  }
  const watcher = new InterventionWatcher({ auth, show: (p) => shown.push(p), notify: (p, sound) => notified.push([p.interventionId, sound]), pause: async () => { paused += 1; return { success: pauseOk } }, isCurrent: () => current })
  return { watcher, calls, shown, notified, paused: () => paused }
}

test('처음 보는 PENDING 알림 하나만 강도별 문구로 보여 주고, 같은 알림은 다시 보이지 않는다', async () => {
  const item = { interventionId: 'i-1', type: 'OFF_TASK', status: 'PENDING', channels: [] }
  const t = setup([[item, { ...item, interventionId: 'i-0', status: 'ACKNOWLEDGED' }], { interventions: [item] }])
  await t.watcher.poll('901', '미적분 풀기', OPTIONS)
  await t.watcher.poll('901', '미적분 풀기', OPTIONS)
  assert.equal(t.calls[0].path, '/api/v1/sessions/901/interventions')
  assert.deepEqual(t.shown, [{ interventionId: 'i-1', kind: 'OFF_TASK', level: 'NORMAL', goalText: '미적분 풀기', say: '어? 또 다른 데 보고 있네 👀', sub: '지금 목표로 돌아가자!' }])
})

test('채널에 따라 포키·macOS 알림·소리를 고른다. 휴식 제안은 휴식 문구다', async () => {
  const t = setup([[{ interventionId: 'b-1', type: 'BREAK_SUGGESTION', status: 'PENDING', channels: ['MACOS_NOTIFICATION', 'SOUND'] }]])
  await t.watcher.poll('901', '미적분 풀기', { ...OPTIONS, interventionLevel: 'ACTIVE' })
  assert.deepEqual(t.shown, [], 'MOLE_UI가 없으면 포키 창은 띄우지 않음')
  assert.deepEqual(t.notified, [['b-1', true]])
  const g = setup([[{ interventionId: 'g-1', type: 'OFF_TASK', status: 'PENDING', channels: [] }]])
  await g.watcher.poll('901', '목표', { ...OPTIONS, interventionLevel: 'GENTLE' })
  assert.equal(g.shown[0].say, '어, 잠깐 샜네.')
})

test('조금 더 보기·잠시 쉬기는 확인, 닫기는 해제로 기록하고 잠시 쉬기는 세션을 멈춘다. 확인했어는 기록하지 않는다', async () => {
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
  assert.equal(isActInput({ interventionId: 'i-1', action: 'RECENTER' }), true)
  assert.equal(isActInput({ interventionId: 'i-1', action: 'DELETE' }), false)
  assert.equal(isActInput({ interventionId: '../x', action: 'ACK' }), false)
  assert.equal(isActInput({ interventionId: 'i-1', action: 'ACK', extra: 1 }), false)
})

test('소리만 골라도 소리 있는 알림을 띄우고, 이미 끝난 세션의 조회 결과는 보이지 않는다', async () => {
  const sound = setup([[{ interventionId: 's-1', type: 'OFF_TASK', status: 'PENDING', channels: ['SOUND'] }]])
  await sound.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(sound.notified, [['s-1', true]])
  const stale = setup([[{ interventionId: 'o-1', type: 'OFF_TASK', status: 'PENDING', channels: [] }]], { current: false })
  await stale.watcher.poll('901', '목표', OPTIONS)
  assert.deepEqual(stale.shown, [])
})

test('잠시 쉬기에서 일시정지에 실패하면 실패로 알린다', async () => {
  const t = setup([], { pauseOk: false })
  assert.equal(await t.watcher.act('i-1', 'REST'), false)
  assert.equal(await t.watcher.act('i-1', 'CLOSE'), true)
})
