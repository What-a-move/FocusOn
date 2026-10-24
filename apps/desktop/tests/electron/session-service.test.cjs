const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { SessionService } = require('../../dist-electron/main/session-ipc.cjs')
const { NotificationReadiness } = require('../../dist-electron/main/notification-readiness.cjs')
const { isValidStartInput, parseFocusSettings, parseStartedSession } = require('../../dist-electron/shared/session-contract.cjs')

const options = { interventionLevel: 'NORMAL', notificationChannels: ['MOLE_UI', 'MACOS_NOTIFICATION'], cameraAnalysisEnabled: false }
const input = { goalId: '12', targetDurationMs: 30 * 60000, timeZone: 'Asia/Seoul', sessionOptions: options, idempotencyKey: '3f2b8a52-7c1e-4d6a-9b0f-1e2d3c4b5a69' }

test('시작 입력은 5~300분·분 단위·IANA 시간대·알림 1개 이상·UUID 키만 받는다', () => {
  assert.equal(isValidStartInput(input), true)
  assert.equal(isValidStartInput({ ...input, targetDurationMs: 4 * 60000 }), false)
  assert.equal(isValidStartInput({ ...input, targetDurationMs: 301 * 60000 }), false)
  assert.equal(isValidStartInput({ ...input, targetDurationMs: 30 * 60000 + 1 }), false)
  assert.equal(isValidStartInput({ ...input, targetDurationMs: null }), false, 'MVP에는 종료 없는 세션이 없다')
  assert.equal(isValidStartInput({ ...input, timeZone: 'Mars/Base' }), false)
  assert.equal(isValidStartInput({ ...input, idempotencyKey: 'same' }), false)
  assert.equal(isValidStartInput({ ...input, sessionOptions: { ...options, notificationChannels: [] } }), false)
  assert.equal(isValidStartInput({ ...input, sessionOptions: { ...options, notificationChannels: ['SOUND', 'SOUND'] } }), false)
  assert.equal(isValidStartInput({ ...input, startReadiness: { screenAnalysisReady: true } }), false, '준비 상태는 Renderer가 보내지 않는다')
})

test('집중 설정·세션 응답은 계약 형식만 통과시킨다', () => {
  assert.deepEqual(parseFocusSettings({ ...options, breakSuggestionEnabled: true }), options)
  assert.equal(parseFocusSettings({ ...options, interventionLevel: 'LOUD' }), null)
  assert.equal(parseStartedSession({ sessionId: '901', status: 'RUNNING', version: 1, targetDurationMs: 1800000, accumulatedActiveMs: 0, startedAt: '2026-10-24T01:00:00Z', interventionPolicyVersion: 'v1' }).sessionId, '901')
  assert.equal(parseStartedSession({ sessionId: '901', status: 'RUNNING', targetDurationMs: 1800000, startedAt: 'bad', interventionPolicyVersion: 'v1' }), null)
})

test('세션 시작은 Idempotency-Key·X-Device-Id와 Main이 확인한 준비 상태를 붙여 POST한다', async () => {
  const requests = []
  const service = new SessionService({ authorizedRequest: async (request) => { requests.push(request); return { success: false, error: { code: 'SESSION_ACTIVE_EXISTS', retryable: false } } } }, () => true, () => true)
  await service.start(input)
  const [request] = requests
  assert.equal(request.path, '/api/v1/sessions')
  assert.equal(request.method, 'POST')
  assert.equal(request.expectedStatus, 201)
  assert.equal(request.withDeviceId, true)
  assert.deepEqual(request.headers, { 'Idempotency-Key': input.idempotencyKey })
  assert.deepEqual(request.body, { goalId: '12', targetDurationMs: 1800000, timeZone: 'Asia/Seoul', sessionOptions: options, startReadiness: { screenAnalysisReady: true } })
})

test('알림 확인 기록이 없으면 서버에 요청하지 않고 Desktop에서 시작을 막는다', async () => {
  let called = false
  const service = new SessionService({ authorizedRequest: async () => { called = true; return { success: true, data: {} } } }, () => true, () => false)
  const result = await service.start(input)
  assert.deepEqual(result, { success: false, error: { code: 'LOCAL_NOTIFICATION_NOT_CONFIRMED', retryable: false } })
  assert.equal(called, false)
})

test('알림 확인 기록은 파일이 없거나 깨지면 확인 안 됨으로 본다', () => {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'focuson-')), 'notification-readiness.json')
  const readiness = new NotificationReadiness(file)
  assert.equal(readiness.isConfirmed(), false)
  readiness.set(true)
  assert.equal(readiness.isConfirmed(), true)
  readiness.set(false)
  assert.equal(readiness.isConfirmed(), false)
  fs.writeFileSync(file, '{broken')
  assert.equal(readiness.isConfirmed(), false)
})
