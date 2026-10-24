// Popup에서 쓰는 chrome.storage 최소 타입. 필요한 API가 늘면 @types/chrome 도입을 검토한다.
type ChromeStorageArea = {
  get(keys: string | string[]): Promise<Record<string, unknown>>
  set(items: Record<string, unknown>): Promise<void>
  remove(keys: string | string[]): Promise<void>
}

declare const chrome: {
  storage?: {
    local: ChromeStorageArea
    session: ChromeStorageArea & { setAccessLevel?(options: { accessLevel: 'TRUSTED_CONTEXTS' | 'TRUSTED_AND_UNTRUSTED_CONTEXTS' }): Promise<void> }
  }
} | undefined
