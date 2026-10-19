(() => {
  if (globalThis.__focusonCaptureInstalled) return;
  globalThis.__focusonCaptureInstalled = true;
  const documentKey = crypto.randomUUID();
  let revision = 0;
  let timer;
  let trackingId = null;
  const dom = import(chrome.runtime.getURL('capture/dom.js'));
  const policy = import(chrome.runtime.getURL('capture/policy.js'));
  new MutationObserver(() => { revision += 1; }).observe(document, {
    childList: true, subtree: true, characterData: true, attributes: true,
  });
  const cancel = () => { clearTimeout(timer); trackingId = null; revision += 1; };
  addEventListener('pagehide', cancel);
  addEventListener('blur', cancel);
  addEventListener('scroll', () => { revision += 1; }, { passive: true });
  addEventListener('resize', () => { revision += 1; }, { passive: true });
  document.addEventListener('visibilitychange', () => { if (document.hidden) cancel(); });
  chrome.runtime.onMessage.addListener((message, sender, respond) => {
    if (sender.id !== chrome.runtime.id || message?.source !== 'service-worker') return;
    if (message.type === 'DWELL_TRACKING_CANCELLED') { cancel(); respond({ success: true }); return; }
    if (message.type === 'DWELL_TRACKING_STARTED') {
      clearTimeout(timer);
      trackingId = message.payload.navigationId;
      const navigationId = trackingId;
      timer = setTimeout(() => {
        if (trackingId === navigationId && document.visibilityState === 'visible' && document.hasFocus())
          chrome.runtime.sendMessage({ type: 'DWELL_THRESHOLD_REACHED', source: 'content-script', payload: { navigationId, documentKey } }).catch(() => {});
      }, 15_000);
      respond({ success: true }); return;
    }
    if (!['SCREEN_PROBE', 'TAB_CONTEXT_REQUESTED'].includes(message.type)) return;
    (async () => {
      const { privacyProbe, privacyVerified, collectDom, overlayMasks } = await dom;
      const { containsSensitiveText } = await policy;
      const startRevision = revision;
      const probe = { documentKey, revision, visible: document.visibilityState === 'visible',
        focused: document.hasFocus(), viewport: { width: innerWidth, height: innerHeight },
        sensitive: privacyProbe(document, window), privacyVerified: privacyVerified(document, window), masks: overlayMasks(document, window) };
      if (message.type === 'SCREEN_PROBE' || probe.sensitive || !probe.privacyVerified || !probe.visible || !probe.focused)
        return { success: true, data: probe };
      const body = collectDom(document, window);
      if (revision !== startRevision) return { success: false, error: { code: 'DOCUMENT_CHANGED', retryable: true } };
      if (containsSensitiveText(body.text)) return { success: true, data: { ...probe, sensitive: true } };
      return { success: true, data: { ...probe, ...body } };
    })().then(respond, () => respond({ success: false, error: { code: 'CONTENT_UNAVAILABLE', retryable: true } }));
    return true;
  });
})();
