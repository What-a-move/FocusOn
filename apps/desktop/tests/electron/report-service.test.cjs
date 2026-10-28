const assert = require('node:assert/strict')
const test = require('node:test')
const { ReportService } = require('../../dist-electron/main/report-ipc.cjs')
const { parseCalendar, parseDaily, isValidDate, isValidMonth, isValidSessionId, parseSessionSummary, parseLearningSummary } = require('../../dist-electron/shared/report-contract.cjs')

const calendar = { month: '2026-10', studyDayCount: 1, days: [{ date: '2026-10-24', totalActiveMs: 1500000, sessionCount: 1, hasStudyActivity: true }] }
const daily = { date: '2026-10-24', totalActiveMs: 1500000, sessions: [{ sessionId: '7', goalTextSnapshot: '미적분 예제 풀기', startedAt: '2026-10-24T01:00:00Z', endedAt: '2026-10-24T01:25:00Z', accumulatedActiveMs: 1500000 }], interruptions: [], summary: {} }

test('달력 응답은 계약 형식만 통과시키고 다른 달 날짜나 음수 시간은 거부한다', () => {
  assert.deepEqual(parseCalendar(calendar), calendar)
  assert.equal(parseCalendar({ ...calendar, days: [{ ...calendar.days[0], date: '2026-11-01' }] }), null)
  assert.equal(parseCalendar({ ...calendar, days: [{ ...calendar.days[0], totalActiveMs: -1 }] }), null)
  assert.equal(parseCalendar({ ...calendar, studyDayCount: '1' }), null)
})

test('일간 리포트 세션은 목표 snapshot과 누적 시간만 화면 형식으로 바꾼다', () => {
  assert.deepEqual(parseDaily(daily), {
    date: '2026-10-24', totalActiveMs: 1500000,
    sessions: [{ sessionId: '7', goal: '미적분 예제 풀기', startedAt: '2026-10-24T01:00:00Z', endedAt: '2026-10-24T01:25:00Z', activeMs: 1500000 }],
  })
  assert.equal(parseDaily({ ...daily, sessions: [{ ...daily.sessions[0], goalTextSnapshot: undefined }] }), null)
})

test('월·날짜 입력은 실제로 있는 값만 받는다', () => {
  assert.equal(isValidMonth('2026-10'), true)
  assert.equal(isValidMonth('2026-13'), false)
  assert.equal(isValidDate('2026-02-29'), false)
  assert.equal(isValidDate('2028-02-29'), true)
  assert.equal(isValidDate('2026-10-24&x=1'), false)
})

test('리포트 조회는 X-Device-Id를 붙인 GET으로 보낸다', async () => {
  const requests = []
  const service = new ReportService({ authorizedRequest: async (request) => { requests.push(request); return { success: true, data: request.parse(request.path.includes('calendar') ? calendar : daily) } } })
  assert.equal((await service.getCalendar('2026-10')).success, true)
  assert.equal((await service.getDaily('2026-10-24')).success, true)
  assert.deepEqual(requests.map(({ path, method, withDeviceId }) => ({ path, method, withDeviceId })), [
    { path: '/api/v1/reports/calendar?month=2026-10', method: 'GET', withDeviceId: true },
    { path: '/api/v1/reports/daily?date=2026-10-24', method: 'GET', withDeviceId: true },
  ])
})

test('세션 요약은 필수 값만 요구하고 나머지는 없으면 null로 둔다', () => {
  const base = { sessionId: '7', goalSnapshot: '미적분 예제 풀기', targetDurationMs: 1500000, accumulatedActiveMs: 1200000 }
  const parsed = parseSessionSummary({ ...base, status: 'ABANDONED', relatedDurationMs: 600000, unrelatedDurationMs: -1, autoPauseReasons: ['SYSTEM', 3] })
  assert.equal(parsed.goalText, '미적분 예제 풀기')
  assert.equal(parsed.status, 'ABANDONED')
  assert.equal(parsed.relatedDurationMs, 600000)
  assert.equal(parsed.unrelatedDurationMs, null)
  assert.equal(parsed.totalPausedMs, null)
  assert.deepEqual(parsed.autoPauseReasons, ['SYSTEM'])
  assert.equal(parseSessionSummary({ ...base, goalSnapshot: undefined }), null)
  assert.equal(parseSessionSummary({ ...base, accumulatedActiveMs: '1' }), null)
})

test('학습 정리는 정해진 상태만 받고 빈 요약은 null로 둔다', () => {
  assert.deepEqual(parseLearningSummary({ learningSummaryStatus: 'READY', overview: '극한 정의를 정리함' }), { status: 'READY', overview: '극한 정의를 정리함' })
  assert.deepEqual(parseLearningSummary({ learningSummaryStatus: 'GENERATING', overview: '' }), { status: 'GENERATING', overview: null })
  assert.equal(parseLearningSummary({ learningSummaryStatus: 'DONE' }), null)
})

test('세션 ID는 경로에 넣어도 안전한 값만 받는다', () => {
  assert.equal(isValidSessionId('7'), true)
  assert.equal(isValidSessionId('a1-b2_c3'), true)
  assert.equal(isValidSessionId('7/../x'), false)
  assert.equal(isValidSessionId(''), false)
  assert.equal(isValidSessionId(7), false)
})

test('세션 요약·학습 정리는 세션 경로로 GET을 보낸다', async () => {
  const requests = []
  const service = new ReportService({ authorizedRequest: async (request) => { requests.push(request); return { success: true, data: null } } })
  await service.getSessionSummary('7')
  await service.getLearningSummary('7')
  assert.deepEqual(requests.map(({ path, method }) => ({ path, method })), [
    { path: '/api/v1/sessions/7/summary', method: 'GET' },
    { path: '/api/v1/sessions/7/learning-summary', method: 'GET' },
  ])
})
