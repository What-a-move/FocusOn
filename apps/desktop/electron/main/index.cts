import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { app, BrowserWindow, Menu, net, protocol } from 'electron'
import {
  APP_HOST,
  DEV_RENDERER_URL,
  PROD_RENDERER_URL,
  isAllowedNavigation,
  resolveRendererFile,
} from './renderer-path.cjs'

const useProductionRenderer = app.isPackaged || process.env.FOCUSON_RENDERER_MODE === 'production'
const rendererUrl = useProductionRenderer ? PROD_RENDERER_URL : DEV_RENDERER_URL

// [주의] Scheme 권한은 app.whenReady() 전에 등록해야 한다.
protocol.registerSchemesAsPrivileged([
  { scheme: 'app', privileges: { standard: true, secure: true } },
])

const hasSingleInstanceLock = app.requestSingleInstanceLock()

if (!hasSingleInstanceLock) {
  app.quit()
} else {
  let mainWindow: BrowserWindow | null = null
  let isQuitting = false

  function showMainWindow(): void {
    if (!mainWindow || mainWindow.isDestroyed()) {
      createMainWindow()
      return
    }

    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.show()
    mainWindow.focus()
  }

  function createMainWindow(): void {
    if (mainWindow && !mainWindow.isDestroyed()) {
      showMainWindow()
      return
    }

    const window = new BrowserWindow({
      width: 1200,
      height: 800,
      minWidth: 800,
      minHeight: 600,
      show: false,
      backgroundColor: '#ffffff',
      webPreferences: {
        preload: path.join(__dirname, '../preload/index.cjs'),
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        webSecurity: true,
      },
    })

    mainWindow = window

    window.once('ready-to-show', () => {
      if (!window.isDestroyed()) window.show()
    })

    window.on('close', (event) => {
      if (!isQuitting) {
        event.preventDefault()
        window.hide()
      }
    })

    window.on('closed', () => {
      if (mainWindow === window) mainWindow = null
    })

    window.webContents.on('will-navigate', (event, targetUrl) => {
      if (!isAllowedNavigation(targetUrl, rendererUrl)) event.preventDefault()
    })

    window.webContents.on('will-redirect', (event, targetUrl) => {
      if (!isAllowedNavigation(targetUrl, rendererUrl)) event.preventDefault()
    })

    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
    window.webContents.on('will-attach-webview', (event) => event.preventDefault())

    // [주의] 기능별 권한 UX가 구현되기 전에는 화면·카메라 등 권한을 자동 승인하지 않는다.
    window.webContents.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false))
    window.webContents.session.setPermissionCheckHandler(() => false)

    void window.loadURL(rendererUrl).catch((error: unknown) => {
      console.error('Renderer를 열지 못했어.', error)
    })
  }

  function installMenu(): void {
    Menu.setApplicationMenu(Menu.buildFromTemplate([
      {
        label: 'FocusOn',
        submenu: [
          { label: 'FocusOn 열기', accelerator: 'CommandOrControl+Shift+O', click: showMainWindow },
          { type: 'separator' },
          { label: 'FocusOn 종료', role: 'quit' },
        ],
      },
      { role: 'editMenu' },
      { role: 'windowMenu' },
    ]))
  }

  app.on('before-quit', () => { isQuitting = true })
  app.on('second-instance', () => showMainWindow())
  app.on('activate', () => showMainWindow())

  void app.whenReady().then(() => {
    const outDirectory = path.join(app.getAppPath(), 'out')

    protocol.handle('app', async (request) => {
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        return new Response(null, { status: 405 })
      }

      const filePath = resolveRendererFile(outDirectory, request.url)
      if (!filePath || new URL(request.url).hostname !== APP_HOST) {
        return new Response(null, { status: 404 })
      }

      try {
        return await net.fetch(pathToFileURL(filePath).toString())
      } catch {
        return new Response(null, { status: 404 })
      }
    })

    installMenu()
    createMainWindow()
  })
}
