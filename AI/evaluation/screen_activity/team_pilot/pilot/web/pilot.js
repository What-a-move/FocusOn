"use strict";
const $ = (id) => document.getElementById(id);
const titles = { STUDY: "공부", OFF_TASK: "딴짓", OTHER: "휴식·회의·음악 등", UNKNOWN: "미확인" };
const sources = {
  APP_META: "앱 제목",
  TAB_META: "탭 제목",
  CHROME_DOM: "Chrome DOM",
  DESKTOP_APP_OCR: "데스크톱 OCR",
};
const stages = {
  dwell: "활성 화면 체류 확인 중",
  waiting: "활성 창 확인 중",
  extension_wait: "Chrome 확장 입력 대기",
  unavailable: "현재 창 관찰 불가",
  awaiting_dom: "활성 탭 본문 확인 중",
  no_content: "본문 확인 불가",
  study: "공부 중",
  off: "딴짓",
  uncertain: "판단 보류",
  protected: "수집 제외 또는 보호 중",
  idle: "대기 중",
};
const fmt = (s) =>
  Math.floor(s / 60)
    .toString()
    .padStart(2, "0") +
  ":" +
  Math.floor(s % 60)
    .toString()
    .padStart(2, "0");
function parseTime(s) {
  if (!/^\d{1,6}:[0-5]\d$/.test(s.trim())) throw Error("분:초 형식으로 입력해 주세요. 예: 05:30");
  const [m, n] = s.split(":").map(Number);
  return 60 * m + n;
}
let selection = null,
  data = null,
  busy = false,
  revision = 0,
  fetching = false,
  eventKey = "",
  rangeKey = "";
const when = (d) =>
  new Date(d).toLocaleString("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
async function request(path, payload) {
  const params = { headers: { "X-Pilot-UI": "1" }, cache: "no-store" };
  if (payload !== undefined) {
    params.method = "POST";
    params.headers["Content-Type"] = "application/json";
    params.body = JSON.stringify(payload);
  }
  const resp = await fetch(path, params);
  if (!resp.ok) {
    let error = {};
    try {
      error = await resp.json();
    } catch {}
    throw Error(error.error || "로컬 서버 연결을 확인해 주세요.");
  }
  return resp.json();
}
function node(tag, txt, cl) {
  const el = document.createElement(tag);
  if (txt !== undefined) el.textContent = txt;
  if (cl) el.className = cl;
  return el;
}
function options(mapping, value) {
  const sel = node("select");
  for (const [v, t] of Object.entries(mapping)) {
    const op = node("option", t);
    op.value = v;
    sel.append(op);
  }
  sel.value = value;
  return sel;
}
async function action(fn) {
  if (busy) return;
  busy = true;
  revision++;
  try {
    await fn();
    $("notice").textContent = "";
  } catch (e) {
    $("notice").textContent = e.message;
  } finally {
    busy = false;
    await refresh();
  }
}
function rangeEdit(a, b, t) {
  $("from").value = fmt(a);
  $("to").value = fmt(b);
  $("truth").value = t === "UNKNOWN" ? "STUDY" : t;
  $("from").focus();
}
function drawEvents(s) {
  const key = s.id + JSON.stringify(s.events);
  if (key === eventKey) return;
  eventKey = key;
  $("events").replaceChildren();
  if (!s.events.length) {
    $("events").append(node("p", "기록된 알림 후보가 없습니다."));
    return;
  }
  for (const e of s.events) {
    const row = node("div", undefined, "entry");
    row.append(
      node("b", fmt(e.atSec)),
      node(
        "span",
        when(new Date(s.startedAt).getTime() + e.atSec * 1000)
          .split(". ")
          .at(-1),
      ),
      node(
        "span",
        (sources[e.source] || e.source) +
          " · " +
          (e.reason === "repeated" ? "반복 이탈" : "지속 이탈"),
      ),
    );
    const sel = options(
      {
        "": "선택해 주세요",
        STUDY: "실제로 공부 중",
        OFF_TASK: "실제로 딴짓",
        OTHER: "휴식·회의·음악 등",
        UNKNOWN: "기억나지 않음",
      },
      e.answer || "",
    );
    sel.setAttribute("aria-label", fmt(e.atSec) + " 후보의 실제 활동");
    sel.onchange = () =>
      action(async () => {
        await request("/api/review", { sessionId: s.id, eventId: e.id, answer: sel.value || null });
        eventKey = "";
      });
    row.append(sel);
    if (e.conflict) row.append(node("small", "구간 표시와 응답이 다릅니다.", "error"));
    $("events").append(row);
  }
}
function drawRanges(s) {
  const key = s.id + JSON.stringify(s.intervals);
  if (key === rangeKey || document.activeElement?.closest("#ranges")) return;
  rangeKey = key;
  $("ranges").replaceChildren();
  for (const it of s.intervals) {
    const row = node("div", undefined, "interval");
    row.append(
      node("b", fmt(it.startSec) + " – " + fmt(it.endSec)),
      node("span", titles[it.truth]),
    );
    const b = node("button", "이 구간 선택");
    b.onclick = () => rangeEdit(it.startSec, it.endSec, it.truth);
    row.append(b);
    $("ranges").append(row);
  }
}
function render(x) {
  const s = x.session;
  const old = data?.session?.id;
  data = x;
  if (s?.id !== old) {
    $("from").value = "";
    $("to").value = "";
    eventKey = rangeKey = "";
  }
  const hist = $("history");
  const ids = x.history.map((r) => r.id + ":" + r.status).join("|");
  if (hist.dataset.key !== ids) {
    hist.replaceChildren();
    for (const h of x.history) {
      const o = node("option", when(h.startedAt) + (h.status === "active" ? " · 진행 중" : ""));
      o.value = h.id;
      hist.append(o);
    }
    hist.dataset.key = ids;
  }
  if (s) hist.value = s.id;
  $("connection").textContent = x.monitor.bridgeConnected
    ? "Chrome 확장 연결됨"
    : "Chrome 확장 미연결";
  const active = x.history.some((v) => v.status === "active");
  $("begin").hidden = active;
  $("active").hidden = s?.status !== "active";
  $("report").hidden = !s;
  $("sessionTitle").textContent = s
    ? { active: "진행 중인 세션", ended: "종료한 세션", interrupted: "중단된 세션" }[s.status]
    : "새 세션";
  $("sessionTime").textContent = s ? when(s.startedAt) + " 시작" : "시작할 준비가 되었습니다.";
  $("elapsed").textContent = fmt(s?.durationSec || 0);
  $("live").textContent = stages[x.monitor.status] || x.monitor.status;
  if (!s) return;
  const m = s.metrics;
  $("rate").textContent =
    m.falsePerStudyHour == null
      ? "산출 전"
      : m.unreviewed || m.unsure || m.conflicts || m.reportedFalseStudy > m.falseStudy
        ? "검토 중"
        : m.falsePerStudyHour.toLocaleString("ko-KR", { maximumFractionDigits: 2 }) + " 회/h";
  $("falseCount").textContent = m.falseStudy + "회";
  $("study").textContent = fmt(m.studySec);
  $("completion").textContent = m.rateProvisional ? "잠정 결과" : "검토 완료";
  $("metricNote").textContent =
    "공부 구간에서 직접 확인한 오알림 ÷ 직접 확인한 공부 시간(시간 단위). 올바른 딴짓 후보 " +
    m.correctOff +
    "회 · 기타 활동 중 오알림 " +
    m.otherFalse +
    "회 · 미응답 " +
    m.unreviewed +
    "회 · 기억 안 남 " +
    m.unsure +
    "회.";
  $("coverage").textContent =
    "실제 활동 미확인 " +
    fmt(m.unknownSec) +
    (m.conflicts ? " · 응답 충돌 " + m.conflicts + "건" : "") +
    (m.observedStudySec != null
      ? " · 공부 중 판정 가능 " +
        fmt(m.observedStudySec) +
        (m.studyCoveragePct != null ? " (" + Math.round(m.studyCoveragePct) + "%)" : "")
      : "") +
    ".";
  $("eventCount").textContent = s.events.length + "건";
  if (m.reportedFalseStudy > m.falseStudy)
    $("metricNote").textContent += " 공부로 응답한 후보의 실제 활동 구간도 표시해 주세요.";
  if (m.coverageByState) {
    const c = m.coverageByState;
    $("coverage").textContent +=
      " 전체 관찰 중 수집 불가 " +
      fmt(c.UNAVAILABLE) +
      " · 보호 " +
      fmt(c.GUARD) +
      " · 체류 및 판정 대기 " +
      fmt(c.DWELL + c.PENDING) +
      ".";
  }
  drawEvents(s);
  drawRanges(s);
}
async function refresh() {
  if (fetching || busy) return;
  fetching = true;
  const turn = revision;
  try {
    let x = await request("/api/state" + (selection ? "?id=" + encodeURIComponent(selection) : ""));
    if (turn === revision && !busy) render(x);
  } catch (e) {
    $("notice").textContent = e.message;
  } finally {
    fetching = false;
  }
}
$("setup").onclick = async () => {
  const b = $("settings");
  b.hidden = !b.hidden;
  $("setup").setAttribute("aria-expanded", String(!b.hidden));
  if (b.hidden) {
    $("token").hidden = true;
    $("token").value = "";
  } else await permissionStatus();
};
async function permissionStatus() {
  try {
    const p = await request("/api/permission-status", {});
    $("perm").textContent = p.screenRecordingGranted
      ? "화면 기록 권한이 허용되어 있습니다."
      : "화면 기록 권한을 확인해 주세요.";
  } catch (e) {
    $("perm").textContent = e.message;
  }
}
$("permission").onclick = () =>
  action(async () => {
    await request("/api/permission", {});
    await permissionStatus();
  });
$("pair").onclick = () =>
  action(async () => {
    const p = await request("/api/pairing", {});
    $("token").hidden = false;
    $("token").value = p.token;
    $("token").select();
  });
$("consent").onchange = () => {
  $("start").disabled = !$("consent").checked;
};
$("start").onclick = () =>
  action(async () => {
    if (!$("consent").checked) return;
    const s = await request("/api/start", { consent: true });
    selection = s.id;
    $("consent").checked = false;
    $("start").disabled = true;
  });
$("end").onclick = () =>
  action(async () => {
    await request("/api/end", { sessionId: data.session.id });
  });
$("history").onchange = () => {
  selection = $("history").value;
  revision++;
  refresh();
};
$("range").onsubmit = (e) => {
  e.preventDefault();
  action(async () => {
    const a = parseTime($("from").value),
      b = parseTime($("to").value);
    if (b <= a || b > data.session.durationSec)
      throw Error("세션 시간 안에서 시작보다 늦은 끝 시각을 선택해 주세요.");
    await request("/api/interval", {
      sessionId: data.session.id,
      startSec: a,
      endSec: b,
      truth: $("truth").value,
    });
  });
};
$("download").onclick = () => {
  if (!data?.session) return;
  const blob = new Blob(
    [JSON.stringify({ modelRows: 4743, notifications: false, session: data.session }, null, 2)],
    { type: "application/json" },
  );
  const a = node("a");
  const u = URL.createObjectURL(blob);
  a.href = u;
  a.download = "focuson-pilot-" + data.session.id + ".json";
  a.click();
  setTimeout(() => URL.revokeObjectURL(u), 3000);
};
refresh();
setInterval(refresh, 2500);
