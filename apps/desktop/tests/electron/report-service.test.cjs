const assert = require('node:assert/strict')
const test = require('node:test')
const { ReportService } = require('../../dist-electron/main/report-ipc.cjs')
const { parseCalendar, parseDaily, isValidDate, isValidMonth, isValidSessionId, parseSessionSummary, parseSessionPauses, parseLearningSummary } = require('../../dist-electron/shared/report-contract.cjs')

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
  assert.deepEqual(parseLearningSummary({ learningSummaryStatus: 'READY', overview: '극한 정의를 정리함' }), { status: 'READY', overview: '극한 정의를 정리함', keyConcepts: [], nextStartPrompt: null })
  assert.deepEqual(parseLearningSummary({ learningSummaryStatus: 'GENERATING', overview: '' }), { status: 'GENERATING', overview: null, keyConcepts: [], nextStartPrompt: null })
  assert.equal(parseLearningSummary({ learningSummaryStatus: 'DONE' }), null)
})

test('핵심 개념은 이름·설명이 있는 것만 최대 5개 받고 다음 시작 지점을 읽는다', () => {
  const concept = (name) => ({ name, briefExplanation: `${name} 설명`, sessionEvidence: '예제로 확인함' })
  const parsed = parseLearningSummary({
    learningSummaryStatus: 'READY', overview: '요약', nextStartPrompt: '예제 5번 풀기',
    keyConcepts: [concept('극한'), { name: '설명 없음' }, { name: '  ', briefExplanation: '  ' }, concept('좌극한'), concept('우극한'), concept('약분'), concept('연속'), concept('여섯째')],
  })
  assert.deepEqual(parsed.keyConcepts.map((item) => item.name), ['극한', '좌극한', '우극한', '약분', '연속'])
  assert.equal(parsed.nextStartPrompt, '예제 5번 풀기')
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

test('노트 목록은 형식이 맞는 노트만 남기고, 다음 쪽은 cursor로 GET /learning-summaries를 부른다', async () => {
  const { parseLearningNoteList, isNoteCursor } = require('../../dist-electron/shared/report-contract.cjs')
  const note = { sessionId: '7', goalSnapshot: '미적분 예제 풀기', studyDate: '2026-10-24', startedAt: '2026-10-24T01:00:00Z', endedAt: '2026-10-24T01:25:00Z', accumulatedActiveMs: 1500000, overview: '요약', keyConcepts: [{ name: '극한', briefExplanation: '다가가는 값' }, { name: '' }] }
  assert.deepEqual(parseLearningNoteList({ items: [note, { ...note, sessionId: '../x' }], nextCursor: 'c1' }), {
    items: [{ sessionId: '7', goalText: '미적분 예제 풀기', studyDate: '2026-10-24', accumulatedActiveMs: 1500000, keyConcepts: [{ name: '극한', briefExplanation: '다가가는 값', sessionEvidence: null }] }], nextCursor: 'c1',
  })
  assert.equal(parseLearningNoteList({ items: 'x', nextCursor: null }), null)
  assert.equal(isNoteCursor(''), false)
  const paths = []
  const service = new ReportService({ authorizedRequest: async (request) => { paths.push(request.path); return { success: true, data: { items: [], nextCursor: null } } } })
  await service.listLearningNotes(null)
  await service.listLearningNotes('a b')
  assert.deepEqual(paths, ['/api/v1/learning-summaries', '/api/v1/learning-summaries?cursor=a%20b'])
})

test('일시정지는 순서와 열린 마지막 구간을 검증하고 본인 세션 경로로 조회한다', async () => {
  const pause = { pausedAt: '2026-10-24T01:05:00Z', resumedAt: '2026-10-24T01:07:00Z', pauseReason: 'USER' }
  const last = { pausedAt: '2026-10-24T01:12:00Z', resumedAt: null, pauseReason: 'REQUIRED_PERMISSION' }
  assert.deepEqual(parseSessionPauses([pause, last]), [pause, last])
  assert.deepEqual(parseSessionPauses([]), [])
  assert.equal(parseSessionPauses([last, pause]), null)
  assert.equal(parseSessionPauses([pause, { ...last, pausedAt: '2026-10-24T01:06:00Z' }]), null)
  assert.equal(parseSessionPauses([{ ...pause, resumedAt: '2026-10-24T01:01:00Z' }]), null)
  assert.equal(parseSessionPauses([{ ...pause, resumedAt: 'invalid' }]), null)
  const service = new ReportService({ authorizedRequest: async ({ path, method, parse }) => {
    assert.equal(path, '/api/v1/sessions/7/pauses')
    assert.equal(method, 'GET')
    return { success: true, data: parse([pause, last]) }
  } })
  assert.deepEqual(await service.getSessionPauses('7'), { success: true, data: [pause, last] })
})

test('목표 진행과 회고는 Desktop 기기 인증을 사용하며 원문 메모를 렌더러에 돌려주지 않는다', async () => {
  const { isRetrospectiveInput, parseFocusProgress } = require('../../dist-electron/shared/report-contract.cjs')
  const date = '2026-11-29'
  const progress = { date, today: { totalActiveMs: 600000, dailyTargetMs: 3600000 }, week: { totalActiveMs: 1200000, weeklyTargetMs: null } }
  assert.deepEqual(parseFocusProgress(progress), progress)
  const streak = { currentDays: 0, longestDays: 12 }
  assert.deepEqual(parseFocusProgress({ ...progress, streak }), { ...progress, streak })
  assert.deepEqual(parseFocusProgress({ ...progress, streak: { currentDays: 4, longestDays: 3 } }), progress)
  assert.deepEqual(parseFocusProgress({ ...progress, streak: { currentDays: -1, longestDays: 12 } }), progress)
  assert.equal(parseFocusProgress({ ...progress, week: { totalActiveMs: 0, weeklyTargetMs: 0 } }), null)
  assert.equal(parseCalendar({ ...calendar, days: [{ ...calendar.days[0], dailyTargetAchieved: true }] }).days[0].dailyTargetAchieved, true)

  const input = { sessionId: '7', rating: 'BAD', reasonCodes: ['SELF_TIRED'], note: '  피곤했어요  ' }
  assert.equal(isRetrospectiveInput(input), true)
  assert.equal(isRetrospectiveInput({ ...input, rating: 'GOOD' }), false)
  assert.equal(isRetrospectiveInput({ ...input, note: '가'.repeat(101) }), false)
  assert.equal(isRetrospectiveInput({ ...input, reasonCodes: ['SELF_TIRED', 'SELF_TIRED'] }), false)

  const requests = []
  const service = new ReportService({ authorizedRequest: async (request) => {
    requests.push(request)
    return { success: true, data: request.parse(request.method === 'GET' ? progress : { sessionId: '7', note: input.note }) }
  } })
  assert.deepEqual(await service.getProgress(date), { success: true, data: progress })
  assert.deepEqual(await service.saveRetrospective(input), { success: true, data: { saved: true } })
  assert.deepEqual(requests.map(({ path, method, withDeviceId, body }) => ({ path, method, withDeviceId, body })), [
    { path: '/api/v1/reports/progress?date=2026-11-29', method: 'GET', withDeviceId: true, body: undefined },
    { path: '/api/v1/sessions/7/retrospective', method: 'PUT', withDeviceId: true, body: { rating: 'BAD', reasonCodes: ['SELF_TIRED'], note: '피곤했어요' } },
  ])
})
