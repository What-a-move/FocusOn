// Extension 공용 로거 (Notion 모듈 연동 로깅 규칙, Desktop 결정 030과 같은 형식).
// [주의] 이 파일은 node --test로 직접 실행하므로 상대 import 없이 필요한 것을 주입받는다.
// ponytail: 마스킹 규칙은 Desktop logger.cts와 같은 내용을 복사해 둔다. shared-types는 타입만 두기로 해서(Issue #69) 함수를 공유하지 않는다.

export type Level = 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'
export type LogEvent =
  | 'api.request.out' | 'api.response.in' | 'api.request.fail'
  | 'decision.request.out' | 'decision.response.in' | 'decision.request.fail'
  | 'sw.restart'
export type ErrorClass = 'timeout' | 'network' | 'auth' | 'rate_limit' | 'bad_response' | 'unknown'
export type Logger = (level: Level, event: LogEvent, fields?: Record<string, unknown>) => void
export type LogStore = { read(): Promise<unknown[]>; write(lines: unknown[]): Promise<void> }

// [주의] 허용 목록 밖의 필드는 버린다. 부르는 쪽이 제목·본문·토큰을 실수로 넘겨도 저장되지 않게 하는 마지막 방어선이다.
const ALLOWED = new Set([
  'traceId', 'sessionId', 'method', 'path', 'status', 'code', 'errorClass', 'durMs',
  'contentSource', 'guardStatus', 'relation', 'domain', 'titleLen', 'textLen',
])
const LENGTH_ONLY: Record<string, string> = { title: 'titleLen', pageTitle: 'titleLen', text: 'textLen' }
export const MAX_LINES = 500
export const LOG_KEY = 'focuson.logs'

/** 남겨도 되는 필드만 고른다. */
export function sanitize(fields: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(fields)) {
    if (LENGTH_ONLY[key]) { if (typeof value === 'string') out[LENGTH_ONLY[key]] = value.length; continue }
    if (!ALLOWED.has(key) || value === undefined) continue
    if (key === 'path' && typeof value === 'string') out.path = value.split('?')[0]
    else if (key === 'domain' && typeof value === 'string') out.domain = value.replace(/^[a-z]+:\/\//i, '').split(/[/?#]/)[0]
    else out[key] = value
  }
  return out
}

/**
 * JSON 한 줄 형식의 기록을 만든다. INFO 이상은 store에 최근 500건만 남긴다.
 * MV3 service worker는 쉬면 꺼져 콘솔 기록이 사라지므로 chrome.storage.local에 함께 둔다.
 */
export function createLogger(component: string, store: LogStore | null, options: { now?: () => Date; echo?: boolean } = {}): Logger {
  const now = options.now ?? (() => new Date())
  // 같은 실행 환경 안에서는 읽고-쓰기를 순서대로 처리해 기록이 서로 덮이지 않게 한다.
  // ponytail: 팝업과 service worker가 동시에 쓰면 한쪽 줄이 빠질 수 있다. 문제가 되면 service worker 한 곳에서만 저장한다.
  let queue = Promise.resolve()
  return (level, event, fields = {}) => {
    let line: Record<string, unknown>
    try {
      line = JSON.parse(JSON.stringify({ ts: now().toISOString(), level, module: 'extension', component, event, traceId: null, durMs: 0, ...sanitize(fields) }))
    } catch {
      return
    }
    if (options.echo) console.log(JSON.stringify(line))
    if (!store || level === 'DEBUG') return
    queue = queue.then(async () => {
      const lines = await store.read()
      lines.push(line)
      await store.write(lines.slice(-MAX_LINES))
    }).catch(() => {
      // 기록을 못 남겨도 Extension 동작은 그대로 둔다.
    })
  }
}

/** chrome.storage.local에 쌓는 저장소. */
export const chromeLogStore: LogStore = {
  async read() {
    const saved = (await chrome.storage.local.get(LOG_KEY))[LOG_KEY]
    return Array.isArray(saved) ? saved : []
  },
  async write(lines) {
    await chrome.storage.local.set({ [LOG_KEY]: lines })
  },
}

/** HTTP 상태와 우리 오류 코드를 로그용 실패 분류로 바꾼다. */
export function errorClassOf(code: string, status?: number): ErrorClass {
  if (code === 'LOCAL_NETWORK_UNAVAILABLE') return 'network'
  if (code === 'INVALID_RESPONSE') return 'bad_response'
  if (status === 401 || status === 403) return 'auth'
  if (status === 429) return 'rate_limit'
  return 'unknown'
}
