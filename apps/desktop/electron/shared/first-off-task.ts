import type { OffTaskReport, SessionPause, SessionSummary } from './report-contract.cjs'

/** 두 판정 사이에 멈춤이 있으면 같은 집중 구간에 연속한 판정으로 보지 않는다. */
export function firstOffTaskAt(report: OffTaskReport, summary: SessionSummary, pauses: SessionPause[]): string | null {
  if (!summary.startedAt || !summary.endedAt) return null
  const sessionStart = Date.parse(summary.startedAt)
  const sessionEnd = Date.parse(summary.endedAt)
  const intervals = pauses.map(({ pausedAt, resumedAt }) => [Date.parse(pausedAt), resumedAt ? Date.parse(resumedAt) : sessionEnd])
  return report.consecutive.find(({ firstAt, secondAt }) => {
    const first = Date.parse(firstAt)
    const second = Date.parse(secondAt)
    return first >= sessionStart && second <= sessionEnd
      && !intervals.some(([pause, resume]) => first < resume && second >= pause)
  })?.firstAt ?? null
}
