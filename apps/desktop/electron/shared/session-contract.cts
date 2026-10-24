import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 세션 Channel. 세션 설정(D09)·집중 화면(D10)이 쓴다.
export const SESSION_CHANNELS = {
  focusSettings: 'session:focus-settings:get',
  start: 'session:start',
  current: 'session:current:get',
  changed: 'session:changed',
  pause: 'session:pause',
  resume: 'session:resume',
  extend: 'session:extend',
  end: 'session:end',
} as const

export type InterventionLevel = 'GENTLE' | 'NORMAL' | 'ACTIVE'
export type NotificationChannel = 'MOLE_UI' | 'MACOS_NOTIFICATION' | 'SOUND'
export type FocusSettings = { interventionLevel: InterventionLevel; notificationChannels: NotificationChannel[]; cameraAnalysisEnabled: boolean }
export type SessionOptions = FocusSettings
/** goalText는 집중 화면 표시용이며 서버로 보내지 않는다(서버는 goalId로 snapshot을 만든다). */
export type StartSessionInput = { goalId: string; goalText: string; targetDurationMs: number; timeZone: string; sessionOptions: SessionOptions; idempotencyKey: string }
export type StartedSession = { sessionId: string; status: string; version: number; targetDurationMs: number; accumulatedActiveMs: number; startedAt: string; interventionPolicyVersion: string }

/** 서버 세션 상태 (세션 상태 전이 정책). */
export type SessionStatus = 'RUNNING' | 'PAUSED' | 'AUTO_PAUSED' | 'AWAITING_END_CONFIRMATION' | 'ENDING' | 'ENDED' | 'ABANDONED'
/** GET /sessions/active · GET /sessions/{id} 응답 중 화면에 필요한 값 (2026-10-25 확정, 결정 023). */
export type RemoteSession = { sessionId: string; status: SessionStatus; version: number; goalText: string; targetDurationMs: number; accumulatedActiveMs: number }

/**
 * Main이 Renderer에 보내는 현재 세션. 누적 시간은 Main의 monotonic clock으로 계산한 값이며,
 * Renderer는 `RUNNING`일 때만 받은 뒤 흐른 시간을 더해 1초마다 다시 그린다.
 */
export type SessionSnapshot = {
  sessionId: string
  goalText: string
  status: SessionStatus
  targetDurationMs: number
  accumulatedActiveMs: number
  /** 마지막 명령이 실패한 이유. 화면 안내용이며 상태는 바꾸지 않는다. */
  error: string | null
  busy: boolean
}

export type SessionsApi = {
  getFocusSettings(): Promise<IpcResult<FocusSettings>>
  start(input: StartSessionInput): Promise<IpcResult<StartedSession>>
  /** 진행 중 세션. Main에 없으면 서버의 활성 세션을 복구한다. 없으면 null. */
  getCurrent(): Promise<IpcResult<SessionSnapshot | null>>
  onChanged(callback: (snapshot: SessionSnapshot | null) => void): () => void
  pause(): Promise<IpcResult<SessionSnapshot>>
  resume(): Promise<IpcResult<SessionSnapshot>>
  extend(input: { minutes: number }): Promise<IpcResult<SessionSnapshot>>
  end(): Promise<IpcResult<SessionSnapshot>>
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
  if (!isRecord(value) || Object.keys(value).length !== 6 || !isId(value.goalId) || !isUuid(value.idempotencyKey)) return false
  if (typeof value.goalText !== 'string' || value.goalText.trim().length < 2 || [...value.goalText].length > 40) return false
  const ms = value.targetDurationMs
  return typeof ms === 'number' && Number.isInteger(ms) && ms % 60000 === 0 && ms >= MIN_MINUTES * 60000 && ms <= MAX_MINUTES * 60000
    && isValidTimeZone(value.timeZone) && isSessionOptions(value.sessionOptions)
}

export function parseFocusSettings(value: unknown): FocusSettings | null {
  if (!isRecord(value) || !LEVELS.includes(value.interventionLevel as InterventionLevel) || !isChannelList(value.notificationChannels) || typeof value.cameraAnalysisEnabled !== 'boolean') return null
  return { interventionLevel: value.interventionLevel as InterventionLevel, notificationChannels: value.notificationChannels, cameraAnalysisEnabled: value.cameraAnalysisEnabled }
}

const STATUSES: readonly SessionStatus[] = ['RUNNING', 'PAUSED', 'AUTO_PAUSED', 'AWAITING_END_CONFIRMATION', 'ENDING', 'ENDED', 'ABANDONED']
const isCount = (value: unknown): value is number => typeof value === 'number' && Number.isInteger(value) && value >= 0
export const isSessionStatus = (value: unknown): value is SessionStatus => STATUSES.includes(value as SessionStatus)

export function parseStartedSession(value: unknown): StartedSession | null {
  if (!isRecord(value) || !isId(value.sessionId) || typeof value.status !== 'string' || typeof value.interventionPolicyVersion !== 'string') return null
  if (!isCount(value.version) || !isCount(value.targetDurationMs) || !isCount(value.accumulatedActiveMs)) return null
  if (typeof value.startedAt !== 'string' || !Number.isFinite(Date.parse(value.startedAt))) return null
  return { sessionId: value.sessionId, status: value.status, version: value.version, targetDurationMs: value.targetDurationMs, accumulatedActiveMs: value.accumulatedActiveMs, startedAt: value.startedAt, interventionPolicyVersion: value.interventionPolicyVersion }
}

// GET /sessions/active · /sessions/{id} 확정 형식(결정 023): { sessionId, status, version, goalTextSnapshot, targetDurationMs, accumulatedActiveMs }
export function parseRemoteSession(value: unknown): RemoteSession | null {
  if (!isRecord(value) || !isId(value.sessionId) || !isSessionStatus(value.status) || !isCount(value.version)) return null
  if (typeof value.goalTextSnapshot !== 'string' || !isCount(value.targetDurationMs) || !isCount(value.accumulatedActiveMs)) return null
  return { sessionId: value.sessionId, status: value.status, version: value.version, goalText: value.goalTextSnapshot, targetDurationMs: value.targetDurationMs, accumulatedActiveMs: value.accumulatedActiveMs }
}

/** 상태 명령(pause·resume·extend·end) 응답의 공통 부분. 없는 값은 이전 값을 유지한다. */
export type CommandResult = { status?: SessionStatus; version: number; accumulatedActiveMs?: number; targetDurationMs?: number }
export function parseCommandResult(value: unknown): CommandResult | null {
  if (!isRecord(value) || !isCount(value.version)) return null
  if (value.status !== undefined && !isSessionStatus(value.status)) return null
  if (value.accumulatedActiveMs !== undefined && !isCount(value.accumulatedActiveMs)) return null
  if (value.targetDurationMs !== undefined && !isCount(value.targetDurationMs)) return null
  return { status: value.status as SessionStatus | undefined, version: value.version, accumulatedActiveMs: value.accumulatedActiveMs as number | undefined, targetDurationMs: value.targetDurationMs as number | undefined }
}

export function parseHeartbeat(value: unknown): { status: SessionStatus; accumulatedActiveMs: number } | null {
  if (!isRecord(value) || !isSessionStatus(value.status) || !isCount(value.accumulatedActiveMs)) return null
  return { status: value.status, accumulatedActiveMs: value.accumulatedActiveMs }
}

/** Main이 보낸 Event가 화면용 세션 형식인지 확인한다. */
export function isSessionSnapshot(value: unknown): value is SessionSnapshot {
  return isRecord(value) && isId(value.sessionId) && typeof value.goalText === 'string' && isSessionStatus(value.status)
    && isCount(value.targetDurationMs) && isCount(value.accumulatedActiveMs) && (value.error === null || typeof value.error === 'string') && typeof value.busy === 'boolean'
}

/** 연장은 5~300분 (로그인 설계서 7.1과 같은 범위). */
export const isValidExtendMinutes = (value: unknown): value is number => Number.isInteger(value) && (value as number) >= MIN_MINUTES && (value as number) <= MAX_MINUTES
