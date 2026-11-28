import type { ApiResult } from './auth-api.cjs'
import type { ReminderEstimate } from '../shared/report-contract.cjs'
import type { SessionSnapshot } from '../shared/session-contract.cjs'

type ReminderSession = Pick<SessionSnapshot, 'sessionId' | 'status' | 'accumulatedActiveMs'>

export class PreDistractionReminder {
  private attempt: { key: string; at: number | null; checking: boolean } | null = null

  constructor(private readonly options: {
    session(): ReminderSession | null
    account(): string | null
    estimate(): Promise<ApiResult<ReminderEstimate>>
    enabled(): Promise<boolean>
    deliver(account: string, sessionId: string): void
    onError(): void
  }) {}

  async tick(): Promise<void> {
    const session = this.options.session()
    const account = this.options.account()
    if (!account || !session || session.status !== 'RUNNING') return
    const key = JSON.stringify([account, session.sessionId])

    try {
      if (this.attempt?.key !== key) {
        const attempt = { key, at: null as number | null, checking: false }
        this.attempt = attempt
        const estimate = await this.options.estimate()
        if (estimate.success && estimate.data.status === 'ready' && session.accumulatedActiveMs < estimate.data.activeMs) {
          attempt.at = estimate.data.activeMs
        }
        return
      }

      const attempt = this.attempt
      const at = attempt.at
      if (at === null || attempt.checking || session.accumulatedActiveMs < at) return
      attempt.checking = true
      try {
        const enabled = await this.options.enabled()
        const current = this.options.session()
        if (this.attempt !== attempt || this.options.account() !== account || current?.sessionId !== session.sessionId) return
        if (current.status !== 'RUNNING') return
        attempt.at = null
        if (enabled && current.accumulatedActiveMs < at + 15_000) this.options.deliver(account, current.sessionId)
      } finally {
        attempt.checking = false
      }
    } catch {
      if (this.attempt?.key === key) this.attempt.at = null
      this.options.onError()
    }
  }
}
