import { POKI_CURIOUS, POKI_PAUSED, POKI_WAVE } from './oauth-page-poki.cjs'

/** 로그인 결과 종류. 결과마다 다른 포키를 쓴다. */
export type OAuthPageKind = 'ok' | 'cancelled' | 'failed'

const PAGE: Record<OAuthPageKind, { title: string; body: string; poki: string }> = {
  ok: { title: '로그인했어요', body: 'FocusOn 앱으로 돌아가 주세요.', poki: POKI_WAVE },
  cancelled: { title: '로그인을 취소했어요', body: '다시 하려면 FocusOn 앱에서 로그인해 주세요.', poki: POKI_PAUSED },
  failed: { title: '로그인하지 못했어요', body: 'FocusOn 앱에서 다시 로그인해 주세요.', poki: POKI_CURIOUS },
}

const escape = (text: string) => text.replace(/[&<>"']/gu, (char) => `&#${char.charCodeAt(0)};`)

/**
 * 브라우저에 보여 줄 로그인 결과 페이지. loopback 응답이라 외부 파일 없이 HTML·CSS·이미지를 한 번에 보낸다.
 * 상자 없이 흰(다크: 검은) 배경 가운데에 포키와 글자만 둔다.
 */
export function oauthPage(kind: OAuthPageKind): string {
  const { title, body, poki } = PAGE[kind]
  return `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>FocusOn · ${escape(title)}</title>
<style>
:root{--bg:#fff;--ink:#171717;--muted:#6b6f76;--danger:#d92d20}
@media (prefers-color-scheme:dark){:root{--bg:#0e0e10;--ink:#f4f2ee;--muted:#a5a29c;--danger:#ff7a6e}}
*{box-sizing:border-box}
html,body{height:100%}
body{display:grid;place-items:center;margin:0;padding:32px 24px;background:var(--bg);color:var(--ink);font-family:"Pretendard",-apple-system,BlinkMacSystemFont,"Apple SD Gothic Neo",sans-serif;-webkit-font-smoothing:antialiased}
main{display:flex;flex-direction:column;align-items:center;max-width:520px;text-align:center;animation:in .6s cubic-bezier(.22,1,.36,1) both}
img{width:clamp(160px,26vw,220px);height:auto}
h1{margin:28px 0 0;font-size:clamp(26px,3.4vw,32px);font-weight:700;letter-spacing:-.03em;word-break:keep-all}
.failed h1{color:var(--danger)}
p{margin:12px 0 0;max-width:400px;color:var(--muted);font-size:17px;line-height:1.6;word-break:keep-all;text-wrap:balance}
.close{margin-top:32px;font-size:14px}
@keyframes in{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
@media (prefers-reduced-motion:reduce){main{animation:none}}
</style>
</head>
<body>
<main class="${kind}" role="${kind === 'ok' ? 'status' : 'alert'}">
<img src="${poki}" alt="" width="400" height="320">
<h1>${escape(title)}</h1>
<p>${escape(body)}</p>
<p class="close">이 탭은 닫아도 돼요.</p>
</main>
</body>
</html>`
}
