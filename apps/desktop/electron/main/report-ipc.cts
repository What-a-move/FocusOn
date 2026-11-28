import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { REPORT_CHANNELS, isNoteCursor, isRetrospectiveInput, isValidDate, isValidMonth, isValidSessionId, parseCalendar, parseFocusProgress, parseDaily, parseLearningNoteList, parseLearningSummary, parseOffTaskBreakdown, parseSessionPauses, parseSessionSummary } from '../shared/report-contract.cjs'
import type { CalendarMonth, DailyReport, FocusProgress, LearningNoteList, LearningSummary, OffTaskReport, ReminderEstimate, RetrospectiveInput, SessionPause, SessionSummary } from '../shared/report-contract.cjs'
import { firstOffTaskAt } from '../shared/first-off-task.js'
import type { ApiResult } from './auth-api.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { AuthService } from './auth-service.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const LOCAL_APP_ERROR = { success: false, error: { code: 'LOCAL_APP_ERROR', retryable: true } } as const

/** 리포트 조회 Backend 계약 (Notion FocusOn API 명세서). GET이라 AuthService가 일시 오류를 1초 → 3초로 재시도한다. */
export class ReportService {
  constructor(private readonly auth: Pick<AuthService, 'authorizedRequest'>) {}

  getCalendar(month: string): Promise<ApiResult<CalendarMonth>> {
    return this.auth.authorizedRequest({ path: `/api/v1/reports/calendar?month=${month}`, method: 'GET', expectedStatus: 200, parse: parseCalendar, withDeviceId: true })
  }

  getProgress(date: string): Promise<ApiResult<FocusProgress>> {
    return this.auth.authorizedRequest({ path: `/api/v1/reports/progress?date=${date}`, method: 'GET', expectedStatus: 200, parse: parseFocusProgress, withDeviceId: true })
  }

  getDaily(date: string): Promise<ApiResult<DailyReport>> {
    return this.auth.authorizedRequest({ path: `/api/v1/reports/daily?date=${date}`, method: 'GET', expectedStatus: 200, parse: parseDaily, withDeviceId: true })
  }

  saveRetrospective({ sessionId, rating, reasonCodes, note }: RetrospectiveInput): Promise<ApiResult<{ saved: true }>> {
    const body = { rating, ...(reasonCodes === undefined ? {} : { reasonCodes }), ...(note === undefined ? {} : { note: note.trim() }) }
    return this.auth.authorizedRequest({
      path: `/api/v1/sessions/${encodeURIComponent(sessionId)}/retrospective`, method: 'PUT', expectedStatus: 200,
      withDeviceId: true, body, parse: (value) => value && typeof value === 'object' && 'sessionId' in value && value.sessionId === sessionId ? { saved: true } : null,
    })
  }

  getSessionSummary(sessionId: string): Promise<ApiResult<SessionSummary>> {
    return this.auth.authorizedRequest({ path: `/api/v1/sessions/${encodeURIComponent(sessionId)}/summary`, method: 'GET', expectedStatus: 200, parse: parseSessionSummary })
  }

  getSessionPauses(sessionId: string): Promise<ApiResult<SessionPause[]>> {
    return this.auth.authorizedRequest({ path: `/api/v1/sessions/${encodeURIComponent(sessionId)}/pauses`, method: 'GET', expectedStatus: 200, parse: parseSessionPauses })
  }

  getOffTaskBreakdown(sessionId: string): Promise<ApiResult<OffTaskReport>> {
    return this.auth.authorizedRequest({ path: `/api/v1/sessions/${encodeURIComponent(sessionId)}/analysis-runs`, method: 'GET', expectedStatus: 200, parse: parseOffTaskBreakdown })
  }

  async getReminderEstimate(now = Date.now()): Promise<ApiResult<ReminderEstimate>> {
    const date = (days: number) => new Date(now + days * 86_400_000).toISOString().slice(0, 10)
    // shortcut: 최신 10건만 한 번 읽는다. 종료되지 않은 회차가 많아 표본이 부족하면 페이지를 이어 읽는다.
    const recent = await this.auth.authorizedRequest({
      path: `/api/v1/sessions?from=${date(-366)}&to=${date(1)}&limit=10`, method: 'GET', expectedStatus: 200, withDeviceId: true,
      parse: (value) => {
        if (!value || typeof value !== 'object' || !('items' in value) || !Array.isArray(value.items)) return null
        const ids = new Set<string>()
        for (const item of value.items) {
          if (!item || !isValidSessionId(item.sessionId) || typeof item.status !== 'string') return null
          if (item.status === 'ENDED') ids.add(item.sessionId)
        }
        return [...ids].slice(0, 5)
      },
    })
    if (!recent.success) return recent
    if (recent.data.length < 3) return { success: true, data: { status: 'insufficient' } }

    const durations = await Promise.all(recent.data.map(async (id) => {
      const analysis = await this.getOffTaskBreakdown(id)
      if (!analysis.success) return analysis
      if (!analysis.data.consecutive.length) return { success: true, data: null } as const
      const [summary, pauses] = await Promise.all([this.getSessionSummary(id), this.getSessionPauses(id)])
      if (!summary.success) return summary
      if (!pauses.success) return pauses
      const { startedAt, status, accumulatedActiveMs } = summary.data
      const first = firstOffTaskAt(analysis.data, summary.data, pauses.data)
      if (!first || !startedAt || status !== 'ENDED') return { success: true, data: null } as const
      const start = Date.parse(startedAt)
      const end = Date.parse(first)
      const pausedMs = pauses.data.reduce((sum, pause) => sum + Math.max(0,
        Math.min(end, pause.resumedAt ? Date.parse(pause.resumedAt) : end) - Math.max(start, Date.parse(pause.pausedAt))), 0)
      const activeMs = end - start - pausedMs
      return { success: true, data: activeMs >= 0 && activeMs <= accumulatedActiveMs ? activeMs : null } as const
    }))
    const samples: number[] = []
    for (const duration of durations) {
      if (!duration.success) return duration
      if (duration.data !== null) samples.push(duration.data)
    }
    if (samples.length < 3) return { success: true, data: { status: 'insufficient' } }
    samples.sort((a, b) => a - b)
    const middle = Math.floor(samples.length / 2)
    const median = samples.length % 2 ? samples[middle] : (samples[middle - 1] + samples[middle]) / 2
    return { success: true, data: median > 180_000
      ? { status: 'ready', activeMs: median - 180_000 }
      : { status: 'tooEarly' } }
  }

  listLearningNotes(cursor: string | null): Promise<ApiResult<LearningNoteList>> {
    return this.auth.authorizedRequest({ path: `/api/v1/learning-summaries${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`, method: 'GET', expectedStatus: 200, parse: parseLearningNoteList })
  }

  getLearningSummary(sessionId: string): Promise<ApiResult<LearningSummary>> {
    return this.auth.authorizedRequest({ path: `/api/v1/sessions/${encodeURIComponent(sessionId)}/learning-summary`, method: 'GET', expectedStatus: 200, parse: parseLearningSummary })
  }
}

const onlyKey = (args: unknown[], key: string, valid: (value: unknown) => boolean): boolean => {
  const [input] = args
  return args.length === 1 && typeof input === 'object' && input !== null && !Array.isArray(input)
    && Object.keys(input).length === 1 && valid((input as Record<string, unknown>)[key])
}

export function registerReportIpc(getMainWindow: () => BrowserWindow | null, rendererUrl: string, service: ReportService | null): void {
  function handle<T>(channel: string, accept: (args: unknown[]) => boolean, run: (service: ReportService, args: unknown[]) => Promise<ApiResult<T>>): void {
    ipcMain.handle(channel, async (event: IpcMainInvokeEvent, ...args: unknown[]): Promise<IpcResult<T>> => {
      if (!isTrustedAuthSender(event, getMainWindow(), rendererUrl) || !accept(args)) return INVALID_REQUEST
      if (!service) return LOCAL_APP_ERROR
      try {
        return await run(service, args)
      } catch {
        return LOCAL_APP_ERROR
      }
    })
  }

  handle(REPORT_CHANNELS.reminderEstimate, (args) => args.length === 0, (s) => s.getReminderEstimate())
  handle(REPORT_CHANNELS.calendar, (args) => onlyKey(args, 'month', isValidMonth), (s, args) => s.getCalendar((args[0] as { month: string }).month))
  handle(REPORT_CHANNELS.progress, (args) => onlyKey(args, 'date', isValidDate), (s, args) => s.getProgress((args[0] as { date: string }).date))
  handle(REPORT_CHANNELS.daily, (args) => onlyKey(args, 'date', isValidDate), (s, args) => s.getDaily((args[0] as { date: string }).date))
  handle(REPORT_CHANNELS.retrospective, (args) => args.length === 1 && isRetrospectiveInput(args[0]), (s, args) => s.saveRetrospective(args[0] as RetrospectiveInput))
  handle(REPORT_CHANNELS.sessionSummary, (args) => onlyKey(args, 'sessionId', isValidSessionId), (s, args) => s.getSessionSummary((args[0] as { sessionId: string }).sessionId))
  handle(REPORT_CHANNELS.sessionPauses, (args) => onlyKey(args, 'sessionId', isValidSessionId), (s, args) => s.getSessionPauses((args[0] as { sessionId: string }).sessionId))
  handle(REPORT_CHANNELS.offTaskBreakdown, (args) => onlyKey(args, 'sessionId', isValidSessionId), (s, args) => s.getOffTaskBreakdown((args[0] as { sessionId: string }).sessionId))
  handle(REPORT_CHANNELS.learningSummary, (args) => onlyKey(args, 'sessionId', isValidSessionId), (s, args) => s.getLearningSummary((args[0] as { sessionId: string }).sessionId))
  handle(REPORT_CHANNELS.learningNotes, (args) => onlyKey(args, 'cursor', isNoteCursor), (s, args) => s.listLearningNotes((args[0] as { cursor: string | null }).cursor))
}
