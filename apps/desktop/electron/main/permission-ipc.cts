import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { desktopCapturer, ipcMain, Notification, shell, systemPreferences } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import {
  NOTIFICATION_TEXT,
  PERMISSION_CHANNELS,
  isNotificationCode,
  toPermissionStatus,
} from '../shared/permission-contract.cjs'
import type { PermissionStatus } from '../shared/permission-contract.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const
// macOS 시스템 설정 › 개인정보 보호 및 보안 › 화면 기록
const SCREEN_SETTINGS_URL = 'x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture'

export function mediaStatus(media: 'screen' | 'camera'): PermissionStatus {
  if (process.platform !== 'darwin') return 'unknown'
  return toPermissionStatus(systemPreferences.getMediaAccessStatus(media))
}

export function isValidNotificationInput(args: unknown[]): args is [{ titleCode: keyof typeof NOTIFICATION_TEXT; bodyCode: keyof typeof NOTIFICATION_TEXT }] {
  if (args.length !== 1) return false
  const input = args[0]
  if (typeof input !== 'object' || input === null || Array.isArray(input)) return false
  const { titleCode, bodyCode, ...rest } = input as Record<string, unknown>
  return Object.keys(rest).length === 0 && isNotificationCode(titleCode) && isNotificationCode(bodyCode)
}

export function registerPermissionIpc(getMainWindow: () => BrowserWindow | null, rendererUrl: string): void {
  const trusted = (event: IpcMainInvokeEvent) => isTrustedAuthSender(event, getMainWindow(), rendererUrl)

  function handleVoid<T>(channel: string, run: () => Promise<T> | T): void {
    ipcMain.handle(channel, async (event, ...args: unknown[]): Promise<IpcResult<T>> => {
      if (args.length !== 0 || !trusted(event)) return INVALID_REQUEST
      try {
        return { success: true, data: await run() }
      } catch {
        // [주의] macOS·Electron 내부 오류 메시지는 Renderer로 보내지 않는다.
        return SERVICE_UNAVAILABLE
      }
    })
  }

  handleVoid(PERMISSION_CHANNELS.screenStatus, () => ({ status: mediaStatus('screen') }))

  handleVoid(PERMISSION_CHANNELS.screenOpenSettings, async () => {
    // [주의] 화면 기록은 앱 안에서 허용 창을 띄우는 API가 없다. 아직 한 번도 묻지 않은 상태라면
    // 캡처 목록을 한 번만 조회해 시스템 설정 목록에 FocusOn이 나타나게 하고, 그 뒤 설정 화면을 연다.
    if (mediaStatus('screen') === 'not-determined') {
      await desktopCapturer.getSources({ types: ['screen'], thumbnailSize: { width: 0, height: 0 } }).catch(() => [])
    }
    await shell.openExternal(SCREEN_SETTINGS_URL)
    return { opened: true }
  })

  handleVoid(PERMISSION_CHANNELS.cameraStatus, () => ({ status: mediaStatus('camera') }))

  handleVoid(PERMISSION_CHANNELS.cameraRequest, async () => {
    // [주의] 이미 거부했다면 OS 창을 반복해서 띄우지 않고 현재 상태만 돌려준다 (MACOS_PERMISSION_FLOW.md).
    if (mediaStatus('camera') === 'not-determined') await systemPreferences.askForMediaAccess('camera')
    return { status: mediaStatus('camera') }
  })

  ipcMain.handle(PERMISSION_CHANNELS.notificationShow, async (event, ...args: unknown[]): Promise<IpcResult<{ shown: boolean }>> => {
    if (!trusted(event) || !isValidNotificationInput(args)) return INVALID_REQUEST
    if (!Notification.isSupported()) return { success: true, data: { shown: false } }
    const { titleCode, bodyCode } = args[0]
    // macOS는 앱이 처음 알림을 보낼 때 허용 여부를 묻는다. 알림 상태를 직접 조회하는 API는 없다.
    new Notification({ title: NOTIFICATION_TEXT[titleCode].title, body: NOTIFICATION_TEXT[bodyCode].body, silent: true }).show()
    return { success: true, data: { shown: true } }
  })
}
