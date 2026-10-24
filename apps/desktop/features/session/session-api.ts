import type { FocusSettings, InterventionLevel, NotificationChannel, StartSessionInput, StartedSession } from '../../electron/shared/session-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { FocusSettings, InterventionLevel, NotificationChannel, StartedSession }

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

export const sessionApi = {
  getFocusSettings: () => viaSessions<FocusSettings>((api) => api.getFocusSettings()),
  start: (input: StartSessionInput) => viaSessions<StartedSession>((api) => api.start(input)),
}

/** 시작한 세션을 집중 화면으로 넘긴다. 세션 id·목표·시간만 두며 민감 정보가 아니다. */
export const ACTIVE_SESSION_KEY = 'focuson.activeSession'

/** 5~300분, 정수만 허용한다 (로그인 설계서 7.1). */
export const isValidMinutes = (value: number) => Number.isInteger(value) && value >= MIN_MINUTES && value <= MAX_MINUTES

export function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return hours === 0 ? `${rest}분` : rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}
