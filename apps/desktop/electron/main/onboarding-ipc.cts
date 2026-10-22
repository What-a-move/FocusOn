import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { ONBOARDING_CHANNELS, isValidConsentList, isValidDomainList, isValidNickname } from '../shared/onboarding-contract.cjs'
import type { ApiResult } from './auth-api.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { OnboardingService } from './onboarding-service.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

function onlyKeys(value: unknown, keys: string[]): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    && Object.keys(value).every((key) => keys.includes(key)) && keys.every((key) => key in value)
}

/** 입력 검증 함수가 통과시킨 값만 Service로 넘긴다. Backend API 설정이 없으면 SERVICE_UNAVAILABLE이다. */
export function registerOnboardingIpc(getMainWindow: () => BrowserWindow | null, rendererUrl: string, service: OnboardingService | null): void {
  function handle<T>(channel: string, accept: (args: unknown[]) => boolean, run: (service: OnboardingService, args: unknown[]) => Promise<ApiResult<T>>): void {
    ipcMain.handle(channel, async (event: IpcMainInvokeEvent, ...args: unknown[]): Promise<IpcResult<T>> => {
      if (!isTrustedAuthSender(event, getMainWindow(), rendererUrl) || !accept(args)) return INVALID_REQUEST
      if (!service) return SERVICE_UNAVAILABLE
      try {
        return await run(service, args)
      } catch {
        // [주의] 내부 오류 메시지는 Renderer로 보내지 않는다.
        return SERVICE_UNAVAILABLE
      }
    })
  }
  const none = (args: unknown[]) => args.length === 0

  handle(ONBOARDING_CHANNELS.readiness, none, (s) => s.getReadiness())
  handle(ONBOARDING_CHANNELS.issuePairingCode, none, (s) => s.issuePairingCode())
  handle(ONBOARDING_CHANNELS.summary, none, (s) => s.getSummary())
  handle(
    ONBOARDING_CHANNELS.saveProfile,
    (args) => args.length === 1 && onlyKeys(args[0], ['nickname']) && isValidNickname(args[0].nickname),
    (s, args) => s.saveProfile((args[0] as { nickname: string }).nickname),
  )
  handle(
    ONBOARDING_CHANNELS.savePermissionsStep,
    (args) => args.length === 1 && onlyKeys(args[0], ['consents', 'cameraConsent'])
      && isValidConsentList(args[0].consents) && (args[0].consents as unknown[]).length === 3 && typeof args[0].cameraConsent === 'boolean',
    (s, args) => {
      const input = args[0] as { consents: Parameters<OnboardingService['savePermissionsStep']>[0]; cameraConsent: boolean }
      return s.savePermissionsStep(input.consents, input.cameraConsent)
    },
  )
  handle(
    ONBOARDING_CHANNELS.saveExcludedDomains,
    (args) => args.length === 1 && onlyKeys(args[0], ['domains']) && isValidDomainList(args[0].domains),
    (s, args) => s.saveExcludedDomains((args[0] as { domains: string[] }).domains),
  )
}
