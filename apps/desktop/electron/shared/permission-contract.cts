import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 확정 Channel 목록과 같은 이름을 쓴다.
export const PERMISSION_CHANNELS = {
  screenStatus: 'permission:screen:get-status',
  screenOpenSettings: 'permission:screen:open-settings',
  cameraStatus: 'permission:camera:get-status',
  cameraRequest: 'permission:camera:request',
  notificationShow: 'notification:show',
  notificationOpenSettings: 'permission:notification:open-settings',
  notificationStatus: 'permission:notification:get-status',
  notificationConfirm: 'permission:notification:confirm',
} as const

export type PermissionStatus = 'not-determined' | 'granted' | 'denied' | 'restricted' | 'unknown'

// [주의] Renderer는 문구 대신 허용된 code만 보낸다. 실제 문구는 Main이 정한다.
export const NOTIFICATION_TEXT = {
  ONBOARDING_TEST: { title: 'FocusOn', body: '집중이 흐트러지면 이렇게 알려 드릴게요.' },
} as const

export type NotificationCode = keyof typeof NOTIFICATION_TEXT

export type PermissionsApi = {
  getScreenStatus(): Promise<IpcResult<{ status: PermissionStatus }>>
  openScreenSettings(): Promise<IpcResult<{ opened: boolean }>>
  getCameraStatus(): Promise<IpcResult<{ status: PermissionStatus }>>
  requestCamera(): Promise<IpcResult<{ status: PermissionStatus }>>
  showNotification(input: { titleCode: NotificationCode; bodyCode: NotificationCode }): Promise<IpcResult<{ shown: boolean }>>
  openNotificationSettings(): Promise<IpcResult<{ opened: boolean }>>
  /** 사용자가 시험 알림을 보고 확인한 기록. macOS 허용 상태가 아니라 이 기기의 사용자 확인 결과다. */
  getNotificationStatus(): Promise<IpcResult<{ confirmed: boolean }>>
  confirmNotification(input: { seen: boolean }): Promise<IpcResult<{ confirmed: boolean }>>
}

const STATUSES: readonly PermissionStatus[] = ['not-determined', 'granted', 'denied', 'restricted', 'unknown']

export function toPermissionStatus(value: string): PermissionStatus {
  return STATUSES.includes(value as PermissionStatus) ? value as PermissionStatus : 'unknown'
}

export function isNotificationCode(value: unknown): value is NotificationCode {
  return typeof value === 'string' && Object.hasOwn(NOTIFICATION_TEXT, value)
}
