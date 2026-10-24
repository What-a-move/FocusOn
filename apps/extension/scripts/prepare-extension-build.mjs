// Next.js 정적 Export(out/)를 Chrome Extension Popup에서 실행되게 정리한다.
// Manifest V3의 기본 CSP(script-src 'self')는 인라인 <script>를 막아 React가 시작하지 못한다.
// → 인라인 스크립트를 같은 순서의 외부 파일로 옮긴다. (docs/features/pairing-flow-inline-script-csp-ERROR.md)
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
console.log(`Extension build prepared: ${pages.length} pages`)
