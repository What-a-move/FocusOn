import { readFileSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { app, BrowserWindow, globalShortcut, ipcMain, Menu, nativeTheme, net, Notification, powerMonitor, protocol, screen, shell } from 'electron'
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
import { createLogger, initLogger, log } from './logger.cjs'
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
import { SESSION_CHANNELS, type SessionSnapshot } from '../shared/session-contract.cjs'
import { ExcludedApps } from './excluded-apps.cjs'
import { ObserverHelper } from './observer-helper.cjs'
import { CHROME, ScreenObserver, parseFrontmost } from './screen-observer.cjs'
import { ObservationContext } from './observation-context.cjs'
import { AnalysisSender } from './analysis-sender.cjs'
import { analyzeObservation } from './screen-analysis.cjs'
import { registerAnalysisIpc } from './analysis-ipc.cjs'
import { SettingsService, ThemePreference, registerSettingsIpc } from './settings-ipc.cjs'
import { COPY, InterventionWatcher } from './intervention-watcher.cjs'
import { CameraMonitor, parseCameraReply } from './camera-monitor.cjs'
import { installDemoMode } from './demo-mode.cjs'
import { CAMERA_ID, DISCONNECTED_ID, INTERVENTION_CHANNELS, isActInput, type FloatingShow } from '../shared/intervention-contract.cjs'

const useProductionRenderer = app.isPackaged || process.env.FOCUSON_RENDERER_MODE === 'production'
const rendererUrl = useProductionRenderer ? PROD_RENDERER_URL : DEV_RENDERER_URL
// 패키지 앱에는 .env가 없어 빌드할 때 넣은 값을 쓴다(scripts/write-build-config.cjs). 개발 실행은 환경 변수가 우선한다.
const buildConfig: { apiBaseUrl?: string; googleClientId?: string } = (() => {
  try { return JSON.parse(readFileSync(path.join(__dirname, '../build-config.json'), 'utf8')) } catch { return {} }
})()

// [주의] Scheme 권한은 app.whenReady() 전에 등록해야 한다.
protocol.registerSchemesAsPrivileged([
  { scheme: 'app', privileges: { standard: true, secure: true } },
])

// 설치한 앱은 `FocusOn` 폴더에 데이터를 둔다. 개발 실행(@focuson/desktop)과 섞이지 않고 함께 띄울 수 있다.
// [주의] 단일 실행 잠금과 userData를 쓰기 전에 정해야 한다.
if (app.isPackaged) app.setPath('userData', path.join(app.getPath('appData'), 'FocusOn'))
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
  const googleClientId = process.env.FOCUSON_GOOGLE_CLIENT_ID?.trim() || buildConfig.googleClientId
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
          error: outcome.type === 'cancelled' ? null : { code: 'LOCAL_APP_CONFIG_MISSING', retryable: false },
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
    // 클립보드 쓰기(연결 코드 복사)만 허용한다. 읽기는 막는다.
    window.webContents.session.setPermissionRequestHandler((_contents, permission, callback) => callback(permission === 'clipboard-sanitized-write'))
    window.webContents.session.setPermissionCheckHandler((_contents, permission) => permission === 'clipboard-sanitized-write')

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
    // 모듈 연동 로그 (Notion 모듈 연동 로깅 규칙). 개발 중에는 터미널에도 찍는다.
    initLogger(createLogger(path.join(app.getPath('userData'), 'logs', 'focuson.log'), { echo: !app.isPackaged }))
    // 절전 시간을 집중 시간에 넣을지는 아직 정하지 않았다. 판단 근거로 쓰도록 두 시계의 차이만 남긴다.
    let suspendedAt: { mono: number; wall: number } | null = null
    powerMonitor.on('suspend', () => {
      suspendedAt = { mono: performance.now(), wall: Date.now() }
      log('INFO', 'power.suspend', { component: 'power' })
    })
    powerMonitor.on('resume', () => {
      const heldMono = suspendedAt ? Math.round(performance.now() - suspendedAt.mono) : null
      const heldWall = suspendedAt ? Date.now() - suspendedAt.wall : null
      log('INFO', 'power.resume', { component: 'power', heldMono, heldWall, sleepSuspected: heldMono !== null && heldWall !== null && heldWall - heldMono > 5_000 })
      suspendedAt = null
    })
    const apiBaseUrl = resolveAuthApiBaseUrl(process.env.FOCUSON_API_BASE_URL || buildConfig.apiBaseUrl)
    if (apiBaseUrl && process.platform === 'darwin') {
      authService = new AuthService(
        authStore,
        new AuthApiClient(apiBaseUrl),
        // [주의] 같은 Mac에서 설치 앱과 개발 실행이 같은 서버를 써도 로그인이 서로 풀리지 않게 Keychain 항목을 나눈다. 개발 실행 항목은 그대로 둔다.
        new MacKeychainRefreshTokenVault(app.isPackaged ? `${apiBaseUrl}#installed` : apiBaseUrl),
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
    // 화면 모드는 창을 만들기 전에 적용해야 첫 화면부터 맞는 색으로 그린다.
    const themePreference = new ThemePreference(path.join(app.getPath('userData'), 'preferences.json'))
    nativeTheme.themeSource = themePreference.read()
    registerSettingsIpc(() => mainWindow, rendererUrl, authService ? new SettingsService(authService) : null, {
      get: () => nativeTheme.themeSource,
      set: (theme) => { themePreference.write(theme); nativeTheme.themeSource = theme },
    })
    registerAuthIpc(() => mainWindow, rendererUrl, authStore, oauthFlow, authService)
    const notificationReadiness = new NotificationReadiness(path.join(app.getPath('userData'), 'notification-readiness.json'))
    registerPermissionIpc(() => mainWindow, rendererUrl, notificationReadiness)
    // [주의] 화면 기록 준비 상태는 Renderer 주장이 아니라 Main이 macOS에서 직접 확인한 값으로 서버에 보낸다.
    registerOnboardingIpc(() => mainWindow, rendererUrl, authService ? new OnboardingService(authService, () => mediaStatus('screen') === 'granted') : null)
    registerReportIpc(() => mainWindow, rendererUrl, authService ? new ReportService(authService) : null)
    registerGoalIpc(() => mainWindow, rendererUrl, authService ? new GoalService(authService) : null)
    const sessionReady = { screen: () => mediaStatus('screen') === 'granted', notification: () => notificationReadiness.isConfirmed() }
    let lastSession: SessionSnapshot | null = null
    sessionRuntime = authService ? new SessionRuntime(authService, (snapshot) => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(SESSION_CHANNELS.changed, snapshot)
      const disconnected = disconnectedNotice(lastSession, snapshot)
      if (disconnected) showFloating(disconnected)
      else if (snapshot?.status === 'RUNNING' && pendingShow?.interventionId === DISCONNECTED_ID) hideFloating()
      lastSession = snapshot
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
    // 지금 앞에 떠 있는 화면(전경 context)을 서버에 알린다(#182). 알림은 이 context 기준으로만 만들어지고 표시된다.
    const context = authService ? new ObservationContext(authService, path.join(app.getPath('userData'), 'observation-context.json')) : null
    let observing = false
    // 맨 앞 창이 전체 화면인지. 바뀌면 떠 있는 포키도 바닥 위치를 다시 맞춘다(#225).
    let frontFullScreen = false
    // 카메라 분석(#222). 화면 판정은 같은 화면(key)일 때만 최신으로 본다. 화면이 바뀌면 이전 판정은 쓰지 않는다.
    let latestScreen: { key: string; relation: string } | null = null
    const cameraMonitor = new CameraMonitor(() => performance.now())
    let cameraOn = false
    let cameraBusy = false
    // 화면 위 포키 (화면 분석 4단계, floating-poki.html). 평소엔 숨기고 이탈 알림이 올 때만 포커스를 빼앗지 않고 띄운다.
    let floatingWindow: BrowserWindow | null = null
    let pendingShow: FloatingShow | null = null
    // 고개 내민 포키 + 왼쪽 카드가 딱 들어가는 크기에, Dock 위로 올릴 여유(80px)를 더했다. 화면 오른쪽 아래 모서리에 붙인다(#225).
    const FLOATING_SIZE = { width: 720, height: 380 }
    function showFloating(payload: FloatingShow): void {
      if (!floatingWindow || floatingWindow.isDestroyed()) {
        const area = screen.getPrimaryDisplay().bounds
        const window = new BrowserWindow({
          ...FLOATING_SIZE, x: area.x + area.width - FLOATING_SIZE.width, y: area.y + area.height - FLOATING_SIZE.height,
          // macOS panel: 다른 앱을 쓰는 중에도 클릭할 수 있고 FocusOn을 앞으로 가져오지 않는다.
          type: 'panel', frame: false, transparent: true, backgroundColor: '#00000000', hasShadow: false, resizable: false, movable: false,
          minimizable: false, maximizable: false, fullscreenable: false, skipTaskbar: true, show: false,
          webPreferences: { preload: path.join(__dirname, '../preload/index.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true, webSecurity: true },
        })
        // [주의] Dock보다 한 층 아래(modal-panel)에 둔다. 보통은 Dock 위에 앉고, 전체 화면에서 Dock이 잠깐 올라오면 Dock이 포키를 덮는다(#225).
        window.setAlwaysOnTop(true, 'modal-panel')
        window.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
        // 투명한 빈 곳은 아래 앱이 클릭되게 통과시킨다. 포인터 이동은 계속 받아서, 카드·포키 위에 오면 창이 클릭을 받게 바꾼다(#225).
        window.setIgnoreMouseEvents(true, { forward: true })
        window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
        window.webContents.on('will-navigate', (event) => event.preventDefault())
        window.webContents.on('did-finish-load', () => {
          if (pendingShow) { window.webContents.send(INTERVENTION_CHANNELS.show, pendingShow); window.showInactive(); pinFloating(window) }
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
      pinFloating(floatingWindow)
    }
    /**
     * 포키 창은 늘 화면 진짜 맨 아래 오른쪽에 둔다. [주의] 처음 만들 때 준 위치는 macOS가 Dock을 피해 작업 영역 안으로 밀어 올리므로 띄운 뒤 다시 준다.
     * 창은 Dock보다 아래 레벨이고 빈 곳은 클릭이 통과해서 Dock을 가리지 않는다. 포키를 Dock 위로 올리는 건 창 안 CSS가 한다(lift, #225).
     */
    function pinFloating(window: BrowserWindow): void {
      const area = screen.getPrimaryDisplay().bounds
      window.setBounds({ ...FLOATING_SIZE, x: area.x + area.width - FLOATING_SIZE.width, y: area.y + area.height - FLOATING_SIZE.height })
      sendLift(window)
    }
    /** Dock이 보이면 Dock 높이만큼, 전체 화면 앱이라 Dock이 숨겨지면 0만큼 포키를 올린다. Dock 자동 숨기기면 둘 다 0이다. */
    function sendLift(window: BrowserWindow): void {
      const display = screen.getPrimaryDisplay()
      const dock = display.bounds.y + display.bounds.height - (display.workArea.y + display.workArea.height)
      window.webContents.send(INTERVENTION_CHANNELS.lift, frontFullScreen ? 0 : Math.max(0, dock))
    }
    /** 맨 앞 창의 전체 화면 여부를 다시 보고, 바뀌었으면 떠 있는 포키 자리를 옮긴다. */
    let placing = false
    async function refreshPlacement(): Promise<void> {
      if (!helper || placing) return
      placing = true
      try {
        // 도우미가 답하지 못하면(null) 전체 화면 여부를 모르는 것이라 지금 자리를 그대로 둔다.
        const front = parseFrontmost(await helper.request('frontmost', { timeoutMs: 500 }))
        if (!front) return
        const fullScreen = front.fullScreen === true
        if (fullScreen === frontFullScreen) return
        frontFullScreen = fullScreen
        if (pendingShow && floatingWindow && !floatingWindow.isDestroyed()) sendLift(floatingWindow)
      } finally {
        placing = false
      }
    }
    /**
     * F07: 실행 중이던 세션을 서버가 Chrome 쪽 이유로 멈췄을 때만 포키로 알린다(floating-poki.html disconnected).
     * 화면 기록 권한은 Desktop이 직접 멈추고 집중 화면이 안내하므로 띄우지 않는다. 이유를 모르면 추측하지 않고 띄우지 않는다.
     */
    function disconnectedNotice(prev: SessionSnapshot | null, next: SessionSnapshot | null): FloatingShow | null {
      if (prev?.status !== 'RUNNING' || next?.status !== 'AUTO_PAUSED' || next.sessionId !== prev.sessionId) return null
      const base = { interventionId: DISCONNECTED_ID, kind: 'DISCONNECTED' as const, level: 'NORMAL' as const, goalText: next.goalText }
      if (next.pauseReason === 'EXTENSION_UNLINKED') return { ...base, say: 'Chrome 연결이 끊겼어요.', sub: '집중을 잠시 멈췄어요.\n앱에서 다시 연결해 주세요.' }
      if (next.pauseReason === 'REQUIRED_PERMISSION' && next.error === null) return { ...base, say: '사이트 접근이 꺼졌어요.', sub: '집중을 잠시 멈췄어요.\nChrome에서 다시 허용해 주세요.' }
      return null
    }
    function hideFloating(): void {
      pendingShow = null
      if (floatingWindow && !floatingWindow.isDestroyed()) { floatingWindow.setIgnoreMouseEvents(true, { forward: true }); floatingWindow.hide() }
    }
    const watcher = authService && runtime ? new InterventionWatcher({
      auth: authService,
      show: (payload) => { showFloating(payload); return true },
      notify: (payload, sound) => {
        if (!Notification.isSupported()) return false
        new Notification({ title: 'FocusOn', body: `${payload.say} ${payload.sub}`, silent: !sound }).show()
        return true
      },
      pause: () => runtime.pause('USER'),
      isCurrent: (sessionId) => { const current = runtime.snapshot(); return current?.status === 'RUNNING' && current.sessionId === sessionId },
      currentContext: () => context?.currentId() ?? null,
      journalFile: path.join(app.getPath('userData'), 'intervention-receipts.json'),
    }) : null
    // 시연 모드(#189): `pnpm dev:demo`로 실행할 때만 켠다(빌드한 화면을 써서 Next 개발 표시가 영상에 안 나온다). 배포 앱은 환경 변수가 있어도 켜지 않는다.
    if (authService && runtime && process.env.FOCUSON_DEMO === '1' && !app.isPackaged) {
      const demo = installDemoMode(authService, () => runtime.snapshot(), () => context?.currentId() ?? null)
      // ⌘⇧D: 진행 중 세션에 이탈 알림을 만들고 10초 조회를 기다리지 않고 바로 보여 준다.
      globalShortcut.register('CommandOrControl+Shift+D', () => {
        const current = runtime.snapshot()
        if (current && demo.triggerAlert()) void watcher?.poll(current.sessionId, current.goalText, runtime.sessionOptions())
      })
      app.on('will-quit', () => globalShortcut.unregisterAll())
    }
    ipcMain.on(INTERVENTION_CHANNELS.interactive, (event, interactive: unknown) => {
      // 포키 창이 보낸 boolean만 받는다. 다른 창이 클릭 통과를 바꾸지 못하게 한다.
      if (!floatingWindow || floatingWindow.isDestroyed() || event.sender !== floatingWindow.webContents || typeof interactive !== 'boolean') return
      floatingWindow.setIgnoreMouseEvents(!interactive, { forward: true })
    })
    ipcMain.handle(INTERVENTION_CHANNELS.act, async (event, ...args: unknown[]) => {
      if (args.length !== 1 || !isActInput(args[0]) || !isTrustedAuthSender(event, floatingWindow, rendererUrl)) return { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } }
      const { interventionId, action } = args[0]
      // F07은 서버 알림이 아니라 기록하지 않는다. 앱 열기는 FocusOn 창을 앞으로 가져온다.
      if (interventionId === DISCONNECTED_ID) {
        hideFloating()
        // [주의] macOS에서 창 focus()만으로는 비활성 앱이 앞으로 오지 않는다. 사용자가 누른 버튼이라 앱을 직접 활성화한다.
        if (action === 'OPEN_APP') { app.focus({ steal: true }); showMainWindow() }
        return { success: true, data: { done: true } }
      }
      // 카메라 알림도 서버 기록이 없다. 잠시 쉬기는 세션만 멈춘다.
      const done = interventionId === CAMERA_ID
        ? action !== 'REST' || (await runtime?.pause('USER').catch(() => null))?.success === true
        : await watcher?.act(interventionId, action).catch(() => action !== 'REST') ?? true
      // 잠시 쉬기에서 일시정지에 실패하면 창을 그대로 두고 포키 창이 안내한다. 확인했어는 같은 창에서 목표 안내로 이어진다.
      if (!done) return { success: false, error: { code: 'SESSION_PAUSE_FAILED', retryable: true } }
      if (action !== 'RECENTER') hideFloating()
      return { success: true, data: { done: true } }
    })
    let watchedSession: string | null = null

    // [유지보수] 창을 숨겨도 Main에서 목표 시간 도달(1초)과 서버 생존 신호(10초, 학습 세션 설계서·세션 상태 전이 정책)를 계속 처리한다.
    if (runtime) {
      setInterval(() => {
        runtime.tick()
        // 세션이 RUNNING일 때만 맨 앞 앱을 본다(결정 024). 멈추면 15초 계산을 처음부터 다시 한다.
        const session = runtime.snapshot()
        if (session?.status !== 'RUNNING') { observer.reset(); context?.reset(); return }
        if (!helper || observing) return
        observing = true
        void helper.request('frontmost')
          .then((reply) => {
            // [주의] 확인하는 사이 세션이 멈췄거나 바뀌었으면 그 결과는 버린다. 멈춘 동안의 앱 정보를 보내지 않는다.
            const current = runtime.snapshot()
            if (current?.status !== 'RUNNING' || current.sessionId !== session.sessionId) return
            const front = parseFrontmost(reply)
            const fullScreen = front?.fullScreen === true
            if (front && fullScreen !== frontFullScreen) {
              frontFullScreen = fullScreen
              if (pendingShow && floatingWindow && !floatingWindow.isDestroyed()) sendLift(floatingWindow)
            }
            // 맨 앞 앱이 바뀌면 15초를 기다리지 않고 바로 전경 context를 바꾼다. Chrome은 Extension이 관측을 맡는다.
            const foreground = front?.bundleId ? `${front.bundleId}\n${front.windowTitle ?? ''}` : null
            void context?.update(session.sessionId, foreground, !foreground ? 'NONE' : CHROME.has(front?.bundleId ?? '') ? 'EXTENSION' : 'DESKTOP')
            const observation = observer.observe(front)
            if (!observation || !sender) return
            // 관측한 시점의 context를 분석 요청에 붙인다. 화면이 바뀌는 중이면 붙이지 않는다(서버가 정책에 쓰지 않음).
            const observedContext = context?.currentId() ?? null
            void analyzeObservation({
              send: async (sessionId, next) => {
                const run = await sender.send(sessionId, next, observedContext)
                // 카메라 최종 게이트용으로 지금 화면의 최신 판정만 기억한다(설계서 5.3). 보호 상태는 판정 없이도 PRIVACY_BLOCKED다.
                const relation = next.guardStatus === 'PRIVACY_BLOCKED' ? 'PRIVACY_BLOCKED' : run?.relation
                const key = observer.currentKey()
                if (relation && key) latestScreen = { key, relation }
                return run
              },
              // 창 캡처·Vision OCR은 몇 초 걸릴 수 있다. 글자만 받고 이미지는 도우미 안에서 버린다.
              ocr: async (windowId) => {
                const result = await helper.request('ocr', { windowId, timeoutMs: 5000 })
                return typeof result?.text === 'string' ? result.text : null
              },
              stillShowing: (key, sessionId) => {
                const current = runtime.snapshot()
                return observer.currentKey() === key && current?.status === 'RUNNING' && current.sessionId === sessionId
              },
              now: () => new Date(),
            }, session.sessionId, observation)
          })
          .finally(() => { observing = false })
      }, 1000)
      // 포키가 떠 있는 동안에만 0.25초마다 전체 화면 여부를 본다. 화면을 바꾼 직후 자리를 빨리 맞춘다(#225).
      setInterval(() => { if (pendingShow && floatingWindow?.isVisible()) void refreshPlacement() }, 250)
      // 카메라는 2초마다 한 번 본다(설계서 7.3 샘플링). RUNNING·카메라 켬·권한 허용·보호 화면 아님일 때만 켜고, 아니면 바로 해제한다.
      setInterval(() => {
        const session = runtime.snapshot()
        const screenNow = latestScreen && latestScreen.key === observer.currentKey() ? latestScreen.relation : null
        const wanted = !!helper && session?.status === 'RUNNING' && runtime.sessionOptions().cameraAnalysisEnabled
          && mediaStatus('camera') === 'granted' && screenNow !== 'PRIVACY_BLOCKED'
        if (!wanted) {
          if (cameraOn) {
            cameraOn = false
            log('INFO', 'state.transition', { component: 'camera', from: cameraMonitor.current(), to: 'CAMERA_PAUSED' })
            cameraMonitor.reset()
            void helper?.request('cameraStop')
          }
          return
        }
        if (cameraBusy || !helper) return
        cameraBusy = true
        cameraOn = true
        void helper.request('camera', { timeoutMs: 1500 }).then((reply) => {
          const current = runtime.snapshot()
          if (current?.status !== 'RUNNING' || current.sessionId !== session?.sessionId || !cameraOn) return
          const before = cameraMonitor.current()
          const { state, alert } = cameraMonitor.update(parseCameraReply(reply), { screenRelated: screenNow === 'RELATED', canShow: pendingShow === null })
          // 상태가 바뀔 때만 남긴다. 얼굴·시선 값은 남기지 않는다.
          if (state !== before) log('INFO', 'state.transition', { component: 'camera', from: before, to: state })
          if (!alert) return
          const level = runtime.sessionOptions().interventionLevel
          showFloating({ interventionId: CAMERA_ID, kind: 'OFF_TASK', level, goalText: current.goalText, ...COPY[level] })
          log('INFO', 'notify.fire', { component: 'camera', interventionType: 'CAMERA_AWAY', channels: ['MOLE_UI'] })
        }).finally(() => { cameraBusy = false })
      }, 2000)
      setInterval(() => {
        // 세션 중 화면 기록 권한이 꺼지면 바로 멈춘다(Notion 화면 분석 7.2).
        if (runtime.snapshot()?.status === 'RUNNING' && mediaStatus('screen') !== 'granted') runtime.screenPermissionLost()
        // 확정된 전경 context가 있을 때만 heartbeat에 붙여 lease(30초)를 이어 간다.
        const heartbeatContext = context?.currentId() ?? null
        void runtime.heartbeat(heartbeatContext).then((ok) => { if (ok && heartbeatContext) context?.renewed(heartbeatContext) })
        // 이탈 알림은 heartbeat와 같은 10초 주기로 조회한다(Notion interventions 비고). 진행 중이 아니면 포키를 숨긴다.
        const current = runtime.snapshot()
        // 세션이 바뀌면 이전 세션의 포키를 닫는다. 그 알림으로 새 세션을 멈추지 않게 한다.
        if (current?.sessionId !== watchedSession) { watchedSession = current?.sessionId ?? null; watcher?.reset(); hideFloating() }
        if (current?.status === 'RUNNING') void watcher?.poll(current.sessionId, current.goalText, runtime.sessionOptions())
        else hideFloating()
      }, 10_000)
    }
    createMainWindow()
    void authService?.restore()
  })
}
