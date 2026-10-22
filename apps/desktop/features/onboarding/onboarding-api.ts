export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }

type OnboardingApi = {
  checkNickname(nickname: string): Promise<ApiResult<{ available: boolean }>>
  saveNickname(nickname: string): Promise<ApiResult<{ nickname: string }>>
  issuePairingCode(): Promise<ApiResult<{ code: string; expiresAt: string }>>
  openScreenRecordingSettings(): Promise<ApiResult<{ opened: true }>>
  checkScreenRecording(): Promise<ApiResult<{ granted: boolean }>>
  requestCamera(): Promise<ApiResult<{ granted: boolean }>>
  requestNotifications(): Promise<ApiResult<{ granted: boolean }>>
  savePermissionsStep(input: { consents: string[]; cameraConsent: boolean }): Promise<ApiResult<{ saved: true }>>
  saveExcludedSites(domains: string[]): Promise<ApiResult<{ saved: true }>>
}

// ponytail: Desktop Main의 온보딩 IPC와 Backend API가 생기기 전까지 연결 지점만 둔다.
// 서버·OS 응답을 성공으로 꾸미지 않고 "서비스를 쓸 수 없음"으로 돌려준다. 연결 시 이 객체만 교체한다.
async function unavailable(): Promise<ApiResult<never>> {
  return { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } }
}

export const onboardingApi: OnboardingApi = {
  checkNickname: unavailable,
  saveNickname: unavailable,
  issuePairingCode: unavailable,
  openScreenRecordingSettings: unavailable,
  checkScreenRecording: unavailable,
  requestCamera: unavailable,
  requestNotifications: unavailable,
  savePermissionsStep: unavailable,
  saveExcludedSites: unavailable,
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
