import { randomUUID } from 'node:crypto'
import type { ApiResult } from './auth-api.cjs'
import type { AuthService } from './auth-service.cjs'
import { parseCommandResult, parseHeartbeat, parseRemoteSession } from '../shared/session-contract.cjs'
import type { CommandResult, RemoteSession, SessionSnapshot, SessionStatus, StartedSession } from '../shared/session-contract.cjs'

type Command = 'pause' | 'resume' | 'extend' | 'end'
type State = {
  sessionId: string
  goalText: string
  status: SessionStatus
  version: number
  targetDurationMs: number
  /** RUNNING이 아니거나 마지막으로 멈춘 시점까지 확정된 누적 시간 */
  baseActiveMs: number
  /** RUNNING일 때 다시 센 시작점 (monotonic ms) */
  resumedAt: number | null
  error: string | null
  busy: boolean
}

const NONE = Symbol('no-active-session')
const SCREEN_PERMISSION_REVOKED = 'LOCAL_SCREEN_PERMISSION_REVOKED'
const ACTIVE: readonly SessionStatus[] = ['RUNNING', 'PAUSED', 'AUTO_PAUSED', 'AWAITING_END_CONFIRMATION', 'ENDING']
const isActive = (status: SessionStatus) => ACTIVE.includes(status)

/**
 * 현재 학습 세션의 타이머와 상태 명령을 Main에서 맡는다 (로그인 설계서 8장, 세션 상태 전이 정책).
 * - 누적 시간은 monotonic clock으로만 센다. 시스템 시계를 바꿔도 변하지 않는다.
 * - 상태 명령은 Idempotency-Key·If-Match-Version을 붙이고 자동 재전송하지 않는다. 같은 의도(같은 명령·사유·연장 시간)로 다시 누를 때만
 *   처음 보낸 키와 본문을 그대로 다시 보낸다. 의도가 바뀌면 새 키를 만든다.
 * - 목표 시간에 닿으면 누적을 멈추고 사용자 확인(끝내기·더 공부하기)을 기다린다.
 */
export class SessionRuntime {
  private state: State | null = null
  private pending = new Map<Command, { intent: string; key: string; body?: object }>()
  // 서버에 아직 알리지 못한 로컬 이벤트. 다음 heartbeat 주기에 순서대로 다시 보낸다.
  private localEvents: { sessionId: string; localEventCode: string; occurredAt: string }[] = []
  private flushing = false

  constructor(
    private readonly auth: Pick<AuthService, 'authorizedRequest'>,
    private readonly emit: (snapshot: SessionSnapshot | null) => void,
    private readonly ready: { screen: () => boolean; notification: () => boolean },
    private readonly now: () => number = () => performance.now(),
    private readonly wallClock: () => Date = () => new Date(),
  ) {}

  snapshot(): SessionSnapshot | null {
    const s = this.state
    if (!s) return null
    return { sessionId: s.sessionId, goalText: s.goalText, status: s.status, targetDurationMs: s.targetDurationMs, accumulatedActiveMs: this.activeMs(), error: s.error, busy: s.busy }
  }

  /** 세션 설정에서 시작에 성공하면 그 응답으로 타이머를 시작한다. */
  begin(started: StartedSession, goalText: string): void {
    this.pending.clear()
    this.state = {
      sessionId: started.sessionId, goalText, status: 'RUNNING', version: started.version, targetDurationMs: started.targetDurationMs,
      baseActiveMs: started.accumulatedActiveMs, resumedAt: this.now(), error: null, busy: false,
    }
    this.publish()
  }

  /** 앱을 다시 켰을 때 서버의 활성 세션을 이어받는다. 실행 중이었다면 사용자 확인 없이 다시 세지 않도록 APP_RESTART로 멈춘다. */
  async restore(): Promise<ApiResult<SessionSnapshot | null>> {
    if (this.state && isActive(this.state.status)) return { success: true, data: this.snapshot() }
    const before = this.state
    // 활성 세션이 없으면 서버는 204를 준다(parse(null) → NONE).
    const result = await this.auth.authorizedRequest<RemoteSession | typeof NONE>({
      path: '/api/v1/sessions/active', method: 'GET', expectedStatus: 200, parse: (value) => value === null ? NONE : parseRemoteSession(value),
    })
    // [주의] 조회하는 사이에 새 세션이 시작됐으면 늦게 온 복구 결과로 덮어쓰지 않는다.
    if (this.state !== before) return { success: true, data: this.snapshot() }
    if (!result.success) return result
    if (result.data === NONE || !isActive(result.data.status)) { this.state = null; this.publish(); return { success: true, data: null } }
    const remote = result.data
    this.pending.clear()
    this.state = {
      sessionId: remote.sessionId, goalText: remote.goalText, status: remote.status, version: remote.version,
      // [주의] 꺼져 있던 동안의 시간은 서버가 마지막으로 확정한 값만 쓴다. 로컬에서 추정해 더하지 않는다(resumedAt 없음).
      targetDurationMs: remote.targetDurationMs, baseActiveMs: remote.accumulatedActiveMs, resumedAt: null, error: null, busy: false,
    }
    if (remote.status === 'RUNNING') await this.pause('APP_RESTART')
    this.publish()
    return { success: true, data: this.snapshot() }
  }

  /** 로그아웃하면 다음 사용자가 이전 세션을 보지 않도록 비운다. */
  reset(): void {
    this.state = null
    this.pending.clear()
    this.localEvents = []
    this.publish()
  }

  /** 1초마다 불린다. 목표 시간에 닿으면 누적을 멈추고 확인을 기다린다. */
  tick(): void {
    const s = this.state
    if (!s || s.status !== 'RUNNING' || this.activeMs() < s.targetDurationMs) return
    s.baseActiveMs = s.targetDurationMs
    s.resumedAt = null
    s.status = 'AWAITING_END_CONFIRMATION'
    this.publish()
  }

  /** 10초마다 불린다. 서버가 세션을 살아 있다고 보도록 Desktop이 확정한 누적 시간을 보낸다. */
  async heartbeat(): Promise<void> {
    void this.flushLocalEvents()
    const s = this.state
    if (!s || !isActive(s.status) || s.busy) return
    const result = await this.auth.authorizedRequest({
      path: `/api/v1/sessions/${s.sessionId}/heartbeats`, method: 'POST', expectedStatus: 200, parse: parseHeartbeat, withDeviceId: true,
      body: { clientObservedAt: this.wallClock().toISOString(), accumulatedActiveMs: this.activeMs() },
    })
    if (this.state !== s) return
    if (!result.success && result.error.code === 'SESSION_ABANDONED') {
      // 서버가 응답 없음으로 이미 끝낸 세션은 되살리지 않는다. 서버가 확정한 시간만 남는다.
      this.settle('ABANDONED')
      this.publish()
    }
  }

  pause(reason: 'USER' | 'APP_RESTART' = 'USER'): Promise<ApiResult<SessionSnapshot>> {
    return this.command('pause', reason, ['RUNNING'], (s) => ({ path: `/api/v1/sessions/${s.sessionId}/pause`, body: { pauseReason: reason, accumulatedActiveMs: this.activeMs() } }), (s, data) => {
      this.settle(data.status ?? (reason === 'USER' ? 'PAUSED' : 'AUTO_PAUSED'), data.accumulatedActiveMs)
    })
  }

  /**
   * 세션 중 화면 기록 권한이 사라지면 서버 응답을 기다리지 않고 바로 멈춘다(Notion 화면 분석 7.2).
   * 서버에는 local-events로 알리고, 서버가 AUTO_PAUSED로 맞춘다. 권한을 다시 켜야 재개할 수 있다.
   */
  screenPermissionLost(): void {
    const s = this.state
    if (!s || s.status !== 'RUNNING') return
    this.settle('AUTO_PAUSED')
    s.error = SCREEN_PERMISSION_REVOKED
    this.localEvents.push({ sessionId: s.sessionId, localEventCode: SCREEN_PERMISSION_REVOKED, occurredAt: this.wallClock().toISOString() })
    this.publish()
    void this.flushLocalEvents()
  }

  resume(): Promise<ApiResult<SessionSnapshot>> {
    if (this.state && !this.ready.screen()) return Promise.resolve(this.fail(SCREEN_PERMISSION_REVOKED))
    // [주의] 서버는 알림 권한을 검증하지 않는다. 시작과 같이 Desktop이 사용자 확인 기록으로 막는다(결정 022).
    if (this.state && !this.ready.notification()) return Promise.resolve(this.fail('LOCAL_NOTIFICATION_NOT_CONFIRMED'))
    return this.command('resume', 'resume', ['PAUSED', 'AUTO_PAUSED'], (s) => ({ path: `/api/v1/sessions/${s.sessionId}/resume`, body: { readiness: { screenAnalysisReady: this.ready.screen() } } }), (s) => {
      s.status = 'RUNNING'
      s.resumedAt = this.now()
    })
  }

  extend(minutes: number): Promise<ApiResult<SessionSnapshot>> {
    return this.command('extend', String(minutes), ['RUNNING', 'PAUSED', 'AUTO_PAUSED', 'AWAITING_END_CONFIRMATION'], (s) => ({ path: `/api/v1/sessions/${s.sessionId}/extend`, body: { additionalDurationMs: minutes * 60_000 } }), (s, data) => {
      s.targetDurationMs = data.targetDurationMs ?? s.targetDurationMs + minutes * 60_000
      // 시간이 끝나 기다리던 중이면 연장과 함께 다시 센다.
      if (s.status === 'AWAITING_END_CONFIRMATION') { s.status = 'RUNNING'; s.resumedAt = this.now() }
    })
  }

  end(): Promise<ApiResult<SessionSnapshot>> {
    return this.command('end', 'end', ACTIVE.filter((status) => status !== 'ENDING'), (s) => ({ path: `/api/v1/sessions/${s.sessionId}/end` }), (s, data) => {
      this.settle('ENDED', data.accumulatedActiveMs)
    })
  }

  private activeMs(): number {
    const s = this.state
    if (!s) return 0
    const running = s.status === 'RUNNING' && s.resumedAt !== null ? this.now() - s.resumedAt : 0
    // 서버와 화면에는 정수 ms만 보낸다.
    return Math.floor(Math.min(s.targetDurationMs, s.baseActiveMs + Math.max(0, running)))
  }

  /** 지금까지 센 시간을 확정하고 멈춘다. 서버가 확정값을 주면 그 값을 쓴다. */
  private settle(status: SessionStatus, serverActiveMs?: number): void {
    const s = this.state
    if (!s) return
    s.baseActiveMs = serverActiveMs ?? this.activeMs()
    s.resumedAt = null
    s.status = status
  }

  private fail(code: string): ApiResult<SessionSnapshot> {
    if (this.state) { this.state.error = code; this.publish() }
    return { success: false, error: { code, retryable: false } }
  }

  private async command(
    name: Command,
    intent: string,
    allowed: readonly SessionStatus[],
    request: (s: State) => { path: string; body?: object },
    apply: (s: State, data: CommandResult) => void,
  ): Promise<ApiResult<SessionSnapshot>> {
    const s = this.state
    if (!s) return { success: false, error: { code: 'SESSION_NOT_FOUND', retryable: false } }
    if (s.busy || !allowed.includes(s.status)) return this.fail('SESSION_STATE_INVALID')
    // 같은 의도로 실패 뒤 다시 누르면 처음 보낸 키와 본문을 그대로 보내 서버가 두 번 처리하지 않게 한다.
    // (일시정지 본문의 누적 시간은 시간이 지나면 달라지므로 본문도 처음 것을 쓴다.)
    const { path, body: freshBody } = request(s)
    const saved = this.pending.get(name)
    const attempt = saved && saved.intent === intent ? saved : { intent, key: randomUUID(), body: freshBody }
    this.pending.set(name, attempt)
    const body = attempt.body
    const key = attempt.key
    s.busy = true
    s.error = null
    this.publish()
    const result = await this.auth.authorizedRequest({
      path, method: 'POST', expectedStatus: 200, parse: parseCommandResult, body,
      headers: { 'Idempotency-Key': key, 'If-Match-Version': String(s.version) },
    })
    if (this.state !== s) return { success: false, error: { code: 'SESSION_NOT_FOUND', retryable: false } }
    s.busy = false
    if (!result.success) {
      if (result.error.code === 'SESSION_VERSION_CONFLICT') await this.refresh(s)
      return this.fail(result.error.code)
    }
    this.pending.delete(name)
    s.version = result.data.version
    apply(s, result.data)
    this.publish()
    return { success: true, data: this.snapshot() as SessionSnapshot }
  }

  /** 다른 기기에서 상태가 바뀌었으면 서버 최신값으로 맞춘다. 로컬 값으로 서버를 덮어쓰지 않는다. */
  private async refresh(s: State): Promise<void> {
    const latest = await this.auth.authorizedRequest({ path: `/api/v1/sessions/${s.sessionId}`, method: 'GET', expectedStatus: 200, parse: parseRemoteSession })
    if (!latest.success || this.state !== s) return
    this.pending.clear()
    s.version = latest.data.version
    s.targetDurationMs = latest.data.targetDurationMs
    s.baseActiveMs = latest.data.accumulatedActiveMs
    s.status = latest.data.status
    s.resumedAt = latest.data.status === 'RUNNING' ? this.now() : null
  }

  private async flushLocalEvents(): Promise<void> {
    if (this.flushing) return
    this.flushing = true
    try {
      while (this.localEvents.length) {
        const { sessionId, ...body } = this.localEvents[0]
        const result = await this.auth.authorizedRequest({ path: `/api/v1/sessions/${sessionId}/local-events`, method: 'POST', expectedStatus: 200, parse: () => true, body, withDeviceId: true })
        // 일시적인 실패면 다음 주기에 다시 보낸다. 세션이 없는 등 다시 보내도 안 되는 실패는 버린다.
        if (!result.success && result.error.retryable) break
        this.localEvents.shift()
      }
    } finally {
      this.flushing = false
    }
  }

  private publish(): void {
    this.emit(this.snapshot())
  }
}
