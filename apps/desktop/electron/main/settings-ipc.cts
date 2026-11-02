import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { isValidDomainList } from '../shared/onboarding-contract.cjs'
import { isFocusSettingsInput, parseFocusSettings, type FocusSettings } from '../shared/session-contract.cjs'
import { parseReadiness } from '../shared/onboarding-contract.cjs'
import { SETTINGS_CHANNELS, isDeviceId, isThemeSource, parseConsentList, parseDeviceStatus, type ConsentList, type DeviceStatus, type ThemeSource } from '../shared/settings-contract.cjs'
import type { ApiResult } from './auth-api.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { AuthService } from './auth-service.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const onlyKeys = (value: unknown, keys: string[]): value is Record<string, unknown> =>
  isRecord(value) && Object.keys(value).length === keys.length && keys.every((key) => Object.hasOwn(value, key))
const parseDomains = (value: unknown): { domains: string[] } | null => (isRecord(value) && isValidDomainList(value.domains) ? { domains: value.domains } : null)

/** 설정 화면 Backend 계약 (Notion FocusOn API 명세서 focus-settings·analysis-excluded-domains). */
export class SettingsService {
  constructor(private readonly auth: Pick<AuthService, 'authorizedRequest' | 'currentDeviceId' | 'updateOnboardingStatus'>) {}

  /** 전체 객체를 저장한다. 서버가 저장한 값을 돌려준다(200 저장된 설정 전체). */
  saveFocusSettings(input: FocusSettings): Promise<ApiResult<FocusSettings>> {
    return this.auth.authorizedRequest({ path: '/api/v1/users/me/focus-settings', method: 'PUT', expectedStatus: 200, parse: parseFocusSettings, body: input })
  }

  getExcludedDomains(): Promise<ApiResult<{ domains: string[] }>> {
    return this.auth.authorizedRequest({ path: '/api/v1/users/me/analysis-excluded-domains', method: 'GET', expectedStatus: 200, parse: parseDomains, withDeviceId: true })
  }

  /** 전체 교체. [주의] 온보딩과 달리 onboarding-readiness 단계 완료는 기록하지 않는다. */
  saveExcludedDomains(domains: string[]): Promise<ApiResult<{ domains: string[] }>> {
    return this.auth.authorizedRequest({ path: '/api/v1/users/me/analysis-excluded-domains', method: 'PUT', expectedStatus: 200, parse: parseDomains, body: { domains }, withDeviceId: true })
  }

  getConsents(): Promise<ApiResult<ConsentList>> {
    return this.auth.authorizedRequest({ path: '/api/v1/users/me/consents', method: 'GET', expectedStatus: 200, parse: parseConsentList })
  }

  /**
   * 화면 분석 동의를 철회하거나 다시 동의한다. 화면에 보여 준 문서와 같은 버전으로 저장되도록 서버의 현재 버전을 쓴다.
   * 철회하면 서버 온보딩 상태가 미완료가 되어 새 집중을 시작할 수 없다. 바뀐 상태를 화면 상태에도 반영한다.
   */
  async setScreenConsent(granted: boolean): Promise<ApiResult<ConsentList>> {
    const current = await this.getConsents()
    if (!current.success) return current
    if (!current.data.currentVersion) return { success: false, error: { code: 'INVALID_RESPONSE', retryable: false } }
    const saved = await this.auth.authorizedRequest({
      path: '/api/v1/users/me/consents/SCREEN_ANALYSIS', method: 'PUT', expectedStatus: 200, parse: (value) => (isRecord(value) ? {} : null),
      body: { isGranted: granted, consentVersion: current.data.currentVersion, consentSource: 'DESKTOP_SETTINGS' },
    })
    if (!saved.success) return saved
    await this.syncOnboardingStatus()
    // [주의] 저장은 이미 끝났다. 다시 읽기가 실패해도 실패로 보이지 않게 저장한 값으로 목록을 맞춘다.
    const after = await this.getConsents()
    if (after.success) return after
    const now = new Date().toISOString()
    return { success: true, data: { ...current.data, items: current.data.items.map((item) => item.type !== 'SCREEN_ANALYSIS' ? item
      : { ...item, granted, grantedAt: granted ? now : item.grantedAt, withdrawnAt: granted ? null : now }) } }
  }

  getDevices(): Promise<ApiResult<DeviceStatus>> {
    return this.auth.authorizedRequest({ path: '/api/v1/devices', method: 'GET', expectedStatus: 200, parse: (value) => parseDeviceStatus(value, this.auth.currentDeviceId()) })
  }

  /** 이 Desktop에 연결된 Extension만 해제한다. 해제하면 온보딩 상태가 Extension 단계로 돌아간다. */
  async unlinkExtension(deviceId: string): Promise<ApiResult<DeviceStatus>> {
    const devices = await this.getDevices()
    if (!devices.success) return devices
    if (devices.data.extension?.deviceId !== deviceId) return { success: false, error: { code: 'DEVICE_NOT_FOUND', retryable: false } }
    const removed = await this.auth.authorizedRequest({ path: `/api/v1/extension-pairings/${deviceId}`, method: 'DELETE', expectedStatus: 204, parse: () => ({}) })
    if (!removed.success) return removed
    await this.syncOnboardingStatus()
    // 해제는 이미 끝났다. 다시 읽기가 실패해도 연결 없음으로 보여 준다.
    const after = await this.getDevices()
    return after.success ? after : { success: true, data: { extension: null } }
  }

  // 실패해도 철회·해제 결과는 그대로다. 다음 화면 이동 때 readiness를 다시 읽는다.
  private async syncOnboardingStatus(): Promise<void> {
    const readiness = await this.auth.authorizedRequest({ path: '/api/v1/users/me/onboarding-readiness', method: 'GET', expectedStatus: 200, parse: parseReadiness })
    if (readiness.success) this.auth.updateOnboardingStatus(readiness.data.onboardingStatus)
  }
}

/** 화면 모드를 이 기기에 저장한다. 파일이 없거나 깨졌으면 macOS 설정을 따른다. */
export class ThemePreference {
  constructor(private readonly file: string) {}

  read(): ThemeSource {
    try {
      const saved = (JSON.parse(readFileSync(this.file, 'utf8')) as { theme?: unknown }).theme
      return isThemeSource(saved) ? saved : 'system'
    } catch {
      return 'system'
    }
  }

  write(theme: ThemeSource): void {
    const temp = `${this.file}.tmp`
    writeFileSync(temp, JSON.stringify({ theme }))
    renameSync(temp, this.file)
  }
}

export function registerSettingsIpc(
  getMainWindow: () => BrowserWindow | null,
  rendererUrl: string,
  service: SettingsService | null,
  theme: { get(): ThemeSource; set(theme: ThemeSource): void },
): void {
  function handle<T>(channel: string, accept: (args: unknown[]) => boolean, run: (service: SettingsService, args: unknown[]) => Promise<ApiResult<T>>): void {
    ipcMain.handle(channel, async (event: IpcMainInvokeEvent, ...args: unknown[]): Promise<IpcResult<T>> => {
      if (!isTrustedAuthSender(event, getMainWindow(), rendererUrl) || !accept(args)) return INVALID_REQUEST
      if (!service) return SERVICE_UNAVAILABLE
      try {
        return await run(service, args)
      } catch {
        return SERVICE_UNAVAILABLE
      }
    })
  }

  handle(SETTINGS_CHANNELS.saveFocusSettings, (args) => args.length === 1 && isFocusSettingsInput(args[0]), (s, args) => s.saveFocusSettings(args[0] as FocusSettings))
  handle(SETTINGS_CHANNELS.excludedDomainsGet, (args) => args.length === 0, (s) => s.getExcludedDomains())
  handle(
    SETTINGS_CHANNELS.excludedDomainsSave,
    (args) => args.length === 1 && onlyKeys(args[0], ['domains']) && isValidDomainList(args[0].domains),
    (s, args) => s.saveExcludedDomains((args[0] as { domains: string[] }).domains),
  )
  handle(SETTINGS_CHANNELS.consentsGet, (args) => args.length === 0, (s) => s.getConsents())
  handle(
    SETTINGS_CHANNELS.screenConsentSet,
    (args) => args.length === 1 && onlyKeys(args[0], ['granted']) && typeof args[0].granted === 'boolean',
    (s, args) => s.setScreenConsent((args[0] as { granted: boolean }).granted),
  )
  handle(SETTINGS_CHANNELS.devicesGet, (args) => args.length === 0, (s) => s.getDevices())
  handle(
    SETTINGS_CHANNELS.extensionUnlink,
    (args) => args.length === 1 && onlyKeys(args[0], ['deviceId']) && isDeviceId(args[0].deviceId),
    (s, args) => s.unlinkExtension((args[0] as { deviceId: string }).deviceId),
  )

  ipcMain.handle(SETTINGS_CHANNELS.themeGet, (event, ...args: unknown[]): IpcResult<{ theme: ThemeSource }> => {
    if (args.length !== 0 || !isTrustedAuthSender(event, getMainWindow(), rendererUrl)) return INVALID_REQUEST
    return { success: true, data: { theme: theme.get() } }
  })
  ipcMain.handle(SETTINGS_CHANNELS.themeSet, (event, ...args: unknown[]): IpcResult<{ theme: ThemeSource }> => {
    if (args.length !== 1 || !onlyKeys(args[0], ['theme']) || !isThemeSource(args[0].theme) || !isTrustedAuthSender(event, getMainWindow(), rendererUrl)) return INVALID_REQUEST
    try {
      theme.set(args[0].theme)
      return { success: true, data: { theme: args[0].theme } }
    } catch {
      return SERVICE_UNAVAILABLE
    }
  })
}
