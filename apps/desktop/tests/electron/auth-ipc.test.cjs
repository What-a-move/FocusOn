const assert = require('node:assert/strict')
const test = require('node:test')
const { AuthSessionStore, beginGoogleLogin, isTrustedAuthSender, isValidAuthRequest } = require('../../dist-electron/main/auth-ipc.cjs')
const { parseAuthSessionSnapshot } = require('../../dist-electron/shared/auth-contract.cjs')

test('인증 상태 변경은 revision을 증가시키고 안전한 Snapshot을 보낸다', () => {
  const notifications = []
  const store = new AuthSessionStore((snapshot) => notifications.push(snapshot))

  assert.deepEqual(store.getSnapshot(), {
    revision: 0, status: 'SIGNED_OUT', user: null, onboardingStatus: null, error: null,
  })

  store.publish({
    status: 'SIGNED_IN',
    user: { userId: 'user-1', nickname: '포커스', refreshToken: 'secret' },
    onboardingStatus: 'INCOMPLETE',
    error: null,
    accessToken: 'secret',
  })

  assert.equal(store.getSnapshot().revision, 1)
  assert.equal(notifications.length, 1)
  assert.equal(notifications[0].user.userId, 'user-1')
  assert.equal(JSON.stringify(notifications).includes('secret'), false)
})

test('이벤트 Snapshot은 계약 필드만 통과시킨다', () => {
  const snapshot = parseAuthSessionSnapshot({
    revision: 2,
    status: 'SIGNED_IN',
    user: { userId: 'user-1', nickname: null, refreshToken: 'secret' },
    onboardingStatus: 'COMPLETED',
    error: null,
    accessToken: 'secret',
  })

  assert.deepEqual(snapshot, {
    revision: 2,
    status: 'SIGNED_IN',
    user: { userId: 'user-1', nickname: null },
    onboardingStatus: 'COMPLETED',
    error: null,
  })
  assert.equal(parseAuthSessionSnapshot({ revision: -1, status: 'SIGNED_OUT' }), null)
})

test('현재 창의 Main Frame과 허용 주소에서 온 요청만 신뢰한다', () => {
  const mainFrame = { url: 'app://focuson/' }
  const webContents = { mainFrame }
  const window = { isDestroyed: () => false, webContents }
  const validEvent = { sender: webContents, senderFrame: mainFrame }

  assert.equal(isTrustedAuthSender(validEvent, window, 'app://focuson/'), true)
  assert.equal(isValidAuthRequest(validEvent, [], window, 'app://focuson/'), true)
  assert.equal(isValidAuthRequest(validEvent, [{ token: 'secret' }], window, 'app://focuson/'), false)
  assert.equal(isTrustedAuthSender({ sender: {}, senderFrame: mainFrame }, window, 'app://focuson/'), false)
  assert.equal(isTrustedAuthSender({ sender: webContents, senderFrame: { url: 'app://focuson/' } }, window, 'app://focuson/'), false)
  assert.equal(isTrustedAuthSender({ sender: webContents, senderFrame: { url: 'app://untrusted/' } }, window, 'app://focuson/'), false)
  assert.equal(isTrustedAuthSender(validEvent, null, 'app://focuson/'), false)
})

test('Google 로그인 시작과 다시 시작은 계약대로 상태를 바꾼다', async () => {
  const events = []
  const store = new AuthSessionStore((snapshot) => events.push(snapshot))
  let calls = 0
  let disposed = 0
  const flow = { start: async () => { calls += 1; return true }, dispose: () => { disposed += 1 } }

  assert.deepEqual(await beginGoogleLogin(store, flow), { success: true, data: { started: true } })
  assert.equal(store.getSnapshot().status, 'AUTHENTICATING')
  assert.equal(disposed, 0)
  // 진행 중에 다시 누르면 이전 시도를 정리하고 새로 시작한다 (브라우저 탭을 닫은 경우)
  assert.deepEqual(await beginGoogleLogin(store, flow), { success: true, data: { started: true } })
  assert.equal(disposed, 1)
  assert.equal(calls, 2)
  assert.equal(store.getSnapshot().status, 'AUTHENTICATING')
})

test('설정 누락 또는 브라우저 실패를 로그인 성공으로 취급하지 않는다', async () => {
  const store = new AuthSessionStore(() => {})
  // 설정 누락은 다시 눌러도 같으므로 retryable이 아니고, 브라우저 실패와 다른 code로 화면에 원인을 알린다.
  assert.deepEqual(await beginGoogleLogin(store, null), { success: false, error: { code: 'LOCAL_APP_CONFIG_MISSING', retryable: false } })
  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')

  const result = await beginGoogleLogin(store, { start: async () => false })
  assert.deepEqual(result, { success: false, error: { code: 'LOCAL_BROWSER_OPEN_FAILED', retryable: true } })
  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
  assert.equal(store.getSnapshot().error.code, 'LOCAL_BROWSER_OPEN_FAILED')

  const thrown = await beginGoogleLogin(store, { start: async () => { throw new Error('secret internal error') } })
  assert.deepEqual(thrown, { success: false, error: { code: 'LOCAL_BROWSER_OPEN_FAILED', retryable: true } })
  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
})
