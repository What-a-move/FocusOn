import type { IpcResult } from './auth-contract.cjs'
import type { FocusSettings } from './session-contract.cjs'

// IPC_CONTRACT.md 설정 Channel (2026-10-27, Issue #80). 집중 설정 조회는 기존 session:focus-settings:get을 쓴다.
export const SETTINGS_CHANNELS = {
  saveFocusSettings: 'settings:focus-settings:save',
  excludedDomainsGet: 'settings:excluded-domains:get',
  excludedDomainsSave: 'settings:excluded-domains:save',
  themeGet: 'app:theme:get',
  themeSet: 'app:theme:set',
} as const

/** 화면 모드. system은 macOS 설정을 따른다 (Electron nativeTheme.themeSource와 같은 값). */
export type ThemeSource = 'light' | 'dark' | 'system'
export const isThemeSource = (value: unknown): value is ThemeSource => value === 'light' || value === 'dark' || value === 'system'

export type SettingsApi = {
  saveFocusSettings(input: FocusSettings): Promise<IpcResult<FocusSettings>>
  getExcludedDomains(): Promise<IpcResult<{ domains: string[] }>>
  saveExcludedDomains(input: { domains: string[] }): Promise<IpcResult<{ domains: string[] }>>
  getTheme(): Promise<IpcResult<{ theme: ThemeSource }>>
  setTheme(input: { theme: ThemeSource }): Promise<IpcResult<{ theme: ThemeSource }>>
}
