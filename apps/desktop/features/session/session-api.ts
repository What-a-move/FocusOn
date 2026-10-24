import type { FocusSettings, InterventionLevel, NotificationChannel, SessionSnapshot, StartSessionInput, StartedSession } from '../../electron/shared/session-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { FocusSettings, InterventionLevel, NotificationChannel, SessionSnapshot, StartedSession }

// [주의] Renderer는 electron/shared에서 타입만 가져온다(goal-setting-shared-runtime-import-ERROR.md). Main의 값과 같다.
export const MIN_MINUTES = 5
export const MAX_MINUTES = 300

const UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

// [유지보수] 세션 요청도 Main IPC(window.focusOn.sessions)로만 보낸다. 브라우저에서 화면만 열면 연결이 없어 실패로 보인다.
async function viaSessions<T>(call: (api: NonNullable<Window['focusOn']>['sessions']) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.sessions
  if (!api) return UNAVAILABLE
  try {
    return await call(api)
  } catch {
    return UNAVAILABLE
  }
}

async function viaPermissions<T>(call: (api: NonNullable<Window['focusOn']>['permissions']) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.permissions
  if (!api) return UNAVAILABLE
  try {
    return await call(api)
  } catch {
    return UNAVAILABLE
  }
}

export const sessionApi = {
  getFocusSettings: () => viaSessions<FocusSettings>((api) => api.getFocusSettings()),
  start: (input: StartSessionInput) => viaSessions<StartedSession>((api) => api.start(input)),
  // 알림은 서버가 검증하지 않아 Desktop에서 시험 알림 + 사용자 확인으로 준비를 막는다(결정 022).
  sendTestNotification: () => viaPermissions<{ shown: boolean }>((api) => api.showNotification({ titleCode: 'ONBOARDING_TEST', bodyCode: 'ONBOARDING_TEST' })),
  confirmNotification: (seen: boolean) => viaPermissions<{ confirmed: boolean }>((api) => api.confirmNotification({ seen })),
  openNotificationSettings: () => viaPermissions<{ opened: boolean }>((api) => api.openNotificationSettings()),
  // 집중 화면: 타이머와 상태 명령은 Main SessionRuntime이 맡는다.
  getCurrent: () => viaSessions<SessionSnapshot | null>((api) => api.getCurrent()),
  pause: () => viaSessions<SessionSnapshot>((api) => api.pause()),
  resume: () => viaSessions<SessionSnapshot>((api) => api.resume()),
  extend: (minutes: number) => viaSessions<SessionSnapshot>((api) => api.extend({ minutes })),
  end: () => viaSessions<SessionSnapshot>((api) => api.end()),
  onChanged: (callback: (snapshot: SessionSnapshot | null) => void) => (typeof window === 'undefined' ? undefined : window.focusOn?.sessions?.onChanged(callback)) ?? (() => {}),
}

/** 5~300분, 정수만 허용한다 (로그인 설계서 7.1). */
export const isValidMinutes = (value: number) => Number.isInteger(value) && value >= MIN_MINUTES && value <= MAX_MINUTES

export function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return hours === 0 ? `${rest}분` : rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}
