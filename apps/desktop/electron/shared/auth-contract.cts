import type { PermissionsApi } from './permission-contract.cjs'
import type { OnboardingApi } from './onboarding-contract.cjs'
import type { ReportsApi } from './report-contract.cjs'
import type { GoalsApi } from './goal-contract.cjs'
import type { SessionsApi } from './session-contract.cjs'
import type { AnalysisApi } from './analysis-contract.cjs'
import type { InterventionsApi } from './intervention-contract.cjs'

export const AUTH_CHANNELS = {
  getSession: 'auth:session:get',
  retrySession: 'auth:session:retry',
  startGoogleLogin: 'auth:google-login:start',
  logout: 'auth:logout',
  sessionChanged: 'auth:session:changed',
} as const

export type IpcError = { code: string; retryable: boolean }

export type IpcResult<T> =
  | { success: true; data: T }
  | { success: false; error: IpcError }

export type AuthUiStatus =
  | 'SIGNED_OUT'
  | 'AUTHENTICATING'
  | 'SIGNED_IN'
  | 'LINK_REQUIRED'
  | 'EXPIRED'
  | 'OFFLINE'

export type AuthSessionSnapshot = {
  revision: number
  status: AuthUiStatus
  user: { userId: string; nickname: string | null } | null
  onboardingStatus: 'INCOMPLETE' | 'COMPLETED' | null
  error: IpcError | null
}

export type FocusOnApi = {
  auth: {
    getSession(): Promise<IpcResult<AuthSessionSnapshot>>
    retrySession(): Promise<IpcResult<AuthSessionSnapshot>>
    startGoogleLogin(): Promise<IpcResult<{ started: boolean }>>
    logout(): Promise<IpcResult<{ loggedOut: true }>>
    onSessionChanged(callback: (snapshot: AuthSessionSnapshot) => void): () => void
  }
  permissions: PermissionsApi
  onboarding: OnboardingApi
  reports: ReportsApi
  goals: GoalsApi
  sessions: SessionsApi
  analysis: AnalysisApi
  interventions: InterventionsApi
}

const AUTH_STATUSES: readonly AuthUiStatus[] = [
  'SIGNED_OUT', 'AUTHENTICATING', 'SIGNED_IN', 'LINK_REQUIRED', 'EXPIRED', 'OFFLINE',
]

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

// [주의] Event에 예상 밖 필드가 섞여도 Renderer로 전달하지 않도록 허용 필드만 새로 만든다.
export function parseAuthSessionSnapshot(value: unknown): AuthSessionSnapshot | null {
  if (!isRecord(value)) return null
  if (!Number.isSafeInteger(value.revision) || Number(value.revision) < 0) return null
  if (!AUTH_STATUSES.includes(value.status as AuthUiStatus)) return null

  const user = value.user
  if (user !== null && (!isRecord(user)
    || typeof user.userId !== 'string'
    || (user.nickname !== null && typeof user.nickname !== 'string'))) return null

  const onboardingStatus = value.onboardingStatus
  if (onboardingStatus !== null && onboardingStatus !== 'INCOMPLETE' && onboardingStatus !== 'COMPLETED') return null

  const error = value.error
  if (error !== null && (!isRecord(error)
    || typeof error.code !== 'string'
    || typeof error.retryable !== 'boolean')) return null

  return {
    revision: Number(value.revision),
    status: value.status as AuthUiStatus,
    user: user === null ? null : { userId: user.userId as string, nickname: user.nickname as string | null },
    onboardingStatus,
    error: error === null ? null : { code: error.code as string, retryable: error.retryable as boolean },
  }
}
