const assert = require('node:assert/strict')
const test = require('node:test')
const { AuthService } = require('../../dist-electron/main/auth-service.cjs')
const { AuthSessionStore } = require('../../dist-electron/main/auth-ipc.cjs')

// 실제 서비스와 저장 상태를 사용하고, 외부 응답·Keychain 완료 순서만 제어한다.
const tick = () => new Promise((resolve) => setImmediate(resolve))
const device = async () => ({ installationId: 'synthetic-installation', deviceName: 'QA Mac', platform: 'MACOS' })
const identity = (name) => ({ user: { userId: `user-${name}`, nickname: null }, device: { deviceId: `device-${name}`, deviceType: 'DESKTOP' }, onboardingStatus: 'INCOMPLETE' })
const pair = (name, seconds = 900) => ({ ...identity(name), accessToken: `synthetic-access-${name}`, refreshToken: `synthetic-refresh-${name}`, accessTokenExpiresAt: new Date(Date.now() + seconds * 1000).toISOString() })
const outcome = (name) => ({ type: 'code', data: { authorizationCode: name, codeVerifier: 'a'.repeat(43), state: 'synthetic-state', redirectUri: 'http://127.0.0.1:49152' } })
const error = (code) => ({ success: false, error: { code, retryable: false } })
const request = (method = 'GET') => ({ path: '/api/v1/users/me/profile', method, expectedStatus: 200, parse: (value) => value, body: method === 'PUT' ? { nickname: 'old-account-value' } : undefined })
function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
function fixture(overrides = {}, options = {}) {
  let saved = null
  const vault = { read: async () => saved, write: async (value) => { saved = value }, clear: async () => { saved = null } }
  const store = new AuthSessionStore(() => {})
  const api = {
    exchange: async (code) => ({ success: true, data: pair(code.authorizationCode, options.seconds) }),
    logout: async () => ({ success: true, data: { loggedOut: true } }),
    refresh: async () => ({ success: true, data: pair('rotated') }),
    me: async () => ({ success: true, data: identity('A') }),
    call: async () => ({ success: true, data: { saved: true } }),
    ...overrides,
  }
  const service = new AuthService(store, api, vault, device, options.sleep ?? (async () => {}))
  return { service, store, api, vault }
}

for (const code of ['AUTH_RELOGIN_REQUIRED', 'AUTH_TOKEN_REUSE_DETECTED', 'AUTH_OAUTH_REAUTH_REQUIRED']) {
 for (const afterExpiry of [false, true]) {
  test(`${code} 일반 API 오류는 token과 화면 상태를 정리한다 (만료 재시도=${afterExpiry})`, async () => {
    let calls = 0
    const qa = fixture({ call: async () => (++calls === 1 && afterExpiry) ? error('AUTH_ACCESS_TOKEN_EXPIRED') : error(code) })
    await qa.service.handleOAuthOutcome(outcome('A'))
    assert.equal((await qa.service.authorizedRequest(request())).success, false)
    assert.equal(qa.store.getSnapshot().status, 'SIGNED_OUT')
    assert.equal(await qa.vault.read(), null)
  })
 }
}

for (const code of ['SERVICE_UNAVAILABLE', 'LOCAL_NETWORK_UNAVAILABLE', 'UNRECOGNIZED_AUTH_ERROR']) {
  test(`${code} 일반 요청 실패는 유효 자격 증명을 보존한다`, async () => {
    const qa = fixture({ call: async () => error(code) })
    await qa.service.handleOAuthOutcome(outcome('A'))
    assert.equal((await qa.service.authorizedRequest(request())).success, false)
    assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
    assert.equal(await qa.vault.read(), pair('A').refreshToken)
  })
}

test('같은 설치의 교환은 이전 응답 정리 뒤 새 교환을 실행한다', async () => {
  const release = deferred()
  const live = new Set()
  const commit = (name) => { live.clear(); live.add(name); return { success: true, data: pair(name) } }
  const qa = fixture({
    exchange: async (code) => { if (code.authorizationCode === 'A') await release.promise; return commit(code.authorizationCode) },
    logout: async (token) => { live.delete(token.endsWith('-A') ? 'A' : 'B'); return { success: true, data: { loggedOut: true } } },
  })
  const first = qa.service.handleOAuthOutcome(outcome('A'))
  await tick()
  qa.service.invalidatePendingLogin()
  const second = qa.service.handleOAuthOutcome(outcome('B'))
  await tick()
  release.resolve()
  await Promise.all([first, second])
  assert.deepEqual([...live], ['B'])
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
  assert.equal(await qa.vault.read(), pair('B').refreshToken)
})

test('이전 Keychain 쓰기 뒤 cleanup이 새 로그인 token을 지우지 않는다', async () => {
  const entered = deferred(), release = deferred()
  const qa = fixture()
  const write = qa.vault.write
  qa.vault.write = async (value) => { await write(value); if (value === pair('A').refreshToken) { entered.resolve(); await release.promise } }
  const first = qa.service.handleOAuthOutcome(outcome('A'))
  await entered.promise
  qa.service.invalidatePendingLogin()
  const second = qa.service.handleOAuthOutcome(outcome('B'))
  await tick()
  release.resolve()
  await Promise.all([first, second])
  assert.equal(await qa.vault.read(), pair('B').refreshToken)
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
})

test('restore와 refresh는 같은 token 회전 한 번을 공유한다', async () => {
  const release = deferred(), inputs = []
  const qa = fixture({ refresh: async (token) => { inputs.push(token); return inputs.length === 1 ? release.promise : error('AUTH_TOKEN_REUSE_DETECTED') } })
  await qa.service.handleOAuthOutcome(outcome('A'))
  const first = qa.service.refresh()
  await tick()
  const restored = qa.service.restore()
  await tick()
  release.resolve({ success: true, data: pair('rotated') })
  await Promise.all([first, restored])
  assert.equal(inputs.length, 1)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
  assert.equal(await qa.vault.read(), pair('rotated').refreshToken)
})

test('진행 중 갱신이 있어도 logout 완료 뒤 token이 다시 저장되지 않는다', async () => {
  const release = deferred()
  let calls = 0
  const qa = fixture({ refresh: async () => release.promise, call: async () => { calls++; return error('AUTH_RELOGIN_REQUIRED') } }, { seconds: 20 })
  await qa.service.handleOAuthOutcome(outcome('A'))
  const pending = qa.service.authorizedRequest(request())
  await tick()
  const logout = qa.service.logout()
  await tick()
  release.resolve({ success: true, data: pair('rotated') })
  const [, result] = await Promise.all([pending, logout])
  assert.equal(result.success, true)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_OUT')
  assert.equal(await qa.vault.read(), null)
  assert.equal(calls, 0)
})

test('이전 계정의 만료된 PUT을 새 계정 token으로 다시 보내지 않는다', async () => {
  const release = deferred(), calls = []
  const qa = fixture({ call: async (...args) => { calls.push(args); return calls.length === 1 ? release.promise : { success: true, data: { saved: true } } } })
  await qa.service.handleOAuthOutcome(outcome('A'))
  const pending = qa.service.authorizedRequest(request('PUT'))
  await tick()
  await qa.service.logout()
  await qa.service.handleOAuthOutcome(outcome('B'))
  release.resolve(error('AUTH_ACCESS_TOKEN_EXPIRED'))
  assert.equal((await pending).success, false)
  assert.equal(calls.length, 1)
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
  assert.equal(await qa.vault.read(), pair('B').refreshToken)
})

test('GET 재시도 대기 중 계정이 바뀌면 추가 요청을 보내지 않는다', async () => {
  const release = deferred()
  let calls = 0
  const qa = fixture({ call: async () => { calls++; return error('SERVICE_UNAVAILABLE') } }, { sleep: () => release.promise })
  await qa.service.handleOAuthOutcome(outcome('A'))
  const pending = qa.service.authorizedRequest(request())
  await tick()
  await qa.service.logout()
  await qa.service.handleOAuthOutcome(outcome('B'))
  release.resolve()
  await pending
  assert.equal(calls, 1)
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
})

for (const response of [{ success: true, data: { oldUserData: true } }, error('AUTH_RELOGIN_REQUIRED')]) {
  test(`계정 전환 뒤 이전 ${response.success ? '성공' : '인증 실패'} 응답은 새 상태에 적용하지 않는다`, async () => {
    const release = deferred()
    const qa = fixture({ call: async () => release.promise })
    await qa.service.handleOAuthOutcome(outcome('A'))
    const pending = qa.service.authorizedRequest(request())
    await tick()
    await qa.service.logout()
    await qa.service.handleOAuthOutcome(outcome('B'))
    release.resolve(response)
    assert.equal((await pending).success, false)
    assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
    assert.equal(await qa.vault.read(), pair('B').refreshToken)
  })
}

test('로그아웃 중 시작한 로그인은 logout cleanup 뒤 저장한다', async () => {
  const entered = deferred(), release = deferred()
  const qa = fixture({ logout: async () => { entered.resolve(); await release.promise; return { success: true, data: { loggedOut: true } } } })
  await qa.service.handleOAuthOutcome(outcome('A'))
  const logout = qa.service.logout()
  await entered.promise
  const login = qa.service.handleOAuthOutcome(outcome('B'))
  await tick()
  release.resolve()
  await Promise.all([logout, login])
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
  assert.equal(await qa.vault.read(), pair('B').refreshToken)
})

test('이전 restore의 늦은 me 응답이 새 로그인 사용자를 덮지 않는다', async () => {
  const release = deferred(), entered = deferred()
  const qa = fixture({ me: async () => { entered.resolve(); return release.promise } })
  await qa.service.handleOAuthOutcome(outcome('A'))
  const restored = qa.service.restore()
  await entered.promise
  const login = qa.service.handleOAuthOutcome(outcome('B'))
  await login
  release.resolve({ success: true, data: identity('A') })
  await restored
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
})

test('Keychain 삭제 실패도 인증 요청을 reject시키거나 다음 로그인 큐를 막지 않는다', async () => {
  const qa = fixture({ call: async () => error('AUTH_RELOGIN_REQUIRED') })
  await qa.service.handleOAuthOutcome(outcome('A'))
  const clear = qa.vault.clear
  qa.vault.clear = async () => { throw new Error('synthetic locked Keychain') }
  assert.equal((await qa.service.authorizedRequest(request())).success, false)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_OUT')
  qa.vault.clear = clear
  await qa.service.handleOAuthOutcome(outcome('B'))
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
})

test('로그아웃 실패 뒤 보존한 로그인은 새 인증 요청에 계속 사용할 수 있다', async () => {
  const qa = fixture({ logout: async () => error('SERVICE_UNAVAILABLE') })
  await qa.service.handleOAuthOutcome(outcome('A'))
  assert.equal((await qa.service.logout()).success, false)
  assert.equal((await qa.service.authorizedRequest(request())).success, true)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
  assert.equal(await qa.vault.read(), pair('A').refreshToken)
})

test('Keychain 읽기 실패의 logout은 실패 결과를 반환하고 큐를 복구한다', async () => {
  const qa = fixture()
  const read = qa.vault.read
  qa.vault.read = async () => { throw new Error('synthetic locked Keychain') }
  assert.equal((await qa.service.logout()).success, false)
  assert.equal(qa.store.getSnapshot().status, 'OFFLINE')
  qa.vault.read = read
  await qa.service.handleOAuthOutcome(outcome('A'))
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
})

test('저장된 Refresh Token이 없으면 만료된 인증 상태를 정리한다', async () => {
  const qa = fixture({}, { seconds: 20 })
  await qa.service.handleOAuthOutcome(outcome('A'))
  await qa.vault.clear()
  assert.equal((await qa.service.authorizedRequest(request())).success, false)
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_OUT')
})

test('저장 token이 없는 첫 실행은 만료 오류 없이 시작한다', async () => {
  const qa = fixture()
  await qa.service.restore()
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_OUT')
  assert.equal(qa.store.getSnapshot().error, null)
})

test('로그인 중 복구의 늦은 빈 Keychain 읽기가 새 로그인을 지우지 않는다', async () => {
  const exchange = deferred(), readRelease = deferred()
  const qa = fixture({ exchange: async () => { await exchange.promise; return { success: true, data: pair('B') } }, me: async () => ({ success: true, data: identity('B') }) })
  const read = qa.vault.read
  qa.vault.read = async () => { const value = await read(); await readRelease.promise; return value }
  const login = qa.service.handleOAuthOutcome(outcome('B'))
  await tick()
  const restored = qa.service.restore()
  await tick()
  exchange.resolve()
  await login
  readRelease.resolve()
  await restored
  assert.equal(qa.store.getSnapshot().status, 'SIGNED_IN')
  assert.equal(qa.store.getSnapshot().user.userId, 'user-B')
})

for (const operation of ['read', 'write']) {
  test(`자동 갱신의 Keychain ${operation} 실패는 로그인 화면을 멈추지 않고 복구 상태로 전환한다`, async () => {
    const qa = fixture({}, { seconds: 20 })
    await qa.service.handleOAuthOutcome(outcome('A'))
    const original = qa.vault[operation]
    qa.vault[operation] = async () => { throw new Error('synthetic locked Keychain') }
    assert.equal((await qa.service.authorizedRequest(request())).success, false)
    assert.equal(qa.store.getSnapshot().status, 'OFFLINE')
    qa.vault[operation] = original
    // write 실패 때는 서버가 이미 회전했으므로 남은 옛 token으로 재로그인할 수 없다.
    if (operation === 'write') qa.api.refresh = async () => error('AUTH_TOKEN_REUSE_DETECTED')
    await qa.service.restore()
    assert.equal(qa.store.getSnapshot().status, operation === 'write' ? 'SIGNED_OUT' : 'SIGNED_IN')
    if (operation === 'write') assert.equal(await qa.vault.read(), null)
  })
}
