import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { isValidDomainList } from '../shared/onboarding-contract.cjs'
import { isFocusSettingsInput, parseFocusSettings, type FocusSettings } from '../shared/session-contract.cjs'
import { SETTINGS_CHANNELS, isThemeSource, type ThemeSource } from '../shared/settings-contract.cjs'
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
  constructor(private readonly auth: Pick<AuthService, 'authorizedRequest'>) {}

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
