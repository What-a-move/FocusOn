import type { AuthService } from './auth-service.cjs'
import type { FloatingShow, InterventionAction } from '../shared/intervention-contract.cjs'
import type { FocusSettings } from '../shared/session-contract.cjs'

// 포키 코칭 문구는 친근한 반말이다(Notion UX 문구·알림 문구 정책 2.1, floating-poki.html). 구체 활동은 추측해 말하지 않는다.
const COPY = {
  GENTLE: { say: '어, 잠깐 샜네.', sub: '아까 목표로 다시 가보자.' },
  NORMAL: { say: '어? 또 다른 데 보고 있네 👀', sub: '지금 목표로 돌아가자!' },
  ACTIVE: { say: '어? 또 집중 놓쳤어?? 😏', sub: '아까 목표부터 가자!' },
  BREAK: { say: '최근에 세 번 흐트러졌어.', sub: '잠깐 쉬었다 갈까?' },
} as const

export type PendingIntervention = { interventionId: string; type: string; channels: string[] }

/**
 * 알림 목록 응답에서 아직 보이지 않은(PENDING) 알림만 꺼낸다.
 * Notion 명세에는 필드(type, createdAt, channels, status, policyVersion)만 있어 배열 또는 { interventions: [] } 둘 다 읽는다.
 */
export function parsePending(value: unknown): PendingIntervention[] | null {
  const list = Array.isArray(value) ? value : (value as { interventions?: unknown } | null)?.interventions
  if (!Array.isArray(list)) return null
  return list.flatMap((item) => {
    if (typeof item !== 'object' || item === null) return []
    const { interventionId, type, status, channels } = item as Record<string, unknown>
    if (status !== 'PENDING' || typeof interventionId !== 'string' || typeof type !== 'string') return []
    return [{ interventionId, type, channels: Array.isArray(channels) ? channels.filter((channel): channel is string => typeof channel === 'string') : [] }]
  })
}

type Deps = {
  auth: Pick<AuthService, 'authorizedRequest'>
  /** 포키 창에 알림을 보여 준다. */
  show(payload: FloatingShow): void
  /** macOS 알림(소리 포함 여부)을 띄운다. */
  notify(payload: FloatingShow, sound: boolean): void
  /** 잠시 쉬기: 세션을 일시정지한다. */
  pause(): Promise<{ success: boolean }>
  /** 조회 결과가 아직 지금 진행 중인 세션의 것인지. 늦게 온 이전 세션 결과는 보이지 않는다. */
  isCurrent(sessionId: string): boolean
}

/**
 * 서버(Policy Engine)가 만든 이탈·휴식 알림을 Desktop이 보여 준다 (화면 분석 5.3·5.4, 결정 024).
 * - 세션이 RUNNING일 때 10초마다 조회하고, 처음 보는 PENDING 알림 하나만 보여 준다.
 * - 사용자 반응은 acknowledgements로 기록한다. 실패해도 다시 보내지 않는다(판정·타이머와 무관).
 */
export class InterventionWatcher {
  private readonly seen = new Set<string>()
  private polling = false

  constructor(private readonly deps: Deps) {}

  async poll(sessionId: string, goalText: string, options: FocusSettings): Promise<void> {
    if (this.polling) return
    this.polling = true
    try {
      const result = await this.deps.auth.authorizedRequest({ path: `/api/v1/sessions/${sessionId}/interventions`, method: 'GET', expectedStatus: 200, parse: parsePending })
      if (!result.success || !this.deps.isCurrent(sessionId)) return
      const next = result.data.find((item) => !this.seen.has(item.interventionId))
      if (!next) return
      this.seen.add(next.interventionId)
      const kind = next.type.includes('BREAK') ? 'BREAK' : 'OFF_TASK'
      const payload: FloatingShow = { interventionId: next.interventionId, kind, level: options.interventionLevel, goalText, ...COPY[kind === 'BREAK' ? 'BREAK' : options.interventionLevel] }
      // 알림에 채널이 없으면 세션 시작 때 고른 채널을 쓴다.
      const channels = next.channels.length ? next.channels : options.notificationChannels
      if (channels.includes('MOLE_UI')) this.deps.show(payload)
      // 소리만 골랐어도 소리 있는 알림으로 알린다. 알림과 소리를 함께 골랐으면 알림은 한 번만 띄운다.
      if (channels.includes('MACOS_NOTIFICATION') || channels.includes('SOUND')) this.deps.notify(payload, channels.includes('SOUND'))
    } finally {
      this.polling = false
    }
  }

  /** 잠시 쉬기에서 일시정지에 실패하면 false. 그 밖에는 기록 실패와 상관없이 true다. */
  async act(interventionId: string, action: InterventionAction): Promise<boolean> {
    // 확인했어(ACK)는 이미 RECENTER에서 기록했으므로 창만 닫는다. 5분 재집중은 화면 상태일 뿐이다.
    if (action === 'ACK') return true
    await this.deps.auth.authorizedRequest({
      path: `/api/v1/interventions/${encodeURIComponent(interventionId)}/acknowledgements`, method: 'POST', expectedStatus: 200, parse: () => true, withDeviceId: true,
      body: { action: action === 'CLOSE' ? 'DISMISSED' : 'ACKNOWLEDGED' },
    })
    if (action === 'REST') return (await this.deps.pause()).success
    return true
  }

  /** 세션이 바뀌면 이전 세션의 알림 기록을 비운다. */
  reset(): void {
    this.seen.clear()
  }
}
