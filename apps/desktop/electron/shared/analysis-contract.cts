import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 화면 분석 Channel과 같은 이름을 쓴다.
export const ANALYSIS_CHANNELS = {
  installedApps: 'analysis:installed-apps:list',
  excludedAppsGet: 'analysis:excluded-apps:get',
  excludedAppsSave: 'analysis:excluded-apps:save',
} as const

export const MAX_EXCLUDED_APPS = 50

/** 분석 제외 앱. 이 기기에만 저장하고 서버로 보내지 않는다(결정 024, Notion 화면 분석 6.1.1). */
export type ExcludedApp = { bundleId: string; name: string }
export type InstalledApp = ExcludedApp & { icon: string | null; running: boolean }

export type AnalysisApi = {
  listInstalledApps(): Promise<IpcResult<{ apps: InstalledApp[] }>>
  getExcludedApps(): Promise<IpcResult<{ apps: ExcludedApp[] }>>
  saveExcludedApps(input: { apps: ExcludedApp[] }): Promise<IpcResult<{ apps: ExcludedApp[] }>>
}

// 번들 ID 형식: 영문·숫자·점·하이픈 (예: com.kakao.KakaoTalkMac)
const BUNDLE_ID = /^[A-Za-z0-9][A-Za-z0-9.-]{0,254}$/u

export function isExcludedApp(value: unknown): value is ExcludedApp {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const { bundleId, name, ...rest } = value as Record<string, unknown>
  return Object.keys(rest).length === 0 && typeof bundleId === 'string' && BUNDLE_ID.test(bundleId)
    && typeof name === 'string' && name.trim().length > 0 && name.length <= 100
}

export function isExcludedAppList(value: unknown): value is ExcludedApp[] {
  if (!Array.isArray(value) || value.length > MAX_EXCLUDED_APPS || !value.every(isExcludedApp)) return false
  return new Set(value.map((app) => app.bundleId)).size === value.length
}
