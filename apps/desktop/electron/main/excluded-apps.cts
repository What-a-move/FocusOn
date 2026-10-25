import { readFileSync, writeFileSync } from 'node:fs'
import { isExcludedAppList, type ExcludedApp } from '../shared/analysis-contract.cjs'

/**
 * 분석 제외 앱 목록을 이 기기에만 저장한다 (결정 024, Notion 화면 분석 6.1.1).
 * 어떤 앱을 제외했는지 자체가 민감할 수 있고 설치된 앱은 기기마다 달라 서버로 보내지 않는다.
 */
export class ExcludedApps {
  private cache: ExcludedApp[] | null = null

  constructor(private readonly file: string) {}

  list(): ExcludedApp[] {
    if (this.cache) return this.cache
    try {
      const saved = (JSON.parse(readFileSync(this.file, 'utf8')) as { apps?: unknown }).apps
      this.cache = isExcludedAppList(saved) ? saved : []
    } catch {
      this.cache = []
    }
    return this.cache
  }

  has(bundleId: string): boolean {
    return this.list().some((app) => app.bundleId === bundleId)
  }

  save(apps: ExcludedApp[]): ExcludedApp[] {
    const clean = apps.map(({ bundleId, name }) => ({ bundleId, name: name.trim() }))
    writeFileSync(this.file, JSON.stringify({ apps: clean, updatedAt: new Date().toISOString() }))
    this.cache = clean
    return clean
  }
}
