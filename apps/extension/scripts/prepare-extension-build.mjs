// Next.js 정적 Export(out/)를 Chrome Extension Popup에서 실행되게 정리한다.
// Manifest V3의 기본 CSP(script-src 'self')는 인라인 <script>를 막아 React가 시작하지 못한다.
// → 인라인 스크립트를 같은 순서의 외부 파일로 옮긴다. (docs/features/pairing-flow-inline-script-csp-ERROR.md)
import { build } from 'esbuild'
import { createHash } from 'node:crypto'
import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
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
// Service Worker(TypeScript)를 한 파일로 묶는다. API 주소는 팝업과 같은 빌드 환경 변수를 쓴다.
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
