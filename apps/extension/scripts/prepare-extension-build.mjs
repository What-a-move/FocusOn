// Next.js 정적 Export(out/)를 Chrome Extension Popup에서 실행되게 정리한다.
// Manifest V3의 기본 CSP(script-src 'self')는 인라인 <script>를 막아 React가 시작하지 못한다.
// → 인라인 스크립트를 같은 순서의 외부 파일로 옮긴다. (docs/features/pairing-flow-inline-script-csp-ERROR.md)
import { build } from 'esbuild'
import { createHash } from 'node:crypto'
import { mkdir, readdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const out = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'out')
const inlineDir = path.join(out, '_next', 'inline')

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = await Promise.all(entries.map((entry) => {
    const full = path.join(directory, entry.name)
    return entry.isDirectory() ? walk(full) : [full]
  }))
  return files.flat()
}

await mkdir(inlineDir, { recursive: true })
const pages = (await walk(out)).filter((file) => file.endsWith('.html'))

for (const file of pages) {
  const writes = []
  const html = (await readFile(file, 'utf8')).replace(/<script>([\s\S]*?)<\/script>/gu, (_, code) => {
    const name = `${createHash('sha256').update(code).digest('hex').slice(0, 16)}.js`
    writes.push(writeFile(path.join(inlineDir, name), code))
    return `<script src="/_next/inline/${name}"></script>`
  })
  await Promise.all(writes)
  await writeFile(file, html)
  if (/<script>/u.test(html)) throw new Error(`인라인 스크립트가 남았습니다: ${file}`)
}
// Chrome은 최상위의 '_'로 시작하는 파일·폴더를 예약 이름으로 보고 Extension 로딩을 거부한다. (docs/features/extension-build-reserved-filename-ERROR.md)
// - RSC 데이터(__next.*.txt)는 정적 팝업에서 없어도 일반 페이지 이동으로 동작하므로 지운다. 404 페이지(_not-found)도 쓰지 않는다.
// - 정적 자원 폴더 _next는 next로 바꾸고 HTML·JS·CSS 안의 '/_next/' 경로도 함께 바꾼다(Next 런타임은 이 경로로 청크 위치를 찾는다).
for (const file of (await walk(out)).filter((name) => path.basename(name).startsWith('__next.'))) await rm(file)
await rm(path.join(out, '_not-found'), { recursive: true, force: true })
await rename(path.join(out, '_next'), path.join(out, 'next'))
for (const file of (await walk(out)).filter((name) => /\.(html|js|css|json|txt)$/u.test(name))) {
  const text = await readFile(file, 'utf8')
  if (text.includes('/_next/')) await writeFile(file, text.replaceAll('/_next/', '/next/'))
}
const reserved = (await readdir(out)).filter((name) => name.startsWith('_'))
if (reserved.length) throw new Error(`Chrome 예약 이름이 남았습니다: ${reserved.join(', ')}`)
// Service Worker(TypeScript)를 한 파일로 묶는다. API 주소는 팝업과 같은 빌드 환경 변수를 쓴다.
// [주의] next build는 .env 파일을 읽지만 이 스크립트는 따로 실행된다. Next와 같은 우선순위로 읽는다(이미 있는 값은 덮지 않음).
for (const file of ['.env.production.local', '.env.local', '.env.production', '.env']) {
  try { process.loadEnvFile(path.join(out, '..', file)) } catch { /* 파일이 없으면 넘어간다 */ }
}
const apiBase = process.env.NEXT_PUBLIC_FOCUSON_API_BASE_URL ?? ''
await build({
  entryPoints: [path.join(out, '..', 'extension', 'background', 'index.ts')],
  outfile: path.join(out, 'service-worker.js'),
  bundle: true,
  format: 'iife',
  target: 'chrome120',
  define: { 'process.env.NEXT_PUBLIC_FOCUSON_API_BASE_URL': JSON.stringify(apiBase) },
  logLevel: 'warning',
})

// [주의] 사이트 권한은 사용자가 허용할 때만 받는다(optional). 그러면 서버 호출이 CORS로 막히므로 FocusOn 서버 origin 하나만 필수로 둔다.
const manifestPath = path.join(out, 'manifest.json')
const manifest = JSON.parse(await readFile(manifestPath, 'utf8'))
let apiOrigin = null
try { apiOrigin = apiBase ? new URL(apiBase).origin : null } catch { apiOrigin = null }
if (apiOrigin) manifest.host_permissions = [`${apiOrigin}/*`]
await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`)
console.log(`Extension build prepared: ${pages.length} pages, service worker, API origin ${apiOrigin ?? '(없음)'}`)
