// Chrome 탭 분석의 개인정보 규칙 (결정 013, Notion 화면 분석 6·AI 분석 계약 2.3). 순수 함수만 둔다.

export const MAX_TEXT_LENGTH = 2000

/** 분석할 수 있는 주소인지. Chrome 내부·확장·파일 페이지는 보지 않는다. */
export function isWebPage(url: string): boolean {
  return /^https?:\/\//iu.test(url)
}

/** 주소에서 host만 남긴다. 경로·query·hash는 보내지 않는다. */
export function hostOf(url: string): string | null {
  try {
    return new URL(url).hostname.replace(/\.$/u, '') || null
  } catch {
    return null
  }
}

/** 제외 도메인과 그 하위 도메인. `not-example.com`이 `example.com`에 걸리지 않게 점으로 나눠 비교한다. */
export function isExcludedHost(host: string, domains: readonly string[]): boolean {
  return domains.some((domain) => host === domain || host.endsWith(`.${domain}`))
}

// 메일·메신저는 내용을 보지 않는다(민감 화면). ponytail: 고정 목록이라 놓친 곳은 사용자가 제외 도메인으로 추가한다.
const PRIVATE_HOSTS = [
  'mail.google.com', 'outlook.live.com', 'outlook.office.com', 'outlook.office365.com', 'mail.naver.com', 'mail.daum.net', 'mail.yahoo.com',
  'web.whatsapp.com', 'web.telegram.org', 'messenger.com', 'discord.com', 'app.slack.com', 'teams.microsoft.com', 'talk.naver.com',
]

export function isPrivateHost(host: string): boolean {
  return isExcludedHost(host, PRIVATE_HOSTS)
}

const SENSITIVE_WORDS = /비밀번호|password|passcode|인증\s?번호|인증\s?코드|verification code|\botp\b|카드\s?번호|계좌\s?번호|cvc|cvv|api\s?key|secret key|access token/iu
const CARD_NUMBER = /\b(?:\d[ -]?){13,19}\b/u
const EMAIL = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/gu
const PHONE = /(?:\+?\d{1,3}[ -]?)?\(?\d{2,4}\)?[ -]?\d{3,4}[ -]?\d{4}/gu
const URL_TEXT = /\bhttps?:\/\/\S+/giu
// 영문·숫자가 섞인 24자 이상 덩어리는 토큰·키일 수 있다.
const LONG_TOKEN = /\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{24,}\b/gu

/**
 * 페이지 본문을 보내기 전에 정리한다. 민감한 단어·카드 번호처럼 보이는 값이 있으면 sensitive로 알려 본문 전체를 보내지 않게 한다.
 * 이메일·전화번호·주소·긴 토큰은 지우고, 공백을 줄여 최대 2,000자로 자른다.
 */
export function cleanPageText(raw: string): { text: string; sensitive: boolean } {
  if (SENSITIVE_WORDS.test(raw) || CARD_NUMBER.test(raw)) return { text: '', sensitive: true }
  const text = raw.replace(URL_TEXT, ' ').replace(EMAIL, ' ').replace(PHONE, ' ').replace(LONG_TOKEN, ' ').replace(/\s+/gu, ' ').trim()
  return { text: text.slice(0, MAX_TEXT_LENGTH), sensitive: false }
}
