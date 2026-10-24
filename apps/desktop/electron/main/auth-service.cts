import type { AuthSessionSnapshot, IpcResult } from '../shared/auth-contract.cjs'
import type { AuthApiError, AuthApiClient, TokenPair, DesktopDevice, ApiResult } from './auth-api.cjs'
import type { RefreshTokenVault } from './auth-keychain.cjs'
import type { GoogleOAuthOutcome } from './google-oauth.cjs'
import { AuthSessionStore } from './auth-ipc.cjs'

const SERVICE_UNAVAILABLE: AuthApiError = { code: 'SERVICE_UNAVAILABLE', retryable: false }
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
  private restoring: Promise<void> = Promise.resolve()
  private restoringInFlight = false
  private generation = 0

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

  private remember(tokens: TokenPair): void {
    // [주의] Access Token은 Main 메모리에만 둔다. Renderer에는 상태 요약만 전달한다.
    this.accessToken = tokens.accessToken
    this.accessTokenExpiresAt = Date.parse(tokens.accessTokenExpiresAt)
  }

  private forget(): void {
    this.accessToken = null
    this.accessTokenExpiresAt = 0
    this.deviceId = null
  }

  /**
   * 인증이 필요한 Backend 요청. 만료가 가까우면 먼저 refresh하고, 서버가 만료(401)를 알리면
   * single-flight refresh 1회 뒤 원 요청을 1회만 다시 보낸다 (AUTH_SESSION_FLOW.md).
   * refresh가 재로그인 오류면 저장된 토큰을 지우고 SIGNED_OUT으로 전환한다.
   */
  async authorizedRequest<T>(request: AuthorizedRequest<T>): Promise<ApiResult<T>> {
    await this.ready()
    if (!this.accessToken || this.accessTokenExpiresAt - EXPIRY_MARGIN_MS <= Date.now()) {
      const refreshed = await this.refreshOrSignOut()
      if (!refreshed.success) return refreshed
    }
    if (request.withDeviceId && !this.deviceId) return { success: false, error: RELOGIN_REQUIRED }

    const send = () => this.api.call(
      request.path, request.method, request.expectedStatus, request.parse, request.body,
      this.accessToken ?? undefined, request.withDeviceId && this.deviceId ? { 'X-Device-Id': this.deviceId } : {},
    )
    const sendWithRetry = async () => {
      let result = await send()
      if (request.method !== 'GET') return result
      for (const delay of GET_RETRY_DELAYS_MS) {
        if (result.success || !TRANSIENT_CODES.has(result.error.code)) return result
        await this.sleep(delay)
        result = await send()
      }
      return result
    }

    const first = await sendWithRetry()
    if (first.success || first.error.code !== ACCESS_TOKEN_EXPIRED) return first
    const refreshed = await this.refreshOrSignOut()
    if (!refreshed.success) return refreshed
    return sendWithRetry()
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

  private async refreshOrSignOut(): Promise<ApiResult<TokenPair>> {
    const refreshed = await this.refresh()
    if (!refreshed.success && isReloginError(refreshed.error.code)) {
      this.forget()
      this.publish('SIGNED_OUT', refreshed.error)
    }
    return refreshed
  }

  restore(): Promise<void> {
    if (this.restoringInFlight) return this.restoring
    this.restoringInFlight = true
    this.restoring = this.restoreSession().finally(() => { this.restoringInFlight = false })
    return this.restoring
  }

  ready(): Promise<void> {
    return this.restoring
  }

  private async restoreSession(): Promise<void> {
    try {
      const saved = await this.vault.read()
      if (!saved) {
        this.publish('SIGNED_OUT')
        return
      }

      const rotated = await this.rotateRefresh(saved)
      if (!rotated.success) {
        this.publish(isReloginError(rotated.error.code) ? 'SIGNED_OUT' : 'OFFLINE', rotated.error)
        return
      }

      const identity = await this.api.me(rotated.data.accessToken)
      if (!identity.success) {
        if (isReloginError(identity.error.code)) {
          await this.vault.clear()
          this.forget()
          this.publish('SIGNED_OUT', identity.error)
        } else {
          this.publish('OFFLINE', identity.error)
        }
        return
      }
      this.deviceId = identity.data.device.deviceId
      this.publish('SIGNED_IN', null, identity.data)
    } catch {
      // [주의] Keychain이 잠겼다면 토큰이 없다고 단정하거나 파일 저장소로 우회하지 않는다.
      this.publish('OFFLINE', SERVICE_UNAVAILABLE)
    }
  }

  private async rotateRefresh(refreshToken: string): Promise<ApiResult<TokenPair>> {
    const result = await this.api.refresh(refreshToken)
    if (!result.success) {
      if (isReloginError(result.error.code)) {
        await this.vault.clear()
        this.forget()
      }
      return result
    }
    // [주의] 서버가 기존 Refresh Token을 폐기하므로 새 토큰을 먼저 Keychain에 저장한다.
    try {
      await this.vault.write(result.data.refreshToken)
      this.remember(result.data)
      return result
    } catch {
      this.forget()
      return { success: false, error: SERVICE_UNAVAILABLE }
    }
  }

  refresh(): Promise<ApiResult<TokenPair>> {
    if (!this.refreshPromise) {
      const work = this.vault.read().then((token) => token
        ? this.rotateRefresh(token)
        : { success: false as const, error: RELOGIN_REQUIRED }).catch(() => ({ success: false as const, error: SERVICE_UNAVAILABLE }))
      this.refreshPromise = work
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

  async handleOAuthOutcome(outcome: GoogleOAuthOutcome): Promise<void> {
    if (outcome.type === 'cancelled') {
      this.publish('SIGNED_OUT')
      return
    }
    if (outcome.type === 'failed') {
      this.publish('SIGNED_OUT', SERVICE_UNAVAILABLE)
      return
    }

    const generation = this.generation
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
        if (generation === this.generation) this.publish('SIGNED_OUT', SERVICE_UNAVAILABLE)
        return
      }
      if (generation !== this.generation) {
        await this.vault.clear()
        await this.api.logout(result.data.accessToken)
        return
      }
      this.remember(result.data)
      this.deviceId = result.data.device.deviceId
      this.publish('SIGNED_IN', null, result.data)
    } catch {
      if (generation === this.generation) this.publish('SIGNED_OUT', SERVICE_UNAVAILABLE)
    }
  }

  async logout(): Promise<IpcResult<{ loggedOut: true }>> {
    this.generation += 1
    await this.ready()
    let token = this.accessToken
    if (!token || this.accessTokenExpiresAt <= Date.now()) {
      const refreshed = await this.refresh()
      if (!refreshed.success) {
        if (isReloginError(refreshed.error.code)) {
          this.publish('SIGNED_OUT', refreshed.error)
          return { success: true, data: { loggedOut: true } }
        }
        return { success: false, error: refreshed.error }
      }
      token = refreshed.data.accessToken
    }

    const result = await this.api.logout(token)
    if (!result.success) return { success: false, error: result.error }
    try {
      await this.vault.clear()
    } catch {
      this.forget()
      this.publish('SIGNED_OUT', SERVICE_UNAVAILABLE)
      return { success: false, error: SERVICE_UNAVAILABLE }
    }
    this.forget()
    this.publish('SIGNED_OUT')
    return result
  }
}
