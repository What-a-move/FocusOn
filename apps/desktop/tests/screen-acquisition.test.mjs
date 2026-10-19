import test from 'node:test';
import assert from 'node:assert/strict';
import { DesktopScreenAcquisition } from '../dist-electron/main/screen-acquisition.js';
const now = 1_000_000;
const policy = () => ({ sessionId: 'session-1', sessionStatus: 'RUNNING', resumeRequired: false,
  version: 1, validUntil: now + 45_000, privacyBlocked: false,
  excludedBundleIds: [], ownBundleIds: ['com.focuson.desktop'] });
const target = () => ({ pid: 123, bundleId: 'com.apple.Preview', appName: 'Preview',
  windowTitle: '학습 자료', x: 0, y: 0, width: 1000, height: 800 });
const body = 'useState로 객체 상태를 변경하는 예제입니다.';
const decision = observation => ({ clientRunId: observation.clientRunId, navigationId: observation.navigationId,
  contentSource: observation.contentSource, relation: 'UNCERTAIN', reasonCode: 'METADATA_INSUFFICIENT' });
test('Desktop metadata is first, then Apple Vision OCR uses the same target and new event ID', async () => {
  const calls = [];
  const acquisition = new DesktopScreenAcquisition(async request => {
    calls.push(request.operation);
    return { target: Object.fromEntries(Object.entries(target()).reverse()), text: body, confidence: 0.91, imageHash: 'hash-1' };
  }, () => now);
  acquisition.setContext(policy());
  const metadata = await acquisition.observeMetadata();
  assert.equal(metadata.contentSource, 'APP_META'); assert.equal(metadata.text, '');
  const ocr = await acquisition.collectNext(decision(metadata));
  assert.equal(ocr.contentSource, 'DESKTOP_APP_OCR'); assert.equal(ocr.text, body);
  assert.equal(ocr.navigationId, metadata.navigationId); assert.notEqual(ocr.clientRunId, metadata.clientRunId);
  assert.deepEqual(calls, ['metadata', 'ocr']);
});
test('inactive or unknown policy never invokes native capture', async () => {
  const acquisition = new DesktopScreenAcquisition(() => assert.fail('Unexpected native call'), () => now);
  await assert.rejects(acquisition.observeMetadata(), /SESSION_INACTIVE/);
  acquisition.setContext({ ...policy(), sessionStatus: 'PAUSED' });
  await assert.rejects(acquisition.observeMetadata(), /SESSION_INACTIVE/);
});
test('own, excluded and Chrome targets are never processed as Desktop content', async () => {
  for (const bundleId of ['com.focuson.desktop', 'com.private.app', 'com.google.Chrome']) {
    const acquisition = new DesktopScreenAcquisition(async () => ({ target: { ...target(), bundleId } }), () => now);
    acquisition.setContext({ ...policy(), excludedBundleIds: ['com.private.app'] });
    await assert.rejects(acquisition.observeMetadata(), /FOCUSON_UI_EXCLUDED|EXCLUDED|CHROME_HANDLED_BY_EXTENSION/);
  }
});
test('clear metadata does not execute OCR', async () => {
  const calls = [];
  const acquisition = new DesktopScreenAcquisition(async request => { calls.push(request.operation); return { target: target() }; }, () => now);
  acquisition.setContext(policy());
  const metadata = await acquisition.observeMetadata();
  assert.equal(await acquisition.collectNext({ ...decision(metadata), relation: 'RELATED' }), null);
  assert.deepEqual(calls, ['metadata']);
});
test('session change while OCR runs discards the returned text', async () => {
  const acquisition = new DesktopScreenAcquisition(async request => {
    if (request.operation === 'ocr') acquisition.setContext({ ...policy(), sessionStatus: 'PAUSED' });
    return { target: target(), text: body, confidence: 0.95, imageHash: 'hash' };
  }, () => now);
  acquisition.setContext(policy());
  const metadata = await acquisition.observeMetadata();
  await assert.rejects(acquisition.collectNext(decision(metadata)), /SESSION_INACTIVE|STALE_EVENT/);
});
test('target switch and local sensitive OCR never produce an observation', async () => {
  for (const changed of [ { target: { ...target(), pid: 999 }, text: body }, { target: target(), text: 'password=synthetic-private-value' } ]) {
    const acquisition = new DesktopScreenAcquisition(async request => request.operation === 'metadata'
      ? { target: target() } : { ...changed, confidence: 0.95, imageHash: 'hash' }, () => now);
    acquisition.setContext(policy());
    const metadata = await acquisition.observeMetadata();
    await assert.rejects(acquisition.collectNext(decision(metadata)), /STALE_EVENT|PRIVACY_BLOCKED/);
  }
});
test('refreshing an unchanged policy lease preserves the current observation', async () => {
  const acquisition = new DesktopScreenAcquisition(async () => ({ target: target(), text: body, confidence: 0.95, imageHash: 'hash' }), () => now);
  acquisition.setContext(policy());
  const metadata = await acquisition.observeMetadata();
  acquisition.setContext({ ...policy(), validUntil: now + 50_000 });
  assert.equal((await acquisition.collectNext(decision(metadata))).contentSource, 'DESKTOP_APP_OCR');
});
test('policy revocation aborts the native operation instead of waiting for OCR to finish', async () => {
  let abortSeen = false;
  const acquisition = new DesktopScreenAcquisition(async (request, signal) => {
    if (request.operation === 'ocr') {
      acquisition.setContext(null);
      abortSeen = signal.aborted;
    }
    return { target: target(), text: body, confidence: 0.95, imageHash: 'hash' };
  }, () => now);
  acquisition.setContext(policy());
  const metadata = await acquisition.observeMetadata();
  await assert.rejects(acquisition.collectNext(decision(metadata)), /SESSION_INACTIVE/);
  assert.equal(abortSeen, true);
});
