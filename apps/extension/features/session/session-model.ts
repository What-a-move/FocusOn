// GET /api/v1/sessions/active 응답 (Desktop과 같은 계약, Desktop 결정 023). 활성 세션이 없으면 204 → null.
// [주의] node --test로 직접 실행하므로 상대 import를 두지 않는다.

export type SessionStatus = 'RUNNING' | 'PAUSED' | 'AUTO_PAUSED' | 'AWAITING_END_CONFIRMATION' | 'ENDING' | 'ENDED' | 'ABANDONED'
export type ActiveSession = { sessionId: string; status: SessionStatus; goalText: string; targetDurationMs: number; accumulatedActiveMs: number }

const STATUSES: readonly SessionStatus[] = ['RUNNING', 'PAUSED', 'AUTO_PAUSED', 'AWAITING_END_CONFIRMATION', 'ENDING', 'ENDED', 'ABANDONED']
const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const isCount = (value: unknown): value is number => typeof value === 'number' && Number.isInteger(value) && value >= 0

/** 활성 세션이 없으면 { none: true }. 형식이 맞지 않으면 null(계약 위반). */
export function parseActiveSession(value: unknown): ActiveSession | { none: true } | null {
  if (value === null) return { none: true }
  if (!isRecord(value) || typeof value.sessionId !== 'string' || !STATUSES.includes(value.status as SessionStatus)) return null
  if (typeof value.goalTextSnapshot !== 'string' || !isCount(value.targetDurationMs) || !isCount(value.accumulatedActiveMs)) return null
  return { sessionId: value.sessionId, status: value.status as SessionStatus, goalText: value.goalTextSnapshot, targetDurationMs: value.targetDurationMs, accumulatedActiveMs: value.accumulatedActiveMs }
}

/**
 * 팝업에 그릴 누적 시간. 시간은 서버(Desktop이 보고한 값)가 확정하고, 팝업은 받은 뒤 흐른 시간만 화면용으로 더한다(결정 003).
 * 목표 시간을 넘겨 그리지 않는다.
 */
export function displayActiveMs(session: ActiveSession, fetchedAt: number, now: number): number {
  const running = session.status === 'RUNNING' ? Math.max(0, now - fetchedAt) : 0
  return Math.min(session.targetDurationMs, session.accumulatedActiveMs + running)
}

export function formatClock(ms: number): string {
  const seconds = Math.max(0, Math.ceil(ms / 1000))
  const hours = Math.floor(seconds / 3600)
  const mm = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')
  const ss = String(seconds % 60).padStart(2, '0')
  return hours ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
}
