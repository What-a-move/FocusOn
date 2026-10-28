import type { IpcResult } from './auth-contract.cjs'
import type { InterventionLevel } from './session-contract.cjs'

// IPC_CONTRACT.md 화면 위 포키 Channel (화면 분석 4단계).
export const INTERVENTION_CHANNELS = {
  show: 'floating:show',
  act: 'intervention:act',
} as const

/** 포키 창에 보여 줄 알림 하나. 문구는 Main이 정한다. 분석 근거·화면 내용은 넣지 않는다. */
export type FloatingShow = { interventionId: string; kind: 'OFF_TASK' | 'BREAK'; level: InterventionLevel; goalText: string; say: string; sub: string }
/** RECENTER: 조금 더 보기(확인 기록) · ACK: 확인했어(창 닫기) · REST: 잠시 쉬기(확인 기록 + 일시정지) · CLOSE: 닫기(해제 기록) */
export type InterventionAction = 'RECENTER' | 'ACK' | 'REST' | 'CLOSE'

export type InterventionsApi = {
  onShow(callback: (show: FloatingShow) => void): () => void
  act(input: { interventionId: string; action: InterventionAction }): Promise<IpcResult<{ done: true }>>
}

const ACTIONS: readonly InterventionAction[] = ['RECENTER', 'ACK', 'REST', 'CLOSE']
const LEVELS: readonly InterventionLevel[] = ['GENTLE', 'NORMAL', 'ACTIVE']

export function isActInput(value: unknown): value is { interventionId: string; action: InterventionAction } {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const { interventionId, action, ...rest } = value as Record<string, unknown>
  return Object.keys(rest).length === 0 && typeof interventionId === 'string' && /^[\w-]{1,64}$/u.test(interventionId) && ACTIONS.includes(action as InterventionAction)
}

export function isFloatingShow(value: unknown): value is FloatingShow {
  if (typeof value !== 'object' || value === null) return false
  const { interventionId, kind, level, goalText, say, sub } = value as Record<string, unknown>
  return typeof interventionId === 'string' && (kind === 'OFF_TASK' || kind === 'BREAK') && LEVELS.includes(level as InterventionLevel)
    && typeof goalText === 'string' && typeof say === 'string' && typeof sub === 'string'
}
