// 세션 중 맨 앞 앱 관측 규칙 (결정 024, Notion 화면 분석 3.1·6.1.1·6.2).
// 이 파일은 순수 로직이다. 1초마다 맨 앞 앱을 넣으면, 같은 화면이 15초 유지됐을 때 관측 1건을 돌려준다.

export type Frontmost = { bundleId: string | null; appName: string | null; pid: number; windowTitle: string | null }
import type { GuardStatus } from '@focuson/shared-types'

export type { GuardStatus }
export type Observation =
  | { observedAt: string; guardStatus: GuardStatus }
  | { observedAt: string; guardStatus: null; contentSource: 'APP_META'; metadata: { appName: string; windowTitle: string | null } }

export const DWELL_MS = 15_000

/** 도우미 `frontmost` 응답을 확인한다. 형식이 다르거나 맨 앞 앱이 없으면 null(확인 불가)이다. */
export function parseFrontmost(reply: Record<string, unknown> | null): Frontmost | null {
  const app = reply?.app
  if (typeof app !== 'object' || app === null) return null
  const { bundleId, appName, pid, windowTitle } = app as Record<string, unknown>
  if (typeof pid !== 'number') return null
  const text = (value: unknown) => typeof value === 'string' && value ? value : null
  return { bundleId: text(bundleId), appName: text(appName), pid, windowTitle: text(windowTitle) }
}

// Chrome 탭은 Extension이 맡는다. Desktop은 현재 URL을 몰라 제외·민감 페이지를 거를 수 없다.
const CHROME = new Set(['com.google.Chrome', 'com.google.Chrome.beta', 'com.google.Chrome.dev', 'com.google.Chrome.canary'])

// MVP는 macOS + Chrome만 지원한다. 다른 브라우저는 URL을 몰라 제외·민감 페이지를 거를 수 없어 내용을 보지 않고 EXCLUDED로만 알린다.
// [주의] 아무것도 보내지 않으면 서버는 직전 판정(예: UNRELATED)이 계속된다고 보고 이탈 시간을 쌓는다.
export const UNSUPPORTED_BROWSERS = new Set([
  'com.apple.Safari', 'com.apple.SafariTechnologyPreview', 'company.thebrowser.Browser', 'com.microsoft.edgemac', 'org.mozilla.firefox',
  'com.naver.Whale', 'com.brave.Browser', 'com.operasoftware.Opera', 'com.vivaldi.Vivaldi', 'org.chromium.Chromium', 'com.duckduckgo.macos.browser',
])

// ponytail: 고정 목록 기반이라 놓친 앱이 있을 수 있다. 사용자는 분석 제외 앱으로 직접 추가할 수 있다.
export const SENSITIVE_APPS = new Set([
  // 비밀번호·키체인
  'com.apple.keychainaccess', 'com.apple.Passwords', 'com.1password.1password', 'com.agilebits.onepassword7', 'com.bitwarden.desktop',
  // 메시지·메일
  'com.apple.MobileSMS', 'com.kakao.KakaoTalkMac', 'com.tinyspeck.slackmacgap', 'com.hnc.Discord', 'ru.keepcoder.Telegram',
  'net.whatsapp.WhatsApp', 'jp.naver.line.mac', 'com.apple.mail', 'com.microsoft.Outlook',
  // 시스템 설정(암호·보안 항목)
  'com.apple.systempreferences',
])
const SENSITIVE_TITLE = /비밀번호|password|passcode|\botp\b|인증\s?번호|인증\s?코드|verification code|결제|payment|계좌|api\s?key|access token|secret/iu

type Deps = { selfPid: number; isExcluded: (bundleId: string) => boolean; now: () => number; wallClock: () => Date }

export class ScreenObserver {
  private current: { key: string; since: number; done: boolean } | null = null

  constructor(private readonly deps: Deps) {}

  /** 맨 앞 앱을 넣는다. 같은 화면이 15초 유지된 순간에만 관측을 돌려주고, 그 화면에서는 다시 만들지 않는다. */
  observe(front: Frontmost | null): Observation | null {
    // [주의] 대상 앱을 확인할 수 없으면 이탈로 추정하지 않는다. 진행 중인 15초 계산도 바꾸지 않는다.
    if (!front) return null
    const key = `${front.bundleId ?? ''}\n${front.windowTitle ?? ''}`
    const now = this.deps.now()
    if (this.current?.key !== key) {
      this.current = { key, since: now, done: this.skipped(front) }
      return null
    }
    if (this.current.done || now - this.current.since < DWELL_MS) return null
    this.current.done = true
    return this.classify(front)
  }

  /** 세션이 RUNNING이 아니면 비운다. 다시 시작하면 처음부터 15초를 센다. */
  reset(): void {
    this.current = null
  }

  // Chrome은 Extension이 관측을 보낸다. 번들 ID가 없으면 대상 앱을 알 수 없어 관측하지 않는다.
  private skipped(front: Frontmost): boolean {
    return !front.bundleId || CHROME.has(front.bundleId)
  }

  private classify(front: Frontmost): Observation {
    const observedAt = this.deps.wallClock().toISOString()
    const bundleId = front.bundleId as string
    // FocusOn 자기 창은 관련·무관 어느 쪽 근거도 아니다(Notion 화면 분석 3.1). 지원하지 않는 브라우저와 함께 분석 제외로만 알린다.
    if (front.pid === this.deps.selfPid || UNSUPPORTED_BROWSERS.has(bundleId) || this.deps.isExcluded(bundleId)) return { observedAt, guardStatus: 'EXCLUDED' }
    if (SENSITIVE_APPS.has(bundleId) || (front.windowTitle && SENSITIVE_TITLE.test(front.windowTitle))) return { observedAt, guardStatus: 'PRIVACY_BLOCKED' }
    return { observedAt, guardStatus: null, contentSource: 'APP_META', metadata: { appName: front.appName ?? bundleId, windowTitle: front.windowTitle } }
  }
}
