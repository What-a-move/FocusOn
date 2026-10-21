import type { AuthSessionSnapshot, IpcResult } from '../shared/auth-contract.cjs'
import type { AuthApiError, AuthApiClient, TokenPair, DesktopDevice, ApiResult } from './auth-api.cjs'
import type { RefreshTokenVault } from './auth-keychain.cjs'
import type { GoogleOAuthOutcome } from './google-oauth.cjs'
import { AuthSessionStore } from './auth-ipc.cjs'

const SERVICE_UNAVAILABLE: AuthApiError = { code: 'SERVICE_UNAVAILABLE', retryable: false }
const RELOGIN_REQUIRED: AuthApiError = { code: 'AUTH_RELOGIN_REQUIRED', retryable: false }

function isReloginError(code: string): boolean {
  // [주의] Google 재인증이 필요한 경우도 저장된 토큰으로 복구할 수 없으므로 다시 로그인하게 한다.
  return code === 'AUTH_RELOGIN_REQUIRED' || code === 'AUTH_TOKEN_REUSE_DETECTED' || code === 'AUTH_OAUTH_REAUTH_REQUIRED'
}

export class AuthService {
  private accessToken: string | null = null
  private accessTokenExpiresAt = 0
  private refreshPromise: Promise<ApiResult<TokenPair>> | null = null
  private restoring: Promise<void> = Promise.resolve()
  private restoringInFlight = false
  private generation = 0

  constructor(
    private readonly store: AuthSessionStore,
    private readonly api: AuthApiClient,
    private readonly vault: RefreshTokenVault,
    private readonly getDevice: () => Promise<DesktopDevice>,
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
