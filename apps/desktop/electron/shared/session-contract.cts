import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 세션 Channel. 세션 설정 화면(D09)이 쓴다.
export const SESSION_CHANNELS = {
  focusSettings: 'session:focus-settings:get',
  start: 'session:start',
} as const

export type InterventionLevel = 'GENTLE' | 'NORMAL' | 'ACTIVE'
export type NotificationChannel = 'MOLE_UI' | 'MACOS_NOTIFICATION' | 'SOUND'
export type FocusSettings = { interventionLevel: InterventionLevel; notificationChannels: NotificationChannel[]; cameraAnalysisEnabled: boolean }
export type SessionOptions = FocusSettings
export type StartSessionInput = { goalId: string; targetDurationMs: number; timeZone: string; sessionOptions: SessionOptions; idempotencyKey: string }
export type StartedSession = { sessionId: string; status: string; targetDurationMs: number; startedAt: string; interventionPolicyVersion: string }

export type SessionsApi = {
  getFocusSettings(): Promise<IpcResult<FocusSettings>>
  start(input: StartSessionInput): Promise<IpcResult<StartedSession>>
}

const LEVELS: readonly InterventionLevel[] = ['GENTLE', 'NORMAL', 'ACTIVE']
const CHANNELS: readonly NotificationChannel[] = ['MOLE_UI', 'MACOS_NOTIFICATION', 'SOUND']
export const MIN_MINUTES = 5
export const MAX_MINUTES = 300

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const isId = (value: unknown): value is string => typeof value === 'string' && /^[A-Za-z0-9_-]{1,64}$/u.test(value)
const isUuid = (value: unknown): value is string => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value)

function isChannelList(value: unknown): value is NotificationChannel[] {
  return Array.isArray(value) && value.length > 0 && new Set(value).size === value.length && value.every((item) => CHANNELS.includes(item as NotificationChannel))
}

function isSessionOptions(value: unknown): value is SessionOptions {
  return isRecord(value) && Object.keys(value).length === 3 && LEVELS.includes(value.interventionLevel as InterventionLevel)
    && isChannelList(value.notificationChannels) && typeof value.cameraAnalysisEnabled === 'boolean'
}

/** IANA 시간대 이름인지 Intl로 확인한다. 서버는 이상한 값이면 Asia/Seoul로 처리한다. */
export function isValidTimeZone(value: unknown): value is string {
  if (typeof value !== 'string' || value.length > 64) return false
  try { new Intl.DateTimeFormat('en-US', { timeZone: value }); return true } catch { return false }
}

/** 집중 시간은 5~300분, 분 단위다 (로그인 설계서 7.1). MVP에는 종료 없는 세션이 없다. */
export function isValidStartInput(value: unknown): value is StartSessionInput {
  if (!isRecord(value) || Object.keys(value).length !== 5 || !isId(value.goalId) || !isUuid(value.idempotencyKey)) return false
  const ms = value.targetDurationMs
  return typeof ms === 'number' && Number.isInteger(ms) && ms % 60000 === 0 && ms >= MIN_MINUTES * 60000 && ms <= MAX_MINUTES * 60000
    && isValidTimeZone(value.timeZone) && isSessionOptions(value.sessionOptions)
}

export function parseFocusSettings(value: unknown): FocusSettings | null {
  if (!isRecord(value) || !LEVELS.includes(value.interventionLevel as InterventionLevel) || !isChannelList(value.notificationChannels) || typeof value.cameraAnalysisEnabled !== 'boolean') return null
  return { interventionLevel: value.interventionLevel as InterventionLevel, notificationChannels: value.notificationChannels, cameraAnalysisEnabled: value.cameraAnalysisEnabled }
}

export function parseStartedSession(value: unknown): StartedSession | null {
  if (!isRecord(value) || !isId(value.sessionId) || typeof value.status !== 'string' || typeof value.interventionPolicyVersion !== 'string') return null
  if (typeof value.targetDurationMs !== 'number' || typeof value.startedAt !== 'string' || !Number.isFinite(Date.parse(value.startedAt))) return null
  return { sessionId: value.sessionId, status: value.status, targetDurationMs: value.targetDurationMs, startedAt: value.startedAt, interventionPolicyVersion: value.interventionPolicyVersion }
}
