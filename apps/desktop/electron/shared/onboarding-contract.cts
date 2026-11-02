import type { IpcResult } from './auth-contract.cjs'

// 온보딩 Backend 호출은 Main이 Desktop 토큰으로 처리하고 Renderer에는 화면에 필요한 값만 넘긴다.
export const ONBOARDING_CHANNELS = {
  readiness: 'onboarding:readiness:get',
  saveProfile: 'onboarding:profile:save',
  issuePairingCode: 'onboarding:pairing-code:issue',
  savePermissionsStep: 'onboarding:permissions-step:save',
  saveExcludedDomains: 'onboarding:excluded-domains:save',
  summary: 'onboarding:summary:get',
} as const

export const ONBOARDING_STEPS = ['NICKNAME', 'EXTENSION', 'PERMISSIONS_AND_CONSENTS', 'EXCLUDED_SITES', 'COMPLETED'] as const
export type OnboardingStep = typeof ONBOARDING_STEPS[number]

export type OnboardingReadiness = {
  onboardingStatus: 'INCOMPLETE' | 'COMPLETED'
  currentStep: OnboardingStep
  steps: {
    profileCompleted: boolean
    extensionLinked: boolean
    screenAnalysisReady: boolean
    requiredConsentsCompleted: boolean
    analysisExcludedDomainsStepCompleted: boolean
  }
}

export const REQUIRED_CONSENTS = ['TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SCREEN_ANALYSIS'] as const
export type RequiredConsent = typeof REQUIRED_CONSENTS[number]

export type OnboardingSummary = {
  nickname: string | null
  screenAnalysisReady: boolean
  cameraConsent: boolean
  excludedDomainCount: number
}

export type OnboardingApi = {
  getReadiness(): Promise<IpcResult<OnboardingReadiness>>
  saveProfile(input: { nickname: string }): Promise<IpcResult<{ nickname: string }>>
  issuePairingCode(): Promise<IpcResult<{ codeDisplay: string; expiresAt: string }>>
  savePermissionsStep(input: { consents: RequiredConsent[]; cameraConsent: boolean }): Promise<IpcResult<{ saved: true }>>
  saveExcludedDomains(input: { domains: string[] }): Promise<IpcResult<{ domains: string[] }>>
  getSummary(): Promise<IpcResult<OnboardingSummary>>
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function parseReadiness(value: unknown): OnboardingReadiness | null {
  if (!isRecord(value) || !isRecord(value.steps)) return null
  if (value.onboardingStatus !== 'INCOMPLETE' && value.onboardingStatus !== 'COMPLETED') return null
  if (!ONBOARDING_STEPS.includes(value.currentStep as OnboardingStep)) return null
  const keys = ['profileCompleted', 'extensionLinked', 'screenAnalysisReady', 'requiredConsentsCompleted', 'analysisExcludedDomainsStepCompleted'] as const
  if (!keys.every((key) => typeof (value.steps as Record<string, unknown>)[key] === 'boolean')) return null
  const steps = value.steps as Record<typeof keys[number], boolean>
  return {
    onboardingStatus: value.onboardingStatus,
    currentStep: value.currentStep as OnboardingStep,
    steps: Object.fromEntries(keys.map((key) => [key, steps[key]])) as OnboardingReadiness['steps'],
  }
}

// [주의] Main은 Renderer 입력을 다시 검증한다. 형식 검증만 하고 최종 판단은 서버 응답을 따른다.
export function isValidNickname(value: unknown): value is string {
  return typeof value === 'string' && /^[가-힣A-Za-z0-9]{2,12}$/u.test(value)
}

export function isValidDomainList(value: unknown): value is string[] {
  return Array.isArray(value) && value.length <= 50
    && value.every((item) => typeof item === 'string' && item.length >= 4 && item.length <= 253 && /^[a-z0-9.-]+$/u.test(item))
}

export function isValidConsentList(value: unknown): value is RequiredConsent[] {
  return Array.isArray(value) && value.every((item) => REQUIRED_CONSENTS.includes(item as RequiredConsent))
    && new Set(value).size === value.length
}
