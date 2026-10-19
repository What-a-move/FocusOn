import { CaptureError, DWELL_MS, checkedUrl, containsSensitiveText, nextSource, policyKey, requirePolicy, sameTab } from './policy.js';

const STATE_KEY = 'screenCapture';
const CONTEXT_KEY = 'screenCaptureContext';
const message = (type, payload) => ({ type, source: 'service-worker', payload });
const digest = async value => [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)))].map(n => n.toString(16).padStart(2, '0')).join('');

export class ScreenAcquisition {
  constructor(api) { this.api = api; this.epoch = 0; this.busy = false; }

  async setContext(context) {
    // Only a trusted Server-state adapter may call this method. No Page message can set it.
    if (context === null) { await this.invalidate(); await this.api.storage.session.remove(CONTEXT_KEY); return; }
    try { requirePolicy(context); }
    catch (error) { await this.invalidate(); await this.api.storage.session.remove(CONTEXT_KEY); throw error; }
    const old = (await this.api.storage.session.get(CONTEXT_KEY))[CONTEXT_KEY];
    if (!old || policyKey(old) !== policyKey(context)) await this.invalidate();
    await this.api.storage.session.set({ [CONTEXT_KEY]: structuredClone(context) });
  }

  async invalidate() {
    this.epoch += 1;
    const state = (await this.api.storage.session.get(STATE_KEY))[STATE_KEY];
    await this.api.storage.session.remove(STATE_KEY);
    if (state?.tabId) await this.api.tabs.sendMessage(state.tabId, message('DWELL_TRACKING_CANCELLED', { navigationId: state.navigationId, reason: 'navigation' }), { frameId: 0 }).catch(() => {});
    const contexts = await this.api.runtime.getContexts({ contextTypes: ['OFFSCREEN_DOCUMENT'], documentUrls: [this.api.runtime.getURL('capture/offscreen.html')] });
    if (contexts.length) await this.api.offscreen.closeDocument().catch(() => {});
  }

  async context() {
    const context = (await this.api.storage.session.get(CONTEXT_KEY))[CONTEXT_KEY];
    requirePolicy(context);
    return context;
  }

  async selected(context) {
    const window = await this.api.windows.getLastFocused({ windowTypes: ['normal'] });
    if (!window.focused || window.incognito || !Number.isInteger(window.id)) throw new CaptureError('INACTIVE_TARGET');
    const [tab] = await this.api.tabs.query({ active: true, windowId: window.id });
    if (!tab?.active || tab.incognito || tab.discarded || !Number.isInteger(tab.id)) throw new CaptureError('INACTIVE_TARGET');
    if (tab.pendingUrl || tab.status === 'loading') throw new CaptureError('TARGET_UNAVAILABLE');
    const host = checkedUrl(tab.url, context.excludedDomains);
    if (containsSensitiveText(tab.title ?? '')) throw new CaptureError('PRIVACY_BLOCKED');
    return { tab, host };
  }

  async probe(tab, type = 'SCREEN_PROBE') {
    let response;
    try { response = await this.api.tabs.sendMessage(tab.id, message(type, {}), { frameId: 0 }); }
    catch { throw new CaptureError('PRIVACY_UNVERIFIED'); }
    if (!response?.success || !response.data) throw new CaptureError('CONTENT_UNAVAILABLE');
    const probe = response.data;
    if (probe.sensitive) throw new CaptureError('PRIVACY_BLOCKED');
    if (probe.privacyVerified !== true) throw new CaptureError('PRIVACY_UNVERIFIED');
    if (!probe.visible || !probe.focused || typeof probe.documentKey !== 'string') throw new CaptureError('INACTIVE_TARGET');
    if (!Array.isArray(probe.masks) || probe.masks.length > 32 || probe.masks.some(mask => ![mask.x, mask.y, mask.width, mask.height].every(Number.isFinite))) throw new CaptureError('OVERLAY_UNAVAILABLE');
    return probe;
  }

  async validate(state, epoch, originalTab) {
    if (epoch !== this.epoch) throw new CaptureError('STALE_EVENT');
    const context = await this.context();
    if (policyKey(context) !== state.policyKey) throw new CaptureError('STALE_EVENT');
    const { tab } = await this.selected(context);
    if (!sameTab(originalTab, tab) || await digest(tab.url) !== state.urlHash) throw new CaptureError('STALE_EVENT');
    const stored = (await this.api.storage.session.get(STATE_KEY))[STATE_KEY];
    if (!stored || stored.navigationId !== state.navigationId) throw new CaptureError('STALE_EVENT');
    const probe = await this.probe(tab);
    if (probe.documentKey !== state.documentKey || epoch !== this.epoch) throw new CaptureError('STALE_EVENT');
    return probe;
  }

  async exclusive(action) {
    if (this.busy) throw new CaptureError('CAPTURE_BUSY');
    this.busy = true;
    try { return await action(); }
    catch (error) {
      if (!(error instanceof CaptureError) || !['DWELL_PENDING', 'CAPTURE_BUSY'].includes(error.code)) await this.invalidate().catch(() => {});
      throw error instanceof CaptureError ? error : new CaptureError('CAPTURE_UNAVAILABLE');
    } finally { this.busy = false; }
  }

  async observeMetadata() {
    return this.exclusive(async () => {
      const epoch = this.epoch;
      const context = await this.context();
      const { tab, host } = await this.selected(context);
      const observedAt = new Date().toISOString();
      const probe = await this.probe(tab);
      const old = (await this.api.storage.session.get(STATE_KEY))[STATE_KEY];
      const urlHash = await digest(tab.url);
      const reusable = old && old.tabId === tab.id && old.windowId === tab.windowId && old.urlHash === urlHash && old.documentKey === probe.documentKey;
      const state = { tabId: tab.id, windowId: tab.windowId, urlHash, documentKey: probe.documentKey,
        navigationId: reusable ? old.navigationId : crypto.randomUUID(),
        activeSince: reusable ? old.activeSince : Date.now(),
        imageHash: reusable ? old.imageHash : null, policyKey: policyKey(context) };
      if (epoch !== this.epoch) throw new CaptureError('STALE_EVENT');
      await this.api.storage.session.set({ [STATE_KEY]: state });
      await this.validate(state, epoch, tab);
      if (!reusable) await this.api.tabs.sendMessage(tab.id, message('DWELL_TRACKING_STARTED', { navigationId: state.navigationId, thresholdMs: DWELL_MS }), { frameId: 0 });
      return this.observation(state, epoch, tab, host, 'TAB_META', '', 'SUCCESS', undefined, observedAt);
    });
  }

  async collectNext(decision) {
    return this.exclusive(async () => {
      const epoch = this.epoch;
      const state = (await this.api.storage.session.get(STATE_KEY))[STATE_KEY];
      if (!state?.last) throw new CaptureError('METADATA_STAGE_REQUIRED');
      const source = nextSource(state.last, decision);
      if (source === null) { await this.invalidate(); return { done: true }; }
      const context = await this.context();
      const { tab, host } = await this.selected(context);
      const probe = await this.validate(state, epoch, tab);
      if (Date.now() - state.activeSince < DWELL_MS) throw new CaptureError('DWELL_PENDING');
      const observedAt = new Date().toISOString();
      if (source === 'CHROME_DOM') {
        const dom = await this.probe(tab, 'TAB_CONTEXT_REQUESTED');
        const after = await this.validate(state, epoch, tab);
        if (dom.documentKey !== state.documentKey || dom.revision !== after.revision) throw new CaptureError('STALE_EVENT');
        return this.observation(state, epoch, tab, host, source, dom.text ?? '', dom.complete ? 'SUCCESS' : 'PARTIAL', undefined, observedAt);
      }
      if (!await this.api.permissions.contains({ origins: ['<all_urls>'] })) throw new CaptureError('OCR_PERMISSION_REQUIRED');
      if (!probe.viewport || probe.viewport.width <= 0 || probe.viewport.height <= 0) throw new CaptureError('EMPTY_CAPTURE');
      // Create the isolated OCR context before taking a short-lived screenshot.
      const existing = await this.api.runtime.getContexts({ contextTypes: ['OFFSCREEN_DOCUMENT'], documentUrls: [this.api.runtime.getURL('capture/offscreen.html')] });
      if (existing.length) await this.api.offscreen.closeDocument();
      await this.api.offscreen.createDocument({ url: 'capture/offscreen.html', reasons: ['WORKERS', 'BLOBS'], justification: 'Recognize Korean and English text locally from the current permitted viewport.' });
      try {
        const before = await this.validate(state, epoch, tab);
        let image = await this.api.tabs.captureVisibleTab(tab.windowId, { format: 'png' });
        const after = await this.validate(state, epoch, tab);
        if (before.revision !== after.revision) throw new CaptureError('DOCUMENT_CHANGED');
        const imageHash = await digest(image);
        if (state.imageHash === imageHash) throw new CaptureError('DUPLICATE');
        let timer;
        const response = await Promise.race([
          this.api.runtime.sendMessage({ type: 'LOCAL_OCR', target: 'offscreen', image, viewport: after.viewport, masks: after.masks }),
          new Promise((_, reject) => { timer = setTimeout(() => reject(new CaptureError('OCR_TIMEOUT')), Math.max(1, Math.min(45_000, context.validUntil - Date.now()))); }),
        ]).finally(() => clearTimeout(timer));
        image = null;
        const finalProbe = await this.validate(state, epoch, tab);
        if (after.revision !== finalProbe.revision) throw new CaptureError('DOCUMENT_CHANGED');
        if (!response?.success) throw new CaptureError(response?.error?.code === 'PRIVACY_BLOCKED' ? 'PRIVACY_BLOCKED' : response?.error?.code ?? 'OCR_FAILED');
        const { text, confidence } = response.data;
        if (typeof text !== 'string' || text.length > 6000 || containsSensitiveText(text) || !Number.isFinite(confidence) || confidence < 0 || confidence > 1) throw new CaptureError('INVALID_OCR_RESULT');
        state.imageHash = imageHash;
        return await this.observation(state, epoch, tab, host, source, text, 'SUCCESS', { confidence, captureStatus: 'SUCCESS' }, observedAt);
      } finally { await this.api.offscreen.closeDocument().catch(() => {}); }
    });
  }

  async observation(state, epoch, tab, host, source, text, extractionStatus, ocr, observedAt) {
    await this.validate(state, epoch, tab);
    if (containsSensitiveText(text)) throw new CaptureError('PRIVACY_BLOCKED');
    const context = await this.context();
    const counts = (await this.api.storage.session.get('screenSequences')).screenSequences;
    const sequences = counts?.sessionId === context.sessionId ? counts.values : {};
    const sequence = (sequences[source] ?? 0) + 1;
    const observation = { sessionId: context.sessionId, clientRunId: crypto.randomUUID(), sequence,
      navigationId: state.navigationId, observedAt, isActiveTarget: true,
      contentSource: source, contentType: 'WEB_PAGE', appName: 'Chrome', windowTitle: '',
      pageTitle: (tab.title ?? '').slice(0, 800), urlHost: host, text, extractionStatus,
      isSensitive: false, isExcluded: false, ...(ocr ? { ocr } : {}) };
    state.last = { clientRunId: observation.clientRunId, navigationId: state.navigationId, contentSource: source };
    await this.api.storage.session.set({ [STATE_KEY]: state, screenSequences: { sessionId: context.sessionId, values: { ...sequences, [source]: sequence } } });
    if (epoch !== this.epoch) { await this.invalidate(); throw new CaptureError('STALE_EVENT'); }
    return { observation };
  }
}
