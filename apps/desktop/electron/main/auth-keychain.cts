import { AsyncEntry } from '@napi-rs/keyring'
import { createHash } from 'node:crypto'

const SERVICE = 'com.focuson.desktop.auth'

export type RefreshTokenVault = {
  read(): Promise<string | null>
  write(token: string): Promise<void>
  clear(): Promise<void>
}

// [주의] 토큰 자체를 macOS Keychain에 저장한다. 파일·Renderer·환경 변수로 대체하지 않는다.
export class MacKeychainRefreshTokenVault implements RefreshTokenVault {
  private readonly entry: AsyncEntry

  constructor(apiBaseUrl: string) {
    if (process.platform !== 'darwin') throw new Error('macOS Keychain is required')
    // [주의] 개발 서버와 운영 서버가 같은 Keychain 항목을 덮어쓰지 않도록 API origin별로 분리한다.
    const account = `refresh-token:${createHash('sha256').update(apiBaseUrl).digest('hex')}`
    this.entry = new AsyncEntry(SERVICE, account)
  }

  read(): Promise<string | null> {
    return this.entry.getPassword()
  }

  write(token: string): Promise<void> {
    if (!token) throw new TypeError('Empty refresh token')
    return this.entry.setPassword(token)
  }

  async clear(): Promise<void> {
    await this.entry.deletePassword()
  }
}
