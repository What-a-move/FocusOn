// 카메라 분석 상태 기계 (Notion 카메라 분석 3·5·6장, #222).
// 도우미가 2초마다 주는 추상 신호로 이탈 지속 시간을 잰다. 시간은 monotonic(now)으로만 센다.

export type CameraSample = { faceCount: number; eyesVisible: boolean; gaze: 'SCREEN' | 'AWAY' | 'UNKNOWN'; quality: 'GOOD' | 'LOW' | 'UNUSABLE' }
export type CameraState = 'CAMERA_STARTING' | 'CAMERA_ATTENTION' | 'CAMERA_AWAY_CANDIDATE' | 'CAMERA_ALERT_CANDIDATE' | 'CAMERA_AWAY_CONFIRMED' | 'CAMERA_UNKNOWN' | 'LOCAL_CAMERA_UNAVAILABLE'
/** 도우미 응답 하나. 켜는 중(STARTING)과 실패(UNAVAILABLE)도 들어온다. */
export type CameraReading = { kind: 'SAMPLE'; sample: CameraSample } | { kind: 'STARTING' } | { kind: 'UNAVAILABLE' }

export const CANDIDATE_MS = 60_000
export const CONFIRM_MS = 90_000
// 카메라 알림끼리의 간격. 서버 알림 cooldown(첫 SENT부터 5분)과 같게 둔다.
export const CAMERA_COOLDOWN_MS = 5 * 60_000

const GAZES = ['SCREEN', 'AWAY', 'UNKNOWN'] as const
const QUALITIES = ['GOOD', 'LOW', 'UNUSABLE'] as const

/** 도우미 응답에서 정한 값만 꺼낸다. 모르는 모양이면 UNAVAILABLE로 본다. */
export function parseCameraReply(reply: Record<string, unknown> | null): CameraReading {
  if (!reply) return { kind: 'UNAVAILABLE' }
  if (reply.state === 'STARTING') return { kind: 'STARTING' }
  const { faceCount, eyesVisible, gaze, quality } = reply
  if (reply.state !== 'SAMPLE' || !Number.isSafeInteger(faceCount) || typeof eyesVisible !== 'boolean'
    || !GAZES.includes(gaze as CameraSample['gaze']) || !QUALITIES.includes(quality as CameraSample['quality'])) return { kind: 'UNAVAILABLE' }
  return { kind: 'SAMPLE', sample: { faceCount: faceCount as number, eyesVisible, gaze: gaze as CameraSample['gaze'], quality: quality as CameraSample['quality'] } }
}

/**
 * 유효한 AWAY가 끊기지 않고 60초면 후보, 90초면 확정이다.
 * - SCREEN 복귀, 신호 불량(LOW·UNUSABLE·UNKNOWN), 켜는 중, 카메라 실패는 누적을 지운다. 불확실한 신호를 AWAY로 바꾸지 않는다(설계서 4.3).
 * - 최신 화면 판정이 RELATED이면(screenRelated) 누적을 지우고 알리지 않는다(설계서 5.3).
 * - 한 번 이탈한 동안 알림은 한 번이고, 카메라 알림 사이는 5분을 둔다.
 */
export class CameraMonitor {
  private awaySince: number | null = null
  private alerted = false
  private lastAlertAt: number | null = null
  private state: CameraState = 'CAMERA_STARTING'

  constructor(private readonly now: () => number) {}

  /** 새 상태와, 이번에 포키를 띄울지(alert)를 돌려준다. canShow는 세션·화면·포키 창 등 바깥 최종 게이트다. */
  update(reading: CameraReading, { screenRelated, canShow }: { screenRelated: boolean; canShow: boolean }): { state: CameraState; alert: boolean } {
    const t = this.now()
    const away = reading.kind === 'SAMPLE' && reading.sample.quality === 'GOOD' && reading.sample.gaze === 'AWAY'
    if (!away || screenRelated) {
      this.awaySince = null
      this.alerted = false
      this.state = reading.kind === 'STARTING' ? 'CAMERA_STARTING'
        : reading.kind === 'UNAVAILABLE' ? 'LOCAL_CAMERA_UNAVAILABLE'
        // 화면 판정이 RELATED라 지운 AWAY도 이탈로 보지 않으므로 ATTENTION이다.
        : reading.sample.quality === 'GOOD' && reading.sample.gaze !== 'UNKNOWN' ? 'CAMERA_ATTENTION'
        : 'CAMERA_UNKNOWN'
      return { state: this.state, alert: false }
    }
    this.awaySince ??= t
    const elapsed = t - this.awaySince
    this.state = elapsed >= CONFIRM_MS ? 'CAMERA_AWAY_CONFIRMED' : elapsed >= CANDIDATE_MS ? 'CAMERA_ALERT_CANDIDATE' : 'CAMERA_AWAY_CANDIDATE'
    const cooled = this.lastAlertAt === null || t - this.lastAlertAt >= CAMERA_COOLDOWN_MS
    if (this.state !== 'CAMERA_AWAY_CONFIRMED' || this.alerted || !cooled || !canShow) return { state: this.state, alert: false }
    this.alerted = true
    this.lastAlertAt = t
    return { state: this.state, alert: true }
  }

  current(): CameraState {
    return this.state
  }

  /** 세션이 멈추거나 끝나거나 카메라를 끄면 처음부터 센다. cooldown은 앱이 켜져 있는 동안 유지한다. */
  reset(): void {
    this.awaySince = null
    this.alerted = false
    this.state = 'CAMERA_STARTING'
  }
}
