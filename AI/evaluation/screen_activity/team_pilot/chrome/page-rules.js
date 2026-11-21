export const MAX_TEXT_LENGTH = 2000;
export function isWebPage(url) {
  return /^https?:\/\//iu.test(url);
}
export function hostOf(url) {
  try {
    return new URL(url).hostname.replace(/\.$/u, "") || null;
  } catch {
    return null;
  }
}
export function isExcludedHost(host, domains) {
  return domains.some((domain) => host === domain || host.endsWith(`.${domain}`));
}
const PRIVATE_HOSTS = [
  "mail.google.com",
  "outlook.live.com",
  "outlook.office.com",
  "outlook.office365.com",
  "mail.naver.com",
  "mail.daum.net",
  "mail.yahoo.com",
  "web.whatsapp.com",
  "web.telegram.org",
  "messenger.com",
  "discord.com",
  "app.slack.com",
  "teams.microsoft.com",
  "talk.naver.com",
];
export function isPrivateHost(host) {
  return isExcludedHost(host, PRIVATE_HOSTS);
}
const SENSITIVE_WORDS =
  /비밀번호|password|passcode|인증\s?번호|인증\s?코드|verification code|\botp\b|카드\s?번호|계좌\s?번호|cvc|cvv|api\s?key|secret key|access token/iu;
const CARD_NUMBER = /\b(?:\d[ -]?){13,19}\b/u;
const EMAIL = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/gu;
const PHONE = /(?:\+?\d{1,3}[ -]?)?\(?\d{2,4}\)?[ -]?\d{3,4}[ -]?\d{4}/gu;
const URL_TEXT = /\bhttps?:\/\/\S+/giu;
const LONG_TOKEN = /\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{24,}\b/gu;
export function cleanPageText(raw) {
  if (SENSITIVE_WORDS.test(raw) || CARD_NUMBER.test(raw))
    return {
      text: "",
      sensitive: true,
    };
  const text = raw
    .replace(URL_TEXT, " ")
    .replace(EMAIL, " ")
    .replace(PHONE, " ")
    .replace(LONG_TOKEN, " ")
    .replace(/\s+/gu, " ")
    .trim();
  return {
    text: text.slice(0, MAX_TEXT_LENGTH),
    sensitive: false,
  };
}
