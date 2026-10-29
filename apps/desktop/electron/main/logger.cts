import fs from 'node:fs'
import path from 'node:path'

export type Level = 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'
// Notion 모듈 연동 로깅 규칙 3.4. 새 이름은 정본 목록에 먼저 추가한 뒤 쓴다.
export type LogEvent =
  | 'api.request.out' | 'api.response.in' | 'api.request.fail'
  | 'decision.request.out' | 'decision.response.in' | 'decision.request.fail'
  | 'session.start' | 'session.end' | 'state.transition' | 'notify.fire'
  | 'power.suspend' | 'power.resume'
export type ErrorClass = 'timeout' | 'network' | 'auth' | 'rate_limit' | 'bad_response' | 'unknown'

// [주의] 허용 목록 밖의 필드는 버린다. 부르는 쪽이 제목·본문·토큰을 실수로 넘겨도 파일에 남지 않게 하는 마지막 방어선이다.
const ALLOWED = new Set([
  'traceId', 'sessionId', 'method', 'path', 'status', 'code', 'errorClass', 'durMs',
  'contentSource', 'guardStatus', 'relation', 'from', 'to', 'trigger', 'heldMono', 'heldWall', 'sleepSuspected',
  'interventionType', 'channels', 'domain', 'titleLen', 'textLen', 'retry',
])
// 원문이 될 수 있는 필드는 값 대신 글자 수만 남긴다.
const LENGTH_ONLY: Record<string, string> = { title: 'titleLen', windowTitle: 'titleLen', pageTitle: 'titleLen', text: 'textLen' }
const MAX_BYTES = 1024 * 1024

export type Logger = (level: Level, event: LogEvent, fields?: Record<string, unknown> & { component?: string }) => void

/** 남겨도 되는 필드만 고른다. 테스트에서 마스킹 규칙을 직접 확인하려고 공개한다. */
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

/** JSON 한 줄씩 파일에 쌓는다. 1MB를 넘으면 `.1.log`로 넘기고 그 이전 것은 지운다. */
export function createLogger(file: string, options: { now?: () => Date; echo?: boolean } = {}): Logger {
  const now = options.now ?? (() => new Date())
  const rotated = file.replace(/\.log$/, '.1.log')
  try { fs.mkdirSync(path.dirname(file), { recursive: true }) } catch { /* 쓰기 실패와 같이 아래에서 건너뛴다 */ }
  return (level, event, fields = {}) => {
    const { component = 'main', ...rest } = fields
    const line = JSON.stringify({ ts: now().toISOString(), level, module: 'desktop', component, event, traceId: null, durMs: 0, ...sanitize(rest) })
    if (options.echo) console.log(line)
    // ponytail: 동기 쓰기. 요청·상태 전이 단위라 양이 적다. 초당 수백 줄이 되면 비동기 버퍼로 바꾼다.
    try {
      if (fs.existsSync(file) && fs.statSync(file).size > MAX_BYTES) fs.renameSync(file, rotated)
      fs.appendFileSync(file, line + '\n')
    } catch {
      // 로그를 못 써도 앱 동작은 그대로 둔다.
    }
  }
}

let current: Logger = () => {}

/** 앱 시작 때 한 번 파일 위치를 정한다. 그 전 기록은 버린다. */
export function initLogger(logger: Logger): void {
  current = logger
}

export const log: Logger = (level, event, fields) => current(level, event, fields)

/** HTTP 상태와 우리 오류 코드를 로그용 실패 분류로 바꾼다. */
export function errorClassOf(code: string, status?: number): ErrorClass {
  if (code === 'LOCAL_NETWORK_UNAVAILABLE') return 'network'
  if (code === 'INVALID_RESPONSE') return 'bad_response'
  if (status === 401 || status === 403) return 'auth'
  if (status === 429) return 'rate_limit'
  return 'unknown'
}
