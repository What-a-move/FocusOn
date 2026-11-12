import type { IpcResult } from './auth-contract.cjs'
import type { InterventionLevel } from './session-contract.cjs'

// IPC_CONTRACT.md 화면 위 포키 Channel (화면 분석 4단계).
export const INTERVENTION_CHANNELS = {
  show: 'floating:show',
  act: 'intervention:act',
  // 포키 창에서만 보낸다. 포인터가 카드·포키 위면 true(클릭 받음), 아니면 false(아래 앱으로 통과).
  interactive: 'floating:interactive',
  // Main → 포키 창. 화면 맨 아래에서 포키를 얼마나 올릴지(px). Dock이 보이면 Dock 높이, 전체 화면이면 0.
  lift: 'floating:lift',
} as const

/**
 * 포키 창에 보여 줄 알림 하나. 문구는 Main이 정한다. 분석 근거·화면 내용은 넣지 않는다.
 * DISCONNECTED(F07)는 서버 알림이 아니라 Chrome 쪽 이유로 세션이 멈췄다는 안내다. interventionId는 `DISCONNECTED_ID`다.
 */
export type FloatingShow = { interventionId: string; kind: 'OFF_TASK' | 'BREAK' | 'DISCONNECTED'; level: InterventionLevel; goalText: string; say: string; sub: string }
export const DISCONNECTED_ID = 'local-disconnected'
/** 카메라 분석(#222)이 Desktop에서 직접 띄운 이탈 알림. 서버 알림이 아니라 확인 기록을 보내지 않는다. */
export const CAMERA_ID = 'local-camera'
/** RECENTER: 확인했어(확인 기록) · ACK: 알겠어(목표 안내 닫기) · REST: 잠시 쉬기(확인 기록 + 일시정지) · CLOSE: 닫기(해제 기록) · OPEN_APP: F07 앱 열기(기록 없음) */
export type InterventionAction = 'RECENTER' | 'ACK' | 'REST' | 'CLOSE' | 'OPEN_APP'

export type InterventionsApi = {
  onShow(callback: (show: FloatingShow) => void): () => void
  act(input: { interventionId: string; action: InterventionAction }): Promise<IpcResult<{ done: true }>>
  setInteractive(interactive: boolean): void
  onLift(callback: (px: number) => void): () => void
}

const ACTIONS: readonly InterventionAction[] = ['RECENTER', 'ACK', 'REST', 'CLOSE', 'OPEN_APP']
const LEVELS: readonly InterventionLevel[] = ['GENTLE', 'NORMAL', 'ACTIVE']

export function isActInput(value: unknown): value is { interventionId: string; action: InterventionAction } {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const { interventionId, action, ...rest } = value as Record<string, unknown>
  return Object.keys(rest).length === 0 && typeof interventionId === 'string' && /^[\w-]{1,64}$/u.test(interventionId) && ACTIONS.includes(action as InterventionAction)
}

export function isFloatingShow(value: unknown): value is FloatingShow {
  if (typeof value !== 'object' || value === null) return false
  const { interventionId, kind, level, goalText, say, sub } = value as Record<string, unknown>
  return typeof interventionId === 'string' && (kind === 'OFF_TASK' || kind === 'BREAK' || kind === 'DISCONNECTED') && LEVELS.includes(level as InterventionLevel)
    && typeof goalText === 'string' && typeof say === 'string' && typeof sub === 'string'
}
