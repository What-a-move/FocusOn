const { test } = require('node:test')
const assert = require('node:assert/strict')

const { PreDistractionReminder } = require('../../dist-electron/main/pre-distraction-reminder.cjs')
const { ReportService } = require('../../dist-electron/main/report-ipc.cjs')
const { parseFocusSettings, isFocusSettingsInput } = require('../../dist-electron/shared/session-contract.cjs')
const ok = (data) => ({ success: true, data })
const failure = { success: false, error: { code: 'INVALID_RESPONSE', retryable: false } }

test('최근 회차의 첫 이탈까지 멈춘 시간을 빼고 중간값으로 알림 시각을 계산한다', async () => {
  const time = (minute) => new Date(Date.UTC(2026, 10, 28, 10, minute)).toISOString()
  const samples = { '9': 12, '8': 5, '7': 8, '6': 6, '5': null }
  const makeReport = (items, failAt) => new ReportService({
    authorizedRequest: async ({ path, parse, withDeviceId }) => {
      if (path.startsWith('/api/v1/sessions?')) {
        assert.equal(withDeviceId, true)
        return ok(parse({ items }))
      }
      const [, id, resource] = path.match(/sessions\/(\d+)\/(.+)$/)
      const minute = samples[id]
      if (resource === 'analysis-runs') return ok(parse(minute === null ? [] : [minute, minute + 1].map((value) => ({
        observedAt: time(value), relation: 'UNRELATED', analysisStatus: 'COMPLETED',
      }))))
      if (resource === 'pauses') {
        if (id === failAt) return failure
        return ok(parse(id === '9' ? [{ pausedAt: time(2), resumedAt: time(8), pauseReason: 'USER' }] : []))
      }
      return ok(parse({
        sessionId: id, goalSnapshot: 'Test', status: 'ENDED', startedAt: time(0), endedAt: time(20),
        accumulatedActiveMs: 13 * 60_000, targetDurationMs: 15 * 60_000,
      }))
    },
  })
  const items = [
    { sessionId: '10', status: 'RUNNING' },
    ...Object.keys(samples).reverse().map((sessionId) => ({ sessionId, status: 'ENDED' })),
  ]
  const now = Date.parse(time(0))
  assert.deepEqual(await makeReport(items).getReminderEstimate(now), ok({ status: 'ready', activeMs: 3 * 60_000 }))
  assert.deepEqual(await makeReport(items.slice(0, 3)).getReminderEstimate(now), ok({ status: 'insufficient' }))
  assert.deepEqual(await makeReport(items, '9').getReminderEstimate(now), failure)
  samples['9'] = 3
  samples['7'] = 2
  samples['8'] = 3
  samples['6'] = 3
  assert.deepEqual(await makeReport(items).getReminderEstimate(now), ok({ status: 'tooEarly' }))
})

test('목표 시각에 확인하는 동안 멈추면 재개 뒤에 같은 회차에서 한 번만 알린다', async () => {
  let session = { sessionId: '42', status: 'RUNNING', accumulatedActiveMs: 0 }
  let resolveEnabled
  let enabled = () => new Promise((resolve) => { resolveEnabled = resolve })
  let settingsReads = 0
  const sent = []
  const reminder = new PreDistractionReminder({
    session: () => session,
    account: () => '20',
    estimate: async () => ok({ status: 'ready', activeMs: 60_000 }),
    enabled: () => { settingsReads++; return enabled() },
    deliver: (...args) => sent.push(args),
    onError: () => assert.fail('unexpected error'),
  })
  await reminder.tick()
  session = { ...session, accumulatedActiveMs: 60_000 }
  const pending = reminder.tick()
  await reminder.tick()
  assert.equal(settingsReads, 1)
  session = { ...session, status: 'PAUSED' }
  resolveEnabled(true)
  await pending
  await reminder.tick()
  assert.deepEqual(sent, [])
  enabled = async () => true
  session = { ...session, status: 'RUNNING' }
  await reminder.tick()
  await reminder.tick()
  assert.deepEqual(sent, [['20', '42']])
  session = { ...session, sessionId: '43', accumulatedActiveMs: 0 }
  await reminder.tick()
  session = { ...session, accumulatedActiveMs: 60_000 }
  enabled = async () => false
  await reminder.tick()
  assert.equal(sent.length, 1)
  session = { ...session, sessionId: '44', accumulatedActiveMs: 70_000 }
  await reminder.tick()
  await reminder.tick()
  assert.equal(sent.length, 1)
})

test('설정의 미리 알림 값은 누락과 명시적인 끄기를 구분한다', () => {
  const settings = { interventionLevel: 'NORMAL', notificationChannels: ['MOLE_UI'], cameraAnalysisEnabled: false, breakSuggestionEnabled: true }
  assert.equal(parseFocusSettings(settings).preDistractionReminderEnabled, undefined)
  const disabled = { ...settings, preDistractionReminderEnabled: false }
  assert.deepEqual(parseFocusSettings(disabled), disabled)
  assert.equal(isFocusSettingsInput(disabled), true)
  assert.equal(isFocusSettingsInput({ ...settings, preDistractionReminderEnabled: 'false' }), false)
})
