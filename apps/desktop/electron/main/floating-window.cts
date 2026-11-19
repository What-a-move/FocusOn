import { BrowserWindow, screen } from 'electron'
import type { FloatingShow } from '../shared/intervention-contract.cjs'

const FLOATING_SIZE = { width: 720, height: 380 }

export function hasFloatingBackdrop(show: FloatingShow | null): boolean {
  return show?.presentation?.stage === 'normal' || show?.presentation?.stage === 'active'
}

export function createFloatingWindow(preload: string, backdrop: boolean): BrowserWindow {
  const window = new BrowserWindow({
    ...(backdrop ? screen.getPrimaryDisplay().bounds : FLOATING_SIZE),
    type: 'panel', frame: false, transparent: true, backgroundColor: '#00000000', hasShadow: false,
    resizable: false, movable: false, minimizable: false, maximizable: false, fullscreenable: false, skipTaskbar: true, show: false,
    // 전체 화면 패널은 작업 영역 제한을 풀어 노치 줄과 Dock 영역도 덮는다.
    ...(backdrop ? { enableLargerThanScreen: true, roundedCorners: false, vibrancy: 'hud', visualEffectState: 'active' } : {}),
    webPreferences: { preload, contextIsolation: true, nodeIntegration: false, sandbox: true, webSecurity: true },
  })
  window.setAlwaysOnTop(true, backdrop ? 'screen-saver' : 'modal-panel')
  window.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
  window.setIgnoreMouseEvents(!backdrop, { forward: true })
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  window.webContents.on('will-navigate', (event) => event.preventDefault())
  return window
}

export function pinFloatingWindow(window: BrowserWindow, show: FloatingShow): void {
  const area = screen.getPrimaryDisplay().bounds
  const left = show.presentation && 'side' in show.presentation && show.presentation.side === 'left'
  const width = Math.min(area.width, FLOATING_SIZE.width)
  // macOS가 최초 창 위치를 작업 영역으로 옮기므로 showInactive() 뒤 실제 모서리를 지정한다.
  window.setBounds(hasFloatingBackdrop(show) ? area : {
    ...FLOATING_SIZE, width, x: left ? area.x : area.x + area.width - width, y: area.y + area.height - FLOATING_SIZE.height,
  })
}
