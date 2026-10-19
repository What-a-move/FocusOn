import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import { AUTH_CHANNELS, parseAuthSessionSnapshot } from '../shared/auth-contract.cjs'
import type { AuthSessionSnapshot, IpcResult } from '../shared/auth-contract.cjs'
import { isAllowedNavigation } from './renderer-path.cjs'
import type { GoogleOAuthFlow } from './google-oauth.cjs'
import type { AuthService } from './auth-service.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: false } } as const

// [주의] Channel 이름을 아는 것만으로 호출을 허용하지 않는다. 현재 앱의 Main Frame에서 왔는지도 확인한다.
export function isTrustedAuthSender(
  event: Pick<IpcMainInvokeEvent, 'sender' | 'senderFrame'>,
  window: BrowserWindow | null,
  rendererUrl: string,
): boolean {
  if (!window || window.isDestroyed()) return false
  if (event.sender !== window.webContents) return false
  if (!event.senderFrame || event.senderFrame !== window.webContents.mainFrame) return false
  return isAllowedNavigation(event.senderFrame.url, rendererUrl)
}

export function isValidAuthRequest(
  event: Pick<IpcMainInvokeEvent, 'sender' | 'senderFrame'>,
  args: unknown[],
  window: BrowserWindow | null,
  rendererUrl: string,
): boolean {
  // [주의] 인증 Channel은 입력이 없다. 추가 인자가 들어오면 나중에 계약이 바뀌어도 묵인하지 않는다.
  return args.length === 0 && isTrustedAuthSender(event, window, rendererUrl)
}

export class AuthSessionStore {
  private snapshot: AuthSessionSnapshot = {
    revision: 0,
    status: 'SIGNED_OUT',
    user: null,
    onboardingStatus: null,
    error: null,
  }

  constructor(private readonly notify: (snapshot: AuthSessionSnapshot) => void) {}

  getSnapshot(): AuthSessionSnapshot {
    return {
      ...this.snapshot,
      user: this.snapshot.user ? { ...this.snapshot.user } : null,
      error: this.snapshot.error ? { ...this.snapshot.error } : null,
    }
  }

  // [유지보수] 인증 결과를 받으면 이 메서드로만 갱신해 revision과 Event 순서를 한곳에서 관리한다.
  publish(next: Omit<AuthSessionSnapshot, 'revision'>): void {
    // [주의] 미래의 서버 응답 객체에 Token이 섞여도 화면용 필드만 남긴다.
    const safeSnapshot = parseAuthSessionSnapshot({ ...next, revision: this.snapshot.revision + 1 })
    if (!safeSnapshot) throw new TypeError('Invalid authentication snapshot')
    this.snapshot = safeSnapshot
    this.notify(this.getSnapshot())
  }
}

export async function beginGoogleLogin(
  store: AuthSessionStore,
  oauthFlow: Pick<GoogleOAuthFlow, 'start'> | null,
): Promise<IpcResult<{ started: boolean }>> {
  if (!oauthFlow) return SERVICE_UNAVAILABLE
  if (store.getSnapshot().status === 'AUTHENTICATING') return { success: true, data: { started: false } }

  store.publish({ status: 'AUTHENTICATING', user: null, onboardingStatus: null, error: null })
  let started = false
  try {
    started = await oauthFlow.start()
  } catch {
    // [주의] 브라우저·포트의 내부 오류 메시지는 Renderer에 전달하지 않는다.
  }
  if (!started) {
    store.publish({ status: 'SIGNED_OUT', user: null, onboardingStatus: null, error: SERVICE_UNAVAILABLE.error })
    return SERVICE_UNAVAILABLE
  }
  return { success: true, data: { started: true } }
}

export function registerAuthIpc(
  getMainWindow: () => BrowserWindow | null,
  rendererUrl: string,
  store: AuthSessionStore,
  oauthFlow: Pick<GoogleOAuthFlow, 'start' | 'dispose'> | null = null,
  authService: AuthService | null = null,
): void {
  function accepts(event: IpcMainInvokeEvent, args: unknown[]): boolean {
    return isValidAuthRequest(event, args, getMainWindow(), rendererUrl)
  }

  ipcMain.handle(AUTH_CHANNELS.getSession, async (event, ...args: unknown[]): Promise<IpcResult<AuthSessionSnapshot>> => {
    if (!accepts(event, args)) return INVALID_REQUEST
    // [유지보수] 앱 시작 직후 Keychain 복구가 끝난 상태를 첫 Snapshot으로 보낸다.
    await authService?.ready()
    return { success: true, data: store.getSnapshot() }
  })

  ipcMain.handle(AUTH_CHANNELS.retrySession, async (event, ...args: unknown[]): Promise<IpcResult<AuthSessionSnapshot>> => {
    if (!accepts(event, args)) return INVALID_REQUEST
    if (!authService) return SERVICE_UNAVAILABLE
    await authService.restore()
    return { success: true, data: store.getSnapshot() }
  })

  ipcMain.handle(AUTH_CHANNELS.startGoogleLogin, async (event, ...args: unknown[]): Promise<IpcResult<{ started: boolean }>> => {
    if (!accepts(event, args)) return INVALID_REQUEST
    if (!authService) return SERVICE_UNAVAILABLE
    await authService?.ready()
    return beginGoogleLogin(store, oauthFlow)
  })

  ipcMain.handle(AUTH_CHANNELS.logout, async (event, ...args: unknown[]): Promise<IpcResult<{ loggedOut: true }>> => {
    if (!accepts(event, args)) return INVALID_REQUEST
    if (!authService) return SERVICE_UNAVAILABLE
    oauthFlow?.dispose()
    return authService.logout()
  })
}
