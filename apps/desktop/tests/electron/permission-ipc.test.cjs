const assert = require('node:assert/strict')
const test = require('node:test')
const { isValidNotificationInput } = require('../../dist-electron/main/permission-ipc.cjs')
const { toPermissionStatus } = require('../../dist-electron/shared/permission-contract.cjs')

test('권한 상태는 계약에 있는 값만 통과시키고 나머지는 unknown으로 바꾼다', () => {
  assert.equal(toPermissionStatus('granted'), 'granted')
  assert.equal(toPermissionStatus('not-determined'), 'not-determined')
  assert.equal(toPermissionStatus('something-new'), 'unknown')
})

test('알림은 Main이 정한 code만 받고 임의 문구나 추가 필드는 거부한다', () => {
  assert.equal(isValidNotificationInput([{ titleCode: 'ONBOARDING_TEST', bodyCode: 'ONBOARDING_TEST' }]), true)
  assert.equal(isValidNotificationInput([{ titleCode: '아무 문구', bodyCode: 'ONBOARDING_TEST' }]), false)
  assert.equal(isValidNotificationInput([{ titleCode: 'ONBOARDING_TEST', bodyCode: 'ONBOARDING_TEST', html: '<b>' }]), false)
  assert.equal(isValidNotificationInput([{ titleCode: 'toString', bodyCode: 'ONBOARDING_TEST' }]), false)
  assert.equal(isValidNotificationInput([]), false)
})
