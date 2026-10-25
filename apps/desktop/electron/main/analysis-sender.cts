import { randomUUID } from 'node:crypto'
import type { AuthService } from './auth-service.cjs'
import type { Observation } from './screen-observer.cjs'

export type Relation = 'RELATED' | 'UNRELATED' | 'UNCERTAIN' | 'EXCLUDED' | 'PRIVACY_BLOCKED'
export type AnalysisRun = { analysisRunId: string; relation: Relation; confidence: number | null; reasonCode: string | null; observedAt: string }

const RELATIONS: readonly Relation[] = ['RELATED', 'UNRELATED', 'UNCERTAIN', 'EXCLUDED', 'PRIVACY_BLOCKED']
// ponytail: 서버 요청 제한이 분 단위라 429면 1분 쉰다. 공통 요청 함수가 Retry-After를 넘기게 되면 그 값을 쓴다.
const RATE_LIMIT_PAUSE_MS = 60_000

export function parseAnalysisRun(value: unknown): AnalysisRun | null {
  if (typeof value !== 'object' || value === null) return null
  const { analysisRunId, relation, confidence, reasonCode, observedAt } = value as Record<string, unknown>
  if (typeof analysisRunId !== 'string' || !RELATIONS.includes(relation as Relation) || typeof observedAt !== 'string') return null
  return {
    analysisRunId, relation: relation as Relation, observedAt,
    confidence: typeof confidence === 'number' ? confidence : null,
    reasonCode: typeof reasonCode === 'string' ? reasonCode : null,
  }
}

/**
 * 화면 관측 1건을 `POST /sessions/{id}/analysis-runs`로 보낸다 (결정 024, Notion 화면 분석 4.1·7.3).
 * - 관측마다 새 clientRunId, 세션 안에서 1부터 늘어나는 sequence를 붙인다.
 * - 자동으로 다시 보내지 않고 쌓아 두지 않는다. 실패하면 다음 화면 변경부터 새로 보낸다.
 *   서버가 기록하지 못한 시간은 판단 보류와 같아 알림으로 이어지지 않는다.
 */
export class AnalysisSender {
  private session: { id: string; sequence: number } | null = null
  private pausedUntil = 0

  constructor(private readonly auth: Pick<AuthService, 'authorizedRequest'>, private readonly now: () => number = () => performance.now()) {}

  async send(sessionId: string, observation: Observation): Promise<AnalysisRun | null> {
    if (this.now() < this.pausedUntil) return null
    if (this.session?.id !== sessionId) this.session = { id: sessionId, sequence: 0 }
    const sequence = ++this.session.sequence
    const base = { clientRunId: randomUUID(), contentSource: 'APP_META', observedAt: observation.observedAt, sequence }
    // [주의] 제외·민감 화면은 상태만 보낸다. 앱 이름·창 제목도 넣지 않는다.
    const body = observation.guardStatus
      ? { ...base, guardStatus: observation.guardStatus }
      : { ...base, guardStatus: null, metadata: observation.metadata }
    const result = await this.auth.authorizedRequest({
      path: `/api/v1/sessions/${sessionId}/analysis-runs`, method: 'POST', expectedStatus: 200, parse: parseAnalysisRun, body, withDeviceId: true,
    })
    if (result.success) return result.data
    if (result.error.code === 'TOO_MANY_REQUESTS') this.pausedUntil = this.now() + RATE_LIMIT_PAUSE_MS
    return null
  }
}
