import { readFileSync, writeFileSync } from 'node:fs'

/**
 * macOS는 앱에 알림 허용 상태를 알려 주지 않는다. 사용자가 시험 알림을 보고 `보였어요`/`안 보였어요`를 누른 결과를
 * 이 기기에 기록해, 세션 시작 전에 Desktop이 알림 준비를 막는다(결정 022). 서버는 알림을 검증하지 않는다.
 * ponytail: 사용자가 나중에 시스템 설정에서 알림을 끄면 알 수 없다. macOS가 상태 API를 주면 그걸로 바꾼다.
 */
export class NotificationReadiness {
  constructor(private readonly file: string) {}

  isConfirmed(): boolean {
    try {
      return (JSON.parse(readFileSync(this.file, 'utf8')) as { confirmed?: unknown }).confirmed === true
    } catch {
      return false
    }
  }

  set(confirmed: boolean): void {
    writeFileSync(this.file, JSON.stringify({ confirmed, updatedAt: new Date().toISOString() }))
  }
}
