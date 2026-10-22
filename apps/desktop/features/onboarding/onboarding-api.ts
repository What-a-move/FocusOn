import type { PermissionsApi } from '../../electron/shared/permission-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }

type OnboardingApi = {
  checkNickname(nickname: string): Promise<ApiResult<{ available: boolean }>>
  saveNickname(nickname: string): Promise<ApiResult<{ nickname: string }>>
  issuePairingCode(): Promise<ApiResult<{ code: string; expiresAt: string }>>
  openScreenRecordingSettings(): Promise<ApiResult<{ opened: boolean }>>
  checkScreenRecording(): Promise<ApiResult<{ granted: boolean }>>
  requestCamera(): Promise<ApiResult<{ granted: boolean }>>
  /** macOS는 알림 허용 상태를 조회할 수 없어 시험 알림을 보내 허용 창을 띄운다. */
  requestNotifications(): Promise<ApiResult<{ requested: boolean }>>
  savePermissionsStep(input: { consents: string[]; cameraConsent: boolean }): Promise<ApiResult<{ saved: true }>>
  saveExcludedSites(domains: string[]): Promise<ApiResult<{ saved: true }>>
  // ponytail: 완료 화면 요약용 화면 모델. 연결 시 GET onboarding-readiness·users/me 응답을 이 모양으로 바꾼다.
  getSummary(): Promise<ApiResult<OnboardingSummary>>
}

export type OnboardingSummary = { nickname: string | null; screenAnalysisReady: boolean; cameraConsent: boolean; excludedDomainCount: number }

// ponytail: Backend 온보딩 API가 생기기 전까지 서버 호출은 연결 지점만 둔다.
// 서버 응답을 성공으로 꾸미지 않고 "서비스를 쓸 수 없음"으로 돌려준다. 연결 시 해당 항목만 교체한다.
async function unavailable(): Promise<ApiResult<never>> {
  return { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } }
}

// macOS 권한은 Desktop Main IPC(window.focusOn.permissions)로 확인한다. 브라우저에서 열면 연결이 없어 unavailable이다.
async function viaPermissions<T, R>(call: (api: PermissionsApi) => Promise<ApiResult<T>>, map: (data: T) => R): Promise<ApiResult<R>> {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.permissions
  if (!api) return unavailable()
  try {
    const result = await call(api)
    return result.success ? { success: true, data: map(result.data) } : result
  } catch {
    return unavailable()
  }
}

export const onboardingApi: OnboardingApi = {
  checkNickname: unavailable,
  saveNickname: unavailable,
  issuePairingCode: unavailable,
  openScreenRecordingSettings: () => viaPermissions((api) => api.openScreenSettings(), (data) => data),
  checkScreenRecording: () => viaPermissions((api) => api.getScreenStatus(), ({ status }) => ({ granted: status === 'granted' })),
  requestCamera: () => viaPermissions((api) => api.requestCamera(), ({ status }) => ({ granted: status === 'granted' })),
  requestNotifications: () => viaPermissions(
    (api) => api.showNotification({ titleCode: 'ONBOARDING_TEST', bodyCode: 'ONBOARDING_TEST' }),
    ({ shown }) => ({ requested: shown }),
  ),
  savePermissionsStep: unavailable,
  saveExcludedSites: unavailable,
  getSummary: unavailable,
}

export type Notice = { title: string; body: string }

export function describeError(error: ApiError): Notice {
  switch (error.code) {
    case 'PROFILE_NICKNAME_DUPLICATE': return { title: '이미 사용 중인 닉네임이에요.', body: '다른 닉네임을 입력해 주세요.' }
    case 'PROFILE_NICKNAME_INVALID': return { title: '닉네임 형식을 확인해 주세요.', body: '2~12자의 한글·영문·숫자만 쓸 수 있어요.' }
    case 'LOCAL_NETWORK_UNAVAILABLE': return { title: '인터넷에 연결되어 있지 않아요.', body: '연결을 확인한 뒤 다시 시도해 주세요.' }
    default: return { title: '요청을 마치지 못했어요.', body: '잠시 후 다시 시도해 주세요.' }
  }
}
