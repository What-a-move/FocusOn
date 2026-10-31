import type { PermissionsApi } from '../../electron/shared/permission-contract.cjs'
import type { AnalysisApi, ExcludedApp, InstalledApp } from '../../electron/shared/analysis-contract.cjs'
import type { OnboardingApi as MainOnboardingApi, OnboardingReadiness, OnboardingStep, OnboardingSummary, RequiredConsent } from '../../electron/shared/onboarding-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { OnboardingReadiness, OnboardingSummary, ExcludedApp, InstalledApp }

// 화면 동의 id → 서버 consentType
const CONSENT_TYPES: Record<string, RequiredConsent> = {
  terms: 'TERMS_OF_SERVICE',
  privacy: 'PRIVACY_POLICY',
  'screen-analysis': 'SCREEN_ANALYSIS',
}

// 서버 currentStep → 온보딩 화면 경로
export const STEP_ROUTES: Record<OnboardingStep, string> = {
  NICKNAME: '/onboarding/',
  EXTENSION: '/onboarding/extension/',
  PERMISSIONS_AND_CONSENTS: '/onboarding/permissions/',
  EXCLUDED_SITES: '/onboarding/sites/',
  COMPLETED: '/',
}

async function unavailable(): Promise<ApiResult<never>> {
  return { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } }
}

// [유지보수] Backend 요청과 macOS 권한은 모두 Desktop Main IPC(window.focusOn)를 거친다. 토큰은 Renderer에 없다.
// 브라우저에서 화면만 열면 연결이 없어 SERVICE_UNAVAILABLE이다. 성공을 꾸미지 않는다.
async function via<A, T, R>(pick: (api: NonNullable<Window['focusOn']>) => A | undefined, call: (api: A) => Promise<ApiResult<T>>, map: (data: T) => R): Promise<ApiResult<R>> {
  const bridge = typeof window === 'undefined' ? undefined : window.focusOn
  const api = bridge ? pick(bridge) : undefined
  if (!api) return unavailable()
  try {
    const result = await call(api)
    return result.success ? { success: true, data: map(result.data) } : result
  } catch {
    return unavailable()
  }
}

const viaPermissions = <T, R>(call: (api: PermissionsApi) => Promise<ApiResult<T>>, map: (data: T) => R) => via((api) => api.permissions, call, map)
const viaOnboarding = <T, R>(call: (api: MainOnboardingApi) => Promise<ApiResult<T>>, map: (data: T) => R) => via((api) => api.onboarding, call, map)
const same = <T,>(data: T) => data

export const onboardingApi = {
  getReadiness: () => viaOnboarding((api) => api.getReadiness(), same),
  /** 중복 확인 API가 없어 저장 요청 자체가 중복 확인이다 (409 PROFILE_NICKNAME_DUPLICATE). */
  saveNickname: (nickname: string) => viaOnboarding((api) => api.saveProfile({ nickname }), same),
  issuePairingCode: () => viaOnboarding((api) => api.issuePairingCode(), ({ codeDisplay, expiresAt }) => ({ code: codeDisplay, expiresAt })),
  openScreenRecordingSettings: () => viaPermissions((api) => api.openScreenSettings(), same),
  checkScreenRecording: () => viaPermissions((api) => api.getScreenStatus(), ({ status }) => ({ granted: status === 'granted' })),
  requestCamera: () => viaPermissions((api) => api.requestCamera(), ({ status }) => ({ granted: status === 'granted' })),
  /** macOS는 알림 허용 상태를 조회할 수 없어 시험 알림을 보내 허용 창을 띄운다. */
  requestNotifications: () => viaPermissions(
    (api) => api.showNotification({ titleCode: 'ONBOARDING_TEST', bodyCode: 'ONBOARDING_TEST' }),
    ({ shown }) => ({ requested: shown }),
  ),
  openNotificationSettings: () => viaPermissions((api) => api.openNotificationSettings(), same),
  /** 시험 알림을 본 사용자 확인을 이 기기에 기록한다. 세션 시작 때 Desktop이 이 기록으로 알림 준비를 막는다(결정 022). */
  confirmNotification: (seen: boolean) => viaPermissions((api) => api.confirmNotification({ seen }), same),
  savePermissionsStep: (input: { consents: string[]; cameraConsent: boolean }) => viaOnboarding(
    (api) => api.savePermissionsStep({ consents: input.consents.map((id) => CONSENT_TYPES[id]).filter(Boolean), cameraConsent: input.cameraConsent }),
    same,
  ),
  saveExcludedSites: (domains: string[]) => viaOnboarding((api) => api.saveExcludedDomains({ domains }), same),
  getSummary: () => viaOnboarding((api) => api.getSummary(), same),
}

// 분석 제외 앱은 서버가 아니라 이 기기(Desktop Main)에만 저장한다(결정 024).
const viaAnalysis = <T, R>(call: (api: AnalysisApi) => Promise<ApiResult<T>>, map: (data: T) => R) => via((api) => api.analysis, call, map)

export const analysisApi = {
  listInstalledApps: () => viaAnalysis((api) => api.listInstalledApps(), (data) => data.apps),
  getExcludedApps: () => viaAnalysis((api) => api.getExcludedApps(), (data) => data.apps),
  saveExcludedApps: (apps: ExcludedApp[]) => viaAnalysis((api) => api.saveExcludedApps({ apps }), (data) => data.apps),
}

// info: 오류가 아닌 안내(막힌 이유 등). 빨간색·느낌표 없이 보여 준다.
export type Notice = { title: string; body: string; info?: boolean }

export function describeError(error: ApiError): Notice {
  switch (error.code) {
    case 'PROFILE_NICKNAME_DUPLICATE': return { title: '이미 사용 중인 닉네임이에요.', body: '다른 닉네임을 입력해 주세요.' }
    case 'PROFILE_NICKNAME_INVALID': return { title: '닉네임 형식을 확인해 주세요.', body: '2~12자의 한글·영문·숫자만 쓸 수 있어요.' }
    case 'LOCAL_NETWORK_UNAVAILABLE': return { title: '인터넷에 연결되어 있지 않아요.', body: '연결을 확인한 뒤 다시 시도해 주세요.' }
    case 'TOO_MANY_REQUESTS': return { title: '요청이 너무 잦았어요.', body: '잠시 후 다시 시도해 주세요.' }
    case 'AUTH_RELOGIN_REQUIRED':
    case 'AUTH_TOKEN_REUSE_DETECTED':
    case 'AUTH_OAUTH_REAUTH_REQUIRED': return { title: '로그인이 만료됐어요.', body: 'Google로 다시 로그인해 주세요.' }
    default: return { title: '요청을 마치지 못했어요.', body: '잠시 후 다시 시도해 주세요.' }
  }
}
