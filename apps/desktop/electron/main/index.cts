import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { app, BrowserWindow, ipcMain, Menu, nativeTheme, net, Notification, protocol, screen, shell } from 'electron'
import {
  APP_HOST,
  DEV_RENDERER_URL,
  PROD_RENDERER_URL,
  isAllowedNavigation,
  resolveRendererFile,
} from './renderer-path.cjs'
import { AuthSessionStore, isTrustedAuthSender, registerAuthIpc } from './auth-ipc.cjs'
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
import { ExcludedApps } from './excluded-apps.cjs'
import { ObserverHelper } from './observer-helper.cjs'
import { ScreenObserver, parseFrontmost } from './screen-observer.cjs'
import { AnalysisSender } from './analysis-sender.cjs'
import { registerAnalysisIpc } from './analysis-ipc.cjs'
import { InterventionWatcher } from './intervention-watcher.cjs'
import { INTERVENTION_CHANNELS, isActInput, type FloatingShow } from '../shared/intervention-contract.cjs'

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
  let sessionRuntime: SessionRuntime | null = null
  let isQuitting = false
  const authStore = new AuthSessionStore((snapshot) => {
    // 로그아웃하면 Main에 남은 세션 타이머를 비운다.
    if (snapshot.status === 'SIGNED_OUT') sessionRuntime?.reset()
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
    sessionRuntime = authService ? new SessionRuntime(authService, (snapshot) => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(SESSION_CHANNELS.changed, snapshot)
    }, sessionReady) : null
    const runtime = sessionRuntime
    registerSessionIpc(() => mainWindow, rendererUrl, authService && runtime ? new SessionService(authService, sessionReady.screen, sessionReady.notification, runtime) : null, runtime)
    const excludedApps = new ExcludedApps(path.join(app.getPath('userData'), 'excluded-apps.json'))
    const helper = process.platform === 'darwin'
      ? new ObserverHelper(app.isPackaged ? path.join(process.resourcesPath, 'focuson-observer') : path.join(app.getAppPath(), 'build/native/focuson-observer'))
      : null
    registerAnalysisIpc(() => mainWindow, rendererUrl, excludedApps, helper)
    app.on('will-quit', () => helper?.dispose())
    const observer = new ScreenObserver({ selfPid: process.pid, isExcluded: (bundleId) => excludedApps.has(bundleId), now: () => performance.now(), wallClock: () => new Date() })
    // 관측은 바로 서버로 보내고 Desktop에 쌓아 두지 않는다(결정 024). 창 제목은 로그에 남기지 않는다.
    const sender = authService ? new AnalysisSender(authService, path.join(app.getPath('userData'), 'analysis-sequence.json')) : null
    let observing = false
    // 화면 위 포키 (화면 분석 4단계, floating-poki.html). 평소엔 숨기고 이탈 알림이 올 때만 포커스를 빼앗지 않고 띄운다.
    let floatingWindow: BrowserWindow | null = null
    let pendingShow: FloatingShow | null = null
    const FLOATING_SIZE = { width: 580, height: 400 }
    function showFloating(payload: FloatingShow): void {
      if (!floatingWindow || floatingWindow.isDestroyed()) {
        const area = screen.getPrimaryDisplay().workArea
        const window = new BrowserWindow({
          ...FLOATING_SIZE, x: area.x + area.width - FLOATING_SIZE.width - 48, y: area.y + area.height - FLOATING_SIZE.height,
          // macOS panel: 다른 앱을 쓰는 중에도 클릭할 수 있고 FocusOn을 앞으로 가져오지 않는다.
          type: 'panel', frame: false, transparent: true, backgroundColor: '#00000000', hasShadow: false, resizable: false, movable: false,
          minimizable: false, maximizable: false, fullscreenable: false, skipTaskbar: true, show: false,
          webPreferences: { preload: path.join(__dirname, '../preload/index.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true, webSecurity: true },
        })
        window.setAlwaysOnTop(true, 'floating')
        window.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
        window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
        window.webContents.on('will-navigate', (event) => event.preventDefault())
        window.webContents.on('did-finish-load', () => {
          if (pendingShow) { window.webContents.send(INTERVENTION_CHANNELS.show, pendingShow); window.showInactive() }
        })
        window.on('closed', () => { if (floatingWindow === window) floatingWindow = null })
        floatingWindow = window
        pendingShow = payload
        void window.loadURL(new URL('floating/', rendererUrl).toString()).catch(() => {})
        return
      }
      pendingShow = payload
      floatingWindow.webContents.send(INTERVENTION_CHANNELS.show, payload)
      floatingWindow.showInactive()
    }
    function hideFloating(): void {
      pendingShow = null
      if (floatingWindow && !floatingWindow.isDestroyed()) floatingWindow.hide()
    }
    const watcher = authService && runtime ? new InterventionWatcher({
      auth: authService,
      show: showFloating,
      notify: (payload, sound) => { if (Notification.isSupported()) new Notification({ title: 'FocusOn', body: `${payload.say} ${payload.sub}`, silent: !sound }).show() },
      pause: () => runtime.pause('USER'),
    }) : null
    ipcMain.handle(INTERVENTION_CHANNELS.act, async (event, ...args: unknown[]) => {
      if (args.length !== 1 || !isActInput(args[0]) || !isTrustedAuthSender(event, floatingWindow, rendererUrl)) return { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } }
      const { interventionId, action } = args[0]
      // 조금 더 보기는 같은 창에서 목표 안내로 이어지고, 나머지는 창을 닫는다.
      if (action !== 'RECENTER') hideFloating()
      await watcher?.act(interventionId, action).catch(() => {})
      return { success: true, data: { done: true } }
    })
    let watchedSession: string | null = null

    // [유지보수] 창을 숨겨도 Main에서 목표 시간 도달(1초)과 서버 생존 신호(10초, 학습 세션 설계서·세션 상태 전이 정책)를 계속 처리한다.
    if (runtime) {
      setInterval(() => {
        runtime.tick()
        // 세션이 RUNNING일 때만 맨 앞 앱을 본다(결정 024). 멈추면 15초 계산을 처음부터 다시 한다.
        const session = runtime.snapshot()
        if (session?.status !== 'RUNNING') { observer.reset(); return }
        if (!helper || observing) return
        observing = true
        void helper.request('frontmost')
          .then((reply) => {
            // [주의] 확인하는 사이 세션이 멈췄거나 바뀌었으면 그 결과는 버린다. 멈춘 동안의 앱 정보를 보내지 않는다.
            const current = runtime.snapshot()
            if (current?.status !== 'RUNNING' || current.sessionId !== session.sessionId) return
            const observation = observer.observe(parseFrontmost(reply))
            if (observation) void sender?.send(session.sessionId, observation)
          })
          .finally(() => { observing = false })
      }, 1000)
      setInterval(() => {
        // 세션 중 화면 기록 권한이 꺼지면 바로 멈춘다(Notion 화면 분석 7.2).
        if (runtime.snapshot()?.status === 'RUNNING' && mediaStatus('screen') !== 'granted') runtime.screenPermissionLost()
        void runtime.heartbeat()
        // 이탈 알림은 heartbeat와 같은 10초 주기로 조회한다(Notion interventions 비고). 진행 중이 아니면 포키를 숨긴다.
        const current = runtime.snapshot()
        if (current?.sessionId !== watchedSession) { watchedSession = current?.sessionId ?? null; watcher?.reset() }
        if (current?.status === 'RUNNING') void watcher?.poll(current.sessionId, current.goalText, runtime.sessionOptions())
        else hideFloating()
      }, 10_000)
    }
    createMainWindow()
    void authService?.restore()
  })
}
