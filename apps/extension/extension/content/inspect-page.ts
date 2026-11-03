/**
 * `chrome.scripting.executeScript({ func: inspectPage })`로 페이지 안에서 한 번 실행한다 (결정 014).
 * [주의] 함수 본문만 페이지로 넘어가므로 바깥 import·변수를 쓰면 안 된다.
 * - 비밀번호·일회용 코드·카드 입력칸이 있으면 sensitive만 알리고 본문을 읽지 않는다.
 * - maxLength가 0이면 민감 여부만 확인한다.
 * - 본문은 제목·h1~h3·문단·목록·그림·코드 블록 제목(figcaption)에서만 읽는다. 코드 내용(pre·code)은 키·토큰이 섞이기 쉬워 읽지 않는다.
 *   입력칸·textarea·contenteditable 안의 글도 읽지 않는다.
 */
export function inspectPage(maxLength: number): { sensitive: boolean; text: string } {
  const sensitive = document.querySelector([
    'input[type="password"]', 'input[autocomplete="one-time-code"]', 'input[autocomplete^="cc-"]',
    'input[name*="card" i]', 'input[name*="otp" i]', 'input[id*="otp" i]',
  ].join(',')) !== null
  if (sensitive || maxLength <= 0) return { sensitive, text: '' }
  const parts = [document.title]
  let length = document.title.length
  for (const node of Array.from(document.querySelectorAll('h1, h2, h3, p, li, figcaption'))) {
    if (node.closest('form, pre, code, [contenteditable=""], [contenteditable="true"], textarea, nav, footer, [aria-hidden="true"]')) continue
    const value = (node as HTMLElement).innerText?.trim()
    if (!value) continue
    parts.push(value)
    length += value.length + 1
    if (length >= maxLength * 2) break
  }
  return { sensitive: false, text: parts.join('\n').slice(0, maxLength * 2) }
}
