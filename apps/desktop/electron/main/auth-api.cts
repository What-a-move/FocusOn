import { randomUUID } from 'node:crypto'
import type { GoogleAuthorizationCode } from './google-oauth.cjs'
import { errorClassOf, log, type ErrorClass } from './logger.cjs'

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

// 서버 기준으로도 남은 수명이 없어 보이는 토큰은 이 시간만 쓰고 다시 갱신한다. AuthService의 갱신 여유(30초)보다 길어야 한다.
const STALE_TOKEN_LIFETIME_MS = 60_000

/**
 * 만료 시각은 서버 시계 기준이라 로컬 시계와 바로 비교하지 않는다. 응답 Date 헤더(serverNow)로 남은 수명을 구해
 * 로컬 시각으로 옮겨 돌려준다. Date 헤더가 없으면 로컬 시계를 서버 시계로 본다.
 * [주의] 서버는 응답을 보낼 때 이미 refresh token을 회전했다. 형식이 맞는 응답을 시계 차이로 버리면 다음 갱신이
 * 예전 token을 보내 AUTH_TOKEN_REUSE_DETECTED로 강제 로그아웃된다 (auth-refresh-clock-skew-ERROR.md).
 */
function parseTokenPair(value: unknown, serverNow = Number.NaN): TokenPair | null {
  if (!isRecord(value) || typeof value.accessToken !== 'string' || !value.accessToken
    || typeof value.refreshToken !== 'string' || !value.refreshToken
    || typeof value.accessTokenExpiresAt !== 'string') return null
  const expiresAt = Date.parse(value.accessTokenExpiresAt)
  if (!Number.isFinite(expiresAt)) return null
  const now = Date.now()
  const remaining = expiresAt - (Number.isFinite(serverNow) ? serverNow : now)
  const localExpiresAt = now + (remaining > 0 ? remaining : STALE_TOKEN_LIFETIME_MS)
  return { accessToken: value.accessToken, refreshToken: value.refreshToken, accessTokenExpiresAt: new Date(localExpiresAt).toISOString() }
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

function parseExchange(value: unknown, serverNow?: number): ExchangeResult | null {
  const tokens = parseTokenPair(value, serverNow)
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
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    expectedStatus: number,
    parse: (value: unknown, serverNow?: number) => T | null,
    body?: object,
    accessToken?: string,
    extraHeaders: Record<string, string> = {},
    logFields: Record<string, unknown> = {},
  ): Promise<ApiResult<T>> {
    // 요청을 시작하는 쪽이 traceId를 정했으면(재시도·analysis-runs) 그대로 쓰고, 없으면 여기서 만든다.
    const traceId = extraHeaders['X-Trace-Id'] ?? randomUUID()
    const trace = traceRequest(path, method, traceId, logFields)
    let response: Response
    try {
      response = await this.request(`${this.baseUrl}${path}`, {
        method,
        headers: {
          ...(body ? { 'Content-Type': 'application/json' } : {}),
          ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
          ...extraHeaders,
          'X-Trace-Id': traceId,
        },
        body: body ? JSON.stringify(body) : undefined,
        redirect: 'manual',
        signal: AbortSignal.timeout(10_000),
      })
    } catch (error) {
      return trace.fail(NETWORK_UNAVAILABLE, undefined, isTimeout(error) ? 'timeout' : 'network')
    }

    // 200을 기대한 조회가 204(본문 없음)를 받으면 parse(null)로 '없음'을 판단하게 한다 (예: GET /sessions/active).
    if (response.status === 204 && expectedStatus === 200) {
      const data = parse(null)
      return data ? trace.ok({ success: true, data }, 204) : trace.fail(INVALID_RESPONSE, 204)
    }
    // [주의] 로그인 요청의 redirect를 자동 추적하면 code나 token이 다른 주소로 넘어갈 수 있다.
    if (response.status !== expectedStatus) {
      try { return trace.fail(parseError(await response.json()), response.status) }
      catch { return trace.fail(SERVICE_UNAVAILABLE, response.status) }
    }
    // 본문 없는 성공(204)을 기대한 요청(예: DELETE)은 parse(null)로 결과를 만든다.
    if (expectedStatus === 204) {
      const data = parse(null)
      return data ? trace.ok({ success: true, data }, 204) : trace.fail(INVALID_RESPONSE, 204)
    }
    try {
      const payload: unknown = await response.json()
      // 응답 Date 헤더는 서버 시계다. 토큰 파서가 만료 시각을 로컬 시각으로 옮길 때 쓴다.
      const serverNow = Date.parse(response.headers.get('date') ?? '')
      const data = isRecord(payload) ? parse(payload.data, serverNow) : null
      return data ? trace.ok({ success: true, data }, response.status) : trace.fail(INVALID_RESPONSE, response.status)
    } catch {
      return trace.fail(INVALID_RESPONSE, response.status)
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
    const trace = traceRequest('/api/v1/auth/logout', 'POST', randomUUID())
    try {
      const response = await this.request(`${this.baseUrl}/api/v1/auth/logout`, {
        method: 'POST', headers: { Authorization: `Bearer ${accessToken}`, 'X-Trace-Id': trace.traceId }, redirect: 'manual', signal: AbortSignal.timeout(10_000),
      })
      // 만료 401은 서버 폐기가 실행된 것이 아니다. 오류 코드를 서비스에 전달해 갱신 후 재시도한다.
      if (response.status === 204) return trace.ok({ success: true, data: { loggedOut: true } }, 204)
      try { return trace.fail(parseError(await response.json()), response.status) }
      catch { return trace.fail(SERVICE_UNAVAILABLE, response.status) }
    } catch (error) {
      return trace.fail(NETWORK_UNAVAILABLE, undefined, isTimeout(error) ? 'timeout' : 'network')
    }
  }
}

function isTimeout(error: unknown): boolean {
  return error instanceof Error && error.name === 'TimeoutError'
}

/**
 * 요청 하나의 OUT·IN·fail을 같은 traceId로 남긴다 (Notion 모듈 연동 로깅 규칙 3.4).
 * analysis-runs는 판정 요청이라 decision.*, 나머지는 api.* 이름을 쓴다.
 */
function traceRequest(path: string, method: string, traceId: string, fields: Record<string, unknown> = {}) {
  const kind = path.endsWith('/analysis-runs') ? 'decision' : 'api'
  const started = performance.now()
  const base = { ...fields, component: 'api', traceId, method, path }
  log('INFO', `${kind}.request.out`, base)
  const durMs = () => Math.round(performance.now() - started)
  return {
    traceId,
    ok<T>(result: ApiResult<T>, status: number): ApiResult<T> {
      // 판정 응답이면 결과값(relation)만 함께 남긴다. 본문의 다른 값은 로거가 버린다.
      const relation = result.success && isRecord(result.data) ? result.data.relation : undefined
      log('INFO', `${kind}.response.in`, { ...base, status, relation, durMs: durMs() })
      return result
    },
    fail<T>(error: AuthApiError, status?: number, errorClass: ErrorClass = errorClassOf(error.code, status)): ApiResult<T> {
      // 서버가 거절한 응답(4xx)은 정상 흐름에서도 생겨 WARN, 연결 실패·서버 오류는 ERROR로 남긴다.
      const level = status !== undefined && status < 500 && error.code !== 'INVALID_RESPONSE' ? 'WARN' : 'ERROR'
      log(level, `${kind}.request.fail`, { ...base, status, code: error.code, errorClass, durMs: durMs() })
      return { success: false, error }
    },
  }
}
