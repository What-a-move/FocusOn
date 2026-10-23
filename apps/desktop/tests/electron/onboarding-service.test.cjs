const assert = require('node:assert/strict')
const test = require('node:test')
const { AuthService } = require('../../dist-electron/main/auth-service.cjs')
const { AuthSessionStore } = require('../../dist-electron/main/auth-ipc.cjs')
const { OnboardingService } = require('../../dist-electron/main/onboarding-service.cjs')
const { parseReadiness, isValidDomainList, isValidConsentList, isValidNickname } = require('../../dist-electron/shared/onboarding-contract.cjs')

const future = () => new Date(Date.now() + 15 * 60 * 1000).toISOString()
const identity = { user: { userId: 'user-1', nickname: null }, device: { deviceId: 'desktop-1', deviceType: 'DESKTOP' }, onboardingStatus: 'INCOMPLETE' }
const readiness = { onboardingStatus: 'INCOMPLETE', currentStep: 'EXTENSION', steps: { profileCompleted: true, extensionLinked: false, screenAnalysisReady: false, requiredConsentsCompleted: false, analysisExcludedDomainsStepCompleted: false } }

function vault(initial) {
  let saved = initial
  return { read: async () => saved, write: async (token) => { saved = token }, clear: async () => { saved = null } }
}

// 실제 AuthApiClient.call 대신 경로별 응답을 돌려주는 가짜 API. 호출 기록으로 순서·헤더·토큰을 확인한다.
function signedIn(routes) {
  const calls = []
  let refreshCount = 0
  const api = {
    refresh: async () => { refreshCount += 1; return { success: true, data: { accessToken: `access-${refreshCount}`, refreshToken: `refresh-${refreshCount}`, accessTokenExpiresAt: future() } } },
    me: async () => ({ success: true, data: identity }),
    call: async (path, method, status, parse, body, token, headers) => {
      calls.push({ path, method, body, token, headers })
      const reply = routes[`${method} ${path}`]
      const result = typeof reply === 'function' ? reply(calls) : reply
      if (!result) return { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: false } }
      if (!result.success) return result
      const data = parse(result.data)
      return data ? { success: true, data } : { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: false } }
    },
  }
  const store = new AuthSessionStore(() => {})
  const waits = []
  const auth = new AuthService(store, api, vault('saved-refresh'), async () => { throw new Error('unused') }, async (ms) => { waits.push(ms) })
  return { auth, store, calls, waits, refreshes: () => refreshCount }
}

test('만료 응답을 받으면 refresh를 한 번만 하고 원 요청을 한 번만 다시 보낸다', async () => {
  let attempts = 0
  const { auth, calls, refreshes } = signedIn({
    'GET /api/v1/users/me/onboarding-readiness': () => (++attempts === 1 ? { success: false, error: { code: 'AUTH_ACCESS_TOKEN_EXPIRED', retryable: true } } : { success: true, data: readiness }),
  })
  await auth.restore()
  const result = await auth.authorizedRequest({ path: '/api/v1/users/me/onboarding-readiness', method: 'GET', expectedStatus: 200, parse: parseReadiness })
  assert.equal(result.success, true)
  assert.equal(refreshes(), 2) // 앱 시작 복구 1회 + 만료 뒤 1회
  assert.deepEqual(calls.map((call) => call.token), ['access-1', 'access-2'])
})

test('다시 보낸 요청도 만료면 더 반복하지 않고 오류를 돌려준다', async () => {
  const { auth, calls } = signedIn({
    'GET /api/v1/users/me/onboarding-readiness': { success: false, error: { code: 'AUTH_ACCESS_TOKEN_EXPIRED', retryable: true } },
  })
  await auth.restore()
  const result = await auth.authorizedRequest({ path: '/api/v1/users/me/onboarding-readiness', method: 'GET', expectedStatus: 200, parse: parseReadiness })
  assert.equal(result.success, false)
  assert.equal(calls.length, 2)
})

test('refresh가 재로그인 오류면 로그아웃 상태로 바뀐다', async () => {
  const { auth, store } = signedIn({ 'GET /x': { success: false, error: { code: 'AUTH_ACCESS_TOKEN_EXPIRED', retryable: true } } })
  await auth.restore()
  auth['api'].refresh = async () => ({ success: false, error: { code: 'AUTH_RELOGIN_REQUIRED', retryable: false } })
  const result = await auth.authorizedRequest({ path: '/x', method: 'GET', expectedStatus: 200, parse: () => ({}) })
  assert.equal(result.success, false)
  assert.equal(store.getSnapshot().status, 'SIGNED_OUT')
})

test('이어하기 조회는 Main이 확인한 화면 기록 상태를 먼저 저장하고, 완료면 화면 상태도 완료로 바꾼다', async () => {
  const done = { ...readiness, onboardingStatus: 'COMPLETED', currentStep: 'COMPLETED' }
  const { auth, store, calls } = signedIn({
    'PUT /api/v1/users/me/onboarding-readiness': { success: true, data: {} },
    'GET /api/v1/users/me/onboarding-readiness': { success: true, data: done },
  })
  await auth.restore()
  const result = await new OnboardingService(auth, () => true).getReadiness()
  assert.equal(result.success, true)
  assert.deepEqual(calls.map((call) => `${call.method} ${call.path}`), ['PUT /api/v1/users/me/onboarding-readiness', 'GET /api/v1/users/me/onboarding-readiness'])
  assert.deepEqual(calls[0].body, { screenAnalysisReady: true })
  assert.equal(store.getSnapshot().onboardingStatus, 'COMPLETED')
})

test('제외 사이트는 X-Device-Id와 함께 전체 교체로 저장한 뒤에만 완료 marker를 보낸다', async () => {
  const { auth, calls } = signedIn({
    'PUT /api/v1/users/me/analysis-excluded-domains': { success: true, data: { domains: [] } },
    'PUT /api/v1/users/me/onboarding-readiness': { success: true, data: {} },
  })
  await auth.restore()
  const result = await new OnboardingService(auth, () => false).saveExcludedDomains([])
  assert.equal(result.success, true)
  assert.deepEqual(calls[0].headers, { 'X-Device-Id': 'desktop-1' })
  assert.deepEqual(calls[0].body, { domains: [] })
  assert.deepEqual(calls[1].body, { analysisExcludedDomainsStepCompleted: true })
})

test('제외 사이트 저장이 실패하면 완료 marker를 보내지 않는다', async () => {
  const { auth, calls } = signedIn({ 'PUT /api/v1/users/me/analysis-excluded-domains': { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } })
  await auth.restore()
  const result = await new OnboardingService(auth, () => false).saveExcludedDomains(['youtube.com'])
  assert.equal(result.success, false)
  assert.equal(calls.length, 1)
})

test('권한·동의 단계는 서버의 현재 동의 버전으로 필수 3개와 카메라 동의를 항목별로 저장하고 화면 기록 상태를 기록한다', async () => {
  const { auth, calls } = signedIn({
    'GET /api/v1/users/me/consents': { success: true, data: { currentVersion: '2026-10-01', consents: [] } },
    'PUT /api/v1/users/me/consents/TERMS_OF_SERVICE': { success: true, data: {} },
    'PUT /api/v1/users/me/consents/PRIVACY_POLICY': { success: true, data: {} },
    'PUT /api/v1/users/me/consents/SCREEN_ANALYSIS': { success: true, data: {} },
    'PUT /api/v1/users/me/consents/CAMERA_ANALYSIS': { success: true, data: {} },
    'PUT /api/v1/users/me/onboarding-readiness': { success: true, data: {} },
  })
  await auth.restore()
  const result = await new OnboardingService(auth, () => true).savePermissionsStep(['TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SCREEN_ANALYSIS'], false)
  assert.equal(result.success, true)
  assert.equal(calls.length, 6)
  assert.equal(calls[0].path, '/api/v1/users/me/consents')
  assert.deepEqual(calls[4].body, { isGranted: false, consentVersion: '2026-10-01', consentSource: 'ONBOARDING' })
  assert.deepEqual(calls[5].body, { screenAnalysisReady: true })
})

test('닉네임 저장이 성공하면 화면의 닉네임도 서버 값으로 바뀐다', async () => {
  const { auth, store } = signedIn({ 'PUT /api/v1/users/me/profile': { success: true, data: { nickname: '포커스온' } } })
  await auth.restore()
  const result = await new OnboardingService(auth, () => false).saveProfile('포커스온')
  assert.equal(result.success, true)
  assert.equal(store.getSnapshot().user.nickname, '포커스온')
})

test('Main은 Renderer 입력 형식을 다시 검증한다', () => {
  assert.equal(isValidNickname('포커스온'), true)
  assert.equal(isValidNickname('a'), false)
  assert.equal(isValidNickname('포커스 온'), false)
  assert.equal(isValidDomainList(['youtube.com']), true)
  assert.equal(isValidDomainList(['https://youtube.com/watch']), false)
  assert.equal(isValidDomainList(Array.from({ length: 51 }, (_, i) => `site${i}.com`)), false)
  assert.equal(isValidConsentList(['TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SCREEN_ANALYSIS']), true)
  assert.equal(isValidConsentList(['TERMS_OF_SERVICE', 'TERMS_OF_SERVICE']), false)
  assert.equal(isValidConsentList(['CAMERA_ANALYSIS']), false)
  assert.equal(parseReadiness({ ...readiness, currentStep: 'GOAL' }), null)
})

test('GET은 네트워크 오류·503에서 1초, 3초 간격으로 최대 2번 다시 시도한다', async () => {
  let attempts = 0
  const { auth, waits } = signedIn({
    'GET /api/v1/users/me/onboarding-readiness': () => (++attempts < 3 ? { success: false, error: { code: attempts === 1 ? 'LOCAL_NETWORK_UNAVAILABLE' : 'SERVICE_UNAVAILABLE', retryable: true } } : { success: true, data: readiness }),
  })
  await auth.restore()
  const result = await auth.authorizedRequest({ path: '/api/v1/users/me/onboarding-readiness', method: 'GET', expectedStatus: 200, parse: parseReadiness })
  assert.equal(result.success, true)
  assert.equal(attempts, 3)
  assert.deepEqual(waits, [1000, 3000])
})

test('GET 재시도는 2번까지만 하고 마지막 오류를 돌려준다', async () => {
  const { auth, calls, waits } = signedIn({ 'GET /x': { success: false, error: { code: 'REQUEST_TIMEOUT', retryable: true } } })
  await auth.restore()
  const result = await auth.authorizedRequest({ path: '/x', method: 'GET', expectedStatus: 200, parse: () => ({}) })
  assert.equal(result.success, false)
  assert.equal(calls.length, 3)
  assert.deepEqual(waits, [1000, 3000])
})

test('상태를 바꾸는 PUT과 재시도할 수 없는 오류는 다시 보내지 않는다', async () => {
  const { auth, calls, waits } = signedIn({
    'PUT /api/v1/users/me/profile': { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } },
    'GET /bad': { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } },
  })
  await auth.restore()
  await auth.authorizedRequest({ path: '/api/v1/users/me/profile', method: 'PUT', expectedStatus: 200, parse: () => ({}), body: { nickname: '포커스' } })
  await auth.authorizedRequest({ path: '/bad', method: 'GET', expectedStatus: 200, parse: () => ({}) })
  assert.equal(calls.length, 2)
  assert.deepEqual(waits, [])
})
