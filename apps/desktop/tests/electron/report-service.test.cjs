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

test('딴짓 비율은 유효하게 분류된 UNRELATED 기록만 쓰고 기타와 예전 누락 값을 구분한다', async () => {
  const record = (offTaskCategory) => ({ relation: 'UNRELATED', offTaskCategory })
  const data = [
    record('SNS'),
    record('VIDEO'),
    record('VIDEO'),
    record('OTHER'),
    record(null),
    record(undefined),
    record('UNKNOWN_CATEGORY'),
    { relation: 'RELATED', offTaskCategory: 'GAME' },
    { relation: 'UNCERTAIN', offTaskCategory: 'NEWS' },
    null,
  ]
  const service = new ReportService({ authorizedRequest: async ({ path, method, parse }) => {
    assert.equal(path, '/api/v1/sessions/7/analysis-runs')
    assert.equal(method, 'GET')
    assert.deepEqual(parse([record(null), record(undefined)]), { categories: [], consecutive: [] })
    assert.deepEqual(parse([]), { categories: [], consecutive: [] })
    assert.equal(parse({ items: data }), null)
    return { success: true, data: parse(data) }
  } })
  assert.deepEqual(await service.getOffTaskBreakdown('7'), {
    success: true,
    data: {
      categories: [
        { category: 'VIDEO', share: 0.5 },
        { category: 'SNS', share: 0.25 },
        { category: 'OTHER', share: 0.25 },
      ],
      consecutive: [],
    },
  })
})

test('첫 이탈은 서로 다른 시각에 연속해 확정된 기록만 후보에 넣는다', () => {
  const { parseOffTaskBreakdown } = require('../../dist-electron/shared/report-contract.cjs')
  const run = (minute, relation = 'UNRELATED', analysisStatus = 'COMPLETED') => ({ relation, analysisStatus, observedAt: `2026-11-29T01:${minute}:00Z` })
  const report = parseOffTaskBreakdown([
    run('01'), run('01'), run('02', 'UNCERTAIN', 'NEEDS_CONFIRMATION'),
    run('03'), run('04', 'UNRELATED', 'ERROR'),
    run('05'), run('06'), run('07', 'PRIVACY_BLOCKED', 'SKIPPED'), run('08'),
  ])
  assert.deepEqual(report.consecutive, [{ firstAt: run('05').observedAt, secondAt: run('06').observedAt }])
})

test('할 일별 시간은 회차의 사본·확정 활동 시간을 검증하고 기존 요약을 계속 읽는다', () => {
  const { parseSessionSummary } = require('../../dist-electron/shared/report-contract.cjs')
  const summary = { sessionId: '7', goalSnapshot: '기존 목표', targetDurationMs: 600_000, accumulatedActiveMs: 540_000, relatedDurationMs: 360_000 }
  const goalFocusTimes = [
    { goalId: '13', goalTextSnapshot: 'API 응답 조건 확인', matchedActiveMs: 180_000 },
    { goalId: '14', goalTextSnapshot: '교체 후 응답 테스트', matchedActiveMs: 120_000 },
  ]
  assert.deepEqual(parseSessionSummary({ ...summary, goalFocusTimes }).goalFocusTimes, goalFocusTimes)
  assert.equal(parseSessionSummary(summary).goalFocusTimes, undefined)
  assert.equal(parseSessionSummary({ ...summary, relatedDurationMs: null, goalFocusTimes }).goalFocusTimes, undefined)
  assert.equal(parseSessionSummary({ ...summary, relatedDurationMs: 600_000, goalFocusTimes }).goalFocusTimes, undefined)
  for (const invalid of [[goalFocusTimes[0], goalFocusTimes[0]], [{ ...goalFocusTimes[0], matchedActiveMs: -1 }],
    [...goalFocusTimes, { goalId: '15', goalTextSnapshot: '추가 할 일', matchedActiveMs: 61_000 }],
    [{ ...goalFocusTimes[0], goalTextSnapshot: ' ' }]]) {
    const result = parseSessionSummary({ ...summary, goalFocusTimes: invalid })
    assert.equal(result.goalFocusTimes, undefined)
    assert.equal(result.goalText, '기존 목표')
  }
  const zero = [{ ...goalFocusTimes[0], matchedActiveMs: 0 }]
  assert.deepEqual(parseSessionSummary({ ...summary, relatedDurationMs: 0, goalFocusTimes: zero }).goalFocusTimes, zero)
})

test('이탈 경로는 출처가 확인된 모든 확정 딴짓 기록으로만 계산한다', () => {
  const { parseOffTaskBreakdown } = require('../../dist-electron/shared/report-contract.cjs')
  const run = (contentSource, relation = 'UNRELATED', analysisStatus = 'COMPLETED') => ({ contentSource, relation, analysisStatus })
  const records = [
    run('TAB_META'), run('CHROME_DOM'), run('APP_META'),
    run('DESKTOP_APP_OCR', 'RELATED'), run('APP_META', 'UNRELATED', 'ERROR'),
  ]
  assert.deepEqual(parseOffTaskBreakdown(records).routes, { browser: 2 / 3, otherApp: 1 / 3 })
  assert.deepEqual(parseOffTaskBreakdown([run('DESKTOP_APP_OCR')]).routes, { browser: 0, otherApp: 1 })
  assert.equal(parseOffTaskBreakdown([...records, run(undefined)]).routes, undefined)
  assert.equal(parseOffTaskBreakdown([...records, run('UNKNOWN_SOURCE')]).routes, undefined)
  assert.equal(parseOffTaskBreakdown([run('APP_META', 'UNCERTAIN')]).routes, undefined)
})
