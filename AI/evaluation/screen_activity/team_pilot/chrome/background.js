import { cleanPageText, isExcludedHost, isPrivateHost, MAX_TEXT_LENGTH } from "./page-rules.js";
import { inspectPage } from "./inspect.js";

const BASE = "http://127.0.0.1:18743";
const ALARM = "focuson-local-poll";
const INTERVAL = 4000;
let busy = false;
let activeContext;
let generation = 0;
let lastStatus = "로컬 테스트 연결을 기다리고 있습니다.";

async function request(path, token, data) {
  const response = await fetch(BASE + path, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Pilot-Token": token },
    body: JSON.stringify(data),
    credentials: "omit",
    cache: "no-store",
    redirect: "error",
    signal: AbortSignal.timeout(3500),
  });
  if (!response.ok) throw Error(String(response.status));
  return response;
}

async function foreground() {
  const win = await chrome.windows.getLastFocused();
  if (!win.focused || win.incognito || win.type !== "normal") return null;
  const [tab] = await chrome.tabs.query({ active: true, windowId: win.id });
  if (!tab || tab.incognito || !Number.isInteger(tab.id) || !tab.url || tab.pendingUrl) return null;
  return tab;
}

function guarded(url, excluded) {
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) return true;
  const host = url.hostname.replace(/\.$/, "");
  if (["localhost", "127.0.0.1", "[::1]"].includes(host) && url.port === "18743") return true;
  return isPrivateHost(host) || isExcludedHost(host, excluded);
}

async function inspect(tabId, length, docId) {
  const target = docId ? { tabId, documentIds: [docId] } : { tabId, frameIds: [0] };
  const result = await chrome.scripting.executeScript({
    target,
    func: inspectPage,
    args: [length],
  });
  return result[0]?.result ?? { unavailable: true };
}

async function observation(plan, excluded) {
  const initialGeneration = generation;
  const tab = await foreground();
  if (!tab) {
    activeContext = undefined;
    return { sessionId: plan.sessionId, state: "unavailable", context: "chrome-unavailable" };
  }
  const visit = [plan.sessionId, tab.id, tab.url].join("\n");
  if (
    activeContext?.visit !== visit ||
    (!activeContext.protected && activeContext.title !== tab.title)
  ) {
    activeContext = { visit, tabId: tab.id, title: tab.title, id: crypto.randomUUID() };
  }
  const context = activeContext.id;
  const base = { sessionId: plan.sessionId, context };
  const protectedResult = () => {
    if (activeContext?.id === context) activeContext.protected = true;
    return { ...base, state: "guard" };
  };
  if (activeContext.protected) return protectedResult();
  const url = new URL(tab.url);
  if (guarded(url, excluded)) return protectedResult();
  const title = cleanPageText(tab.title || "");
  if (title.sensitive || cleanPageText(title.text).sensitive) return protectedResult();
  if (!(await chrome.permissions.contains({ origins: [url.origin + "/*"] })))
    return { ...base, state: "guard" };
  // Bind script injection to the document inspected here. A cross-document navigation causes failure.
  const frame = await chrome.webNavigation.getFrame({ tabId: tab.id, frameId: 0 });
  if (!frame?.documentId || frame.url !== tab.url) return { ...base, state: "unavailable" };
  const wantBody = plan.wantDom === context;
  const page = await inspect(tab.id, wantBody ? MAX_TEXT_LENGTH : 0, frame.documentId);
  const current = await foreground();
  if (
    !current ||
    current.id !== tab.id ||
    current.url !== tab.url ||
    current.title !== tab.title ||
    generation !== initialGeneration ||
    activeContext?.id !== context
  )
    return null;
  if (page.unavailable) return { ...base, state: "unavailable" };
  if (page.sensitive) return protectedResult();
  const result = {
    ...base,
    state: "chrome",
    metadata: {
      appName: "Google Chrome",
      pageTitle: title.text.slice(0, 200),
      urlHost: url.hostname.replace(/\.$/, ""),
    },
  };
  if (wantBody) {
    const cleaned = cleanPageText(page.text);
    if (cleaned.sensitive || cleanPageText(cleaned.text).sensitive) return protectedResult();
    result.text = cleaned.text;
  }
  return result;
}

async function poll() {
  if (busy) return;
  busy = true;
  try {
    const { token, excluded } = await chrome.storage.local.get(["token", "excluded"]);
    if (!token || !Array.isArray(excluded)) {
      activeContext = undefined;
      lastStatus = "연결 코드를 입력해 주세요.";
      return;
    }
    const status = await (await request("/bridge/poll", token, {})).json();
    if (!status.active || !status.sessionId) {
      activeContext = undefined;
      lastStatus = "로컬 서버에 연결됨 · 세션 대기 중";
      return;
    }
    let result;
    try {
      result = await observation(status, excluded);
    } catch {
      result = { sessionId: status.sessionId, state: "unavailable", context: "chrome-unavailable" };
    }
    if (result) await request("/bridge/observation", token, result);
    lastStatus = "로컬 서버에 연결됨 · 세션 진행 중";
  } catch (error) {
    lastStatus = ["401", "403"].includes(error.message)
      ? "서버가 연결 코드를 거절했습니다."
      : "로컬 서버 연결을 확인해 주세요.";
    activeContext = undefined;
  } finally {
    busy = false;
  }
}

function changed() {
  generation++;
  activeContext = undefined;
}
chrome.tabs.onActivated.addListener(changed);
chrome.windows.onFocusChanged.addListener(changed);
function navigated(nav) {
  if (nav.frameId === 0 && activeContext && activeContext.tabId === nav.tabId) changed();
}
chrome.webNavigation.onCommitted.addListener(navigated);
chrome.webNavigation.onHistoryStateUpdated.addListener(navigated);
chrome.storage.onChanged.addListener(changed);
chrome.runtime.onMessage.addListener((msg, sender, reply) => {
  if (
    sender.id === chrome.runtime.id &&
    sender.url === chrome.runtime.getURL("popup.html") &&
    msg?.type === "status"
  ) {
    poll().finally(() => reply({ status: lastStatus }));
    return true;
  }
});
async function arm() {
  await chrome.storage.local.setAccessLevel({ accessLevel: "TRUSTED_CONTEXTS" });
  if (!(await chrome.alarms.get(ALARM)))
    await chrome.alarms.create(ALARM, { periodInMinutes: 0.5 });
  void poll();
}
chrome.runtime.onStartup.addListener(arm);
chrome.runtime.onInstalled.addListener(arm);
chrome.alarms.onAlarm.addListener((a) => {
  if (a.name === ALARM) void poll();
});
setInterval(poll, INTERVAL);
void arm();
