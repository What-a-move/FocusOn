import type { SettingsApi, ThemeSource } from '../../electron/shared/settings-contract.cjs'
import type { FocusSettings } from '../../electron/shared/session-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { FocusSettings, ThemeSource }

const UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

// [유지보수] 설정 요청도 Main IPC(window.focusOn.settings)로만 보낸다. 브라우저에서 화면만 열면 연결이 없어 실패로 보인다.
async function viaSettings<T>(call: (api: SettingsApi) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.settings
  if (!api) return UNAVAILABLE
  try {
    return await call(api)
  } catch {
    return UNAVAILABLE
  }
}

export const settingsApi = {
  saveFocusSettings: (input: FocusSettings) => viaSettings((api) => api.saveFocusSettings(input)),
  getExcludedDomains: () => viaSettings((api) => api.getExcludedDomains()),
  saveExcludedDomains: (domains: string[]) => viaSettings((api) => api.saveExcludedDomains({ domains })),
  getTheme: () => viaSettings((api) => api.getTheme()),
  setTheme: (theme: ThemeSource) => viaSettings((api) => api.setTheme({ theme })),
}
