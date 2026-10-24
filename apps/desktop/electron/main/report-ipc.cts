import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { REPORT_CHANNELS, isValidDate, isValidMonth, parseCalendar, parseDaily } from '../shared/report-contract.cjs'
import type { CalendarMonth, DailyReport } from '../shared/report-contract.cjs'
import type { ApiResult } from './auth-api.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { AuthService } from './auth-service.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

/** 리포트 조회 Backend 계약 (Notion FocusOn API 명세서). GET이라 AuthService가 일시 오류를 1초 → 3초로 재시도한다. */
export class ReportService {
  constructor(private readonly auth: Pick<AuthService, 'authorizedRequest'>) {}

  getCalendar(month: string): Promise<ApiResult<CalendarMonth>> {
    return this.auth.authorizedRequest({ path: `/api/v1/reports/calendar?month=${month}`, method: 'GET', expectedStatus: 200, parse: parseCalendar, withDeviceId: true })
  }

  getDaily(date: string): Promise<ApiResult<DailyReport>> {
    return this.auth.authorizedRequest({ path: `/api/v1/reports/daily?date=${date}`, method: 'GET', expectedStatus: 200, parse: parseDaily, withDeviceId: true })
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
      if (!service) return SERVICE_UNAVAILABLE
      try {
        return await run(service, args)
      } catch {
        return SERVICE_UNAVAILABLE
      }
    })
  }

  handle(REPORT_CHANNELS.calendar, (args) => onlyKey(args, 'month', isValidMonth), (s, args) => s.getCalendar((args[0] as { month: string }).month))
  handle(REPORT_CHANNELS.daily, (args) => onlyKey(args, 'date', isValidDate), (s, args) => s.getDaily((args[0] as { date: string }).date))
}
