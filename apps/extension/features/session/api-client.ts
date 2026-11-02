// Extension 인증 요청 (Desktop AuthService와 같은 규칙, 결정 011).
// - Access Token이 만료되기 30초 전이면 먼저 갱신한다. 서버가 AUTH_ACCESS_TOKEN_EXPIRED를 주면 한 번만 갱신하고 원 요청을 한 번만 다시 보낸다.
// - 갱신은 동시에 여러 번 부르지 않는다(single-flight). Refresh Token은 갱신할 때마다 새 값으로 바뀐다(rotation).
// - 재로그인이 필요하거나 연결이 해제됐으면 로컬 토큰을 지우고 RELINK_REQUIRED로 알린다(새 연결 코드로만 복구).
// - GET은 네트워크·503·504에서 1초 → 3초로 두 번까지 다시 보낸다. POST·DELETE 같은 변경 요청은 자동으로 다시 보내지 않는다.
// [주의] 이 파일은 node --test로 직접 실행하므로 상대 import 없이 필요한 것을 주입받는다.

import type { ErrorClass, Logger } from './logger'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type StoredAccess = { accessToken: string; expiresAt: string }
export type TokenStore = {
  getAccess(): Promise<StoredAccess | null>
  getRefreshToken(): Promise<string | null>
  saveRotation(tokens: { accessToken: string; refreshToken: string; accessTokenExpiresAt: string }): Promise<void>
  clear(): Promise<void>
}
type Method = 'GET' | 'POST' | 'DELETE'
/**
 * body는 JSON으로 보낸다. headers에는 X-Device-Id처럼 API가 요구하는 값만 넣는다.
 * traceId: 로그를 서버와 잇는 X-Trace-Id(없으면 요청마다 새로 만든다, analysis-runs는 clientRunId).
 * logFields: 요청 로그에 함께 남길 값. 로거가 허용 필드만 남기고 제목·본문은 글자 수로 바꾼다.
 */
export type RequestOptions = { body?: object; headers?: Record<string, string>; traceId?: string; logFields?: Record<string, unknown> }
type Deps = { baseUrl: string | null; store: TokenStore; request?: typeof fetch; sleep?: (ms: number) => Promise<void>; now?: () => number; log?: Logger; uuid?: () => string }
type Trace = { traceId: string; fields: Record<string, unknown> }

const EXPIRY_MARGIN_MS = 30_000
const GET_RETRY_DELAYS_MS = [1000, 3000]
const RELINK_CODES = new Set(['AUTH_RELOGIN_REQUIRED', 'AUTH_TOKEN_REUSE_DETECTED', 'LINK_TOKEN_REVOKED'])
const TRANSIENT_CODES = new Set(['LOCAL_NETWORK_UNAVAILABLE', 'SERVICE_UNAVAILABLE', 'REQUEST_TIMEOUT'])
const fail = (code: string, retryable = false): { success: false; error: ApiError } => ({ success: false, error: { code, retryable } })
const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)

export function createApiClient({ baseUrl, store, request = fetch, sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)), now = Date.now, log = () => {}, uuid = () => crypto.randomUUID() }: Deps) {
  let refreshing: Promise<ApiResult<string>> | null = null

  // 요청 한 번의 OUT·IN·fail을 같은 traceId로 남긴다. analysis-runs는 판정 요청이라 decision.*, 나머지는 api.*.
  async function send(path: string, method: Method, token?: string, body?: object, headers: Record<string, string> = {}, trace: Trace = { traceId: uuid(), fields: {} }): Promise<{ status: number; payload: unknown } | null> {
    const kind = path.endsWith('/analysis-runs') ? 'decision' : 'api'
    const base = { ...trace.fields, traceId: trace.traceId, method, path }
    const started = performance.now()
    const durMs = () => Math.round(performance.now() - started)
    log('INFO', `${kind}.request.out`, base)
    try {
      const response = await request(`${baseUrl}${path}`, {
        method,
        headers: { ...headers, ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}), 'X-Trace-Id': trace.traceId },
        body: body ? JSON.stringify(body) : undefined,
        redirect: 'manual',
        signal: AbortSignal.timeout(10_000),
      })
      const payload: unknown = response.status === 204 ? null : await response.json().catch(() => null)
      if (response.status >= 400) {
        const code = errorCode(response.status, payload)
        // 서버가 거절한 응답(4xx)은 정상 흐름에서도 생겨 WARN, 서버 오류는 ERROR로 남긴다.
        log(response.status < 500 ? 'WARN' : 'ERROR', `${kind}.request.fail`, { ...base, status: response.status, code, errorClass: classOf(code, response.status), durMs: durMs() })
      } else {
        // 판정 응답이면 결과값(relation)만 함께 남긴다. 본문의 다른 값은 로거가 버린다.
        const data = isRecord(payload) && isRecord(payload.data) ? payload.data : null
        log('INFO', `${kind}.response.in`, { ...base, status: response.status, relation: data?.relation, durMs: durMs() })
      }
      return { status: response.status, payload }
    } catch (error) {
      const errorClass: ErrorClass = error instanceof Error && error.name === 'TimeoutError' ? 'timeout' : 'network'
      log('ERROR', `${kind}.request.fail`, { ...base, code: 'LOCAL_NETWORK_UNAVAILABLE', errorClass, durMs: durMs() })
      return null
    }
  }

  const classOf = (code: string, status: number): ErrorClass =>
    status === 401 || status === 403 ? 'auth' : status === 429 ? 'rate_limit' : code === 'INVALID_RESPONSE' ? 'bad_response' : 'unknown'

  const errorCode = (status: number, payload: unknown) =>
    isRecord(payload) && typeof payload.code === 'string' ? payload.code : status === 503 ? 'SERVICE_UNAVAILABLE' : status === 504 ? 'REQUEST_TIMEOUT' : 'REQUEST_FAILED'

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
  function authorized<T>(path: string, method: Method, expected: number, parse: (data: unknown) => T | null, options: RequestOptions = {}): Promise<ApiResult<T>> {
    if (!baseUrl) return Promise.resolve(fail('SERVICE_UNAVAILABLE', false))
    // [주의] chrome.storage가 실패해도 reject하지 않고 결과로 돌려준다. 팝업이 로딩에 멈추지 않게 한다.
    return run(path, method, expected, parse, options).catch(() => fail('LOCAL_STORAGE_UNAVAILABLE', true))
  }

  async function run<T>(path: string, method: Method, expected: number, parse: (data: unknown) => T | null, { body, headers, traceId, logFields = {} }: RequestOptions): Promise<ApiResult<T>> {
    // [주의] 재시도와 토큰 갱신 뒤 재전송은 같은 요청이라 traceId를 바꾸지 않는다.
    const trace: Trace = { traceId: traceId ?? uuid(), fields: logFields }
    const invalid = () => {
      log('ERROR', `${path.endsWith('/analysis-runs') ? 'decision' : 'api'}.request.fail`, { ...trace.fields, traceId: trace.traceId, method, path, code: 'INVALID_RESPONSE', errorClass: 'bad_response' })
      return fail('INVALID_RESPONSE')
    }
    const once = async (token: string): Promise<ApiResult<T> | { expired: true }> => {
      let result = await send(path, method, token, body, headers, trace)
      for (const delay of method === 'GET' ? GET_RETRY_DELAYS_MS : []) {
        const transient = !result || (result.status !== expected && result.status !== 204 && TRANSIENT_CODES.has(errorCode(result.status, result.payload)))
        if (!transient) break
        await sleep(delay)
        result = await send(path, method, token, body, headers, trace)
      }
      if (!result) return fail('LOCAL_NETWORK_UNAVAILABLE', true)
      if (result.status === 204) { const data = parse(null); return data === null ? invalid() : { success: true, data } }
      if (result.status !== expected) {
        const code = errorCode(result.status, result.payload)
        if (code === 'AUTH_ACCESS_TOKEN_EXPIRED') return { expired: true }
        return RELINK_CODES.has(code) ? relink() : fail(code, TRANSIENT_CODES.has(code))
      }
      const data = parse(isRecord(result.payload) ? result.payload.data : null)
      return data === null ? invalid() : { success: true, data }
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
