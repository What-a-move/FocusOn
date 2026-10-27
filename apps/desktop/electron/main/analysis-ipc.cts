import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { app, ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { ANALYSIS_CHANNELS, isExcludedApp, isExcludedAppList, type ExcludedApp, type InstalledApp } from '../shared/analysis-contract.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { ExcludedApps } from './excluded-apps.cjs'
import type { ObserverHelper } from './observer-helper.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const
// 앱 폴더를 훑는 데 1초보다 오래 걸릴 수 있다.
const APPS_TIMEOUT_MS = 5000

/** 도우미 응답에서 설치된 앱만 꺼낸다. 실행 중인 앱을 먼저, 그다음 이름 순으로 보여 준다. */
export function toInstalledApps(reply: Record<string, unknown>): (ExcludedApp & { path: string; running: boolean })[] {
  const running = new Set(Array.isArray(reply.running) ? reply.running.filter((id): id is string => typeof id === 'string') : [])
  const apps = Array.isArray(reply.apps) ? reply.apps : []
  return apps
    .filter((item): item is { bundleId: string; name: string; path: string } => typeof item === 'object' && item !== null
      && typeof item.bundleId === 'string' && typeof item.name === 'string' && typeof item.path === 'string')
    // [주의] 저장 검증(번들 ID 형식·이름 길이)을 통과하지 못할 앱은 고를 수 없게 처음부터 뺀다. 고른 뒤 저장 전체가 실패하지 않게 한다.
    .filter(({ bundleId, name }) => isExcludedApp({ bundleId, name }))
    .map(({ bundleId, name, path }) => ({ bundleId, name, path, running: running.has(bundleId) }))
    .sort((a, b) => Number(b.running) - Number(a.running) || a.name.localeCompare(b.name, 'ko'))
}

export function registerAnalysisIpc(getMainWindow: () => BrowserWindow | null, rendererUrl: string, excludedApps: ExcludedApps, helper: ObserverHelper | null): void {
  const trusted = (event: IpcMainInvokeEvent) => isTrustedAuthSender(event, getMainWindow(), rendererUrl)

  ipcMain.handle(ANALYSIS_CHANNELS.installedApps, async (event, ...args: unknown[]): Promise<IpcResult<{ apps: InstalledApp[] }>> => {
    if (args.length !== 0 || !trusted(event)) return INVALID_REQUEST
    const reply = await helper?.request('apps', { timeoutMs: APPS_TIMEOUT_MS })
    if (!reply) return SERVICE_UNAVAILABLE
    const apps = await Promise.all(toInstalledApps(reply).map(async ({ path, ...rest }) => ({
      ...rest,
      icon: await app.getFileIcon(path, { size: 'normal' }).then((image) => image.toDataURL(), () => null),
    })))
    return { success: true, data: { apps } }
  })

  ipcMain.handle(ANALYSIS_CHANNELS.excludedAppsGet, (event, ...args: unknown[]): IpcResult<{ apps: ExcludedApp[] }> => {
    if (args.length !== 0 || !trusted(event)) return INVALID_REQUEST
    return { success: true, data: { apps: excludedApps.list() } }
  })

  ipcMain.handle(ANALYSIS_CHANNELS.excludedAppsSave, (event, ...args: unknown[]): IpcResult<{ apps: ExcludedApp[] }> => {
    const input = args[0] as { apps?: unknown } | undefined
    if (!trusted(event) || args.length !== 1 || typeof input !== 'object' || input === null || Object.keys(input).length !== 1 || !isExcludedAppList(input.apps)) return INVALID_REQUEST
    try {
      return { success: true, data: { apps: excludedApps.save(input.apps) } }
    } catch {
      return SERVICE_UNAVAILABLE
    }
  })
}
