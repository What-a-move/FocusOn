import { randomUUID } from 'node:crypto'
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { AuthService } from './auth-service.cjs'
import { log } from './logger.cjs'
import type { FloatingShow, InterventionAction } from '../shared/intervention-contract.cjs'
import type { SessionOptions } from '../shared/session-contract.cjs'

// 포키 코칭 문구는 친근한 반말이다(Notion UX 문구·알림 문구 정책 2.1, floating-poki.html). 구체 활동은 추측해 말하지 않는다.
const COPY = {
  GENTLE: { say: '어, 잠깐 샜네?', sub: '아까 하던 거 마저 할까?' },
  NORMAL: { say: '어? 또 딴 데 보고 있네 👀', sub: '목표로 돌아가자!' },
  ACTIVE: { say: '어 얘 봐라? 또 놀고 있네 😏', sub: '이러다 오늘 목표 다 날아간다?' },
  BREAK: { say: '벌써 세 번째 흐트러졌어.', sub: '잠깐 쉬었다 갈까?' },
} as const

const CHANNELS: readonly string[] = ['MOLE_UI', 'MACOS_NOTIFICATION', 'SOUND']

const TYPES = ['OFF_TASK', 'BREAK_SUGGESTION'] as const
/** observationContextId: 알림을 만든 전경 context(null은 예전 이력). remainingTtlMs: 서버 기준 남은 표시 가능 시간(ms). */
export type PendingIntervention = { interventionId: string; type: (typeof TYPES)[number]; channels: string[]; observationContextId: string | null; remainingTtlMs: number | null }

/**
 * 알림 목록 응답에서 아직 보이지 않은(PENDING) 알림만 꺼낸다. 배열 또는 { interventions: [] } 둘 다 읽는다.
 * type은 `OFF_TASK`(이탈 알림)·`BREAK_SUGGESTION`(휴식 제안)만 보여 준다. 그 밖의 type은 Desktop이 표시하지 않는다(결정 035).
 */
export function parsePending(value: unknown): PendingIntervention[] | null {
  const list = Array.isArray(value) ? value : (value as { interventions?: unknown } | null)?.interventions
  if (!Array.isArray(list)) return null
  return list.flatMap((item) => {
    if (typeof item !== 'object' || item === null) return []
    const { interventionId, type, status, channels, observationContextId, remainingTtlMs } = item as Record<string, unknown>
    if (status !== 'PENDING' || typeof interventionId !== 'string' || !TYPES.includes(type as PendingIntervention['type'])) return []
    return [{
      interventionId, type: type as PendingIntervention['type'],
      channels: Array.isArray(channels) ? channels.filter((channel): channel is string => typeof channel === 'string') : [],
      observationContextId: typeof observationContextId === 'string' ? observationContextId : null,
      remainingTtlMs: Number.isSafeInteger(remainingTtlMs) && (remainingTtlMs as number) >= 0 ? remainingTtlMs as number : null,
    }]
  })
}

/** 표시 시도 기록. DISPATCHING은 띄우는 중(재시작 시 UNKNOWN으로 보고), 나머지는 서버에 보낼 receipt다. */
type Receipt = { interventionId: string; clientDeliveryId: string; dispatchStatus: 'DISPATCHING' | 'SENT' | 'NOT_SENT' | 'UNKNOWN'; channels: string[] }

type Deps = {
  auth: Pick<AuthService, 'authorizedRequest'>
  /** 포키 창에 알림을 보여 준다. 띄우지 못했으면 false. */
  show(payload: FloatingShow): boolean
  /** macOS 알림(소리 포함 여부)을 띄운다. 띄우지 못했으면 false. */
  notify(payload: FloatingShow, sound: boolean): boolean
  /** 잠시 쉬기: 세션을 일시정지한다. */
  pause(): Promise<{ success: boolean }>
  /** 조회 결과가 아직 지금 진행 중인 세션의 것인지. 늦게 온 이전 세션 결과는 보이지 않는다. */
  isCurrent(sessionId: string): boolean
  /** 지금 확정된 전경 context ID(observation-context.cts). 알림의 context와 다르면 지난 화면 알림이라 띄우지 않는다. */
  currentContext(): string | null
  /** 표시 시도·receipt를 남기는 파일. 앱을 다시 켜도 보내지 못한 receipt를 이어 보낸다. */
  journalFile: string
  now?: () => number
  uuid?: () => string
}

/**
 * 서버(Policy Engine)가 만든 이탈·휴식 알림을 Desktop이 보여 준다 (화면 분석 5.3·5.4, 결정 024, #182 실제 알림 연결).
 * 한 곳(이 클래스)에서만 후보 조회·표시·receipt를 차례로 처리한다(intervention-policy-handoff 클라이언트 직렬화).
 * - 보내지 못한 receipt가 있으면 먼저 보내고, 그 주기에는 새 후보를 띄우지 않는다.
 * - 표시 직전 진행 중·같은 전경 context·남은 시간(요청 시작 뒤 경과 차감)을 확인한다. 하나라도 아니면 버린다.
 * - 띄우기 전에 시도를 파일에 남기고, 결과(SENT·NOT_SENT)를 deliveries로 보낸다. 재시작 때 띄우는 중이던 시도는 UNKNOWN이다.
 * - 사용자 반응(확인했어·닫기·잠시 쉬기)은 acknowledgements로 따로 기록한다.
 */
export class InterventionWatcher {
  private readonly seen = new Set<string>()
  private readonly receipts: Receipt[]
  private polling = false
  private readonly now: () => number
  private readonly uuid: () => string

  constructor(private readonly deps: Deps) {
    this.now = deps.now ?? (() => performance.now())
    this.uuid = deps.uuid ?? randomUUID
    this.receipts = this.load().map((r) => r.dispatchStatus === 'DISPATCHING' ? { ...r, dispatchStatus: 'UNKNOWN', channels: [] } : r)
    this.save()
  }

  async poll(sessionId: string, goalText: string, options: SessionOptions): Promise<void> {
    if (this.polling) return
    this.polling = true
    try {
      if (this.receipts.length) { await this.flush(); return }
      const startedAt = this.now()
      const result = await this.deps.auth.authorizedRequest({ path: `/api/v1/sessions/${sessionId}/interventions`, method: 'GET', expectedStatus: 200, parse: parsePending })
      if (!result.success || !this.deps.isCurrent(sessionId)) return
      const elapsed = this.now() - startedAt
      const context = this.deps.currentContext()
      const next = result.data.find((item) => !this.seen.has(item.interventionId) && context !== null && item.observationContextId === context
        && item.remainingTtlMs !== null && item.remainingTtlMs - elapsed > 0)
      if (!next) return
      this.seen.add(next.interventionId)
      const kind = next.type === 'BREAK_SUGGESTION' ? 'BREAK' : 'OFF_TASK'
      const payload: FloatingShow = { interventionId: next.interventionId, kind, level: options.interventionLevel, goalText, ...COPY[kind === 'BREAK' ? 'BREAK' : options.interventionLevel] }
      // 알림에 채널이 없으면 세션 시작 때 고른 채널을 쓴다. 아는 채널만 쓴다.
      const channels = (next.channels.length ? next.channels : options.notificationChannels).filter((channel) => CHANNELS.includes(channel))
      const receipt: Receipt = { interventionId: next.interventionId, clientDeliveryId: this.uuid(), dispatchStatus: 'DISPATCHING', channels: [] }
      this.receipts.push(receipt)
      this.save()
      const sent: string[] = []
      if (channels.includes('MOLE_UI') && this.deps.show(payload)) sent.push('MOLE_UI')
      // 소리만 골랐어도 소리 있는 알림으로 알린다. 알림과 소리를 함께 골랐으면 알림은 한 번만 띄운다.
      if ((channels.includes('MACOS_NOTIFICATION') || channels.includes('SOUND')) && this.deps.notify(payload, channels.includes('SOUND'))) {
        sent.push(...channels.filter((channel) => channel !== 'MOLE_UI'))
      }
      receipt.dispatchStatus = sent.length ? 'SENT' : 'NOT_SENT'
      receipt.channels = sent
      this.save()
      if (sent.length) log('INFO', 'notify.fire', { component: 'intervention', sessionId, interventionType: next.type, channels: sent })
      await this.flush()
    } finally {
      this.polling = false
    }
  }

  // 보낼 receipt를 차례로 보낸다. 일시 실패면 다음 주기에 같은 값(nonce)으로 다시 보낸다.
  private async flush(): Promise<void> {
    while (this.receipts.length) {
      const r = this.receipts[0]
      if (r.dispatchStatus === 'DISPATCHING') return
      const result = await this.deps.auth.authorizedRequest({
        path: `/api/v1/interventions/${encodeURIComponent(r.interventionId)}/deliveries`, method: 'POST', expectedStatus: 200, parse: () => true, withDeviceId: true,
        body: { clientDeliveryId: r.clientDeliveryId, dispatchStatus: r.dispatchStatus, channels: r.dispatchStatus === 'SENT' ? r.channels : [] },
      })
      if (!result.success && result.error.retryable) return
      // 성공했거나 다시 보내도 받지 않는 실패(형식·권한·충돌)면 버린다.
      this.receipts.shift()
      this.save()
    }
  }

  private load(): Receipt[] {
    try {
      const data = JSON.parse(readFileSync(this.deps.journalFile, 'utf8')) as unknown
      return Array.isArray(data) ? data.filter((r): r is Receipt => typeof r?.interventionId === 'string' && typeof r?.clientDeliveryId === 'string' && typeof r?.dispatchStatus === 'string' && Array.isArray(r?.channels)) : []
    } catch {
      return []
    }
  }

  private save(): void {
    try {
      writeFileSync(`${this.deps.journalFile}.tmp`, JSON.stringify(this.receipts))
      renameSync(`${this.deps.journalFile}.tmp`, this.deps.journalFile)
    } catch {
      // ponytail: 저장에 실패하면 이번 실행 안에서만 이어 보낸다. 재시작 뒤 그 시도는 서버에 전송 불명으로 남는다.
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
