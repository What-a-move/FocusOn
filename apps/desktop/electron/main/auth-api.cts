import type { GoogleAuthorizationCode } from './google-oauth.cjs'

export type AuthApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: AuthApiError }
export type TokenPair = { accessToken: string; refreshToken: string; accessTokenExpiresAt: string }
export type AuthIdentity = {
  user: { userId: string; nickname: string | null }
  onboardingStatus: 'INCOMPLETE' | 'COMPLETED'
  device: { deviceId: string; deviceType: string }
}
export type ExchangeResult = TokenPair & AuthIdentity
export type DesktopDevice = { installationId: string; deviceName: string; platform: 'MACOS' }

const SERVICE_UNAVAILABLE: AuthApiError = { code: 'SERVICE_UNAVAILABLE', retryable: true }
// [유지보수] 성공 상태인데 계약과 다른 본문은 일시 장애가 아니므로 재시도 대상에서 뺀다.
const INVALID_RESPONSE: AuthApiError = { code: 'INVALID_RESPONSE', retryable: false }
const NETWORK_UNAVAILABLE: AuthApiError = { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true }

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseTokenPair(value: unknown): TokenPair | null {
  if (!isRecord(value) || typeof value.accessToken !== 'string' || !value.accessToken
    || typeof value.refreshToken !== 'string' || !value.refreshToken
    || typeof value.accessTokenExpiresAt !== 'string') return null
  // [주의] 형식이 깨진 시각은 NaN이 되어 만료 비교를 통과해 버리므로 먼저 걸러 낸다.
  const expiresAt = Date.parse(value.accessTokenExpiresAt)
  if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) return null
  return { accessToken: value.accessToken, refreshToken: value.refreshToken, accessTokenExpiresAt: value.accessTokenExpiresAt }
}

function parseIdentity(value: unknown): AuthIdentity | null {
  if (!isRecord(value) || !isRecord(value.user) || typeof value.user.userId !== 'string'
    || !value.user.userId || (value.user.nickname !== null && typeof value.user.nickname !== 'string')
    || (value.onboardingStatus !== 'INCOMPLETE' && value.onboardingStatus !== 'COMPLETED')
    || !isRecord(value.device) || typeof value.device.deviceId !== 'string' || !value.device.deviceId
    || typeof value.device.deviceType !== 'string') return null
  return {
    user: { userId: value.user.userId, nickname: value.user.nickname },
    onboardingStatus: value.onboardingStatus,
    device: { deviceId: value.device.deviceId, deviceType: value.device.deviceType },
  }
}

function parseExchange(value: unknown): ExchangeResult | null {
  const tokens = parseTokenPair(value)
  const identity = parseIdentity(value)
  return tokens && identity ? { ...tokens, ...identity } : null
}

function parseError(value: unknown): AuthApiError {
  if (!isRecord(value) || typeof value.code !== 'string' || !value.code) return SERVICE_UNAVAILABLE
  return { code: value.code, retryable: typeof value.retryable === 'boolean' ? value.retryable : false }
}

export function resolveAuthApiBaseUrl(value: string | undefined): string | null {
  if (!value) return null
  try {
    const url = new URL(value)
    const loopback = url.hostname === '127.0.0.1' || url.hostname === 'localhost'
    if ((url.protocol !== 'https:' && !(loopback && url.protocol === 'http:'))
      || url.username || url.password || url.search || url.hash || url.pathname !== '/') return null
    return url.origin
  } catch {
    return null
  }
}

export class AuthApiClient {
  constructor(private readonly baseUrl: string, private readonly request: typeof fetch = fetch) {}

  // [유지보수] 인증 API와 이후 인증이 필요한 API가 같은 요청·오류 해석 규칙을 쓰도록 공개한다. 토큰 처리는 AuthService가 맡는다.
  async call<T>(
    path: string,
    method: 'GET' | 'POST' | 'PUT',
    expectedStatus: number,
    parse: (value: unknown) => T | null,
    body?: object,
    accessToken?: string,
    extraHeaders: Record<string, string> = {},
  ): Promise<ApiResult<T>> {
    let response: Response
    try {
      response = await this.request(`${this.baseUrl}${path}`, {
        method,
        headers: {
          ...(body ? { 'Content-Type': 'application/json' } : {}),
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          ...extraHeaders,
        },
        body: body ? JSON.stringify(body) : undefined,
        redirect: 'manual',
        signal: AbortSignal.timeout(10_000),
      })
    } catch {
      return { success: false, error: NETWORK_UNAVAILABLE }
    }

    // [주의] 로그인 요청의 redirect를 자동 추적하면 code나 token이 다른 주소로 넘어갈 수 있다.
    if (response.status !== expectedStatus) {
      try { return { success: false, error: parseError(await response.json()) } }
      catch { return { success: false, error: SERVICE_UNAVAILABLE } }
    }
    try {
      const payload: unknown = await response.json()
      const data = isRecord(payload) ? parse(payload.data) : null
      return data ? { success: true, data } : { success: false, error: INVALID_RESPONSE }
    } catch {
      return { success: false, error: INVALID_RESPONSE }
    }
  }

  exchange(code: GoogleAuthorizationCode, device: DesktopDevice): Promise<ApiResult<ExchangeResult>> {
    return this.call('/api/v1/auth/google/exchange', 'POST', 201, parseExchange, { ...code, device })
  }

  refresh(refreshToken: string): Promise<ApiResult<TokenPair>> {
    // [주의] Refresh API는 Bearer 헤더 없이 JSON body의 refreshToken으로만 호출한다.
    return this.call('/api/v1/auth/refresh', 'POST', 200, parseTokenPair, { refreshToken })
  }

  me(accessToken: string): Promise<ApiResult<AuthIdentity>> {
    return this.call('/api/v1/auth/me', 'GET', 200, parseIdentity, undefined, accessToken)
  }

  async logout(accessToken: string): Promise<ApiResult<{ loggedOut: true }>> {
    try {
      const response = await this.request(`${this.baseUrl}/api/v1/auth/logout`, {
        method: 'POST', headers: { Authorization: `Bearer ${accessToken}` }, redirect: 'manual', signal: AbortSignal.timeout(10_000),
      })
      if (response.status === 204 || response.status === 401) return { success: true, data: { loggedOut: true } }
      try { return { success: false, error: parseError(await response.json()) } }
      catch { return { success: false, error: SERVICE_UNAVAILABLE } }
    } catch {
      return { success: false, error: NETWORK_UNAVAILABLE }
    }
  }
}
