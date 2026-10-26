// Chrome 탭 분석 흐름 (화면 분석 3단계, 결정 013·014). Chrome API는 Deps로 받아 테스트할 수 있게 한다.
// [주의] 이 파일은 node --test로도 실행하므로 상대 import는 확장자(.ts)를 붙인다.
import { cleanPageText, hostOf, isExcludedHost, isPrivateHost, isWebPage, MAX_TEXT_LENGTH } from '../shared/page-rules.ts'

export const DWELL_MS = 15_000
// ponytail: 서버 요청 제한이 분 단위라 429면 1분 쉰다.
const RATE_LIMIT_PAUSE_MS = 60_000

export type Target = { tabId: number; url: string; title: string }
export type Tracking = { key: string; tabId: number; url: string; title: string; navigationId: string; startedAt: number; done: boolean }
type Source = 'TAB_META' | 'CHROME_DOM'
type Guard = 'EXCLUDED' | 'PRIVACY_BLOCKED'
export type RunBody = {
  clientRunId: string; contentSource: Source; observedAt: string; sequence: number; guardStatus: Guard | null
  metadata?: { appName: 'Google Chrome'; pageTitle: string; urlHost: string }; contentPayload?: { text: string }
}
export type RunResult = { ok: true; relation: string } | { ok: false; code: string }
type Sequences = { sessionId: string; TAB_META: number; CHROME_DOM: number }

export type Deps = {
  now(): number
  uuid(): string
  /** 지금 Chrome 창이 맨 앞이고 시크릿이 아닐 때의 활성 탭. 아니면 null. */
  currentTarget(): Promise<Target | null>
  /** 서버 활성 세션. 확인할 수 없으면 null. */
  activeSession(): Promise<{ sessionId: string; status: string } | null>
  /** 서버 제외 도메인(캐시 포함). 한 번도 받지 못했으면 null. */
  excludedDomains(): Promise<string[] | null>
  hasSiteAccess(url: string): Promise<boolean>
  /** 페이지 안에서 민감 입력칸 확인·본문 읽기. 읽을 수 없으면 null. */
  inspect(tabId: number, maxLength: number): Promise<{ sensitive: boolean; text: string } | null>
  post(sessionId: string, body: RunBody): Promise<RunResult>
  loadTracking(): Promise<Tracking | null>
  saveTracking(tracking: Tracking | null): Promise<void>
  loadSequences(): Promise<Sequences | null>
  saveSequences(value: Sequences): Promise<void>
  /** 15초 뒤 다시 확인. Worker가 꺼지면 사라질 수 있어 깨어날 때 resume()으로 이어 간다. */
  schedule(ms: number, run: () => void): void
}

const keyOf = (target: Target) => `${target.tabId}\n${target.url.split('#')[0]}`

/**
 * 1) 탭·주소가 바뀌면 15초를 새로 센다. 2) 15초 뒤 같은 화면·세션 RUNNING이면 판정해 보낸다.
 * 3) 탭 정보 결과가 관련 없음·판단 보류면 본문으로 한 번 더 보낸다. 그 사이 화면이 바뀌었으면 늦은 응답은 버린다.
 */
export class TabAnalyzer {
  private pausedUntil = 0
  private readonly deps: Deps

  // [주의] node --test의 TypeScript 실행은 생성자 매개변수 속성(private readonly deps)을 지원하지 않는다.
  constructor(deps: Deps) {
    this.deps = deps
  }

  /** 탭 전환·이동·창 포커스 변경 때 부른다. */
  async onChange(): Promise<void> {
    const target = await this.deps.currentTarget()
    const tracking = await this.deps.loadTracking()
    if (!target) { if (tracking) await this.deps.saveTracking(null); return }
    if (tracking?.key === keyOf(target)) return
    const next: Tracking = { key: keyOf(target), tabId: target.tabId, url: target.url, title: target.title, navigationId: this.deps.uuid(), startedAt: this.deps.now(), done: false }
    await this.deps.saveTracking(next)
    this.deps.schedule(DWELL_MS, () => { void this.check(next.navigationId) })
  }

  /** Worker가 다시 깨어났을 때(시작·알람) 저장된 15초를 이어 간다. */
  async resume(): Promise<void> {
    const tracking = await this.deps.loadTracking()
    if (!tracking || tracking.done) return
    const left = tracking.startedAt + DWELL_MS - this.deps.now()
    if (left > 0) this.deps.schedule(left, () => { void this.check(tracking.navigationId) })
    else await this.check(tracking.navigationId)
  }

  async check(navigationId: string): Promise<void> {
    const tracking = await this.deps.loadTracking()
    if (!tracking || tracking.done || tracking.navigationId !== navigationId) return
    if (this.deps.now() - tracking.startedAt < DWELL_MS) return
    const target = await this.deps.currentTarget()
    if (!target || keyOf(target) !== tracking.key) return
    // [주의] 판정 중 다시 불려도 두 번 보내지 않게 먼저 끝냄으로 표시한다.
    await this.deps.saveTracking({ ...tracking, done: true })
    const session = await this.deps.activeSession()
    if (session?.status !== 'RUNNING' || this.deps.now() < this.pausedUntil) return

    const guard = await this.guardOf(target)
    if (guard) { await this.send(session.sessionId, 'TAB_META', guard); return }
    const metadata = { appName: 'Google Chrome' as const, pageTitle: target.title, urlHost: hostOf(target.url) ?? '' }
    const meta = await this.send(session.sessionId, 'TAB_META', null, metadata)
    // 탭 정보만으로 관련 있음이면 끝낸다. 관련 없음·판단 보류는 본문으로 확인한다(AI 분석 계약 4.1).
    if (!meta.ok || !['UNRELATED', 'UNCERTAIN'].includes(meta.relation)) return
    if (!(await this.stillShowing(tracking))) return
    const page = await this.deps.inspect(target.tabId, MAX_TEXT_LENGTH)
    if (!page || !(await this.stillShowing(tracking))) return
    const cleaned = cleanPageText(page.text)
    if (page.sensitive || cleaned.sensitive) { await this.send(session.sessionId, 'CHROME_DOM', 'PRIVACY_BLOCKED'); return }
    if (cleaned.text) await this.send(session.sessionId, 'CHROME_DOM', null, metadata, cleaned.text)
  }

  /** 제외·민감 화면이면 보낼 상태. 내용 없이 상태만 보낸다. */
  private async guardOf(target: Target): Promise<Guard | null> {
    const host = hostOf(target.url)
    if (!isWebPage(target.url) || !host) return 'EXCLUDED'
    // [주의] 제외 목록을 한 번도 받지 못했으면 제외 사이트인지 알 수 없어 내용을 보내지 않는다.
    const domains = await this.deps.excludedDomains()
    if (!domains || isExcludedHost(host, domains)) return 'EXCLUDED'
    if (isPrivateHost(host)) return 'PRIVACY_BLOCKED'
    if (!(await this.deps.hasSiteAccess(target.url))) return 'EXCLUDED'
    const page = await this.deps.inspect(target.tabId, 0)
    return page?.sensitive ? 'PRIVACY_BLOCKED' : null
  }

  /** 응답을 기다리는 사이 다른 화면으로 옮겼으면 그 결과로 다음 단계를 보내지 않는다(AI 분석 계약 8). */
  private async stillShowing(tracking: Tracking): Promise<boolean> {
    const [saved, target] = await Promise.all([this.deps.loadTracking(), this.deps.currentTarget()])
    return saved?.navigationId === tracking.navigationId && !!target && keyOf(target) === tracking.key
  }

  private async send(sessionId: string, source: Source, guard: Guard | null, metadata?: RunBody['metadata'], text?: string): Promise<RunResult> {
    const saved = await this.deps.loadSequences()
    const sequences = saved?.sessionId === sessionId ? saved : { sessionId, TAB_META: 0, CHROME_DOM: 0 }
    sequences[source] += 1
    await this.deps.saveSequences(sequences)
    const body: RunBody = {
      clientRunId: this.deps.uuid(), contentSource: source, observedAt: new Date(this.deps.now()).toISOString(), sequence: sequences[source], guardStatus: guard,
      ...(guard ? {} : { metadata, ...(text ? { contentPayload: { text } } : {}) }),
    }
    const result = await this.deps.post(sessionId, body)
    if (!result.ok && result.code === 'TOO_MANY_REQUESTS') this.pausedUntil = this.deps.now() + RATE_LIMIT_PAUSE_MS
    return result
  }
}
