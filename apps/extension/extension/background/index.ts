// Service Worker 진입점: Chrome 이벤트를 TabAnalyzer에 잇는다 (화면 분석 3단계, 결정 014).
// [주의] Listener는 최상위에서 바로 등록한다. Worker는 언제든 꺼졌다 다시 켜질 수 있다.
import { resolveApiBase } from '../../features/pairing/pairing-api'
import { chromeTokenStore, getDeviceId } from '../../features/pairing/extension-auth'
import { createApiClient } from '../../features/session/api-client'
import { chromeLogStore, createLogger } from '../../features/session/logger'
import { getActiveSession } from '../../features/session/session-api'
import { inspectPage } from '../content/inspect-page'
import { SITE_ORIGINS } from '../shared/site-access'
import { TabAnalyzer, type Tracking } from './tab-analyzer'

const KEYS = { tracking: 'focuson.analysis.tracking', sequences: 'focuson.analysis.sequences', excluded: 'focuson.analysis.excludedDomains' } as const
const EXCLUDED_TTL_MS = 5 * 60_000
const DWELL_ALARM = 'focuson-dwell'
const log = createLogger('service-worker', chromeLogStore, { echo: process.env.NODE_ENV !== 'production' })
// 이 줄은 Worker가 새로 켜질 때마다 한 번 실행된다. 쉬다가 꺼진 뒤 다시 깨어난 시점을 남긴다.
log('INFO', 'sw.restart')
const client = createApiClient({ baseUrl: resolveApiBase(process.env.NEXT_PUBLIC_FOCUSON_API_BASE_URL), store: chromeTokenStore, log })

async function deviceHeaders(): Promise<Record<string, string>> {
  const deviceId = await getDeviceId()
  return deviceId ? { 'X-Device-Id': deviceId } : {}
}

async function excludedDomains(): Promise<string[] | null> {
  const cached = (await chrome.storage.local.get(KEYS.excluded))[KEYS.excluded] as { domains: string[]; fetchedAt: number } | undefined
  if (cached && Date.now() - cached.fetchedAt < EXCLUDED_TTL_MS) return cached.domains
  const result = await client.authorized('/api/v1/users/me/analysis-excluded-domains', 'GET', 200, (data) => {
    const domains = (data as { domains?: unknown } | null)?.domains
    return Array.isArray(domains) && domains.every((domain) => typeof domain === 'string') ? domains as string[] : null
  }, { headers: await deviceHeaders() })
  if (!result.success) return cached?.domains ?? null
  await chrome.storage.local.set({ [KEYS.excluded]: { domains: result.data, fetchedAt: Date.now() } })
  return result.data
}

const analyzer = new TabAnalyzer({
  now: () => Date.now(),
  uuid: () => crypto.randomUUID(),
  async currentTarget() {
    const window = await chrome.windows.getLastFocused().catch(() => null)
    // Chrome이 뒤에 있으면 Desktop이 맨 앞 앱을 본다. 시크릿 창은 보지 않는다.
    if (!window?.focused || window.incognito) return null
    const [tab] = await chrome.tabs.query({ active: true, windowId: window.id })
    return tab?.id !== undefined && tab.url ? { tabId: tab.id, url: tab.url, title: tab.title ?? '' } : null
  },
  async activeSession() {
    const result = await getActiveSession()
    return result.success ? result.data : null
  },
  excludedDomains,
  async hasSiteAccess(url) {
    return chrome.permissions.contains({ origins: [`${new URL(url).origin}/*`] }).catch(() => false)
  },
  async inspect(tabId, maxLength) {
    try {
      const [result] = await chrome.scripting.executeScript({ target: { tabId }, func: inspectPage, args: [maxLength] })
      return result?.result ?? null
    } catch {
      return null
    }
  },
  async post(sessionId, body) {
    const result = await client.authorized(`/api/v1/sessions/${sessionId}/analysis-runs`, 'POST', 200, (data) => {
      const relation = (data as { relation?: unknown } | null)?.relation
      return typeof relation === 'string' ? { relation } : null
    }, {
      body, headers: await deviceHeaders(),
      // 로그를 서버 판정 기록과 잇도록 traceId는 clientRunId를 그대로 쓴다. 제목·본문은 로거가 글자 수로만 남긴다.
      traceId: body.clientRunId,
      logFields: { sessionId, contentSource: body.contentSource, guardStatus: body.guardStatus, domain: body.metadata?.urlHost, title: body.metadata?.pageTitle, text: body.contentPayload?.text },
    })
    return result.success ? { ok: true, relation: result.data.relation } : { ok: false, code: result.error.code }
  },
  async loadTracking() {
    return ((await chrome.storage.session.get(KEYS.tracking))[KEYS.tracking] as Tracking | undefined) ?? null
  },
  async saveTracking(tracking) {
    if (tracking) await chrome.storage.session.set({ [KEYS.tracking]: tracking })
    else await chrome.storage.session.remove(KEYS.tracking)
  },
  async loadSequences() {
    return ((await chrome.storage.local.get(KEYS.sequences))[KEYS.sequences] as { sessionId: string; TAB_META: number; CHROME_DOM: number } | undefined) ?? null
  },
  async saveSequences(value) {
    await chrome.storage.local.set({ [KEYS.sequences]: value })
  },
  schedule(ms, run) {
    setTimeout(run, ms)
    // Worker가 그 사이 꺼져도 30초 뒤 알람으로 다시 깨어나 resume()으로 이어 간다(Chrome 120 알람 최소 30초).
    void chrome.alarms.create(DWELL_ALARM, { delayInMinutes: 0.5 })
  },
})

const changed = () => { void analyzer.onChange() }
chrome.tabs.onActivated.addListener(changed)
chrome.windows.onFocusChanged.addListener(changed)
chrome.webNavigation.onCommitted.addListener((details) => { if (details.frameId === 0) changed() })
chrome.webNavigation.onHistoryStateUpdated.addListener((details) => { if (details.frameId === 0) changed() })
chrome.alarms.onAlarm.addListener((alarm) => { if (alarm.name === DWELL_ALARM) void analyzer.resume() })
chrome.runtime.onStartup.addListener(() => { void analyzer.resume() })

// 집중 중 사이트 접근을 끄면 서버에 알려 세션을 자동으로 멈추게 한다(공통 Error Code LOCAL_EXTENSION_HOST_PERMISSION_MISSING).
chrome.permissions.onRemoved.addListener((removed) => {
  if (!removed.origins?.some((origin) => SITE_ORIGINS.includes(origin))) return
  void (async () => {
    const session = await getActiveSession()
    if (!session.success || session.data?.status !== 'RUNNING') return
    await client.authorized(`/api/v1/sessions/${session.data.sessionId}/local-events`, 'POST', 200, () => true, {
      body: { localEventCode: 'LOCAL_EXTENSION_HOST_PERMISSION_MISSING', occurredAt: new Date().toISOString() }, headers: await deviceHeaders(),
    })
  })()
})
