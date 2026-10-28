import { randomUUID } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'

const FILE_NAME = 'desktop-installation-id'

// [유지보수] 설치 ID는 기기 식별자이고 비밀 토큰이 아니다. 재실행해도 같은 값을 보내도록 별도 보관한다.
export async function getInstallationId(userDataDirectory: string): Promise<string> {
  const file = path.join(userDataDirectory, FILE_NAME)
  try {
    const existing = (await readFile(file, 'utf8')).trim()
    if (!existing) throw new Error('Empty installation ID')
    return existing
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
  }

  const created = randomUUID()
  try {
    await writeFile(file, created, { encoding: 'utf8', flag: 'wx', mode: 0o600 })
    return created
  } catch (error) {
    // [주의] 앱 인스턴스가 동시에 시작해 이미 만들었다면 기존 값을 읽는다.
    if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error
    const existing = (await readFile(file, 'utf8')).trim()
    if (!existing) throw new Error('Empty installation ID')
    return existing
  }
}

export function getDesktopDeviceName(): string {
  return os.hostname() || 'FocusOn Desktop'
}
