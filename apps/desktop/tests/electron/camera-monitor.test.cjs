const assert = require('node:assert/strict')
const test = require('node:test')
const { CameraMonitor, parseCameraReply, CAMERA_COOLDOWN_MS } = require('../../dist-electron/main/camera-monitor.cjs')

const sample = (gaze, quality = 'GOOD', faceCount = gaze === 'AWAY' ? 0 : 1) => ({ kind: 'SAMPLE', sample: { faceCount, eyesVisible: gaze === 'SCREEN', gaze, quality } })
const OPEN = { screenRelated: false, canShow: true }

// 2초마다 같은 읽기를 넣고, 알림이 뜬 시각(초)을 모은다.
function run(monitor, clock, reading, seconds, gate = OPEN) {
  const alerts = []
  for (let s = 0; s < seconds; s += 2) {
    const { alert } = monitor.update(reading, gate)
    if (alert) alerts.push(clock.t / 1000)
    clock.t += 2000
  }
  return alerts
}

test('유효한 AWAY가 끊기지 않으면 60초에 후보, 90초에 확정되고 알림은 한 번만 뜬다', () => {
  const clock = { t: 0 }
  const m = new CameraMonitor(() => clock.t)
  assert.equal(m.update(sample('AWAY'), OPEN).state, 'CAMERA_AWAY_CANDIDATE')
  clock.t = 60_000
  assert.equal(m.update(sample('AWAY'), OPEN).state, 'CAMERA_ALERT_CANDIDATE')
  clock.t = 89_000
  assert.equal(m.update(sample('AWAY'), OPEN).alert, false)
  clock.t = 90_000
  assert.deepEqual(m.update(sample('AWAY'), OPEN), { state: 'CAMERA_AWAY_CONFIRMED', alert: true })
  clock.t = 120_000
  assert.equal(m.update(sample('AWAY'), OPEN).alert, false, '같은 이탈 동안 다시 띄우지 않음')
})

test('SCREEN 복귀·신호 불량·켜는 중·카메라 실패는 누적을 지운다 (불확실한 신호를 AWAY로 보지 않음)', () => {
  for (const [breaker, state] of [[sample('SCREEN'), 'CAMERA_ATTENTION'], [sample('UNKNOWN', 'LOW'), 'CAMERA_UNKNOWN'], [sample('AWAY', 'UNUSABLE'), 'CAMERA_UNKNOWN'], [{ kind: 'STARTING' }, 'CAMERA_STARTING'], [{ kind: 'UNAVAILABLE' }, 'LOCAL_CAMERA_UNAVAILABLE']]) {
    const clock = { t: 0 }
    const m = new CameraMonitor(() => clock.t)
    run(m, clock, sample('AWAY'), 80)
    assert.equal(m.update(breaker, OPEN).state, state)
    assert.deepEqual(run(m, clock, sample('AWAY'), 88), [], `${state} 뒤에는 다시 90초를 센다`)
  }
})

test('최신 화면 판정이 RELATED이면 누적을 지우고 알리지 않는다. 포키가 떠 있으면 미뤘다가 비면 띄운다', () => {
  const clock = { t: 0 }
  const m = new CameraMonitor(() => clock.t)
  assert.deepEqual(run(m, clock, sample('AWAY'), 120, { screenRelated: true, canShow: true }), [])
  assert.equal(m.current(), 'CAMERA_ATTENTION')
  assert.deepEqual(run(m, clock, sample('AWAY'), 96, { screenRelated: false, canShow: false }), [])
  assert.equal(m.update(sample('AWAY'), OPEN).alert, true)
})

test('카메라 알림 사이는 5분을 둔다. 세션을 멈췄다 다시 해도 cooldown은 유지한다', () => {
  const clock = { t: 0 }
  const m = new CameraMonitor(() => clock.t)
  assert.equal(run(m, clock, sample('AWAY'), 92).length, 1)
  m.reset()
  const first = clock.t
  assert.deepEqual(run(m, clock, sample('AWAY'), 200).length, 0, '5분 안 두 번째 이탈은 알리지 않음')
  clock.t = first + CAMERA_COOLDOWN_MS
  m.reset()
  assert.equal(run(m, clock, sample('AWAY'), 92).length, 1)
})

test('도우미 응답은 정한 값만 받는다', () => {
  assert.deepEqual(parseCameraReply(null), { kind: 'UNAVAILABLE' })
  assert.deepEqual(parseCameraReply({ ok: true, state: 'STARTING' }), { kind: 'STARTING' })
  assert.deepEqual(parseCameraReply({ ok: true, state: 'SAMPLE', faceCount: 1, eyesVisible: true, gaze: 'SCREEN', quality: 'GOOD', landmarks: [1, 2] }),
    { kind: 'SAMPLE', sample: { faceCount: 1, eyesVisible: true, gaze: 'SCREEN', quality: 'GOOD' } }, '정하지 않은 값은 버림')
  assert.deepEqual(parseCameraReply({ ok: true, state: 'SAMPLE', faceCount: 1, eyesVisible: true, gaze: 'LEFT', quality: 'GOOD' }), { kind: 'UNAVAILABLE' })
})
