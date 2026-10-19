export const MAX_TEXT_CHARS = 6000;
export const DWELL_MS = 15_000;
export const policyKey = context => JSON.stringify({ ...context, validUntil: 0 });
const CONTENT_REASONS = new Set([
  'METADATA_INSUFFICIENT', 'CONFLICTING_METADATA', 'EXTRACTION_FAILED',
  'UNSUPPORTED', 'EMPTY_CONTENT', 'TOO_SHORT', 'LOW_CONFIDENCE', 'BROKEN_CONTENT',
  'INSUFFICIENT_BODY', 'INCOMPLETE_REPRESENTATION', 'CONFLICTING_EVIDENCE', 'INSUFFICIENT_EVIDENCE',
]);
export class CaptureError extends Error {
  constructor(code) { super(code); this.code = code; }
}
export function requirePolicy(context, now = Date.now()) {
  if (!context || context.sessionStatus !== 'RUNNING' || context.resumeRequired !== false)
    throw new CaptureError('SESSION_INACTIVE');
  if (typeof context.sessionId !== 'string' || !context.sessionId ||
      !Number.isSafeInteger(context.version) || context.version < 1 ||
      !Number.isFinite(context.validUntil) || context.validUntil <= now ||
      context.validUntil > now + 60_000 || !Array.isArray(context.excludedDomains) ||
      !context.excludedDomains.every(host => typeof host === 'string' && /^[a-z0-9.-]+$/.test(host)))
    throw new CaptureError('POLICY_UNAVAILABLE');
  if (context.privacyBlocked === true) throw new CaptureError('PRIVACY_BLOCKED');
}
export function checkedUrl(rawUrl, excludedDomains) {
  let url;
  try { url = new URL(rawUrl); } catch { throw new CaptureError('UNSUPPORTED_URL'); }
  if (!['https:', 'http:'].includes(url.protocol)) throw new CaptureError('UNSUPPORTED_URL');
  const host = url.hostname.toLowerCase().replace(/\.$/, '');
  if (excludedDomains.some(domain => host === domain || host.endsWith(`.${domain}`))) throw new CaptureError('EXCLUDED');
  if (/(?:^|[./_-])(login|signin|checkout|payment|banking|password|recovery|secrets?|api[-_]?keys?|tokens?|otp)(?:$|[./_-])/i.test(host + url.pathname) ||
      /^(?:mail\.google\.com|outlook\.live\.com|outlook\.office\.com|web\.whatsapp\.com|web\.telegram\.org)$/.test(host))
    throw new CaptureError('PRIVACY_BLOCKED');
  return host;
}
export function containsSensitiveText(text) {
  return /[\w.+-]+@[\w.-]+\.[a-z]{2,}|\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{16,}|eyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+)|\bBearer\s+\S+|(?:api[_ -]?key|password|비밀번호|인증\s*코드|OTP)\s*[:=]\s*\S+|\b(?:\d[ -]?){13,19}\b|\b01[016789][ -]?\d{3,4}[ -]?\d{4}\b/i.test(text);
}
export function nextSource(observation, decision) {
  if (!decision || decision.clientRunId !== observation.clientRunId ||
      decision.navigationId !== observation.navigationId || decision.contentSource !== observation.contentSource)
    throw new CaptureError('STALE_DECISION');
  if (decision.relation !== 'UNCERTAIN' || !CONTENT_REASONS.has(decision.reasonCode)) return null;
  return { TAB_META: 'CHROME_DOM', CHROME_DOM: 'CHROME_VIEWPORT_OCR' }[observation.contentSource] ?? null;
}
export function sameTab(a, b) {
  return a.id === b.id && a.windowId === b.windowId && a.url === b.url && a.title === b.title && b.active === true && !b.incognito && !b.discarded;
}
