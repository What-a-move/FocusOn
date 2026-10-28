import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 리포트 Channel. 홈 대시보드(집중 달력·지난 목표)가 쓴다.
export const REPORT_CHANNELS = {
  calendar: 'report:calendar:get',
  daily: 'report:daily:get',
  sessionSummary: 'report:session-summary:get',
  learningSummary: 'report:learning-summary:get',
} as const

/** GET /api/v1/reports/calendar 의 날짜 하나. hasStudyActivity는 서버가 totalActiveMs >= 60000으로 계산한다. */
export type CalendarDay = { date: string; totalActiveMs: number; sessionCount: number; hasStudyActivity: boolean }
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
export type LearningSummary = { status: LearningSummaryStatus; overview: string | null }

export type ReportsApi = {
  getCalendar(input: { month: string }): Promise<IpcResult<CalendarMonth>>
  getDaily(input: { date: string }): Promise<IpcResult<DailyReport>>
  getSessionSummary(input: { sessionId: string }): Promise<IpcResult<SessionSummary>>
  getLearningSummary(input: { sessionId: string }): Promise<IpcResult<LearningSummary>>
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

export function parseCalendar(value: unknown): CalendarMonth | null {
  if (!isRecord(value) || !isValidMonth(value.month) || !isCount(value.studyDayCount) || !Array.isArray(value.days)) return null
  const days: CalendarDay[] = []
  for (const item of value.days) {
    if (!isRecord(item) || !isValidDate(item.date) || !item.date.startsWith(value.month)) return null
    if (!isCount(item.totalActiveMs) || !isCount(item.sessionCount) || typeof item.hasStudyActivity !== 'boolean') return null
    days.push({ date: item.date, totalActiveMs: item.totalActiveMs, sessionCount: item.sessionCount, hasStudyActivity: item.hasStudyActivity })
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

export function parseLearningSummary(value: unknown): LearningSummary | null {
  if (!isRecord(value) || !LEARNING_STATUSES.includes(value.learningSummaryStatus as LearningSummaryStatus)) return null
  return { status: value.learningSummaryStatus as LearningSummaryStatus, overview: typeof value.overview === 'string' && value.overview ? value.overview : null }
}
