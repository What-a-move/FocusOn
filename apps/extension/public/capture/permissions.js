const button = document.getElementById('allow');
const status = document.getElementById('status');
async function refresh() {
  const allowed = await chrome.permissions.contains({ origins: ['<all_urls>'] });
  button.disabled = allowed;
  status.textContent = allowed ? '현재 탭 OCR 권한이 허용되어 있습니다. 실제 수집은 학습 세션 연결 후에 시작합니다.' : 'OCR 권한이 없습니다. DOM으로 읽을 수 있는 페이지는 기존 경로를 사용합니다.';
}
button.addEventListener('click', () => {
  chrome.permissions.request({ origins: ['<all_urls>'] }).then(refresh, () => { status.textContent = '권한이 허용되지 않았습니다.'; });
});
refresh().catch(() => { status.textContent = '권한을 확인할 수 없습니다.'; });
