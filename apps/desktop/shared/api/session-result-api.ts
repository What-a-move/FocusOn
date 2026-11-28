import type { LearningNote, LearningNoteList, LearningSummary, OffTaskReport, OffTaskShare, ReminderEstimate, RetrospectiveInput, SessionPause, SessionSummary } from '../../electron/shared/report-contract.cjs'

export { firstOffTaskAt } from '../../electron/shared/first-off-task'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { LearningNote, LearningSummary, OffTaskReport, OffTaskShare, ReminderEstimate, SessionPause, SessionSummary }

const UNAVAILABLE = { success: false, error: { code: 'LOCAL_APP_ERROR', retryable: true } } as const

// [유지보수] 리포트는 Main IPC(window.focusOn.reports)로만 조회한다. 브라우저에서 화면만 열면 연결이 없어 실패로 보인다.
async function viaReports<T>(call: (api: NonNullable<Window['focusOn']>['reports']) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.reports
  if (!api) return UNAVAILABLE
  try {
    return await call(api)
  } catch {
    return UNAVAILABLE
  }
}

export const sessionResultApi = {
  getReminderEstimate: () => viaReports<ReminderEstimate>((api) => api.getReminderEstimate()),
  saveRetrospective: (input: RetrospectiveInput) => viaReports<{ saved: true }>((api) => api.saveRetrospective(input)),
  getSummary: (sessionId: string) => viaReports<SessionSummary>((api) => api.getSessionSummary({ sessionId })),
  getPauses: (sessionId: string) => viaReports<SessionPause[]>((api) => api.getSessionPauses({ sessionId })),
  getOffTaskBreakdown: (sessionId: string) => viaReports<OffTaskReport>((api) => api.getOffTaskBreakdown({ sessionId })),
  getLearningSummary: (sessionId: string) => viaReports<LearningSummary>((api) => api.getLearningSummary({ sessionId })),
  listLearningNotes: (cursor: string | null = null) => viaReports<LearningNoteList>((api) => api.listLearningNotes({ cursor })),
}

/** 흐름 막대·범례에 쓸 분. 분석 안 됨은 실제 시간에서 분석된 구간을 뺀 나머지다(리포트 설계서 3.1·3.2). */
export function flowOf(summary: SessionSummary): { related: number; unrelated: number; uncertain: number; unavailable: number; rate: number | null } | null {
  const { relatedDurationMs: related, unrelatedDurationMs: unrelated, uncertainDurationMs: uncertain } = summary
  if (related === null || unrelated === null || uncertain === null) return null
  const unavailable = Math.max(0, summary.accumulatedActiveMs - related - unrelated - uncertain)
  // 관련·무관 합이 1분 미만이면 비율을 보여 주지 않는다.
  const rate = related + unrelated >= 60_000 ? Math.round((related / (related + unrelated)) * 100) : null
  return { related, unrelated, uncertain, unavailable, rate }
}

/** 일시정지를 뺀 가로축에서 각 멈춤 지점의 위치를 계산한다. */
export function pauseTimelinePositions(pauses: SessionPause[], { startedAt, accumulatedActiveMs }: Pick<SessionSummary, 'startedAt' | 'accumulatedActiveMs'>): number[] {
  if (!startedAt || accumulatedActiveMs === 0) return []
  const start = Date.parse(startedAt)
  let previousPausedMs = 0
  return pauses.map(({ pausedAt, resumedAt }) => {
    const paused = Date.parse(pausedAt)
    const activeMs = Math.max(0, paused - start - previousPausedMs)
    if (resumedAt) previousPausedMs += Math.max(0, Date.parse(resumedAt) - Math.max(start, paused))
    return Math.min(100, (activeMs / accumulatedActiveMs) * 100)
  })
}
