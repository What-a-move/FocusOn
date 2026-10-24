import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 리포트 Channel. 홈 대시보드(집중 달력·지난 목표)가 쓴다.
export const REPORT_CHANNELS = {
  calendar: 'report:calendar:get',
  daily: 'report:daily:get',
} as const

/** GET /api/v1/reports/calendar 의 날짜 하나. hasStudyActivity는 서버가 totalActiveMs >= 60000으로 계산한다. */
export type CalendarDay = { date: string; totalActiveMs: number; sessionCount: number; hasStudyActivity: boolean }
export type CalendarMonth = { month: string; studyDayCount: number; days: CalendarDay[] }

/** GET /api/v1/reports/daily 의 세션 카드. 항목 형식은 2026-10-24 확정(결정 020). */
export type DailySession = { sessionId: string; goal: string; startedAt: string; endedAt: string | null; activeMs: number }
export type DailyReport = { date: string; totalActiveMs: number; sessions: DailySession[] }

export type ReportsApi = {
  getCalendar(input: { month: string }): Promise<IpcResult<CalendarMonth>>
  getDaily(input: { date: string }): Promise<IpcResult<DailyReport>>
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
