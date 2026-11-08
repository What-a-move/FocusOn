// 제외 사이트 입력(온보딩·설정)이 같이 쓰는 도메인 규칙.
export const MAX_SITES = 50
export const DOMAIN = /^(?=.{4,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,61}[a-z0-9]?$/u

// [주의] 주소 전체가 아니라 정규화한 host만 남긴다 (API_CONTRACT 분석 제외 도메인).
export function normalizeDomain(raw: string): string {
  let value = raw.trim().toLowerCase()
  if (/[/:]/u.test(value)) {
    try { value = new URL(value.includes('://') ? value : `https://${value}`).hostname } catch { return '' }
  }
  return value.replace(/^www\./u, '').replace(/\.$/u, '')
}
