// Extension 인증 정보 저장 (docs/DECISION_RECORD.md 결정 011, 2026-10-22)
// - Refresh Token: chrome.storage.local (브라우저를 다시 켜도 연결 유지). 쓸 때마다 서버가 회전하고 재사용을 감지한다.
// - Access Token: chrome.storage.session (메모리, 브라우저 종료 시 삭제)
// - installationId: chrome.storage.local (비밀값 아님, 기기 식별용)
// Desktop 토큰과 공유하지 않으며 Content Script에는 전달하지 않는다.

export type TokenPair = { accessToken: string; refreshToken: string; accessTokenExpiresAt: string; deviceId: string }

const KEYS = {
  installationId: 'focuson.installationId',
  refreshToken: 'focuson.auth.refreshToken',
  access: 'focuson.auth.access',
  deviceId: 'focuson.deviceId',
} as const

export function storage() {
  return typeof chrome === 'undefined' ? undefined : chrome?.storage
}

export async function getInstallationId(): Promise<string> {
  const area = storage()
  if (!area) throw new Error('Extension storage unavailable')
  const saved = (await area.local.get(KEYS.installationId))[KEYS.installationId]
  if (typeof saved === 'string' && saved) return saved
  const created = crypto.randomUUID()
  await area.local.set({ [KEYS.installationId]: created })
  return created
}

export async function saveTokens(tokens: TokenPair): Promise<void> {
  const area = storage()
  if (!area) throw new Error('Extension storage unavailable')
  // [주의] Content Script가 session 저장소를 읽지 못하도록 신뢰된 Extension 화면으로만 제한한다.
  await area.session.setAccessLevel?.({ accessLevel: 'TRUSTED_CONTEXTS' })
  // deviceId는 연결 해제(DELETE /extension-pairings/{extensionDeviceId})에 쓴다. 비밀값이 아니다.
  await area.local.set({ [KEYS.refreshToken]: tokens.refreshToken, [KEYS.deviceId]: tokens.deviceId })
  await area.session.set({ [KEYS.access]: { accessToken: tokens.accessToken, expiresAt: tokens.accessTokenExpiresAt } })
}

export async function isLinked(): Promise<boolean> {
  const area = storage()
  if (!area) return false
  const saved = (await area.local.get(KEYS.refreshToken))[KEYS.refreshToken]
  return typeof saved === 'string' && saved.length > 0
}

export async function clearTokens(): Promise<void> {
  const area = storage()
  if (!area) return
  await area.local.remove([KEYS.refreshToken, KEYS.deviceId])
  await area.session.remove(KEYS.access)
}

/** 갱신 요청에 쓰는 저장소 (features/session/api-client.ts의 TokenStore). */
export const chromeTokenStore = {
  async getAccess(): Promise<{ accessToken: string; expiresAt: string } | null> {
    const saved = (await storage()?.session.get(KEYS.access))?.[KEYS.access] as { accessToken?: unknown; expiresAt?: unknown } | undefined
    return saved && typeof saved.accessToken === 'string' && typeof saved.expiresAt === 'string' ? { accessToken: saved.accessToken, expiresAt: saved.expiresAt } : null
  },
  async getRefreshToken(): Promise<string | null> {
    const saved = (await storage()?.local.get(KEYS.refreshToken))?.[KEYS.refreshToken]
    return typeof saved === 'string' && saved ? saved : null
  },
  /** Refresh Token은 쓸 때마다 바뀐다. 새 값으로 바로 바꿔 둔다. */
  async saveRotation(tokens: { accessToken: string; refreshToken: string; accessTokenExpiresAt: string }): Promise<void> {
    const area = storage()
    if (!area) throw new Error('Extension storage unavailable')
    await area.local.set({ [KEYS.refreshToken]: tokens.refreshToken })
    await area.session.set({ [KEYS.access]: { accessToken: tokens.accessToken, expiresAt: tokens.accessTokenExpiresAt } })
  },
  clear: clearTokens,
}

export async function getDeviceId(): Promise<string | null> {
  const saved = (await storage()?.local.get(KEYS.deviceId))?.[KEYS.deviceId]
  return typeof saved === 'string' && saved ? saved : null
}
