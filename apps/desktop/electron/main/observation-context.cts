import { randomUUID } from 'node:crypto'
import { readFileSync, renameSync, writeFileSync } from 'node:fs'
import type { AuthService } from './auth-service.cjs'

export type Observer = 'DESKTOP' | 'EXTENSION' | 'NONE'
type Remote = { observationContextId: string; foregroundSequence: number }

/** PUT·GET observation-context 응답에서 쓰는 값만 확인한다. */
export function parseContext(value: unknown): Remote | null {
  if (typeof value !== 'object' || value === null) return null
  const { observationContextId, foregroundSequence } = value as Record<string, unknown>
  if (typeof observationContextId !== 'string' || !Number.isSafeInteger(foregroundSequence)) return null
  return { observationContextId, foregroundSequence: foregroundSequence as number }
}

// lease는 30초다. heartbeat(10초)가 이어 주지 못한 채 25초가 지나면 끊긴 것으로 보고 새 번호로 다시 등록한다.
const RENEW_LIMIT_MS = 25_000

type Current = { sessionId: string; key: string; observer: Observer; sequence: number; nonce: string; id: string | null; renewedAt: number }

/**
 * 지금 앞에 떠 있는 화면(전경 context)을 서버에 알린다 (API_CONTRACT 실제 알림 연결, #182).
 * - 앱·창이 바뀌거나 확인할 수 없게 되면 순번을 1 늘려 `PUT /sessions/{id}/observation-context`. 앱 이름·창 제목은 보내지 않는다.
 * - 순번은 세션마다 파일에 둔다. 실패하면 같은 번호·nonce로 다시 보낸다. 늦게 온 작은 번호 응답은 버린다.
 * - 확정된 context만 heartbeat·분석 요청에 붙인다(`currentId`).
 */
export class ObservationContext {
  private current: Current | null = null
  private sending = false

  constructor(
    private readonly auth: Pick<AuthService, 'authorizedRequest'>,
    private readonly file: string,
    private readonly now: () => number = () => performance.now(),
    private readonly uuid: () => string = randomUUID,
  ) {}

  /** 1초마다 맨 앞 화면을 넣는다. key가 null이면 확인할 수 없는 상태(NONE)다. */
  async update(sessionId: string, key: string | null, observer: Observer): Promise<void> {
    const c = this.current
    const nextKey = key ?? ''
    const stale = c?.id !== null && c !== null && this.now() - c.renewedAt > RENEW_LIMIT_MS
    if (!c || c.sessionId !== sessionId || c.key !== nextKey || c.observer !== observer || stale) {
      this.current = { sessionId, key: nextKey, observer, sequence: this.nextSequence(sessionId), nonce: this.uuid(), id: null, renewedAt: this.now() }
    }
    await this.register()
  }

  /** 확정된 현재 context ID. 바뀌는 중이거나 모르면 null이라 heartbeat·분석에 붙이지 않는다. */
  currentId(): string | null {
    return this.current?.observer === 'NONE' ? null : this.current?.id ?? null
  }

  /** heartbeat가 이 ID로 성공하면 lease가 이어진 것으로 본다. */
  renewed(id: string): void {
    if (this.current?.id === id) this.current.renewedAt = this.now()
  }

  /** 세션이 RUNNING이 아니면 비운다. 다시 시작하면 새 번호로 등록한다. */
  reset(): void {
    this.current = null
  }

  private async register(): Promise<void> {
    const c = this.current
    if (!c || c.id !== null || this.sending) return
    this.sending = true
    try {
      const result = await this.auth.authorizedRequest({
        path: `/api/v1/sessions/${c.sessionId}/observation-context`, method: 'PUT', expectedStatus: 200, parse: parseContext, withDeviceId: true,
        body: { clientContextId: c.nonce, foregroundSequence: c.sequence, observer: c.observer },
      })
      // [주의] 기다리는 사이 화면이 또 바뀌었으면 이 응답은 지난 화면 것이라 쓰지 않는다.
      if (this.current !== c) return
      // 저장된 순번을 잃어 서버가 더 작은 번호로 거절하면, 번호를 올려 새 전환으로 다시 보낸다.
      if (!result.success) { if (result.error.code === 'IDEMPOTENCY_CONFLICT') { c.sequence = this.nextSequence(c.sessionId); c.nonce = this.uuid() } return }
      if (result.data.foregroundSequence !== c.sequence) return
      c.id = result.data.observationContextId
      c.renewedAt = this.now()
    } finally {
      this.sending = false
    }
  }

  private nextSequence(sessionId: string): number {
    let saved = 0
    try {
      const data = JSON.parse(readFileSync(this.file, 'utf8')) as { sessionId?: unknown; sequence?: unknown }
      if (data.sessionId === sessionId && Number.isSafeInteger(data.sequence)) saved = data.sequence as number
    } catch {
      // 파일이 없으면 이 세션의 첫 번호다.
    }
    const sequence = saved + 1
    try {
      writeFileSync(`${this.file}.tmp`, JSON.stringify({ sessionId, sequence }))
      renameSync(`${this.file}.tmp`, this.file)
    } catch {
      // ponytail: 저장 실패 시 이번 실행은 메모리 순번으로 이어 간다. 재시작 뒤 작은 번호가 409로 거절되면 다음 화면 변경에서 다시 올라간다.
    }
    return sequence
  }
}
