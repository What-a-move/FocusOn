const origins = ["http://*/*", "https://*/*"];
const tokenField = document.getElementById("token");
const excludedField = document.getElementById("excluded");
const statusField = document.getElementById("status");

function domains(text) {
  return [
    ...new Set(
      text
        .split(/[\n,]/)
        .map((t) => t.trim().toLowerCase())
        .filter(Boolean)
        .map((t) => {
          // URL components should not silently widen an exclusion. Subdomains are included automatically.
          if (!/^(?:[\p{Letter}\p{Number}-]+\.)*[\p{Letter}\p{Number}-]+\.?$/u.test(t))
            throw Error("도메인 이름만 입력해 주세요.");
          const host = new URL("https://" + t).hostname.replace(/\.$/, "");
          if (!host) throw Error("도메인 이름을 확인해 주세요.");
          return host;
        }),
    ),
  ];
}

async function showStatus() {
  try {
    const reply = await chrome.runtime.sendMessage({ type: "status" });
    statusField.textContent = reply.status;
  } catch {
    statusField.textContent = "연결을 확인해 주세요.";
  }
}

const saved = await chrome.storage.local.get(["token", "excluded"]);
if (saved.token) tokenField.placeholder = "저장된 코드 사용 중";
excludedField.value = (saved.excluded || []).join("\n");
void showStatus();

document.getElementById("form").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    const excluded = domains(excludedField.value);
    if (!saved.token && !tokenField.value.trim())
      throw Error("대시보드의 연결 코드를 입력해 주세요.");
    if (!(await chrome.permissions.request({ origins })))
      throw Error("Chrome 사이트 접근을 허용해야 테스트할 수 있습니다.");
    const token = tokenField.value.trim() || saved.token;
    await chrome.storage.local.set({ token, excluded });
    saved.token = token;
    tokenField.value = "";
    tokenField.placeholder = "저장된 코드 사용 중";
    await showStatus();
  } catch (error) {
    statusField.textContent = error.message;
  }
});

document.getElementById("disconnect").addEventListener("click", async () => {
  await chrome.storage.local.remove("token");
  saved.token = "";
  tokenField.value = "";
  tokenField.placeholder = "코드를 붙여넣으세요";
  // Remove only optional permission grants. The fixed loopback origin remains in the manifest.
  await chrome.permissions.remove({ origins }).catch(() => {});
  statusField.textContent = "연결을 해제했습니다.";
});
