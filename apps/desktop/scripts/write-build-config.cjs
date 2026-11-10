// 패키지 앱에는 .env가 없다. 빌드할 때 받은 서버 주소·Google Client ID를 dist-electron/build-config.json에 넣는다.
// Client ID는 데스크톱 앱용 공개값이다(비밀값 client secret은 서버만 가진다).
const fs = require('node:fs')
const path = require('node:path')

const apiBaseUrl = process.env.FOCUSON_API_BASE_URL?.trim()
const googleClientId = process.env.FOCUSON_GOOGLE_CLIENT_ID?.trim()
if (!apiBaseUrl || !googleClientId) {
  console.error('FOCUSON_API_BASE_URL과 FOCUSON_GOOGLE_CLIENT_ID를 넣고 다시 실행해 주세요.')
  process.exit(1)
}
// 앱의 resolveAuthApiBaseUrl과 같은 규칙: 로컬이 아니면 https만.
const url = new URL(apiBaseUrl)
const loopback = url.hostname === '127.0.0.1' || url.hostname === 'localhost'
if (url.protocol !== 'https:' && !(loopback && url.protocol === 'http:')) {
  console.error(`서버 주소는 https여야 해요: ${apiBaseUrl}`)
  process.exit(1)
}
fs.writeFileSync(path.join(__dirname, '../dist-electron/build-config.json'), `${JSON.stringify({ apiBaseUrl, googleClientId })}\n`)
console.log(`build-config: ${url.origin}${loopback ? ' (로컬 서버 — 이 Mac에서만 동작)' : ''}`)
