import { getInstallationId, saveTokens, storage, type TokenPair } from './extension-auth'

export type PairingError = { code: string; retryAfterSeconds?: number }
export type PairingResult = { success: true } | { success: false; error: PairingError }

// Backend origin은 빌드할 때 NEXT_PUBLIC_FOCUSON_API_BASE_URL로 넣는다. HTTPS 또는 로컬 개발 loopback HTTP만 허용한다.
export function resolveApiBase(value: string | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    const loopback = url.hostname === '127.0.0.1' || url.hostname === 'localhost'
    if ((url.protocol !== 'https:' && !(loopback && url.protocol === 'http:')) || url.username || url.password || url.search || url.hash || url.pathname !== '/') return null
    return url.origin
  } catch {
    return null
  }
}

function parseTokens(payload: unknown): TokenPair | null {
  const data = typeof payload === 'object' && payload !== null ? (payload as { data?: unknown }).data : null
  if (typeof data !== 'object' || data === null) return null
  const { accessToken, refreshToken, accessTokenExpiresAt } = data as Record<string, unknown>
  if (typeof accessToken !== 'string' || !accessToken || typeof refreshToken !== 'string' || !refreshToken || typeof accessTokenExpiresAt !== 'string') return null
  const expiresAt = Date.parse(accessTokenExpiresAt)
  if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) return null
  return { accessToken, refreshToken, accessTokenExpiresAt }
}

const fail = (code: string, retryAfterSeconds?: number): PairingResult => ({ success: false, error: { code, retryAfterSeconds } })

// POST /api/v1/extension-pairings: { pairingCode: "ABC123", installationId } → 201 Extension 전용 토큰
export async function submitPairing(codeDisplay: string, baseUrl = resolveApiBase(process.env.NEXT_PUBLIC_FOCUSON_API_BASE_URL), request: typeof fetch = fetch): Promise<PairingResult> {
  if (!storage()) return fail('EXTENSION_CONTEXT_REQUIRED')
  if (!baseUrl) return fail('SERVICE_UNAVAILABLE')
  const pairingCode = codeDisplay.replace('-', '')
  if (!/^[A-Z0-9]{6}$/u.test(pairingCode)) return fail('LINK_001_INVALID_FORMAT')

  let response: Response
  try {
    response = await request(`${baseUrl}/api/v1/extension-pairings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pairingCode, installationId: await getInstallationId() }),
      redirect: 'manual',
      signal: AbortSignal.timeout(10_000),
    })
  } catch {
    return fail('LOCAL_NETWORK_UNAVAILABLE')
  }

  let payload: unknown = null
  try { payload = await response.json() } catch { /* 본문 없음 */ }

  if (response.status !== 201) {
    const code = typeof payload === 'object' && payload !== null && typeof (payload as { code?: unknown }).code === 'string'
      ? (payload as { code: string }).code
      : response.status === 429 ? 'TOO_MANY_REQUESTS' : 'SERVICE_UNAVAILABLE'
    const retryAfter = Number(response.headers.get('Retry-After'))
    return fail(code, Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined)
  }

  const tokens = parseTokens(payload)
  if (!tokens) return fail('SERVICE_UNAVAILABLE')
  try {
    await saveTokens(tokens)
  } catch {
    // [주의] 저장하지 못하면 연결 완료로 표시하지 않는다.
    return fail('STORAGE_UNAVAILABLE')
  }
  return { success: true }
}

export type PairingNotice = { title: string; lines: string[] }

export function describePairingError(error: PairingError): PairingNotice {
  switch (error.code) {
    case 'LINK_001_INVALID_FORMAT': return { title: '코드 형식이 맞지 않아요', lines: ['ABC-123 형식으로 입력해 주세요.'] }
    case 'LINK_002_EXPIRED': return { title: '코드 시간이 지났어요', lines: ['코드는 5분 동안만 쓸 수 있어요.', '데스크톱 앱에서 새 코드를 받아 입력해 주세요.'] }
    case 'LINK_003_ALREADY_USED': return { title: '이미 사용한 코드예요', lines: ['코드는 한 번만 쓸 수 있어요.', '데스크톱 앱에서 새 코드를 받아 주세요.'] }
    case 'LINK_004_ATTEMPTS_EXCEEDED': return { title: '이 코드는 더 이상 쓸 수 없어요', lines: ['3번 틀려서 코드가 잠겼어요.', '데스크톱 앱에서 새 코드를 받아 주세요.'] }
    case 'LINK_005_CANCELLED': return { title: '취소된 코드예요', lines: ['데스크톱 앱에서 새 코드를 받아 주세요.'] }
    case 'TOO_MANY_REQUESTS': return { title: '잠시 후 다시 시도해 주세요', lines: [error.retryAfterSeconds ? `${error.retryAfterSeconds}초 뒤에 다시 입력할 수 있어요.` : '입력이 너무 잦았어요.'] }
    case 'LOCAL_NETWORK_UNAVAILABLE': return { title: '서버에 연결할 수 없어요', lines: ['인터넷 연결을 확인해 주세요.'] }
    case 'EXTENSION_CONTEXT_REQUIRED': return { title: 'Chrome 확장 프로그램에서 열어 주세요', lines: ['Chrome 오른쪽 위의 FocusOn 아이콘을 눌러 연결해 주세요.'] }
    case 'STORAGE_UNAVAILABLE': return { title: '연결 정보를 저장하지 못했어요', lines: ['Chrome을 다시 연 뒤 새 코드로 연결해 주세요.'] }
    case 'SERVICE_UNAVAILABLE': return { title: '연결하지 못했어요', lines: ['잠시 후 다시 시도해 주세요.'] }
    // ponytail: 틀린 코드의 오류 code·남은 횟수는 API 명세에 아직 없다. Backend 확정 뒤 이 분기를 맞춘다.
    default: return { title: '코드가 맞지 않아요', lines: ['글자를 다시 확인해 주세요.'] }
  }
}
