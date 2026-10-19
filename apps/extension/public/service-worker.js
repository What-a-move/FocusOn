import { ScreenAcquisition } from './capture/controller.js';

export const screenAcquisition = new ScreenAcquisition(chrome);
const ready = Promise.all([
  chrome.storage.session.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' }),
  chrome.storage.local.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' }),
]);
const invalidate = () => { screenAcquisition.invalidate().catch(() => {}); };
const invalidateSelected = predicate => {
  chrome.storage.session.get('screenCapture').then(({ screenCapture }) => {
    if (screenCapture && predicate(screenCapture)) invalidate();
  }).catch(invalidate);
};
chrome.tabs.onActivated.addListener(details => invalidateSelected(state => state.windowId === details.windowId && state.tabId !== details.tabId));
chrome.tabs.onRemoved.addListener(tabId => invalidateSelected(state => state.tabId === tabId));
chrome.windows.onFocusChanged.addListener(invalidate);
const navigationChanged = details => { if (details.frameId === 0) invalidateSelected(state => state.tabId === details.tabId); };
chrome.webNavigation.onCommitted.addListener(navigationChanged);
chrome.webNavigation.onHistoryStateUpdated.addListener(navigationChanged);
chrome.webNavigation.onReferenceFragmentUpdated.addListener(navigationChanged);
chrome.permissions.onRemoved.addListener(invalidate);

chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (sender.id !== chrome.runtime.id || sender.tab || !sender.url?.startsWith(chrome.runtime.getURL(''))) return;
  if (!['SCREEN_ANALYSIS_OBSERVE', 'SCREEN_ANALYSIS_COLLECT'].includes(message?.type)) return;
  ready.then(() => message.type === 'SCREEN_ANALYSIS_OBSERVE'
    ? screenAcquisition.observeMetadata() : screenAcquisition.collectNext(message.payload?.decision))
    .then(data => respond({ success: true, data }), error => respond({ success: false,
      error: { code: error?.code ?? 'CAPTURE_UNAVAILABLE', retryable: false } }));
  return true;
});
