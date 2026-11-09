import type { AuthService, AuthorizedRequest } from './auth-service.cjs'
import type { ApiResult } from './auth-api.cjs'
import type { SessionSnapshot } from '../shared/session-contract.cjs'

/**
 * 시연 모드 (Issue #189). `pnpm dev:demo`(FOCUSON_DEMO=1)로 개발 실행할 때만 켠다. 배포 앱에서는 index.cts가 켜지 않는다.
 * 서버에 아직 없는 API만 가짜 응답으로 채우고, 나머지 요청은 모두 실제 서버로 보낸다(서버 주소는 바꾸지 않는다).
 * - 이탈 알림: 단축키로 triggerAlert()를 부르면 진행 중 세션에 OFF_TASK 알림 하나를 만든다.
 * - 세션 종료·결과 요약·집중 달력·하루 기록: 실제 서버를 먼저 부르고, 실패하면 이 실행에서 기록한 세션으로 대신 답한다.
 * - 학습 정리(learning-summary)는 건드리지 않는다. 서버에 없으면 리포트에 "확인하지 못했어요"로 보인다.
 * ponytail: 기록은 메모리에만 둔다. 앱을 다시 켜면 가짜 리포트·기록이 사라진다. 서버 API가 생기면 이 파일을 지운다.
 */

type Played = {
  sessionId: string
  goalText: string
  targetDurationMs: number
  startedAt: string
  endedAt: string | null
  activeMs: number
  pauseCount: number
  alerts: number
}

const SESSION_PATH = /^\/api\/v1\/sessions\/([^/?]+)\/(interventions|end|summary|pause)$/
const DEMO_ACK = /^\/api\/v1\/interventions\/demo-[^/]+\/acknowledgements$/

/** 로컬 날짜(YYYY-MM-DD). 리포트 날짜는 사용자 기기 시간대를 따른다. */
const localDay = (iso: string) => new Date(iso).toLocaleDateString('sv-SE')

export function installDemoMode(auth: AuthService, snapshot: () => SessionSnapshot | null): { triggerAlert(): boolean } {
  const real = auth.authorizedRequest.bind(auth)
  const sessions = new Map<string, Played>()
  const pending = new Map<string, string[]>()
  let alertSeq = 0

  const parsed = <T,>(request: AuthorizedRequest<T>, value: unknown): ApiResult<T> => {
    const data = request.parse(value)
    return data === null ? { success: false, error: { code: 'LOCAL_APP_ERROR', retryable: false } } : { success: true, data }
  }
  const remember = (current: SessionSnapshot): Played => {
    const known = sessions.get(current.sessionId)
    if (known) return known
    const played: Played = { sessionId: current.sessionId, goalText: current.goalText, targetDurationMs: current.targetDurationMs, startedAt: new Date(Date.now() - current.accumulatedActiveMs).toISOString(), endedAt: null, activeMs: current.accumulatedActiveMs, pauseCount: 0, alerts: 0 }
    sessions.set(current.sessionId, played)
    return played
  }
  const ended = () => [...sessions.values()].filter((s): s is Played & { endedAt: string } => s.endedAt !== null)

  auth.authorizedRequest = async <T,>(request: AuthorizedRequest<T>): Promise<ApiResult<T>> => {
    const path = request.path
    if (request.method === 'POST' && DEMO_ACK.test(path)) return parsed(request, {})

    // 세션 시작은 실제 서버로 보내고, 시작 시각만 기록해 둔다.
    if (request.method === 'POST' && path === '/api/v1/sessions') {
      const result = await real(request)
      const data = result.success ? result.data as { sessionId?: string; startedAt?: string; targetDurationMs?: number } : null
      if (data?.sessionId) sessions.set(data.sessionId, { sessionId: data.sessionId, goalText: snapshot()?.goalText ?? '', targetDurationMs: data.targetDurationMs ?? 0, startedAt: data.startedAt ?? new Date().toISOString(), endedAt: null, activeMs: 0, pauseCount: 0, alerts: 0 })
      return result
    }

    // 앱 안에서만 끝낸 세션은 서버가 아직 진행 중으로 알려 줘도 다시 불러오지 않는다.
    // 다시 불러오면 heartbeat가 이어져 서버가 그 세션을 자동 종료(무신호 3분)하지 못한다.
    if (request.method === 'GET' && path === '/api/v1/sessions/active') {
      const result = await real(request)
      const remote = result.success ? result.data as { sessionId?: string } | null : null
      return remote?.sessionId && sessions.get(remote.sessionId)?.endedAt ? parsed(request, null) : result
    }

    const match = SESSION_PATH.exec(path)
    if (match) {
      const [, sessionId, action] = match
      if (action === 'interventions' && request.method === 'GET') {
        const ids = pending.get(sessionId) ?? []
        pending.delete(sessionId)
        return parsed(request, ids.map((interventionId) => ({ interventionId, type: 'OFF_TASK', status: 'PENDING', channels: [] })))
      }
      if (action === 'pause' && request.method === 'POST') {
        const result = await real(request)
        const played = sessions.get(sessionId)
        if (result.success && played) played.pauseCount += 1
        return result
      }
      if (action === 'end' && request.method === 'POST') {
        const current = snapshot()
        const result = await real(request)
        if (!current || current.sessionId !== sessionId) return result
        const played = remember(current)
        played.goalText = current.goalText
        played.endedAt = new Date().toISOString()
        played.activeMs = current.accumulatedActiveMs
        // #164 병합 전에는 서버에 종료 API가 없다. 앱 안에서만 끝낸다(서버 세션은 진행 중으로 남는다).
        return result.success ? result : parsed(request, { version: 0, status: 'ENDED', accumulatedActiveMs: current.accumulatedActiveMs })
      }
      if (action === 'summary' && request.method === 'GET') {
        const result = await real(request)
        const played = sessions.get(sessionId)
        if (result.success || !played?.endedAt) return result
        // 이탈 알림이 있었으면 그만큼 다른 화면을 본 것으로 그린다(시연용 값).
        const unrelated = played.alerts ? Math.min(120_000 * played.alerts, Math.floor(played.activeMs * 0.2)) : 0
        const elapsed = Date.parse(played.endedAt) - Date.parse(played.startedAt)
        return parsed(request, {
          sessionId, goalSnapshot: played.goalText, status: 'ENDED', startedAt: played.startedAt, endedAt: played.endedAt,
          targetDurationMs: played.targetDurationMs, accumulatedActiveMs: played.activeMs, totalPausedMs: Math.max(0, elapsed - played.activeMs),
          relatedDurationMs: played.activeMs - unrelated, unrelatedDurationMs: unrelated, uncertainDurationMs: 0,
          extensionCount: 0, extendedDurationMs: 0, pauseCount: played.pauseCount, autoPauseCount: 0, autoPauseReasons: [], interventionCount: played.alerts,
        })
      }
    }

    if (request.method === 'GET' && path.startsWith('/api/v1/reports/')) {
      const result = await real(request)
      if (result.success) return result
      const query = new URL(path, 'http://demo').searchParams
      const month = query.get('month')
      const date = query.get('date')
      if (month) {
        const days = new Map<string, { totalActiveMs: number; sessionCount: number }>()
        for (const s of ended().filter((s) => localDay(s.endedAt).startsWith(month))) {
          const day = days.get(localDay(s.endedAt)) ?? { totalActiveMs: 0, sessionCount: 0 }
          days.set(localDay(s.endedAt), { totalActiveMs: day.totalActiveMs + s.activeMs, sessionCount: day.sessionCount + 1 })
        }
        return parsed(request, { month, studyDayCount: days.size, days: [...days].map(([day, v]) => ({ date: day, ...v, hasStudyActivity: true })) })
      }
      if (date) {
        const list = ended().filter((s) => localDay(s.endedAt) === date)
        return parsed(request, {
          date, totalActiveMs: list.reduce((sum, s) => sum + s.activeMs, 0),
          sessions: list.map((s) => ({ sessionId: s.sessionId, goalTextSnapshot: s.goalText, startedAt: s.startedAt, endedAt: s.endedAt, accumulatedActiveMs: s.activeMs })),
        })
      }
      return result
    }

    return real(request)
  }

  return {
    /** 진행 중 세션에 이탈 알림 하나를 만든다. 진행 중이 아니면 false. */
    triggerAlert() {
      const current = snapshot()
      if (current?.status !== 'RUNNING') return false
      remember(current).alerts += 1
      alertSeq += 1
      pending.set(current.sessionId, [...(pending.get(current.sessionId) ?? []), `demo-${alertSeq}`])
      return true
    },
  }
}
