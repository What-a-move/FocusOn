import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 리포트 Channel. 홈 대시보드(집중 달력·지난 목표)가 쓴다.
export const REPORT_CHANNELS = {
  calendar: 'report:calendar:get',
  progress: 'report:progress:get',
  daily: 'report:daily:get',
  sessionSummary: 'report:session-summary:get',
  retrospective: 'report:retrospective:save',
  sessionPauses: 'report:session-pauses:get',
  offTaskBreakdown: 'report:off-task-breakdown:get',
  learningSummary: 'report:learning-summary:get',
  learningNotes: 'report:learning-notes:list',
} as const

/** GET /api/v1/reports/calendar 의 날짜 하나. hasStudyActivity는 서버가 totalActiveMs >= 60000으로 계산한다. */
export type CalendarDay = { date: string; totalActiveMs: number; sessionCount: number; hasStudyActivity: boolean; dailyTargetAchieved?: boolean }
export type CalendarMonth = { month: string; studyDayCount: number; days: CalendarDay[] }

/** GET /api/v1/reports/daily 의 세션 카드. 항목 형식은 2026-10-24 확정(결정 020). */
export type DailySession = { sessionId: string; goal: string; startedAt: string; endedAt: string | null; activeMs: number }
export type DailyReport = { date: string; totalActiveMs: number; sessions: DailySession[] }

/**
 * GET /api/v1/sessions/{id}/summary. 시간 필드 이름은 리포트 설계서 3.1을 따른다.
 * 나머지(status·startedAt·endedAt·연장·멈춤·자동 멈춤·알림 횟수)는 Notion에 이름이 없어 제안 형식이며, 없으면 null(화면에 `기록 없음`)이다.
 */
export type SessionSummary = {
  sessionId: string
  goalText: string
  status: 'ENDED' | 'ABANDONED' | null
  startedAt: string | null
  endedAt: string | null
  targetDurationMs: number
  accumulatedActiveMs: number
  totalPausedMs: number | null
  relatedDurationMs: number | null
  unrelatedDurationMs: number | null
  uncertainDurationMs: number | null
  extensionCount: number | null
  extendedDurationMs: number | null
  pauseCount: number | null
  autoPauseCount: number | null
  autoPauseReasons: string[]
  interventionCount: number | null
}
/** GET /api/v1/sessions/{id}/learning-summary. 생성 중·근거 부족·실패도 200으로 오고 상태로 구분한다. */
export type LearningSummaryStatus = 'QUEUED' | 'GENERATING' | 'READY' | 'INSUFFICIENT_DATA' | 'FAILED'
/** 핵심 개념 하나(Notion 리포트 설계서 5.3). sessionEvidence는 세션에서 확인한 내용이며 원문·URL을 담지 않는다. */
export type KeyConcept = { name: string; briefExplanation: string; sessionEvidence: string | null }
export type LearningSummary = { status: LearningSummaryStatus; overview: string | null; keyConcepts: KeyConcept[]; nextStartPrompt: string | null }
/** GET /api/v1/learning-summaries 의 노트 한 장. 서버는 정리가 끝난(READY) 회차만 endedAt 최신순으로 준다. */
export type LearningNote = { sessionId: string; goalText: string; studyDate: string; accumulatedActiveMs: number; keyConcepts: KeyConcept[] }
export type LearningNoteList = { items: LearningNote[]; nextCursor: string | null }

export type SessionPause = { pausedAt: string; resumedAt: string | null; pauseReason: string }

export type FocusProgress = {
  date: string
  today: { totalActiveMs: number; dailyTargetMs: number }
  week: { totalActiveMs: number; weeklyTargetMs: number | null }
  streak?: { currentDays: number; longestDays: number }
}

export const RETROSPECTIVE_RATINGS = ['GOOD', 'OKAY', 'BAD'] as const
export const RETROSPECTIVE_REASONS = ['SELF_TIRED', 'SELF_NOISE', 'SELF_TOO_MANY_GOALS', 'APP_FALSE_ALERT', 'APP_TOO_FREQUENT', 'APP_LATE_ALERT', 'APP_BLUR_ANNOYING'] as const
export type RetrospectiveInput = {
  sessionId: string
  rating: typeof RETROSPECTIVE_RATINGS[number]
  reasonCodes?: Array<typeof RETROSPECTIVE_REASONS[number]>
  note?: string
}

const OFF_TASK_CATEGORIES = ['VIDEO', 'SNS', 'COMMUNITY', 'SHOPPING', 'GAME', 'MESSENGER', 'NEWS', 'OTHER'] as const
type OffTaskCategory = typeof OFF_TASK_CATEGORIES[number]
export type OffTaskShare = { category: OffTaskCategory; share: number }
export type OffTaskReport = {
  categories: OffTaskShare[]
  consecutive: Array<{ firstAt: string; secondAt: string }>
}

export type ReportsApi = {
  saveRetrospective(input: RetrospectiveInput): Promise<IpcResult<{ saved: true }>>
  getProgress(input: { date: string }): Promise<IpcResult<FocusProgress>>
  getCalendar(input: { month: string }): Promise<IpcResult<CalendarMonth>>
  getDaily(input: { date: string }): Promise<IpcResult<DailyReport>>
  getSessionSummary(input: { sessionId: string }): Promise<IpcResult<SessionSummary>>
  getSessionPauses(input: { sessionId: string }): Promise<IpcResult<SessionPause[]>>
  getOffTaskBreakdown(input: { sessionId: string }): Promise<IpcResult<OffTaskReport>>
  getLearningSummary(input: { sessionId: string }): Promise<IpcResult<LearningSummary>>
  listLearningNotes(input: { cursor: string | null }): Promise<IpcResult<LearningNoteList>>
}

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const isCount = (value: unknown): value is number => typeof value === 'number' && Number.isInteger(value) && value >= 0
const isTime = (value: unknown): value is string => typeof value === 'string' && Number.isFinite(Date.parse(value))

export function isValidMonth(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/u.test(value)
}

export function isValidDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/u.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  return date.getUTCMonth() === month - 1 && date.getUTCDate() === day
}

export function parseFocusProgress(value: unknown): FocusProgress | null {
  if (!isRecord(value) || !isValidDate(value.date) || !isRecord(value.today) || !isRecord(value.week)) return null
  const { today, week, streak } = value
  if (!isCount(today.totalActiveMs) || !isCount(today.dailyTargetMs) || today.dailyTargetMs === 0
    || !isCount(week.totalActiveMs) || (week.weeklyTargetMs !== null && (!isCount(week.weeklyTargetMs) || week.weeklyTargetMs === 0))) return null
  return { date: value.date, today: { totalActiveMs: today.totalActiveMs, dailyTargetMs: today.dailyTargetMs },
    week: { totalActiveMs: week.totalActiveMs, weeklyTargetMs: week.weeklyTargetMs },
    ...(isRecord(streak) && isCount(streak.currentDays) && isCount(streak.longestDays) && streak.longestDays >= streak.currentDays
      ? { streak: { currentDays: streak.currentDays, longestDays: streak.longestDays } } : {}),
  }
}

export function parseCalendar(value: unknown): CalendarMonth | null {
  if (!isRecord(value) || !isValidMonth(value.month) || !isCount(value.studyDayCount) || !Array.isArray(value.days)) return null
  const days: CalendarDay[] = []
  for (const item of value.days) {
    if (!isRecord(item) || !isValidDate(item.date) || !item.date.startsWith(value.month)) return null
    if (!isCount(item.totalActiveMs) || !isCount(item.sessionCount) || typeof item.hasStudyActivity !== 'boolean') return null
    days.push({ date: item.date, totalActiveMs: item.totalActiveMs, sessionCount: item.sessionCount, hasStudyActivity: item.hasStudyActivity, ...(typeof item.dailyTargetAchieved === 'boolean' ? { dailyTargetAchieved: item.dailyTargetAchieved } : {}) })
  }
  return { month: value.month, studyDayCount: value.studyDayCount, days }
}

// 세션 항목 제안 형식: { sessionId, goalTextSnapshot, startedAt, endedAt, accumulatedActiveMs }
export function parseDaily(value: unknown): DailyReport | null {
  if (!isRecord(value) || !isValidDate(value.date) || !isCount(value.totalActiveMs) || !Array.isArray(value.sessions)) return null
  const sessions: DailySession[] = []
  for (const item of value.sessions) {
    if (!isRecord(item) || typeof item.sessionId !== 'string' || typeof item.goalTextSnapshot !== 'string' || !isTime(item.startedAt)) return null
    if (!(item.endedAt === null || isTime(item.endedAt)) || !isCount(item.accumulatedActiveMs)) return null
    sessions.push({ sessionId: item.sessionId, goal: item.goalTextSnapshot, startedAt: item.startedAt, endedAt: item.endedAt, activeMs: item.accumulatedActiveMs })
  }
  return { date: value.date, totalActiveMs: value.totalActiveMs, sessions }
}

// 세션 ID는 문자열이다(API에서 BIGINT를 string으로 보냄). 경로에 넣으므로 영문·숫자·하이픈만 받는다.
export function isValidSessionId(value: unknown): value is string {
  return typeof value === 'string' && /^[\w-]{1,64}$/u.test(value)
}

const optionalCount = (value: unknown): number | null => isCount(value) ? value : null
const LEARNING_STATUSES: readonly LearningSummaryStatus[] = ['QUEUED', 'GENERATING', 'READY', 'INSUFFICIENT_DATA', 'FAILED']

export function isRetrospectiveInput(value: unknown): value is RetrospectiveInput {
  if (!isRecord(value) || !isValidSessionId(value.sessionId)
    || !RETROSPECTIVE_RATINGS.includes(value.rating as RetrospectiveInput['rating'])
    || !Object.keys(value).every((key) => ['sessionId', 'rating', 'reasonCodes', 'note'].includes(key))) return false
  const { rating, reasonCodes, note } = value
  if (rating !== 'BAD') return reasonCodes === undefined && note === undefined
  return (reasonCodes === undefined || (Array.isArray(reasonCodes) && reasonCodes.length <= RETROSPECTIVE_REASONS.length
    && new Set(reasonCodes).size === reasonCodes.length && reasonCodes.every((reason) => RETROSPECTIVE_REASONS.includes(reason))))
    && (note === undefined || (typeof note === 'string' && note.trim().length <= 100))
}

export function parseSessionSummary(value: unknown): SessionSummary | null {
  if (!isRecord(value) || typeof value.sessionId !== 'string' || typeof value.goalSnapshot !== 'string') return null
  if (!isCount(value.targetDurationMs) || !isCount(value.accumulatedActiveMs)) return null
  return {
    sessionId: value.sessionId,
    goalText: value.goalSnapshot,
    status: value.status === 'ENDED' || value.status === 'ABANDONED' ? value.status : null,
    startedAt: isTime(value.startedAt) ? value.startedAt : null,
    endedAt: isTime(value.endedAt) ? value.endedAt : null,
    targetDurationMs: value.targetDurationMs,
    accumulatedActiveMs: value.accumulatedActiveMs,
    totalPausedMs: optionalCount(value.totalPausedMs),
    relatedDurationMs: optionalCount(value.relatedDurationMs),
    unrelatedDurationMs: optionalCount(value.unrelatedDurationMs),
    uncertainDurationMs: optionalCount(value.uncertainDurationMs),
    extensionCount: optionalCount(value.extensionCount),
    extendedDurationMs: optionalCount(value.extendedDurationMs),
    pauseCount: optionalCount(value.pauseCount),
    autoPauseCount: optionalCount(value.autoPauseCount),
    autoPauseReasons: Array.isArray(value.autoPauseReasons) ? value.autoPauseReasons.filter((reason): reason is string => typeof reason === 'string') : [],
    interventionCount: optionalCount(value.interventionCount),
  }
}

export function parseSessionPauses(value: unknown): SessionPause[] | null {
  if (!Array.isArray(value)) return null
  const pauses: SessionPause[] = []
  let previousEnd = -Infinity
  for (const item of value) {
    if (!isRecord(item) || !isTime(item.pausedAt) || typeof item.pauseReason !== 'string' || !item.pauseReason.trim()) return null
    const { pausedAt, resumedAt, pauseReason } = item
    if (resumedAt !== null && !isTime(resumedAt)) return null
    const start = Date.parse(pausedAt)
    const end = resumedAt === null ? Infinity : Date.parse(resumedAt)
    if (start < previousEnd || end < start) return null
    pauses.push({ pausedAt, resumedAt, pauseReason })
    previousEnd = end
  }
  return pauses
}

/** 분석 기록 중 저장된 딴짓 분류만 집계한다. 이전 회차의 누락 값은 분류된 기록 수에서 뺀다. */
export function parseOffTaskBreakdown(value: unknown): OffTaskReport | null {
  if (!Array.isArray(value)) return null
  const counts = new Map<OffTaskCategory, number>()
  let total = 0
  let previous: string | null = null
  const consecutive: OffTaskReport['consecutive'] = []
  for (const item of value) {
    if (!isRecord(item) || item.relation !== 'UNRELATED') { previous = null; continue }
    const category = OFF_TASK_CATEGORIES.find((entry) => entry === item.offTaskCategory)
    if (category) {
      counts.set(category, (counts.get(category) ?? 0) + 1)
      total += 1
    }
    if (item.analysisStatus !== 'COMPLETED' || !isTime(item.observedAt)) { previous = null; continue }
    if (previous && Date.parse(previous) < Date.parse(item.observedAt)) {
      consecutive.push({ firstAt: previous, secondAt: item.observedAt })
    }
    previous = item.observedAt
  }
  const categories = Array.from(counts, ([category, count]) => ({ category, share: count / total }))
    .sort((left, right) => right.share - left.share)
  return { categories, consecutive }
}

const text = (field: unknown) => (typeof field === 'string' && field.trim() ? field.trim() : null)
// 핵심 개념은 최대 5개. 형식이 맞지 않는 항목은 버린다(이름·설명이 없는 개념은 노트에 쓸 수 없다).
const parseConcepts = (value: unknown): KeyConcept[] => (Array.isArray(value) ? value : []).flatMap((item): KeyConcept[] => isRecord(item) && text(item.name) && text(item.briefExplanation)
  ? [{ name: text(item.name) as string, briefExplanation: text(item.briefExplanation) as string, sessionEvidence: text(item.sessionEvidence) }] : []).slice(0, 5)

export const isNoteCursor = (value: unknown): value is string | null => value === null || (typeof value === 'string' && value.length >= 1 && value.length <= 512)

/** 노트 목록. 항목 하나라도 형식이 다르면 그 항목만 버린다(목록 전체를 잃지 않게). */
export function parseLearningNoteList(value: unknown): LearningNoteList | null {
  if (!isRecord(value) || !Array.isArray(value.items) || !isNoteCursor(value.nextCursor ?? null)) return null
  const items = value.items.flatMap((item): LearningNote[] => isRecord(item) && isValidSessionId(item.sessionId) && typeof item.goalSnapshot === 'string' && isValidDate(item.studyDate) && isCount(item.accumulatedActiveMs)
    ? [{ sessionId: item.sessionId, goalText: item.goalSnapshot, studyDate: item.studyDate, accumulatedActiveMs: item.accumulatedActiveMs, keyConcepts: parseConcepts(item.keyConcepts) }] : [])
  return { items, nextCursor: (value.nextCursor as string | null | undefined) ?? null }
}

export function parseLearningSummary(value: unknown): LearningSummary | null {
  if (!isRecord(value) || !LEARNING_STATUSES.includes(value.learningSummaryStatus as LearningSummaryStatus)) return null
  return { status: value.learningSummaryStatus as LearningSummaryStatus, overview: text(value.overview), keyConcepts: parseConcepts(value.keyConcepts), nextStartPrompt: text(value.nextStartPrompt) }
}
