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
  assert.equal(store.getSnapshot().error.code, 'LOCAL_KEYCHAIN_UNAVAILABLE')
})

test('Google 로그인 창이 실패로 끝나면 서버 장애가 아닌 Google 로그인 실패로 알린다', async () => {
  const store = new AuthSessionStore(() => {})
  const service = new AuthService(store, {}, createVault(null), async () => { throw new Error('unused') })
  await service.handleOAuthOutcome({ type: 'failed' })
  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
  assert.equal(store.getSnapshot().error.code, 'LOCAL_GOOGLE_LOGIN_FAILED')
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

test('로그아웃 API의 만료 401은 성공으로 바꾸지 않고 코드를 서비스에 전달한다', async () => {
  const api = new AuthApiClient('http://127.0.0.1:8080', async () => new Response(
    JSON.stringify({ code: 'AUTH_ACCESS_TOKEN_EXPIRED', retryable: false }), { status: 401 },
  ))
  assert.deepEqual(await api.logout('expired'), {
    success: false, error: { code: 'AUTH_ACCESS_TOKEN_EXPIRED', retryable: false },
  })
})

// 실제 API 파서와 서비스를 함께 사용해 응답 사이의 만료·갱신·폐기 순서를 검사한다.
function logoutExpiryFixture({ retryStatus = 204, refreshStatus = 200, logoutCode = 'AUTH_ACCESS_TOKEN_EXPIRED' } = {}) {
  const calls = []
  const store = new AuthSessionStore(() => {})
  const vault = createVault()
  let logoutCalls = 0
  const api = new AuthApiClient('http://127.0.0.1:8080', async (url, options) => {
    const path = new URL(url).pathname
    calls.push([path, options.headers.Authorization])
    if (path.endsWith('/exchange')) return new Response(JSON.stringify({ data: { ...tokens, ...identity } }), { status: 201 })
    if (path.endsWith('/refresh')) {
      assert.equal(options.headers.Authorization, undefined)
      assert.equal(JSON.parse(options.body).refreshToken, tokens.refreshToken)
      return refreshStatus === 200
        ? new Response(JSON.stringify({ data: { ...tokens, accessToken: 'rotated-access', refreshToken: 'rotated-refresh' } }), { status: 200 })
        : new Response(JSON.stringify({ code: 'SERVICE_UNAVAILABLE', retryable: true }), { status: refreshStatus })
    }
    if (path.endsWith('/logout')) {
      logoutCalls += 1
      if (logoutCalls === 1) return new Response(JSON.stringify({ code: logoutCode, retryable: false }), { status: 401 })
      return retryStatus === 204 ? new Response(null, { status: 204 })
        : new Response(JSON.stringify({ code: 'AUTH_ACCESS_TOKEN_EXPIRED', retryable: false }), { status: retryStatus })
    }
    throw new Error('unexpected authentication path')
  })
  const service = new AuthService(store, api, vault, async () => ({ installationId: 'logout-expiry', deviceName: 'QA Mac', platform: 'MACOS' }))
  const login = () => service.handleOAuthOutcome({ type: 'code', data: {
    authorizationCode: 'code', codeVerifier: 'verifier', state: 'state', redirectUri: 'http://127.0.0.1:49152/',
  } })
  return { calls, store, vault, service, login }
}

test('전송 중 만료된 로그아웃은 refresh 후 새 Access Token으로 한 번만 다시 보낸다', async () => {
  const qa = logoutExpiryFixture()
  await qa.login()
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
  assert.deepEqual(await qa.service.logout(), { success: true, data: { loggedOut: true } })
  assert.deepEqual(qa.calls.slice(1), [
    ['/api/v1/auth/logout', 'Bearer access-secret'],
    ['/api/v1/auth/refresh', undefined],
    ['/api/v1/auth/logout', 'Bearer rotated-access'],
  ])
  assert.equal(qa.vault.peek(), null)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_OUT')
})

test('로그아웃의 갱신이 실패하면 Keychain과 로그인 상태를 유지해 재시도할 수 있다', async () => {
  const qa = logoutExpiryFixture({ refreshStatus: 503 })
  await qa.login()
  const result = await qa.service.logout()
  assert.equal(result.success, false)
  assert.equal(result.error.code, 'SERVICE_UNAVAILABLE')
  assert.equal(qa.calls.length, 3)
  assert.equal(qa.vault.peek(), tokens.refreshToken)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
})

test('로그아웃 재시도도 만료이면 반복 갱신하지 않고 새 Keychain 토큰을 유지한다', async () => {
  const qa = logoutExpiryFixture({ retryStatus: 401 })
  await qa.login()
  const result = await qa.service.logout()
  assert.equal(result.success, false)
  assert.equal(result.error.code, 'AUTH_ACCESS_TOKEN_EXPIRED')
  assert.equal(qa.calls.length, 4)
  assert.equal(qa.vault.peek(), 'rotated-refresh')
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
})

test('의미를 알 수 없는 로그아웃 401은 성공 처리하거나 Keychain을 삭제하지 않는다', async () => {
  const qa = logoutExpiryFixture({ logoutCode: 'UNRECOGNIZED_AUTH_ERROR' })
  await qa.login()
  assert.equal((await qa.service.logout()).success, false)
  assert.equal(qa.calls.length, 2)
  assert.equal(qa.vault.peek(), tokens.refreshToken)
})

test('이미 폐기된 로그인은 불필요한 refresh 없이 로컬 로그아웃을 완료한다', async () => {
  const qa = logoutExpiryFixture({ logoutCode: 'AUTH_RELOGIN_REQUIRED' })
  await qa.login()
  assert.equal((await qa.service.logout()).success, true)
  assert.equal(qa.calls.length, 2)
  assert.equal(qa.vault.peek(), null)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_OUT')
})

test('수명 20초의 Access Token이면 refresh 장애에도 logout 20회가 먼저 성공한다', async (t) => {
  let refreshCalls = 0
  let logoutCalls = 0
  let successes = 0
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const nearExpiry = { ...tokens, accessTokenExpiresAt: new Date(Date.now() + 20_000).toISOString() }
    const store = new AuthSessionStore(() => {})
    const vault = createVault()
    const api = new AuthApiClient('http://127.0.0.1:8080', async (url) => {
      if (url.endsWith('/exchange')) return new Response(JSON.stringify({ data: { ...nearExpiry, ...identity } }), { status: 201 })
      if (url.endsWith('/refresh')) {
        refreshCalls += 1
        return new Response(JSON.stringify({ code: 'SERVICE_UNAVAILABLE', retryable: true }), { status: 503 })
      }
      if (url.endsWith('/logout')) { logoutCalls += 1; return new Response(null, { status: 204 }) }
      throw new Error('unexpected authentication path')
    })
    const service = new AuthService(store, api, vault, async () => ({ installationId: 'margin-qa', deviceName: 'QA', platform: 'MACOS' }))
    await service.handleOAuthOutcome({ type: 'code', data: { authorizationCode: 'synthetic', codeVerifier: 'synthetic', state: 'synthetic', redirectUri: 'http://127.0.0.1:49152/' } })
    if ((await service.logout()).success) {
      successes += 1
      assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
      assert.equal(vault.peek(), null)
    }
  }
  t.diagnostic(JSON.stringify({ attempts: 20, successes, refreshCalls, logoutCalls }))
  assert.equal(successes, 20)
  assert.equal(refreshCalls, 0)
  assert.equal(logoutCalls, 20)
})
