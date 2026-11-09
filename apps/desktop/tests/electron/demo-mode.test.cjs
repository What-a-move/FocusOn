const assert = require('node:assert/strict')
const test = require('node:test')
const { installDemoMode } = require('../../dist-electron/main/demo-mode.cjs')
const { parsePending } = require('../../dist-electron/main/intervention-watcher.cjs')
const { parseCommandResult } = require('../../dist-electron/shared/session-contract.cjs')
const { parseSessionSummary, parseLearningSummary, parseDaily } = require('../../dist-electron/shared/report-contract.cjs')

const missing = { success: false, error: { code: 'NOT_FOUND', retryable: false } }

// 서버에 없는 API는 실패, 세션 시작·일시정지는 성공하는 가짜 서버.
function setup() {
  const calls = []
  const auth = {
    authorizedRequest: async (request) => {
      calls.push(`${request.method} ${request.path}`)
      if (request.path === '/api/v1/sessions') return { success: true, data: { sessionId: 's1', startedAt: '2026-11-09T01:00:00Z', targetDurationMs: 1_500_000 } }
      if (request.path.endsWith('/pause')) return { success: true, data: { version: 2 } }
      return missing
    },
  }
  let snap = null
  const demo = installDemoMode(auth, () => snap)
  return { auth, demo, calls, setSnap: (value) => { snap = value } }
}
const running = { sessionId: 's1', goalText: '선형대수 3장', status: 'RUNNING', targetDurationMs: 1_500_000, accumulatedActiveMs: 600_000, error: null, busy: false, pauseReason: null }

test('단축키 알림은 진행 중 세션에만 한 번 만들어지고 확인 기록은 서버로 보내지 않는다', async () => {
  const { auth, demo, calls, setSnap } = setup()
  assert.equal(demo.triggerAlert(), false)
  setSnap(running)
  assert.equal(demo.triggerAlert(), true)
  const list = await auth.authorizedRequest({ path: '/api/v1/sessions/s1/interventions', method: 'GET', parse: parsePending })
  assert.deepEqual(list, { success: true, data: [{ interventionId: 'demo-1', type: 'OFF_TASK', channels: [] }] })
  assert.deepEqual(await auth.authorizedRequest({ path: '/api/v1/sessions/s1/interventions', method: 'GET', parse: parsePending }), { success: true, data: [] })
  assert.equal((await auth.authorizedRequest({ path: '/api/v1/interventions/demo-1/acknowledgements', method: 'POST', parse: () => true })).success, true)
  assert.deepEqual(calls, [])
})

test('서버 종료 API가 없으면 앱 안에서 끝내고, 그 세션으로 리포트와 하루 기록을 만든다', async () => {
  const { auth, demo, setSnap } = setup()
  await auth.authorizedRequest({ path: '/api/v1/sessions', method: 'POST', parse: (v) => v })
  setSnap(running)
  await auth.authorizedRequest({ path: '/api/v1/sessions/s1/pause', method: 'POST', parse: (v) => v })
  demo.triggerAlert()
  const end = await auth.authorizedRequest({ path: '/api/v1/sessions/s1/end', method: 'POST', parse: parseCommandResult })
  assert.deepEqual(end, { success: true, data: { status: 'ENDED', version: 0, accumulatedActiveMs: 600_000, targetDurationMs: undefined } })

  const summary = await auth.authorizedRequest({ path: '/api/v1/sessions/s1/summary', method: 'GET', parse: parseSessionSummary })
  assert.equal(summary.success, true)
  assert.equal(summary.data.goalText, '선형대수 3장')
  assert.equal(summary.data.pauseCount, 1)
  assert.equal(summary.data.interventionCount, 1)
  assert.equal(summary.data.relatedDurationMs + summary.data.unrelatedDurationMs, 600_000)

  // 학습 정리는 서버 결과 그대로(없으면 실패).
  assert.deepEqual(await auth.authorizedRequest({ path: '/api/v1/sessions/s1/learning-summary', method: 'GET', parse: parseLearningSummary }), missing)

  const today = new Date().toLocaleDateString('sv-SE')
  const daily = await auth.authorizedRequest({ path: `/api/v1/reports/daily?date=${today}`, method: 'GET', parse: parseDaily })
  assert.equal(daily.data.sessions.length, 1)
  assert.equal(daily.data.totalActiveMs, 600_000)
})

test('끝나지 않은 세션의 리포트는 만들지 않고 서버 실패를 그대로 돌려준다', async () => {
  const { auth, setSnap } = setup()
  setSnap(running)
  assert.deepEqual(await auth.authorizedRequest({ path: '/api/v1/sessions/s1/summary', method: 'GET', parse: parseSessionSummary }), missing)
})

test('앱 안에서만 끝낸 세션은 서버가 진행 중이라고 해도 다시 불러오지 않는다', async () => {
  const remote = { sessionId: 's1', status: 'RUNNING', version: 3, goalTextSnapshot: '선형대수 3장', targetDurationMs: 1_500_000, accumulatedActiveMs: 600_000 }
  const NONE = Symbol('none')
  const parse = (value) => value === null ? NONE : value
  let snap = running
  const auth = { authorizedRequest: async (request) => request.path === '/api/v1/sessions/active' ? { success: true, data: parse(remote) } : missing }
  installDemoMode(auth, () => snap)
  assert.equal((await auth.authorizedRequest({ path: '/api/v1/sessions/active', method: 'GET', parse })).data, remote)
  await auth.authorizedRequest({ path: '/api/v1/sessions/s1/end', method: 'POST', parse: parseCommandResult })
  snap = null
  assert.equal((await auth.authorizedRequest({ path: '/api/v1/sessions/active', method: 'GET', parse })).data, NONE)
})
