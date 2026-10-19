const assert = require('node:assert/strict')
const test = require('node:test')

test('서버 온보딩 상태에 따라 로그인·온보딩·홈을 나눈다', async () => {
  const { selectAuthView } = await import('../../app/lib/auth-view.ts')
  const base = { revision: 1, user: { userId: 'user-1', nickname: null }, error: null }

  assert.equal(selectAuthView(null), 'loading')
  assert.equal(selectAuthView({ ...base, status: 'SIGNED_OUT', onboardingStatus: null }), 'login')
  assert.equal(selectAuthView({ ...base, status: 'AUTHENTICATING', onboardingStatus: null }), 'login')
  assert.equal(selectAuthView({ ...base, status: 'OFFLINE', onboardingStatus: null }), 'offline')
  assert.equal(selectAuthView({ ...base, status: 'SIGNED_IN', onboardingStatus: 'INCOMPLETE' }), 'onboarding')
  assert.equal(selectAuthView({ ...base, status: 'SIGNED_IN', onboardingStatus: 'COMPLETED' }), 'home')
  assert.equal(selectAuthView({ ...base, status: 'LINK_REQUIRED', onboardingStatus: 'COMPLETED' }), 'onboarding')
  assert.equal(selectAuthView({ ...base, status: 'SIGNED_IN', onboardingStatus: null }), 'login')
})
