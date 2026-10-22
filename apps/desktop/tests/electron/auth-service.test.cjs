const assert = require('node:assert/strict')
const test = require('node:test')
const { AuthApiClient, resolveAuthApiBaseUrl } = require('../../dist-electron/main/auth-api.cjs')
const { AuthService } = require('../../dist-electron/main/auth-service.cjs')
const { AuthSessionStore } = require('../../dist-electron/main/auth-ipc.cjs')

const tokens = {
  accessToken: 'access-secret',
  refreshToken: 'refresh-secret-new',
  accessTokenExpiresAt: '2030-01-01T00:00:00Z',
}
const identity = {
  user: { userId: 'user-1', nickname: '포커스' },
  device: { deviceId: 'device-1', deviceType: 'DESKTOP' },
  onboardingStatus: 'INCOMPLETE',
}

function createVault(initial = null) {
  let saved = initial
  return {
    read: async () => saved,
    write: async (token) => { saved = token },
    clear: async () => { saved = null },
    peek: () => saved,
  }
}

test('API 주소는 HTTPS 또는 로컬 개발 origin만 허용한다', () => {
  assert.equal(resolveAuthApiBaseUrl('https://api.focuson.example'), 'https://api.focuson.example')
  assert.equal(resolveAuthApiBaseUrl('http://127.0.0.1:8080'), 'http://127.0.0.1:8080')
  assert.equal(resolveAuthApiBaseUrl('http://public.example'), null)
  assert.equal(resolveAuthApiBaseUrl('https://api.focuson.example/path'), null)
})

test('Google code 교환은 redirectUri를 그대로 전송하고 화면용 결과만 꺼낸다', async () => {
  let called
  const api = new AuthApiClient('http://127.0.0.1:8080', async (url, options) => {
    called = { url, options }
    return new Response(JSON.stringify({ data: { ...tokens, ...identity } }), { status: 201 })
  })
  const code = { authorizationCode: 'one-time-code', codeVerifier: 'verifier', state: 'state', redirectUri: 'http://127.0.0.1:49152' }
  const device = { installationId: 'installation-1', deviceName: 'Mac', platform: 'MACOS' }
  const result = await api.exchange(code, device)

  assert.equal(result.success, true)
  assert.equal(called.url, 'http://127.0.0.1:8080/api/v1/auth/google/exchange')
  assert.equal(JSON.parse(called.options.body).redirectUri, code.redirectUri)
  assert.equal(called.options.redirect, 'manual')
  assert.equal(called.options.headers.Authorization, undefined)
})

test('앱 재실행 시 refresh를 한 번 회전하고 새 토큰을 Keychain에 저장한다', async () => {
  const events = []
  const store = new AuthSessionStore((snapshot) => events.push(snapshot))
  const vault = createVault('old-refresh')
  const calls = []
  const api = {
    refresh: async (token) => { calls.push(['refresh', token]); return { success: true, data: tokens } },
    me: async (token) => { calls.push(['me', token]); return { success: true, data: identity } },
  }
  const service = new AuthService(store, api, vault, async () => { throw new Error('unused') })
  await service.restore()

  assert.deepEqual(calls, [['refresh', 'old-refresh'], ['me', 'access-secret']])
  assert.equal(vault.peek(), 'refresh-secret-new')
  assert.equal(store.getSnapshot().status, 'SIGNED_IN')
  assert.equal(store.getSnapshot().onboardingStatus, 'INCOMPLETE')
  assert.equal(JSON.stringify(events).includes('secret'), false)
})

test('오프라인 복구 재시도는 저장된 토큰을 유지하고 중복 refresh를 막는다', async () => {
  const store = new AuthSessionStore(() => {})
  const vault = createVault('saved-refresh')
  let refreshCalls = 0
  const api = {
    refresh: async () => {
      refreshCalls += 1
      if (refreshCalls === 1) return { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } }
      await new Promise((resolve) => setTimeout(resolve, 10))
      return { success: true, data: tokens }
    },
    me: async () => ({ success: true, data: identity }),
  }
  const service = new AuthService(store, api, vault, async () => { throw new Error('unused') })

  await service.restore()
  assert.equal(store.getSnapshot().status, 'OFFLINE')
  assert.equal(vault.peek(), 'saved-refresh')

  await Promise.all([service.restore(), service.restore()])
  assert.equal(refreshCalls, 2)
  assert.equal(store.getSnapshot().status, 'SIGNED_IN')
  assert.equal(vault.peek(), 'refresh-secret-new')
})

test('Backend 교환이 실패하면 Keychain에 저장하거나 로그인 성공으로 표시하지 않는다', async () => {
  const store = new AuthSessionStore(() => {})
  const vault = createVault()
  const api = { exchange: async () => ({ success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } }) }
  const service = new AuthService(store, api, vault, async () => ({ installationId: 'one', deviceName: 'Mac', platform: 'MACOS' }))
  await service.handleOAuthOutcome({ type: 'code', data: { authorizationCode: 'code', codeVerifier: 'verifier', state: 'state', redirectUri: 'http://127.0.0.1:49152' } })

  assert.equal(vault.peek(), null)
  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
  assert.equal(store.getSnapshot().error.code, 'SERVICE_UNAVAILABLE')
})

test('Keychain 저장이 실패하면 발급된 서버 토큰을 폐기하고 로그인 성공을 막는다', async () => {
  const store = new AuthSessionStore(() => {})
  let revoked
  const vault = { read: async () => null, write: async () => { throw new Error('locked keychain') }, clear: async () => {} }
  const api = {
    exchange: async () => ({ success: true, data: { ...tokens, ...identity } }),
    logout: async (token) => { revoked = token; return { success: true, data: { loggedOut: true } } },
  }
  const service = new AuthService(store, api, vault, async () => ({ installationId: 'one', deviceName: 'Mac', platform: 'MACOS' }))
  await service.handleOAuthOutcome({ type: 'code', data: { authorizationCode: 'code', codeVerifier: 'verifier', state: 'state', redirectUri: 'http://127.0.0.1:49152' } })

  assert.equal(revoked, 'access-secret')
  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
})

test('동시에 만료된 요청은 refresh 한 번을 공유한다', async () => {
  const store = new AuthSessionStore(() => {})
  const vault = createVault('saved-refresh')
  let refreshCalls = 0
  const api = {
    refresh: async () => {
      refreshCalls += 1
      await new Promise((resolve) => setTimeout(resolve, 10))
      return { success: true, data: tokens }
    },
  }
  const service = new AuthService(store, api, vault, async () => { throw new Error('unused') })
  const [first, second] = await Promise.all([service.refresh(), service.refresh()])

  assert.equal(first.success, true)
  assert.equal(second.success, true)
  assert.equal(refreshCalls, 1)
  assert.equal(vault.peek(), 'refresh-secret-new')
})

test('로그아웃은 서버가 실패하면 Keychain 토큰을 보존한다', async () => {
  const store = new AuthSessionStore(() => {})
  const vault = createVault('saved-refresh')
  const api = {
    refresh: async () => ({ success: true, data: tokens }),
    logout: async () => ({ success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } }),
  }
  const service = new AuthService(store, api, vault, async () => { throw new Error('unused') })
  const result = await service.logout()

  assert.equal(result.success, false)
  assert.equal(vault.peek(), 'refresh-secret-new')
})

test('Google 재인증이 필요하면 저장된 토큰을 지우고 로그아웃 상태로 돌아간다', async () => {
  const store = new AuthSessionStore(() => {})
  const vault = createVault('saved-refresh')
  const api = {
    refresh: async () => ({ success: true, data: tokens }),
    me: async () => ({ success: false, error: { code: 'AUTH_OAUTH_REAUTH_REQUIRED', retryable: false } }),
  }
  const service = new AuthService(store, api, vault, async () => { throw new Error('unused') })
  await service.restore()

  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
  assert.equal(store.getSnapshot().error.code, 'AUTH_OAUTH_REAUTH_REQUIRED')
  assert.equal(vault.peek(), null)
})

test('다시 로그인을 시작하면 늦게 끝난 이전 교환 결과는 저장하지 않고 서버 토큰을 폐기한다', async () => {
  const store = new AuthSessionStore(() => {})
  const vault = createVault()
  let finishExchange
  const loggedOut = []
  const api = {
    exchange: () => new Promise((resolve) => { finishExchange = resolve }),
    logout: async (token) => { loggedOut.push(token); return { success: true, data: { loggedOut: true } } },
  }
  const service = new AuthService(store, api, vault, async () => ({ installationId: 'i', deviceName: 'Mac', platform: 'MACOS' }))
  const code = { authorizationCode: 'old-code', codeVerifier: 'v', state: 's', redirectUri: 'http://127.0.0.1:49152' }
  const pending = service.handleOAuthOutcome({ type: 'code', data: code })
  await new Promise((resolve) => setImmediate(resolve))
  service.invalidatePendingLogin()
  finishExchange({ success: true, data: { ...tokens, ...identity } })
  await pending

  assert.equal(vault.peek(), null)
  assert.deepEqual(loggedOut, ['access-secret'])
  assert.notEqual(store.getSnapshot().status, 'SIGNED_IN')
})
