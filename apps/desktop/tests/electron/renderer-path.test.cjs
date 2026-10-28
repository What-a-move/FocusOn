const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')
const {
  DEV_RENDERER_URL,
  PROD_RENDERER_URL,
  isAllowedNavigation,
  resolveRendererFile,
} = require('../../dist-electron/main/renderer-path.cjs')

const outDirectory = path.resolve('/focuson', 'out')

test('정적 화면과 에셋을 out 아래에서 찾는다', () => {
  assert.equal(resolveRendererFile(outDirectory, 'app://focuson/'), path.join(outDirectory, 'index.html'))
  assert.equal(resolveRendererFile(outDirectory, 'app://focuson/settings/'), path.join(outDirectory, 'settings/index.html'))
  assert.equal(resolveRendererFile(outDirectory, 'app://focuson/_next/static/app.js'), path.join(outDirectory, '_next/static/app.js'))
})

test('다른 호스트와 경로 이탈 요청을 거부한다', () => {
  assert.equal(resolveRendererFile(outDirectory, 'app://untrusted/index.html'), null)
  assert.equal(resolveRendererFile(outDirectory, 'https://focuson/index.html'), null)
  assert.equal(resolveRendererFile(outDirectory, 'app://focuson/%2F..%2Fsecret.txt'), null)
  assert.equal(resolveRendererFile(outDirectory, 'app://focuson/%5Csecret.txt'), null)
  assert.equal(resolveRendererFile(outDirectory, 'app://focuson/%00secret.txt'), null)
})

test('화면은 현재 앱 출처에서만 이동한다', () => {
  assert.equal(isAllowedNavigation('http://127.0.0.1:3000/settings/', DEV_RENDERER_URL), true)
  assert.equal(isAllowedNavigation('http://localhost:3000/', DEV_RENDERER_URL), false)
  assert.equal(isAllowedNavigation('https://example.com/', DEV_RENDERER_URL), false)
  assert.equal(isAllowedNavigation('app://focuson/settings/', PROD_RENDERER_URL), true)
  assert.equal(isAllowedNavigation('app://untrusted/', PROD_RENDERER_URL), false)
})
