// Extension 인증 정보 저장 (docs/DECISION_RECORD.md 결정 011, 2026-10-22)
// - Refresh Token: chrome.storage.local (브라우저를 다시 켜도 연결 유지). 쓸 때마다 서버가 회전하고 재사용을 감지한다.
// - Access Token: chrome.storage.session (메모리, 브라우저 종료 시 삭제)
// - installationId: chrome.storage.local (비밀값 아님, 기기 식별용)
// Desktop 토큰과 공유하지 않으며 Content Script에는 전달하지 않는다.

export type TokenPair = { accessToken: string; refreshToken: string; accessTokenExpiresAt: string }

const KEYS = {
  installationId: 'focuson.installationId',
  refreshToken: 'focuson.auth.refreshToken',
  access: 'focuson.auth.access',
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
  await area.local.set({ [KEYS.refreshToken]: tokens.refreshToken })
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
  await area.local.remove(KEYS.refreshToken)
  await area.session.remove(KEYS.access)
}
