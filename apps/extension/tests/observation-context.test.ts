import assert from 'node:assert/strict'
import test from 'node:test'
import { pickContextId } from '../extension/shared/observation-context.ts'

test('이 기기가 맡은 Extension context일 때만 ID를 고른다', () => {
  const context = { observationContextId: '42', observer: 'EXTENSION', observerDeviceId: 'ext-1', foregroundSequence: 3 }
  assert.equal(pickContextId(context, 'ext-1'), '42')
  assert.equal(pickContextId(context, 'ext-2'), null, '다른 기기 담당')
  assert.equal(pickContextId({ ...context, observer: 'DESKTOP' }, 'ext-1'), null, 'Desktop 담당')
  assert.equal(pickContextId(null, 'ext-1'), null, '204 context 없음')
  assert.equal(pickContextId(context, null), null, '연결 기기 모름')
})
