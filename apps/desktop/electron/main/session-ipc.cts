import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { SESSION_CHANNELS, isValidStartInput, parseFocusSettings, parseStartedSession } from '../shared/session-contract.cjs'
import type { FocusSettings, StartSessionInput, StartedSession } from '../shared/session-contract.cjs'
import type { ApiResult } from './auth-api.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { AuthService } from './auth-service.cjs'
import type { SessionRuntime } from './session-runtime.cjs'
import { isValidExtendMinutes } from '../shared/session-contract.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

/** 세션 Backend 계약 (Notion FocusOn API 명세서 POST /sessions). 시작은 자동 재전송하지 않고 같은 Idempotency-Key로만 다시 보낸다. */
export class SessionService {
  constructor(
    private readonly auth: Pick<AuthService, 'authorizedRequest'>,
    private readonly screenAnalysisReady: () => boolean,
    private readonly notificationReady: () => boolean,
    private readonly runtime: Pick<SessionRuntime, 'begin'>,
  ) {}

  getFocusSettings(): Promise<ApiResult<FocusSettings>> {
    return this.auth.authorizedRequest({ path: '/api/v1/users/me/focus-settings', method: 'GET', expectedStatus: 200, parse: parseFocusSettings })
  }

  async start(input: StartSessionInput): Promise<ApiResult<StartedSession>> {
    // [주의] 서버는 알림 권한을 검증하지 않는다. 알림 준비(사용자 확인)는 Desktop이 여기서 막는다(결정 022).
    if (!this.notificationReady()) return { success: false, error: { code: 'LOCAL_NOTIFICATION_NOT_CONFIRMED', retryable: false } }
    const result = await this.auth.authorizedRequest({
      path: '/api/v1/sessions', method: 'POST', expectedStatus: 201, parse: parseStartedSession, withDeviceId: true,
      headers: { 'Idempotency-Key': input.idempotencyKey },
      body: {
        goalId: input.goalId,
        targetDurationMs: input.targetDurationMs,
        timeZone: input.timeZone,
        sessionOptions: input.sessionOptions,
        // [주의] 준비 상태는 Renderer 입력이 아니라 Main이 macOS 권한을 직접 확인한 값만 보낸다. 서버가 다시 검증한다.
        startReadiness: { screenAnalysisReady: this.screenAnalysisReady() },
      },
    })
    // 시작이 확정되면 Main 타이머를 바로 센다.
    if (result.success) this.runtime.begin(result.data, input.goalText.trim(), input.sessionOptions)
    return result
  }
}

export function registerSessionIpc(getMainWindow: () => BrowserWindow | null, rendererUrl: string, service: SessionService | null, runtime: SessionRuntime | null): void {
  function handle<T>(channel: string, accept: (args: unknown[]) => boolean, run: (service: SessionService, args: unknown[]) => Promise<ApiResult<T>>): void {
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

  handle(SESSION_CHANNELS.focusSettings, (args) => args.length === 0, (s) => s.getFocusSettings())
  handle(SESSION_CHANNELS.start, (args) => args.length === 1 && isValidStartInput(args[0]), (s, args) => s.start(args[0] as StartSessionInput))

  function handleRuntime<T>(channel: string, accept: (args: unknown[]) => boolean, run: (runtime: SessionRuntime, args: unknown[]) => Promise<ApiResult<T>>): void {
    ipcMain.handle(channel, async (event: IpcMainInvokeEvent, ...args: unknown[]): Promise<IpcResult<T>> => {
      if (!isTrustedAuthSender(event, getMainWindow(), rendererUrl) || !accept(args)) return INVALID_REQUEST
      if (!runtime) return SERVICE_UNAVAILABLE
      try {
        return await run(runtime, args)
      } catch {
        return SERVICE_UNAVAILABLE
      }
    })
  }
  const none = (args: unknown[]) => args.length === 0
  handleRuntime(SESSION_CHANNELS.current, none, (r) => r.restore())
  handleRuntime(SESSION_CHANNELS.pause, none, (r) => r.pause('USER'))
  handleRuntime(SESSION_CHANNELS.resume, none, (r) => r.resume())
  handleRuntime(SESSION_CHANNELS.end, none, (r) => r.end())
  handleRuntime(SESSION_CHANNELS.extend, (args) => {
    const input = args[0] as { minutes?: unknown } | undefined
    return args.length === 1 && typeof input === 'object' && input !== null && Object.keys(input).length === 1 && isValidExtendMinutes(input.minutes)
  }, (r, args) => r.extend((args[0] as { minutes: number }).minutes))
}
