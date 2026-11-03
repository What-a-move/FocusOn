import type { IpcResult } from './auth-contract.cjs'
import type { FocusSettings } from './session-contract.cjs'

// IPC_CONTRACT.md 설정 Channel (2026-10-27, Issue #80). 집중 설정 조회는 기존 session:focus-settings:get을 쓴다.
export const SETTINGS_CHANNELS = {
  saveFocusSettings: 'settings:focus-settings:save',
  excludedDomainsGet: 'settings:excluded-domains:get',
  excludedDomainsSave: 'settings:excluded-domains:save',
  themeGet: 'app:theme:get',
  themeSet: 'app:theme:set',
  consentsGet: 'settings:consents:get',
  screenConsentSet: 'settings:screen-consent:set',
  devicesGet: 'settings:devices:get',
  extensionUnlink: 'settings:extension:unlink',
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
  getConsents(): Promise<IpcResult<ConsentList>>
  setScreenConsent(input: { granted: boolean }): Promise<IpcResult<ConsentList>>
  getDevices(): Promise<IpcResult<DeviceStatus>>
  unlinkExtension(input: { deviceId: string }): Promise<IpcResult<DeviceStatus>>
}

/** GET /users/me/consents의 항목 하나. 시각은 서버가 준 ISO 문자열이다. */
export type ConsentType = 'TERMS_OF_SERVICE' | 'PRIVACY_POLICY' | 'SCREEN_ANALYSIS' | 'CAMERA_ANALYSIS'
export type ConsentItem = { type: ConsentType; granted: boolean; version: string | null; grantedAt: string | null; withdrawnAt: string | null }
export type ConsentList = { currentVersion: string | null; items: ConsentItem[] }
/** 이 Desktop에 연결된 Extension. GET /devices에서 linkedDesktopDeviceId가 이 기기인 EXTENSION만 고른다. */
export type LinkedExtension = { deviceId: string; name: string | null; lastUsedAt: string | null }
export type DeviceStatus = { extension: LinkedExtension | null }

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const CONSENT_TYPES: readonly ConsentType[] = ['TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SCREEN_ANALYSIS', 'CAMERA_ANALYSIS']
const text = (value: unknown): string | null => (typeof value === 'string' && value ? value : null)

// 형식(2026-10-22 합의): { currentVersion, consents: [{ consentType, isGranted, consentVersion, grantedAt, withdrawnAt }] }
export function parseConsentList(value: unknown): ConsentList | null {
  if (!isRecord(value) || !Array.isArray(value.consents)) return null
  const items: ConsentItem[] = []
  for (const item of value.consents) {
    if (!isRecord(item) || !CONSENT_TYPES.includes(item.consentType as ConsentType) || typeof item.isGranted !== 'boolean') return null
    items.push({ type: item.consentType as ConsentType, granted: item.isGranted, version: text(item.consentVersion), grantedAt: text(item.grantedAt), withdrawnAt: text(item.withdrawnAt) })
  }
  return { currentVersion: text(value.currentVersion), items }
}

// GET /devices: { devices: [{ deviceId, type: DESKTOP|EXTENSION, name, lastUsedAt, linkedDesktopDeviceId }] }
export function parseDeviceStatus(value: unknown, desktopDeviceId: string | null): DeviceStatus | null {
  if (!isRecord(value) || !Array.isArray(value.devices)) return null
  for (const item of value.devices) if (!isRecord(item) || typeof item.deviceId !== 'string' || typeof item.type !== 'string') return null
  const found = (value.devices as Record<string, unknown>[]).find((item) => item.type === 'EXTENSION' && desktopDeviceId !== null && item.linkedDesktopDeviceId === desktopDeviceId)
  return { extension: found ? { deviceId: found.deviceId as string, name: text(found.name), lastUsedAt: text(found.lastUsedAt) } : null }
}

export const isDeviceId = (value: unknown): value is string => typeof value === 'string' && /^\d{1,19}$/u.test(value)
