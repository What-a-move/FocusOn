const assert = require('node:assert/strict')
const test = require('node:test')
const { isValidNotificationInput, showAndWait } = require('../../dist-electron/main/permission-ipc.cjs')
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

test('시험 알림은 macOS가 거절하면 보냈다고 하지 않는다', async () => {
  const { EventEmitter } = require('node:events')
  const fake = (event) => Object.assign(new EventEmitter(), { show() { if (event) queueMicrotask(() => this.emit(event, {}, 'UNErrorDomain 1')) } })
  assert.equal(await showAndWait(fake('failed')), false)
  assert.equal(await showAndWait(fake('show')), true)
  // 허용 창 때문에 답이 없으면 보냈다고 보고 사용자의 ‘보였어요’로 확인한다.
  assert.equal(await showAndWait(fake(null), 10), true)
})
