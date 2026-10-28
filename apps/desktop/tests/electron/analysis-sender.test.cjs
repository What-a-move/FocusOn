const assert = require('node:assert/strict')
const test = require('node:test')
const os = require('node:os')
const path = require('node:path')
const { mkdtempSync } = require('node:fs')
const { AnalysisSender } = require('../../dist-electron/main/analysis-sender.cjs')

const run = { analysisRunId: '7001', relation: 'UNRELATED', confidence: 0.91, reasonCode: 'SEMANTIC_GOAL_MISMATCH', observedAt: '2026-10-25T10:00:15Z', policyVersion: 'v1' }
const meta = { observedAt: '2026-10-25T10:00:15Z', guardStatus: null, contentSource: 'APP_META', metadata: { appName: 'Xcode', windowTitle: 'main.swift' } }

function setup(replies, file = path.join(mkdtempSync(path.join(os.tmpdir(), 'focuson-')), 'analysis-sequence.json')) {
  const calls = []
  let clock = 0
  const auth = {
    authorizedRequest: async (request) => {
      calls.push(request)
      const reply = replies.shift() ?? { success: true, data: run }
      return reply.success ? { success: true, data: request.parse(reply.data) } : reply
    },
  }
  return { sender: new AnalysisSender(auth, file, () => clock), calls, file, advance: (ms) => { clock += ms } }
}

test('일반 관측은 앱 정보와 함께, 고유 clientRunId·1부터 늘어나는 sequence로 보낸다', async () => {
  const { sender, calls } = setup([])
  assert.equal((await sender.send('901', meta)).relation, 'UNRELATED')
  await sender.send('901', meta)
  const [first, second] = calls
  assert.equal(first.path, '/api/v1/sessions/901/analysis-runs')
  assert.equal(first.method, 'POST')
  assert.equal(first.withDeviceId, true)
  assert.match(first.body.clientRunId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u)
  assert.notEqual(first.body.clientRunId, second.body.clientRunId)
  assert.deepEqual({ ...first.body, clientRunId: 'x' }, { clientRunId: 'x', contentSource: 'APP_META', observedAt: meta.observedAt, sequence: 1, guardStatus: null, metadata: meta.metadata })
  assert.equal(second.body.sequence, 2)
})

test('제외·민감 화면은 상태만 보내고 앱 정보를 넣지 않는다', async () => {
  const { sender, calls } = setup([])
  await sender.send('901', { observedAt: meta.observedAt, guardStatus: 'PRIVACY_BLOCKED' })
  assert.equal(calls[0].body.guardStatus, 'PRIVACY_BLOCKED')
  assert.equal('metadata' in calls[0].body, false)
  assert.equal('contentPayload' in calls[0].body, false)
})

test('세션이 바뀌면 sequence를 1부터 다시 센다', async () => {
  const { sender, calls } = setup([])
  await sender.send('901', meta)
  await sender.send('901', meta)
  await sender.send('902', meta)
  assert.deepEqual(calls.map((call) => [call.path.split('/')[4], call.body.sequence]), [['901', 1], ['901', 2], ['902', 1]])
})

test('요청 제한(429)이면 1분 동안 보내지 않고, 그 뒤 다시 보낸다', async () => {
  const { sender, calls, advance } = setup([{ success: false, error: { code: 'TOO_MANY_REQUESTS', retryable: true } }])
  assert.equal(await sender.send('901', meta), null)
  advance(30_000)
  assert.equal(await sender.send('901', meta), null)
  assert.equal(calls.length, 1, '쉬는 동안은 요청하지 않음')
  advance(30_000)
  await sender.send('901', meta)
  assert.equal(calls.length, 2)
})

test('AI 장애·네트워크 오류·형식 오류는 다시 보내거나 쌓지 않는다', async () => {
  const { sender, calls } = setup([
    { success: false, error: { code: 'ANALYSIS_SERVICE_UNAVAILABLE', retryable: true } },
    { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } },
    { success: true, data: { relation: 'MAYBE' } },
  ])
  assert.equal(await sender.send('901', meta), null)
  assert.equal(await sender.send('901', meta), null)
  assert.equal(await sender.send('901', meta), null, '허용 밖 relation은 받지 않음')
  assert.equal(calls.length, 3, '실패마다 한 번씩만 보냄')
  assert.deepEqual(calls.map((call) => call.body.sequence), [1, 2, 3])
})

test('앱을 다시 켜도 같은 세션이면 sequence를 이어서 센다', async () => {
  const first = setup([])
  await first.sender.send('901', meta)
  await first.sender.send('901', meta)
  const restarted = setup([], first.file)
  await restarted.sender.send('901', meta)
  await restarted.sender.send('902', meta)
  assert.deepEqual(restarted.calls.map((call) => call.body.sequence), [3, 1], '같은 세션은 3부터, 새 세션은 1부터')
})

test('응답은 필드가 빠지거나 형식이 틀리면 받지 않는다', () => {
  const { parseAnalysisRun } = require('../../dist-electron/main/analysis-sender.cjs')
  assert.deepEqual(parseAnalysisRun(run), { analysisRunId: '7001', relation: 'UNRELATED', confidence: 0.91, reasonCode: 'SEMANTIC_GOAL_MISMATCH', observedAt: run.observedAt, policyVersion: 'v1' })
  assert.notEqual(parseAnalysisRun({ ...run, confidence: null, reasonCode: null, relation: 'EXCLUDED' }), null, '판정이 없으면 null 허용')
  assert.equal(parseAnalysisRun({ ...run, policyVersion: undefined }), null, 'policyVersion 없음')
  assert.equal(parseAnalysisRun({ ...run, confidence: '0.9' }), null, 'confidence 형식')
  assert.equal(parseAnalysisRun({ ...run, confidence: 1.5 }), null, 'confidence 범위')
  assert.equal(parseAnalysisRun({ ...run, reasonCode: 3 }), null, 'reasonCode 형식')
  assert.equal(parseAnalysisRun({ ...run, confidence: undefined }), null, 'confidence 빠짐')
})
