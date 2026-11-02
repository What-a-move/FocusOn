const assert = require('node:assert/strict')
const { createHash } = require('node:crypto')
const { get } = require('node:http')
const test = require('node:test')
const { GoogleOAuthFlow, createGoogleAuthorizationUrl } = require('../../dist-electron/main/google-oauth.cjs')

test('Google URL에는 code 대신 S256 challenge와 일회용 state만 넣는다', () => {
  const url = new URL(createGoogleAuthorizationUrl('desktop-client', 'http://127.0.0.1:1234', 'test-state', 'test-verifier'))
  assert.equal(url.origin, 'https://accounts.google.com')
  assert.equal(url.searchParams.get('response_type'), 'code')
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256')
  assert.equal(url.searchParams.get('scope'), 'openid email profile')
  assert.equal(url.searchParams.get('code_challenge'), createHash('sha256').update('test-verifier').digest('base64url'))
  assert.equal(url.toString().includes('test-verifier'), false)
})

test('정상 callback은 code를 Main 내부에 한 번만 전달하고 listener를 닫는다', async (t) => {
  const outcomes = []
  let browserUrl
  const flow = new GoogleOAuthFlow({
    clientId: 'desktop-client',
    openBrowser: async (url) => { browserUrl = new URL(url) },
    onOutcome: (outcome) => outcomes.push(outcome),
  })
  t.after(() => flow.dispose())

  assert.equal(await flow.start(), true)
  assert.equal(await flow.start(), false)
  const redirectUri = browserUrl.searchParams.get('redirect_uri')
  const state = browserUrl.searchParams.get('state')
  const response = await fetch(`${redirectUri}?code=one-time-code&state=${state}`)
  assert.equal(response.status, 200)
  assert.equal(response.headers.get('cache-control'), 'no-store')
  assert.equal((await response.text()).includes('one-time-code'), false)
  assert.equal(outcomes.length, 1)
  assert.equal(outcomes[0].type, 'code')
  assert.equal(outcomes[0].data.authorizationCode, 'one-time-code')
  assert.equal(outcomes[0].data.state, state)
  assert.equal(outcomes[0].data.redirectUri, redirectUri)
  assert.equal(outcomes[0].data.codeVerifier.length >= 43, true)
})

test('틀린 state와 host는 거부하고 올바른 callback을 계속 기다린다', async (t) => {
  const outcomes = []
  let browserUrl
  const flow = new GoogleOAuthFlow({
    clientId: 'desktop-client',
    openBrowser: async (url) => { browserUrl = new URL(url) },
    onOutcome: (outcome) => outcomes.push(outcome),
  })
  t.after(() => flow.dispose())
  assert.equal(await flow.start(), true)

  const redirectUri = browserUrl.searchParams.get('redirect_uri')
  const state = browserUrl.searchParams.get('state')
  assert.equal((await fetch(`${redirectUri}?code=bad&state=wrong`)).status, 400)
  assert.equal((await fetch(`${redirectUri}?code=bad&state=${state}&state=wrong`)).status, 400)
  assert.equal((await fetch(`${redirectUri}?code=bad&code=other&state=${state}`)).status, 400)
  const wrongHostStatus = await new Promise((resolve, reject) => {
    get(`${redirectUri}?code=bad&state=${state}`, { headers: { Host: 'evil.test' } }, (response) => {
      response.resume()
      resolve(response.statusCode)
    }).on('error', reject)
  })
  assert.equal(wrongHostStatus, 400)
  assert.equal(outcomes.length, 0)
  assert.equal((await fetch(`${redirectUri}?code=good&state=${state}`)).status, 200)
  assert.equal(outcomes.length, 1)
  assert.equal(outcomes[0].data.authorizationCode, 'good')
})

test('Google에서 취소하면 code 없이 취소 상태를 전달한다', async (t) => {
  const outcomes = []
  let browserUrl
  const flow = new GoogleOAuthFlow({
    clientId: 'desktop-client',
    openBrowser: async (url) => { browserUrl = new URL(url) },
    onOutcome: (outcome) => outcomes.push(outcome),
  })
  t.after(() => flow.dispose())
  assert.equal(await flow.start(), true)
  const redirectUri = browserUrl.searchParams.get('redirect_uri')
  const state = browserUrl.searchParams.get('state')
  assert.equal((await fetch(`${redirectUri}?error=access_denied&state=${state}`)).status, 200)
  assert.deepEqual(outcomes, [{ type: 'cancelled' }])
})

test('브라우저 실행 실패 시 실패를 반환하고 callback listener를 정리한다', async () => {
  let browserUrl
  const flow = new GoogleOAuthFlow({
    clientId: 'desktop-client',
    openBrowser: async (url) => {
      browserUrl = new URL(url)
      throw new Error('fake browser failure')
    },
    onOutcome: () => assert.fail('callback이 실행되면 안 돼'),
  })
  assert.equal(await flow.start(), false)
  assert.equal(browserUrl.searchParams.get('response_type'), 'code')
  flow.dispose()
})

test('callback이 오지 않으면 timeout 뒤 시도를 취소하고 listener를 닫는다', async (t) => {
  const outcomes = []
  const flow = new GoogleOAuthFlow({
    clientId: 'desktop-client',
    openBrowser: async () => {},
    onOutcome: (outcome) => outcomes.push(outcome),
    timeoutMs: 20,
  })
  t.after(() => flow.dispose())
  assert.equal(await flow.start(), true)
  await new Promise((resolve) => setTimeout(resolve, 50))
  assert.deepEqual(outcomes, [{ type: 'cancelled' }])
})
