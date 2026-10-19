import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { ScreenAcquisition } from '../public/capture/controller.js';
import { collectDom, privacyProbe, privacyVerified } from '../public/capture/dom.js';
import { checkedUrl } from '../public/capture/policy.js';

const body = 'useState를 사용해 객체 상태를 변경하는 예제입니다. 이전 객체를 복사하고 새로운 상태를 만듭니다.';
const policy = () => ({ sessionId: 'session-1', sessionStatus: 'RUNNING', resumeRequired: false,
  version: 1, validUntil: Date.now() + 45_000, privacyBlocked: false, excludedDomains: [] });
const decision = observation => ({ clientRunId: observation.clientRunId, navigationId: observation.navigationId,
  contentSource: observation.contentSource, relation: 'UNCERTAIN', reasonCode: 'INSUFFICIENT_EVIDENCE' });

function fixture() {
  const entries = {}, calls = [];
  const tab = { id: 1, windowId: 2, active: true, incognito: false, discarded: false, url: 'https://react.dev/learn', title: '학습 자료' };
  const window = { id: 2, focused: true, incognito: false };
  const probe = { documentKey: 'doc-1', revision: 1, visible: true, focused: true, sensitive: false, privacyVerified: true,
    viewport: { width: 1200, height: 800 }, masks: [] };
  const api = {
    storage: { session: {
      get: async key => ({ [key]: structuredClone(entries[key]) }),
      set: async value => Object.assign(entries, structuredClone(value)),
      remove: async key => { delete entries[key]; },
    } },
    windows: { getLastFocused: async () => ({ ...window }) },
    tabs: {
      query: async () => [{ ...tab }],
      sendMessage: async (id, request) => {
        calls.push(request.type);
        return { success: true, data: { ...structuredClone(probe), ...(request.type === 'TAB_CONTEXT_REQUESTED' ? { text: body, complete: true } : {}) } };
      },
      captureVisibleTab: async () => { calls.push('CAPTURE'); return 'data:image/png;base64,c3ludGhldGlj'; },
    },
    permissions: { contains: async () => true },
    offscreen: { createDocument: async () => { calls.push('OPEN_OCR'); }, closeDocument: async () => { calls.push('CLOSE_OCR'); } },
    runtime: {
      getContexts: async () => [], getURL: path => `chrome-extension://unit/${path}`,
      sendMessage: async () => { calls.push('OCR'); return { success: true, data: { text: body, confidence: 0.91 } }; },
    },
  };
  const controller = new ScreenAcquisition(api);
  return { api, entries, calls, tab, window, probe, controller,
    dwell: () => { entries.screenCapture.activeSince = Date.now() - 16_000; } };
}

test('Chrome metadata requires the foreground window and an active non-incognito tab', async () => {
  for (const change of [f => { f.window.focused = false; }, f => { f.tab.active = false; }, f => { f.tab.incognito = true; }]) {
    const f = fixture(); await f.controller.setContext(policy()); change(f);
    await assert.rejects(f.controller.observeMetadata(), /INACTIVE_TARGET/);
    assert.equal(f.calls.includes('TAB_CONTEXT_REQUESTED'), false);
    assert.equal(f.calls.includes('CAPTURE'), false);
  }
});
test('missing or revoked session context never starts collection', async () => {
  const f = fixture();
  await assert.rejects(f.controller.observeMetadata(), /SESSION_INACTIVE/);
  await f.controller.setContext(policy());
  await assert.rejects(f.controller.setContext({ ...policy(), sessionStatus: 'PAUSED' }), /SESSION_INACTIVE/);
  await assert.rejects(f.controller.observeMetadata(), /SESSION_INACTIVE/);
  assert.equal(f.calls.length, 0);
});
test('excluded host matching respects domain boundaries and rejects internal pages', () => {
  assert.throws(() => checkedUrl('https://sub.example.com/a', ['example.com']), /EXCLUDED/);
  assert.equal(checkedUrl('https://not-example.com/a', ['example.com']), 'not-example.com');
  assert.throws(() => checkedUrl('chrome://settings', []), /UNSUPPORTED_URL/);
  assert.throws(() => checkedUrl('https://example.com/api-keys', []), /PRIVACY_BLOCKED/);
});
test('a sensitive screen is blocked before DOM extraction and screenshot', async () => {
  const f = fixture(); await f.controller.setContext(policy()); f.probe.sensitive = true;
  await assert.rejects(f.controller.observeMetadata(), /PRIVACY_BLOCKED/);
  assert.equal(f.calls.includes('TAB_CONTEXT_REQUESTED'), false);
  assert.equal(f.calls.includes('CAPTURE'), false);
});
test('metadata is first and a decisive decision ends acquisition', async () => {
  const f = fixture(); await f.controller.setContext(policy());
  const { observation } = await f.controller.observeMetadata();
  assert.equal(observation.contentSource, 'TAB_META'); assert.equal(observation.text, '');
  assert.equal(f.calls.includes('CAPTURE'), false);
  assert.deepEqual(await f.controller.collectNext({ ...decision(observation), relation: 'RELATED' }), { done: true });
  assert.equal(f.calls.includes('TAB_CONTEXT_REQUESTED'), false);
});
test('DOM follows metadata, OCR follows ambiguous DOM, and raw content is absent from storage', async () => {
  const f = fixture(); await f.controller.setContext(policy());
  const metadata = (await f.controller.observeMetadata()).observation;
  await assert.rejects(f.controller.collectNext({ ...decision(metadata), reasonCode: 'METADATA_INSUFFICIENT' }), /DWELL_PENDING/);
  f.dwell();
  const dom = (await f.controller.collectNext({ ...decision(metadata), reasonCode: 'METADATA_INSUFFICIENT' })).observation;
  assert.equal(dom.contentSource, 'CHROME_DOM'); assert.equal(dom.text, body);
  assert.equal(f.calls.includes('CAPTURE'), false);
  const ocr = (await f.controller.collectNext(decision(dom))).observation;
  assert.equal(ocr.contentSource, 'CHROME_VIEWPORT_OCR'); assert.equal(ocr.ocr.confidence, 0.91);
  assert.equal(f.calls.filter(type => type === 'OCR').length, 1);
  assert.equal(f.calls.filter(type => type === 'CLOSE_OCR').length, 1);
  assert.equal(JSON.stringify(f.entries).includes(body), false);
  assert.equal(JSON.stringify(f.entries).includes('data:image'), false);
  assert.equal(JSON.stringify(f.entries).includes('react.dev/learn'), false);
  assert.equal(metadata.sequence, 1); assert.equal(dom.sequence, 1); assert.equal(ocr.sequence, 1);
  assert.equal(new Set([metadata.clientRunId, dom.clientRunId, ocr.clientRunId]).size, 3);
});
test('an old decision cannot skip metadata or apply to another observation', async () => {
  const f = fixture(); await f.controller.setContext(policy());
  const metadata = (await f.controller.observeMetadata()).observation;
  f.dwell();
  await assert.rejects(f.controller.collectNext({ ...decision(metadata), contentSource: 'CHROME_DOM' }), /STALE_DECISION/);
  assert.equal(f.calls.includes('CAPTURE'), false);
});
test('switching tabs during screenshot discards the image before OCR', async () => {
  const f = fixture(); await f.controller.setContext(policy());
  const metadata = (await f.controller.observeMetadata()).observation; f.dwell();
  const dom = (await f.controller.collectNext({ ...decision(metadata), reasonCode: 'METADATA_INSUFFICIENT' })).observation;
  f.api.tabs.captureVisibleTab = async () => { f.tab.id = 99; return 'data:image/png;base64,c3ludGhldGlj'; };
  await assert.rejects(f.controller.collectNext(decision(dom)), /STALE_EVENT/);
  assert.equal(f.calls.includes('OCR'), false);
  assert.equal(f.calls.filter(type => type === 'CLOSE_OCR').length, 1);
});
test('model or graph failure cannot cause extra capture', async () => {
  const f = fixture(); await f.controller.setContext(policy());
  const metadata = (await f.controller.observeMetadata()).observation; f.dwell();
  assert.deepEqual(await f.controller.collectNext({ ...decision(metadata), reasonCode: 'EMBEDDING_TIMEOUT' }), { done: true });
  assert.equal(f.calls.includes('TAB_CONTEXT_REQUESTED'), false);
});
test('policy revocation closes an in-flight OCR document before any text can be returned', async () => {
  const f = fixture(); await f.controller.setContext(policy());
  const metadata = (await f.controller.observeMetadata()).observation; f.dwell();
  const dom = (await f.controller.collectNext({ ...decision(metadata), reasonCode: 'METADATA_INSUFFICIENT' })).observation;
  let rejectOcr;
  let opened;
  const started = new Promise(resolve => { opened = resolve; });
  f.api.runtime.sendMessage = () => new Promise((resolve, reject) => { rejectOcr = reject; opened(); });
  f.api.runtime.getContexts = async () => rejectOcr ? [{}] : [];
  f.api.offscreen.closeDocument = async () => { f.calls.push('CLOSE_OCR'); rejectOcr?.(new Error('OCR context closed')); rejectOcr = null; };
  const pending = f.controller.collectNext(decision(dom));
  const rejected = assert.rejects(pending, /CAPTURE_UNAVAILABLE|STALE_EVENT/);
  await started;
  await f.controller.setContext(null);
  await rejected;
  assert.ok(f.calls.includes('CLOSE_OCR'));
  assert.equal(JSON.stringify(f.entries).includes(body), false);
});

function documentFixture(html) {
  const dom = new JSDOM(html, { pretendToBeVisual: true });
  dom.window.HTMLElement.prototype.getClientRects = () => [{ x: 0, y: 0, width: 100, height: 20 }];
  return dom;
}
test('DOM preserves headings, code and ordering without menus, forms, hidden or editable descendants', () => {
  const dom = documentFixture('<main><h1>상태 관리</h1><nav><p>메뉴</p></nav><p>본문입니다.<span hidden>숨긴 개인정보</span></p><pre><code class="language-js">const x = { ...prev };</code></pre><p hidden>숨긴 문장</p><p>작성 중 <span contenteditable="true">개인 메시지</span></p><form><p>입력 값</p></form></main>');
  const result = collectDom(dom.window.document, dom.window);
  assert.equal(result.text, '# 상태 관리\n\n본문입니다.\n\n```js\nconst x = { ...prev };\n```\n\n작성 중');
  dom.window.close();
});
test('oversized DOM is not silently clipped into representative evidence', () => {
  const dom = documentFixture(`<main><p>${'가'.repeat(6001)}</p></main>`);
  assert.deepEqual(collectDom(dom.window.document, dom.window), { text: '', complete: false });
  dom.window.close();
});
test('privacy probe checks password structure without reading the password value', () => {
  const dom = documentFixture('<main><input type="password"></main>');
  const input = dom.window.document.querySelector('input');
  Object.defineProperty(input, 'value', { get() { throw new Error('Must not read a password'); } });
  assert.equal(privacyProbe(dom.window.document, dom.window), true);
  dom.window.close();
});
test('an uninspectable visible iframe cannot be bypassed with OCR', () => {
  const dom = documentFixture('<main><iframe></iframe></main>');
  Object.defineProperty(dom.window.document.querySelector('iframe'), 'contentDocument', { get: () => null });
  assert.equal(privacyVerified(dom.window.document, dom.window), false);
  dom.window.close();
});
