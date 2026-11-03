import type { LearningSummary, SessionSummary } from '../../electron/shared/report-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { LearningSummary, SessionSummary }

const UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

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
  getSummary: (sessionId: string) => viaReports<SessionSummary>((api) => api.getSessionSummary({ sessionId })),
  getLearningSummary: (sessionId: string) => viaReports<LearningSummary>((api) => api.getLearningSummary({ sessionId })),
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
