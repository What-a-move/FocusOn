import type { CalendarMonth, DailyReport, DailySession } from '../../electron/shared/report-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { CalendarMonth, DailySession }

const UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

// [유지보수] 리포트도 Main IPC(window.focusOn.reports)로만 조회한다. 브라우저에서 화면만 열면 연결이 없어 실패로 보인다.
async function viaReports<T>(call: (api: NonNullable<Window['focusOn']>['reports']) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.reports
  if (!api) return UNAVAILABLE
  try {
    return await call(api)
  } catch {
    return UNAVAILABLE
  }
}

export const dashboardApi = {
  getCalendar: (month: string) => viaReports<CalendarMonth>((api) => api.getCalendar({ month })),
  getDaily: (date: string) => viaReports<DailyReport>((api) => api.getDaily({ date })),
}

/** 로컬 날짜 기준 YYYY-MM-DD / YYYY-MM. 서버 studyDate도 세션 시작 당시 기기 시간대의 날짜다. */
export const dayKey = (date: Date) => `${monthKey(date)}-${String(date.getDate()).padStart(2, '0')}`
export const monthKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`

/** 최근 기록이 있는 날짜부터 세션을 모아 최대 count개를 돌려준다. 오늘 이후 날짜는 보지 않는다. */
export async function loadRecentSessions(calendar: CalendarMonth, today: string, count = 3): Promise<ApiResult<DailySession[]>> {
  // ponytail: 이번 달 기록만 본다. 월초에 지난달 기록도 보여야 하면 이전 달 달력을 이어서 조회한다.
  const days = calendar.days.filter((day) => day.sessionCount > 0 && day.date <= today).map((day) => day.date).sort().reverse()
  const sessions: DailySession[] = []
  for (const date of days) {
    if (sessions.length >= count) break
    const daily = await dashboardApi.getDaily(date)
    if (!daily.success) return daily
    sessions.push(...[...daily.data.sessions].sort((a, b) => (b.endedAt ?? b.startedAt).localeCompare(a.endedAt ?? a.startedAt)))
  }
  return { success: true, data: sessions.slice(0, count) }
}
