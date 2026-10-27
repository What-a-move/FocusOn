// Desktop OCR 글자 정리 (결정 024, Notion AI 분석 계약 2.3). 순수 함수다.
// Chrome 확장의 cleanPageText와 같은 규칙이다. 실행 코드는 앱끼리 공유하지 않는다(결정 025).

export const MAX_OCR_TEXT = 2000

const SENSITIVE_WORDS = /비밀번호|password|passcode|인증\s?번호|인증\s?코드|verification code|\botp\b|카드\s?번호|계좌\s?번호|cvc|cvv|api\s?key|secret key|access token/iu
const CARD_NUMBER = /\b(?:\d[ -]?){13,19}\b/u
const EMAIL = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/gu
const PHONE = /(?:\+?\d{1,3}[ -]?)?\(?\d{2,4}\)?[ -]?\d{3,4}[ -]?\d{4}/gu
const URL_TEXT = /\bhttps?:\/\/\S+/giu
// 영문·숫자가 섞인 24자 이상 덩어리는 토큰·키일 수 있다.
const LONG_TOKEN = /\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{24,}\b/gu

/**
 * OCR로 읽은 글자를 보내기 전에 정리한다. 민감한 단어·카드 번호처럼 보이는 값이 있으면 sensitive로 알려 글자를 보내지 않게 한다.
 * 주소·이메일·전화번호·긴 토큰은 지우고, 공백을 줄여 최대 2,000자로 자른다.
 */
export function cleanOcrText(raw: string): { text: string; sensitive: boolean } {
  if (SENSITIVE_WORDS.test(raw) || CARD_NUMBER.test(raw)) return { text: '', sensitive: true }
  const text = raw.replace(URL_TEXT, ' ').replace(EMAIL, ' ').replace(PHONE, ' ').replace(LONG_TOKEN, ' ').replace(/\s+/gu, ' ').trim()
  return { text: text.slice(0, MAX_OCR_TEXT), sensitive: false }
}
