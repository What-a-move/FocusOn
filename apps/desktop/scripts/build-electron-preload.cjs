const path = require('node:path')
const { buildSync } = require('esbuild')

// [주의] sandbox Preload는 상대 경로의 모듈을 직접 불러올 수 없어 의존 코드를 한 파일로 묶는다.
buildSync({
  entryPoints: [path.join(__dirname, '../electron/preload/index.cts')],
  outfile: path.join(__dirname, '../dist-electron/preload/index.cjs'),
  bundle: true,
  platform: 'node',
  format: 'cjs',
  target: 'es2022',
  external: ['electron'],
})
