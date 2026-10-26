// 사이트 접근 권한 (E01b, 결정 009·013). 설치 때 받지 않고 사용자가 팝업에서 허용할 때만 요청한다.
export const SITE_ORIGINS = ['https://*/*', 'http://*/*']

export async function hasSiteAccess(): Promise<boolean> {
  try {
    return await chrome.permissions.contains({ origins: SITE_ORIGINS })
  } catch {
    return false
  }
}

/** [주의] Chrome 권한 창은 사용자 클릭 안에서만 열 수 있다. 버튼 핸들러에서 바로 부른다. */
export async function requestSiteAccess(): Promise<boolean> {
  try {
    return await chrome.permissions.request({ origins: SITE_ORIGINS })
  } catch {
    return false
  }
}
