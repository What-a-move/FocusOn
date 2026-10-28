import type { ApiResult } from './auth-api.cjs'
import type { AuthService } from './auth-service.cjs'
import { parseReadiness } from '../shared/onboarding-contract.cjs'
import type { OnboardingReadiness, OnboardingSummary, RequiredConsent } from '../shared/onboarding-contract.cjs'

// 동의 버전은 서버 GET /users/me/consents의 currentVersion을 쓴다(2026-10-22 합의). 응답에 없을 때만 이 값을 쓴다.
const FALLBACK_CONSENT_VERSION = '2026-09-17'

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const anything = () => ({})

function parseNickname(value: unknown): { nickname: string } | null {
  return isRecord(value) && typeof value.nickname === 'string' ? { nickname: value.nickname } : null
}

function parsePairingCode(value: unknown): { codeDisplay: string; expiresAt: string } | null {
  if (!isRecord(value) || typeof value.codeDisplay !== 'string' || typeof value.expiresAt !== 'string') return null
  if (!/^[A-Z0-9]{3}-[A-Z0-9]{3}$/u.test(value.codeDisplay) || !Number.isFinite(Date.parse(value.expiresAt))) return null
  return { codeDisplay: value.codeDisplay, expiresAt: value.expiresAt }
}

function parseDomains(value: unknown): { domains: string[] } | null {
  return isRecord(value) && Array.isArray(value.domains) && value.domains.every((item) => typeof item === 'string')
    ? { domains: value.domains as string[] }
    : null
}

// GET /users/me/consents 합의 형식(2026-10-22): data = { currentVersion, consents: [{ consentType, isGranted, consentVersion, grantedAt, withdrawnAt }] }
function parseConsents(value: unknown): { cameraGranted: boolean; currentVersion: string | null } | null {
  if (!isRecord(value) || !Array.isArray(value.consents)) return null
  return {
    cameraGranted: value.consents.some((item) => isRecord(item) && item.consentType === 'CAMERA_ANALYSIS' && item.isGranted === true),
    currentVersion: typeof value.currentVersion === 'string' && value.currentVersion ? value.currentVersion : null,
  }
}

/** 온보딩 Backend 계약 (Notion FocusOn API 명세서). 모든 요청은 AuthService의 토큰 갱신·재시도 규칙을 거친다. */
export class OnboardingService {
  constructor(
    private readonly auth: Pick<AuthService, 'authorizedRequest' | 'updateNickname' | 'updateOnboardingStatus' | 'currentNickname'>,
    private readonly screenAnalysisReady: () => boolean,
  ) {}

  /** 앱이 실제 macOS 권한을 다시 확인해 서버에 갱신한 뒤 이어갈 단계를 조회한다 (onboarding-readiness 비고). */
  async getReadiness(): Promise<ApiResult<OnboardingReadiness>> {
    const updated = await this.auth.authorizedRequest({
      path: '/api/v1/users/me/onboarding-readiness', method: 'PUT', expectedStatus: 200, parse: anything,
      body: { screenAnalysisReady: this.screenAnalysisReady() },
    })
    if (!updated.success) return updated
    const readiness = await this.auth.authorizedRequest({ path: '/api/v1/users/me/onboarding-readiness', method: 'GET', expectedStatus: 200, parse: parseReadiness })
    if (readiness.success) this.auth.updateOnboardingStatus(readiness.data.onboardingStatus)
    return readiness
  }

  /** 별도 중복 확인 API가 없어 저장 요청의 409 PROFILE_NICKNAME_DUPLICATE로 중복을 판단한다. */
  async saveProfile(nickname: string): Promise<ApiResult<{ nickname: string }>> {
    const result = await this.auth.authorizedRequest({ path: '/api/v1/users/me/profile', method: 'PUT', expectedStatus: 200, parse: parseNickname, body: { nickname } })
    if (result.success) this.auth.updateNickname(result.data.nickname)
    return result
  }

  issuePairingCode(): Promise<ApiResult<{ codeDisplay: string; expiresAt: string }>> {
    return this.auth.authorizedRequest({ path: '/api/v1/pairing-codes', method: 'POST', expectedStatus: 201, parse: parsePairingCode })
  }

  /** 필수 동의 3개 + 선택 카메라 동의를 항목별로 저장하고, Main이 확인한 화면 기록 상태를 준비 상태에 기록한다. */
  async savePermissionsStep(consents: RequiredConsent[], cameraConsent: boolean): Promise<ApiResult<{ saved: true }>> {
    // 화면에 보여준 약관과 같은 버전으로 저장되도록 서버가 내려준 현재 버전을 먼저 읽는다.
    const current = await this.auth.authorizedRequest({ path: '/api/v1/users/me/consents', method: 'GET', expectedStatus: 200, parse: parseConsents })
    if (!current.success) return current
    const consentVersion = current.data.currentVersion ?? FALLBACK_CONSENT_VERSION
    const items: [string, boolean][] = [...consents.map((type): [string, boolean] => [type, true]), ['CAMERA_ANALYSIS', cameraConsent]]
    for (const [type, isGranted] of items) {
      const result = await this.auth.authorizedRequest({
        path: `/api/v1/users/me/consents/${type}`, method: 'PUT', expectedStatus: 200, parse: anything,
        body: { isGranted, consentVersion, consentSource: 'ONBOARDING' },
      })
      if (!result.success) return result
    }
    const readiness = await this.auth.authorizedRequest({
      path: '/api/v1/users/me/onboarding-readiness', method: 'PUT', expectedStatus: 200, parse: anything,
      body: { screenAnalysisReady: this.screenAnalysisReady() },
    })
    return readiness.success ? { success: true, data: { saved: true } } : readiness
  }

  /** 제외 목록을 전체 교체로 저장한 뒤에만 단계 완료 marker를 보낸다. 빈 배열도 사용자가 건너뛰기를 고른 경우다. */
  async saveExcludedDomains(domains: string[]): Promise<ApiResult<{ domains: string[] }>> {
    const saved = await this.auth.authorizedRequest({
      path: '/api/v1/users/me/analysis-excluded-domains', method: 'PUT', expectedStatus: 200, parse: parseDomains, body: { domains }, withDeviceId: true,
    })
    if (!saved.success) return saved
    const marked = await this.auth.authorizedRequest({
      path: '/api/v1/users/me/onboarding-readiness', method: 'PUT', expectedStatus: 200, parse: anything,
      body: { analysisExcludedDomainsStepCompleted: true },
    })
    return marked.success ? saved : marked
  }

  async getSummary(): Promise<ApiResult<OnboardingSummary>> {
    const readiness = await this.auth.authorizedRequest({ path: '/api/v1/users/me/onboarding-readiness', method: 'GET', expectedStatus: 200, parse: parseReadiness })
    if (!readiness.success) return readiness
    const consents = await this.auth.authorizedRequest({ path: '/api/v1/users/me/consents', method: 'GET', expectedStatus: 200, parse: parseConsents })
    if (!consents.success) return consents
    const domains = await this.auth.authorizedRequest({ path: '/api/v1/users/me/analysis-excluded-domains', method: 'GET', expectedStatus: 200, parse: parseDomains, withDeviceId: true })
    if (!domains.success) return domains
    return {
      success: true,
      data: {
        nickname: this.auth.currentNickname(),
        screenAnalysisReady: readiness.data.steps.screenAnalysisReady,
        cameraConsent: consents.data.cameraGranted,
        excludedDomainCount: domains.data.domains.length,
      },
    }
  }
}
