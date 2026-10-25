import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { createInterface } from 'node:readline'

const TIMEOUT_MS = 1000

/**
 * Swift 화면 관측 도우미(native/observer)와 JSON 한 줄씩 주고받는다 (결정 024).
 * - 처음 요청할 때 띄우고, 죽으면 다음 요청 때 다시 띄운다.
 * - 응답이 제때 오지 않으면 null을 돌려준다. 호출하는 쪽은 "확인 불가"로 다룬다.
 */
export class ObserverHelper {
  private child: ChildProcessWithoutNullStreams | null = null
  private nextId = 1
  private waiting = new Map<number, (value: Record<string, unknown> | null) => void>()

  constructor(private readonly binary: string) {}

  request(cmd: 'frontmost' | 'apps', timeoutMs = TIMEOUT_MS): Promise<Record<string, unknown> | null> {
    const child = this.ensure()
    if (!child) return Promise.resolve(null)
    const id = this.nextId++
    return new Promise((resolve) => {
      const timer = setTimeout(() => { this.waiting.delete(id); resolve(null) }, timeoutMs)
      this.waiting.set(id, (value) => { clearTimeout(timer); resolve(value) })
      child.stdin.write(`${JSON.stringify({ id, cmd })}\n`)
    })
  }

  dispose(): void {
    this.child?.kill()
    this.child = null
  }

  private ensure(): ChildProcessWithoutNullStreams | null {
    if (this.child) return this.child
    try {
      const child = spawn(this.binary, [], { stdio: ['pipe', 'pipe', 'pipe'] })
      child.on('error', () => this.drop(child))
      child.on('exit', () => this.drop(child))
      child.stdin.on('error', () => this.drop(child))
      createInterface({ input: child.stdout }).on('line', (line) => {
        let message: unknown
        try { message = JSON.parse(line) } catch { return }
        if (typeof message !== 'object' || message === null) return
        const { id, ok } = message as { id?: unknown; ok?: unknown }
        const done = typeof id === 'number' ? this.waiting.get(id) : undefined
        if (!done) return
        this.waiting.delete(id as number)
        done(ok === true ? message as Record<string, unknown> : null)
      })
      this.child = child
      return child
    } catch {
      return null
    }
  }

  private drop(child: ChildProcessWithoutNullStreams): void {
    if (this.child !== child) return
    this.child = null
    for (const done of this.waiting.values()) done(null)
    this.waiting.clear()
  }
}
