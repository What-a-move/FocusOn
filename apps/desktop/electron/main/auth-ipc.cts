import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import { AUTH_CHANNELS, parseAuthSessionSnapshot } from '../shared/auth-contract.cjs'
import type { AuthSessionSnapshot, IpcResult } from '../shared/auth-contract.cjs'
import { isAllowedNavigation } from './renderer-path.cjs'

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

export function registerAuthIpc(
  getMainWindow: () => BrowserWindow | null,
  rendererUrl: string,
  store: AuthSessionStore,
): void {
  function accepts(event: IpcMainInvokeEvent, args: unknown[]): boolean {
    return isValidAuthRequest(event, args, getMainWindow(), rendererUrl)
  }

  ipcMain.handle(AUTH_CHANNELS.getSession, (event, ...args: unknown[]): IpcResult<AuthSessionSnapshot> => {
    if (!accepts(event, args)) return INVALID_REQUEST
    return { success: true, data: store.getSnapshot() }
  })

  ipcMain.handle(AUTH_CHANNELS.startGoogleLogin, (event, ...args: unknown[]): IpcResult<{ started: boolean }> => {
    if (!accepts(event, args)) return INVALID_REQUEST
    // [주의] OAuth·Backend 연결 전에는 브라우저를 열지 않고 성공을 가장하지 않는다.
    return SERVICE_UNAVAILABLE
  })

  ipcMain.handle(AUTH_CHANNELS.logout, (event, ...args: unknown[]): IpcResult<{ loggedOut: true }> => {
    if (!accepts(event, args)) return INVALID_REQUEST
    // [주의] 서버의 토큰 폐기 없이 로컬 상태만 지우면 로그아웃 완료로 취급할 수 없다.
    return SERVICE_UNAVAILABLE
  })
}
