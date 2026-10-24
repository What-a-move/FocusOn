import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { app, BrowserWindow, Menu, nativeTheme, net, protocol, shell } from 'electron'
import {
  APP_HOST,
  DEV_RENDERER_URL,
  PROD_RENDERER_URL,
  isAllowedNavigation,
  resolveRendererFile,
} from './renderer-path.cjs'
import { AuthSessionStore, registerAuthIpc } from './auth-ipc.cjs'
import { AUTH_CHANNELS } from '../shared/auth-contract.cjs'
import { GoogleOAuthFlow } from './google-oauth.cjs'
import { AuthApiClient, resolveAuthApiBaseUrl } from './auth-api.cjs'
import { MacKeychainRefreshTokenVault } from './auth-keychain.cjs'
import { getDesktopDeviceName, getInstallationId } from './desktop-identity.cjs'
import { AuthService } from './auth-service.cjs'
import { mediaStatus, registerPermissionIpc } from './permission-ipc.cjs'
import { OnboardingService } from './onboarding-service.cjs'
import { registerOnboardingIpc } from './onboarding-ipc.cjs'
import { ReportService, registerReportIpc } from './report-ipc.cjs'
import { GoalService, registerGoalIpc } from './goal-ipc.cjs'
import { SessionService, registerSessionIpc } from './session-ipc.cjs'
import { NotificationReadiness } from './notification-readiness.cjs'
import { SessionRuntime } from './session-runtime.cjs'
import { SESSION_CHANNELS } from '../shared/session-contract.cjs'

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
  const authStore = new AuthSessionStore((snapshot) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send(AUTH_CHANNELS.sessionChanged, snapshot)
    }
  })
  let authService: AuthService | null = null
  const googleClientId = process.env.FOCUSON_GOOGLE_CLIENT_ID?.trim()
  const oauthFlow = googleClientId ? new GoogleOAuthFlow({
    clientId: googleClientId,
    openBrowser: (url) => shell.openExternal(url),
    onOutcome: (outcome) => {
      if (authService) {
        void authService.handleOAuthOutcome(outcome)
      } else {
        // [주의] API 설정이 없으면 callback 수신만으로 로그인 성공을 만들지 않는다.
        authStore.publish({
          status: 'SIGNED_OUT', user: null, onboardingStatus: null,
          error: outcome.type === 'cancelled' ? null : { code: 'SERVICE_UNAVAILABLE', retryable: false },
        })
      }
    },
  }) : null

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
      // [디자인] 화면을 읽기 전에도 macOS의 라이트·다크 배경색을 맞춰 깜빡임을 줄인다.
      backgroundColor: nativeTheme.shouldUseDarkColors ? '#141416' : '#ffffff',
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

    // [유지보수] 개발 실행에서만 FOCUSON_DEV_START_PATH(예: /onboarding/permissions/)로 첫 화면을 바로 열 수 있다.
    // 같은 앱 출처의 경로만 허용하고, 패키징된 앱에서는 무시한다.
    const devStartPath = app.isPackaged ? '' : process.env.FOCUSON_DEV_START_PATH?.trim() ?? ''
    const devStartUrl = devStartPath.startsWith('/') ? new URL(devStartPath, rendererUrl).toString() : ''
    const startUrl = devStartUrl && isAllowedNavigation(devStartUrl, rendererUrl) ? devStartUrl : rendererUrl
    void window.loadURL(startUrl).catch((error: unknown) => {
      console.error('Renderer를 열지 못했어요.', error)
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

  app.on('before-quit', () => {
    isQuitting = true
    oauthFlow?.dispose()
  })
  app.on('second-instance', () => showMainWindow())
  app.on('activate', () => showMainWindow())

  void app.whenReady().then(() => {
    const apiBaseUrl = resolveAuthApiBaseUrl(process.env.FOCUSON_API_BASE_URL)
    if (apiBaseUrl && process.platform === 'darwin') {
      authService = new AuthService(
        authStore,
        new AuthApiClient(apiBaseUrl),
        new MacKeychainRefreshTokenVault(apiBaseUrl),
        async () => ({
          installationId: await getInstallationId(app.getPath('userData')),
          deviceName: getDesktopDeviceName(),
          platform: 'MACOS',
        }),
      )
    }
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
    registerAuthIpc(() => mainWindow, rendererUrl, authStore, oauthFlow, authService)
    const notificationReadiness = new NotificationReadiness(path.join(app.getPath('userData'), 'notification-readiness.json'))
    registerPermissionIpc(() => mainWindow, rendererUrl, notificationReadiness)
    // [주의] 화면 기록 준비 상태는 Renderer 주장이 아니라 Main이 macOS에서 직접 확인한 값으로 서버에 보낸다.
    registerOnboardingIpc(() => mainWindow, rendererUrl, authService ? new OnboardingService(authService, () => mediaStatus('screen') === 'granted') : null)
    registerReportIpc(() => mainWindow, rendererUrl, authService ? new ReportService(authService) : null)
    registerGoalIpc(() => mainWindow, rendererUrl, authService ? new GoalService(authService) : null)
    const sessionReady = { screen: () => mediaStatus('screen') === 'granted', notification: () => notificationReadiness.isConfirmed() }
    const sessionRuntime = authService ? new SessionRuntime(authService, (snapshot) => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(SESSION_CHANNELS.changed, snapshot)
    }, sessionReady) : null
    registerSessionIpc(() => mainWindow, rendererUrl, authService && sessionRuntime ? new SessionService(authService, sessionReady.screen, sessionReady.notification, sessionRuntime) : null, sessionRuntime)
    // [유지보수] 창을 숨겨도 Main에서 목표 시간 도달(1초)과 서버 생존 신호(20초)를 계속 처리한다.
    if (sessionRuntime) {
      setInterval(() => sessionRuntime.tick(), 1000)
      setInterval(() => { void sessionRuntime.heartbeat() }, 20_000)
    }
    createMainWindow()
    void authService?.restore()
  })
}
