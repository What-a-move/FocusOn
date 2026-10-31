import { randomUUID } from 'node:crypto'
import type { AuthSessionSnapshot, IpcResult } from '../shared/auth-contract.cjs'
import type { AuthApiError, AuthApiClient, TokenPair, DesktopDevice, ApiResult } from './auth-api.cjs'
import type { RefreshTokenVault } from './auth-keychain.cjs'
import type { GoogleOAuthOutcome } from './google-oauth.cjs'
import { AuthSessionStore } from './auth-ipc.cjs'

const SERVICE_UNAVAILABLE: AuthApiError = { code: 'SERVICE_UNAVAILABLE', retryable: false }
// [유지보수] 화면이 원인을 알려 줄 수 있게 서버 장애와 Mac 쪽 실패(Keychain·Google 로그인 창)를 나눈다.
const KEYCHAIN_UNAVAILABLE: AuthApiError = { code: 'LOCAL_KEYCHAIN_UNAVAILABLE', retryable: true }
const GOOGLE_LOGIN_FAILED: AuthApiError = { code: 'LOCAL_GOOGLE_LOGIN_FAILED', retryable: true }
// 서버가 Access Token 만료를 알릴 때 쓰는 공통 code (API_CONTRACT 공통 오류 코드)
const ACCESS_TOKEN_EXPIRED = 'AUTH_ACCESS_TOKEN_EXPIRED'
// 만료 직전 토큰으로 요청하다 실패하지 않도록 30초 여유를 두고 미리 갱신한다.
const EXPIRY_MARGIN_MS = 30_000
// 공통 Error Code 정책 4.3: GET은 네트워크 오류·503·504에서 1초 → 3초 간격으로 최대 2회 재시도한다.
// 상태를 바꾸는 요청(PUT·POST)은 자동 재전송하지 않는다.
const GET_RETRY_DELAYS_MS = [1_000, 3_000]
const TRANSIENT_CODES = new Set(['LOCAL_NETWORK_UNAVAILABLE', 'SERVICE_UNAVAILABLE', 'REQUEST_TIMEOUT'])
const wait = (ms: number) => new Promise<void>((resolve) => { setTimeout(resolve, ms) })

export type AuthorizedRequest<T> = {
  path: string
  method: 'GET' | 'POST' | 'PUT'
  expectedStatus: number
  parse: (value: unknown) => T | null
  body?: object
  /** true면 로그인 때 받은 Desktop deviceId를 X-Device-Id 헤더로 보낸다. */
  withDeviceId?: boolean
  /** 요청별 추가 헤더 (예: 세션 명령의 Idempotency-Key). 토큰 갱신 뒤 한 번 다시 보낼 때도 같은 값을 쓴다. */
  headers?: Record<string, string>
  /** 로그를 서버와 잇는 X-Trace-Id. 없으면 요청마다 새로 만든다 (analysis-runs는 clientRunId). */
  traceId?: string
  /** 요청 로그에 함께 남길 값. 로거가 허용 필드만 남기고 제목·본문은 글자 수로 바꾼다. */
  logFields?: Record<string, unknown>
}
const RELOGIN_REQUIRED: AuthApiError = { code: 'AUTH_RELOGIN_REQUIRED', retryable: false }

function isReloginError(code: string): boolean {
  // [주의] Google 재인증이 필요한 경우도 저장된 토큰으로 복구할 수 없으므로 다시 로그인하게 한다.
  return code === 'AUTH_RELOGIN_REQUIRED' || code === 'AUTH_TOKEN_REUSE_DETECTED' || code === 'AUTH_OAUTH_REAUTH_REQUIRED'
}

export class AuthService {
  private accessToken: string | null = null
  private accessTokenExpiresAt = 0
  private deviceId: string | null = null
  private refreshPromise: Promise<ApiResult<TokenPair>> | null = null
  private refreshGeneration = -1
  private restoring: Promise<void> = Promise.resolve()
  private restoringInFlight = false
  private generation = 0
  private credentialGeneration = -1
  private authOperations: Promise<void> = Promise.resolve()

  constructor(
    private readonly store: AuthSessionStore,
    private readonly api: AuthApiClient,
    private readonly vault: RefreshTokenVault,
    private readonly getDevice: () => Promise<DesktopDevice>,
    private readonly sleep: (ms: number) => Promise<void> = wait,
  ) {}

  private publish(status: AuthSessionSnapshot['status'], error: AuthApiError | null = null, data?: {
    user: NonNullable<AuthSessionSnapshot['user']>
    onboardingStatus: NonNullable<AuthSessionSnapshot['onboardingStatus']>
  }): void {
    this.store.publish({ status, user: data?.user ?? null, onboardingStatus: data?.onboardingStatus ?? null, error })
  }

  private remember(tokens: TokenPair, generation: number): void {
    // [주의] Access Token은 Main 메모리에만 둔다. Renderer에는 상태 요약만 전달한다.
    this.accessToken = tokens.accessToken
    this.accessTokenExpiresAt = Date.parse(tokens.accessTokenExpiresAt)
    this.credentialGeneration = generation
  }

  private forget(): void {
    this.accessToken = null
    this.accessTokenExpiresAt = 0
    this.deviceId = null
    this.credentialGeneration = -1
  }

  // 토큰을 소비·저장·삭제하는 작업만 직렬화한다. 일반 API 전체를 줄 세우지는 않는다.
  // 이전 작업의 Keychain 쓰기/cleanup이 끝나야 새 로그인이나 logout이 저장소를 바꿀 수 있다.
  private runAuthOperation<T>(operation: () => Promise<T>): Promise<T> {
    const work = this.authOperations.then(operation)
    this.authOperations = work.then(() => {}, () => {})
    return work
  }

  private isCurrent(generation: number): boolean {
    return generation === this.generation
  }

  // 이전 계정의 요청은 실패로 돌려주되, 새 계정의 자격 증명과 화면은 정리하지 않는다.
  private staleRequest<T>(): ApiResult<T> {
    return { success: false, error: RELOGIN_REQUIRED }
  }

  /**
   * 인증이 필요한 Backend 요청. 만료가 가까우면 먼저 refresh하고, 서버가 만료(401)를 알리면
   * single-flight refresh 1회 뒤 원 요청을 1회만 다시 보낸다 (AUTH_SESSION_FLOW.md).
   * refresh가 재로그인 오류면 저장된 토큰을 지우고 SIGNED_OUT으로 전환한다.
   */
  async authorizedRequest<T>(request: AuthorizedRequest<T>): Promise<ApiResult<T>> {
    const generation = this.generation
    await this.ready()
    if (!this.isCurrent(generation)) return this.staleRequest()
    if (this.credentialGeneration !== generation) return this.staleRequest()
    if (!this.accessToken || this.accessTokenExpiresAt - EXPIRY_MARGIN_MS <= Date.now()) {
      const refreshed = await this.refresh()
      if (!refreshed.success) return refreshed
    }
    if (!this.isCurrent(generation)) return this.staleRequest()
    if (request.withDeviceId && !this.deviceId) return { success: false, error: RELOGIN_REQUIRED }

    // [주의] 재시도와 토큰 갱신 뒤 재전송은 같은 요청이라 traceId를 바꾸지 않는다.
    const traceId = request.traceId ?? randomUUID()
    const send = async (): Promise<ApiResult<T>> => {
      if (!this.isCurrent(generation)) return this.staleRequest()
      const result = await this.api.call(
        request.path, request.method, request.expectedStatus, request.parse, request.body,
        this.accessToken ?? undefined, { ...request.headers, ...(request.withDeviceId && this.deviceId ? { 'X-Device-Id': this.deviceId } : {}), 'X-Trace-Id': traceId },
        request.logFields,
      )
      return this.isCurrent(generation) ? result : this.staleRequest()
    }
    const sendWithRetry = async () => {
      let result = await send()
      if (request.method !== 'GET') return result
      for (const delay of GET_RETRY_DELAYS_MS) {
        if (result.success || !TRANSIENT_CODES.has(result.error.code)) return result
        await this.sleep(delay)
        if (!this.isCurrent(generation)) return this.staleRequest<T>()
        result = await send()
      }
      return result
    }

    const first = await sendWithRetry()
    if (!this.isCurrent(generation)) return this.staleRequest()
    if (first.success || first.error.code !== ACCESS_TOKEN_EXPIRED) {
      return this.finishRequest(first, generation)
    }
    const refreshed = await this.refresh()
    if (!refreshed.success) return refreshed
    return this.finishRequest(await sendWithRetry(), generation)
  }

  private async finishRequest<T>(result: ApiResult<T>, generation: number): Promise<ApiResult<T>> {
    if (!this.isCurrent(generation)) return this.staleRequest()
    if (!result.success && isReloginError(result.error.code)) {
      await this.runAuthOperation(() => this.clearSession(generation, result.error))
    }
    return result
  }

  // 프로필 저장 뒤 화면의 닉네임도 서버가 확정한 값으로 바꾼다.
  updateNickname(nickname: string): void {
    const { revision: _revision, ...snapshot } = this.store.getSnapshot()
    if (snapshot.status !== 'SIGNED_IN' || !snapshot.user) return
    this.store.publish({ ...snapshot, user: { ...snapshot.user, nickname } })
  }

  // 서버 onboarding-readiness가 확정한 완료 상태를 화면 상태에도 반영한다. 반영하지 않으면 홈과 온보딩을 오갈 수 있다.
  updateOnboardingStatus(onboardingStatus: 'INCOMPLETE' | 'COMPLETED'): void {
    const { revision: _revision, ...snapshot } = this.store.getSnapshot()
    if (snapshot.status !== 'SIGNED_IN' || !snapshot.user || snapshot.onboardingStatus === onboardingStatus) return
    this.store.publish({ ...snapshot, onboardingStatus })
  }

  currentNickname(): string | null {
    return this.store.getSnapshot().user?.nickname ?? null
  }

  restore(): Promise<void> {
    if (this.restoringInFlight) return this.restoring
    this.restoringInFlight = true
    this.restoring = this.restoreSession(this.generation).finally(() => { this.restoringInFlight = false })
    return this.restoring
  }

  ready(): Promise<void> {
    return this.restoring
  }

  private async restoreSession(generation: number): Promise<void> {
    try {
      // 복구도 같은 회전을 공유하고, Keychain 읽기부터 토큰 큐 안에서 수행한다.
      // 처음 설치해 저장된 token이 없으면 오류 안내 없이 SIGNED_OUT으로 시작한다.
      const rotated = await this.refreshTokens(true)
      if (!this.isCurrent(generation)) return
      if (!rotated.success) {
        this.publish(isReloginError(rotated.error.code) ? 'SIGNED_OUT' : 'OFFLINE', rotated.error)
        return
      }

      const identity = await this.api.me(rotated.data.accessToken)
      if (!this.isCurrent(generation)) return
      if (!identity.success) {
        if (isReloginError(identity.error.code)) {
          await this.runAuthOperation(() => this.clearSession(generation, identity.error))
        } else {
          this.publish('OFFLINE', identity.error)
        }
        return
      }
      this.deviceId = identity.data.device.deviceId
      this.publish('SIGNED_IN', null, identity.data)
    } catch {
      // [주의] Keychain이 잠겼다면 토큰이 없다고 단정하거나 파일 저장소로 우회하지 않는다.
      if (this.isCurrent(generation)) this.publish('OFFLINE', KEYCHAIN_UNAVAILABLE)
    }
  }

  // runAuthOperation 안에서만 호출한다. logout 내부 갱신은 같은 큐에 다시 들어가지 않는다.
  private async rotateRefresh(generation: number, forLogout = false, silentMissing = false): Promise<ApiResult<TokenPair>> {
    if (!forLogout && !this.isCurrent(generation)) return this.staleRequest()
    const refreshToken = await this.vault.read()
    if (!forLogout && !this.isCurrent(generation)) return this.staleRequest()
    if (!refreshToken) {
      if (!forLogout) await this.clearSession(generation, silentMissing ? null : RELOGIN_REQUIRED, false)
      return { success: false, error: RELOGIN_REQUIRED }
    }
    const result = await this.api.refresh(refreshToken)
    if (!result.success) {
      if (!forLogout && !this.isCurrent(generation)) return this.staleRequest()
      if (!forLogout && isReloginError(result.error.code)) await this.clearSession(generation, result.error)
      return result
    }
    // [주의] 서버가 기존 Refresh Token을 폐기하므로 새 토큰을 먼저 Keychain에 저장한다.
    try {
      await this.vault.write(result.data.refreshToken)
      this.remember(result.data, generation)
      // logout은 이 저장을 기다린 뒤 삭제하고, 새 로그인도 같은 큐에서 뒤이어 실행한다.
      return forLogout || this.isCurrent(generation) ? result : this.staleRequest()
    } catch {
      this.forget()
      if (this.isCurrent(generation)) this.publish('OFFLINE', KEYCHAIN_UNAVAILABLE)
      return { success: false, error: KEYCHAIN_UNAVAILABLE }
    }
  }

  refresh(): Promise<ApiResult<TokenPair>> {
    return this.refreshTokens()
  }

  private refreshTokens(silentMissing = false): Promise<ApiResult<TokenPair>> {
    const generation = this.generation
    if (!this.refreshPromise || this.refreshGeneration !== generation) {
      const work = this.runAuthOperation(async (): Promise<ApiResult<TokenPair>> => {
        try { return await this.rotateRefresh(generation, false, silentMissing) }
        catch {
          // Keychain 조회 실패도 화면을 복구 가능한 상태로 바꾼다. 새 로그인에는 적용하지 않는다.
          if (this.isCurrent(generation)) {
            this.forget()
            this.publish('OFFLINE', KEYCHAIN_UNAVAILABLE)
          }
          return { success: false, error: KEYCHAIN_UNAVAILABLE }
        }
      })
      this.refreshPromise = work
      this.refreshGeneration = generation
      void work.finally(() => {
        if (this.refreshPromise === work) this.refreshPromise = null
      })
    }
    return this.refreshPromise
  }

  // 다시 로그인을 시작하면 진행 중이던 교환 결과는 버린다 (늦게 와도 저장·성공 표시하지 않고 서버 토큰을 폐기).
  invalidatePendingLogin(): void {
    this.generation += 1
  }

  handleOAuthOutcome(outcome: GoogleOAuthOutcome): Promise<void> {
    const generation = ++this.generation
    return this.runAuthOperation(() => this.acceptOAuthOutcome(outcome, generation))
  }

  private async acceptOAuthOutcome(outcome: GoogleOAuthOutcome, generation: number): Promise<void> {
    if (!this.isCurrent(generation)) return
    if (outcome.type === 'cancelled') {
      this.publish('SIGNED_OUT')
      return
    }
    if (outcome.type === 'failed') {
      this.publish('SIGNED_OUT', GOOGLE_LOGIN_FAILED)
      return
    }

    try {
      const device = await this.getDevice()
      if (generation !== this.generation) return
      const result = await this.api.exchange(outcome.data, device)
      if (generation !== this.generation) {
        // [주의] 로그아웃 중 늦게 도착한 로그인 응답은 로컬에 저장하지 않고 서버 토큰도 폐기한다.
        if (result.success) await this.api.logout(result.data.accessToken)
        return
      }
      if (!result.success) {
        this.publish(result.error.code === 'LOCAL_NETWORK_UNAVAILABLE' ? 'OFFLINE' : 'SIGNED_OUT', result.error)
        return
      }
      try {
        await this.vault.write(result.data.refreshToken)
      } catch {
        // [주의] 안전한 저장에 실패하면 서버가 토큰을 줬어도 로그인 완료로 표시하지 않는다.
        await this.api.logout(result.data.accessToken)
        if (generation === this.generation) this.publish('SIGNED_OUT', KEYCHAIN_UNAVAILABLE)
        return
      }
      if (generation !== this.generation) {
        await this.vault.clear()
        await this.api.logout(result.data.accessToken)
        return
      }
      this.remember(result.data, generation)
      this.deviceId = result.data.device.deviceId
      this.publish('SIGNED_IN', null, result.data)
    } catch {
      if (generation === this.generation) this.publish('SIGNED_OUT', SERVICE_UNAVAILABLE)
    }
  }

  logout(): Promise<IpcResult<{ loggedOut: true }>> {
    const generation = ++this.generation
    // 진행 중 refresh의 저장까지 기다린다. 이전 일반 요청의 재전송은 generation으로 막는다.
    return this.runAuthOperation(async () => {
      let result: IpcResult<{ loggedOut: true }>
      try { result = await this.logoutSession(generation) }
      catch {
        if (this.isCurrent(generation)) {
          this.forget()
          this.publish('OFFLINE', SERVICE_UNAVAILABLE)
        }
        result = { success: false, error: SERVICE_UNAVAILABLE }
      }
      // 서버 장애로 logout이 실패하면 보존한 로그인은 새 요청에 계속 쓸 수 있어야 한다.
      // 이미 시작된 이전 요청은 generation이 달라 재전송되지 않는다.
      if (!result.success && this.isCurrent(generation) && this.accessToken) this.credentialGeneration = generation
      return result
    })
  }

  private async logoutSession(generation: number): Promise<IpcResult<{ loggedOut: true }>> {
    let token = this.accessToken
    // logout은 아직 유효한 token으로 먼저 시도해 refresh 장애와 독립적으로 종료한다.
    if (!token || this.accessTokenExpiresAt <= Date.now()) {
      const refreshed = await this.rotateRefresh(generation, true)
      if (!refreshed.success) {
        if (isReloginError(refreshed.error.code)) {
          return this.completeLogout(generation)
        }
        return { success: false, error: refreshed.error }
      }
      token = refreshed.data.accessToken
    }

    let result = await this.api.logout(token)
    // 전송 중 만료되거나 PC 시계가 느려도 서버의 폐기 완료를 확인한 뒤 로컬 token을 지운다.
    if (!result.success && result.error.code === ACCESS_TOKEN_EXPIRED) {
      const refreshed = await this.rotateRefresh(generation, true)
      if (!refreshed.success) {
        return isReloginError(refreshed.error.code)
          ? this.completeLogout(generation) : { success: false, error: refreshed.error }
      }
      result = await this.api.logout(refreshed.data.accessToken)
    }
    // 이미 폐기된 로그인만 로컬 정리를 허용한다. 알 수 없는 401·장애는 성공으로 바꾸지 않는다.
    if (!result.success && !isReloginError(result.error.code)) return { success: false, error: result.error }
    return this.completeLogout(generation)
  }

  /** 서버 폐기 완료 또는 재로그인 필요가 확인된 경우에만 안전한 저장소와 메모리를 정리한다. */
  private async completeLogout(generation: number): Promise<IpcResult<{ loggedOut: true }>> {
    try {
      await this.vault.clear()
    } catch {
      this.forget()
      if (this.isCurrent(generation)) this.publish('SIGNED_OUT', KEYCHAIN_UNAVAILABLE)
      return { success: false, error: KEYCHAIN_UNAVAILABLE }
    }
    this.forget()
    if (this.isCurrent(generation)) this.publish('SIGNED_OUT')
    return { success: true, data: { loggedOut: true } }
  }

  // 인증 실패의 정리도 토큰 큐 안에서 수행한다. 새 로그인 시작 뒤 도착한 오류는 무시한다.
  private async clearSession(generation: number, error: AuthApiError | null, deleteStoredToken = true): Promise<void> {
    if (!this.isCurrent(generation)) return
    const clearedGeneration = ++this.generation
    this.forget()
    let failure = error
    try { if (deleteStoredToken) await this.vault.clear() } catch { failure = KEYCHAIN_UNAVAILABLE }
    if (this.isCurrent(clearedGeneration)) this.publish('SIGNED_OUT', failure)
  }
}
