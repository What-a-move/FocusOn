// Extension 인증 요청 (Desktop AuthService와 같은 규칙, 결정 011).
// - Access Token이 만료되기 30초 전이면 먼저 갱신한다. 서버가 AUTH_ACCESS_TOKEN_EXPIRED를 주면 한 번만 갱신하고 원 요청을 한 번만 다시 보낸다.
// - 갱신은 동시에 여러 번 부르지 않는다(single-flight). Refresh Token은 갱신할 때마다 새 값으로 바뀐다(rotation).
// - 재로그인이 필요하거나 연결이 해제됐으면 로컬 토큰을 지우고 RELINK_REQUIRED로 알린다(새 연결 코드로만 복구).
// - GET은 네트워크·503·504에서 1초 → 3초로 두 번까지 다시 보낸다. DELETE 같은 변경 요청은 자동으로 다시 보내지 않는다.
// [주의] 이 파일은 node --test로 직접 실행하므로 상대 import 없이 필요한 것을 주입받는다.

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type StoredAccess = { accessToken: string; expiresAt: string }
export type TokenStore = {
  getAccess(): Promise<StoredAccess | null>
  getRefreshToken(): Promise<string | null>
  saveRotation(tokens: { accessToken: string; refreshToken: string; accessTokenExpiresAt: string }): Promise<void>
  clear(): Promise<void>
}
type Deps = { baseUrl: string | null; store: TokenStore; request?: typeof fetch; sleep?: (ms: number) => Promise<void>; now?: () => number }

const EXPIRY_MARGIN_MS = 30_000
const GET_RETRY_DELAYS_MS = [1000, 3000]
const RELINK_CODES = new Set(['AUTH_RELOGIN_REQUIRED', 'AUTH_TOKEN_REUSE_DETECTED', 'LINK_TOKEN_REVOKED'])
const TRANSIENT_CODES = new Set(['LOCAL_NETWORK_UNAVAILABLE', 'SERVICE_UNAVAILABLE', 'REQUEST_TIMEOUT'])
const fail = (code: string, retryable = false): { success: false; error: ApiError } => ({ success: false, error: { code, retryable } })
const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)

export function createApiClient({ baseUrl, store, request = fetch, sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)), now = Date.now }: Deps) {
  let refreshing: Promise<ApiResult<string>> | null = null

  async function send(path: string, method: 'GET' | 'DELETE' | 'POST', token?: string, body?: object): Promise<{ status: number; payload: unknown } | null> {
    try {
      const response = await request(`${baseUrl}${path}`, {
        method,
        headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
        body: body ? JSON.stringify(body) : undefined,
        redirect: 'manual',
        signal: AbortSignal.timeout(10_000),
      })
      const payload: unknown = response.status === 204 ? null : await response.json().catch(() => null)
      return { status: response.status, payload }
    } catch {
      return null
    }
  }

  const errorCode = (status: number, payload: unknown) =>
    isRecord(payload) && typeof payload.code === 'string' ? payload.code : status === 503 ? 'SERVICE_UNAVAILABLE' : status === 504 ? 'REQUEST_TIMEOUT' : 'SERVICE_UNAVAILABLE'

  async function relink(): Promise<{ success: false; error: ApiError }> {
    // [주의] 갱신이 막힌 토큰을 계속 쓰지 않도록 지운다. 사용자는 Desktop에서 새 연결 코드를 받아야 한다.
    await store.clear().catch(() => {})
    return fail('RELINK_REQUIRED')
  }

  function refresh(): Promise<ApiResult<string>> {
    refreshing ??= (async (): Promise<ApiResult<string>> => {
      const refreshToken = await store.getRefreshToken()
      if (!refreshToken) return relink()
      const result = await send('/api/v1/auth/refresh', 'POST', undefined, { refreshToken })
      if (!result) return fail('LOCAL_NETWORK_UNAVAILABLE', true)
      if (result.status !== 200) {
        const code = errorCode(result.status, result.payload)
        return RELINK_CODES.has(code) ? relink() : fail(code, true)
      }
      const data = isRecord(result.payload) ? result.payload.data : null
      if (!isRecord(data) || typeof data.accessToken !== 'string' || typeof data.refreshToken !== 'string' || typeof data.accessTokenExpiresAt !== 'string'
        || !Number.isFinite(Date.parse(data.accessTokenExpiresAt))) return fail('INVALID_RESPONSE')
      await store.saveRotation({ accessToken: data.accessToken, refreshToken: data.refreshToken, accessTokenExpiresAt: data.accessTokenExpiresAt })
      return { success: true, data: data.accessToken }
    })().finally(() => { refreshing = null })
    return refreshing
  }

  async function accessToken(): Promise<ApiResult<string>> {
    const saved = await store.getAccess()
    if (saved && Date.parse(saved.expiresAt) - EXPIRY_MARGIN_MS > now()) return { success: true, data: saved.accessToken }
    return refresh()
  }

  /** 인증이 필요한 요청. parse가 null을 돌려주면 계약 위반(INVALID_RESPONSE)이다. 204면 parse(null)로 '없음'을 판단한다. */
  async function authorized<T>(path: string, method: 'GET' | 'DELETE', expected: number, parse: (data: unknown) => T | null): Promise<ApiResult<T>> {
    if (!baseUrl) return fail('SERVICE_UNAVAILABLE', false)
    const once = async (token: string): Promise<ApiResult<T> | { expired: true }> => {
      let result = await send(path, method, token)
      for (const delay of method === 'GET' ? GET_RETRY_DELAYS_MS : []) {
        const transient = !result || (result.status !== expected && result.status !== 204 && TRANSIENT_CODES.has(errorCode(result.status, result.payload)))
        if (!transient) break
        await sleep(delay)
        result = await send(path, method, token)
      }
      if (!result) return fail('LOCAL_NETWORK_UNAVAILABLE', true)
      if (result.status === 204) { const data = parse(null); return data === null ? fail('INVALID_RESPONSE') : { success: true, data } }
      if (result.status !== expected) {
        const code = errorCode(result.status, result.payload)
        if (code === 'AUTH_ACCESS_TOKEN_EXPIRED') return { expired: true }
        return RELINK_CODES.has(code) ? relink() : fail(code, TRANSIENT_CODES.has(code))
      }
      const data = parse(isRecord(result.payload) ? result.payload.data : null)
      return data === null ? fail('INVALID_RESPONSE') : { success: true, data }
    }

    const token = await accessToken()
    if (!token.success) return token
    const first = await once(token.data)
    if (!('expired' in first)) return first
    const renewed = await refresh()
    if (!renewed.success) return renewed
    const second = await once(renewed.data)
    return 'expired' in second ? fail('AUTH_ACCESS_TOKEN_EXPIRED', true) : second
  }

  return { authorized }
}
