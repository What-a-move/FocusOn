import { contextBridge, ipcRenderer } from 'electron'
import { AUTH_CHANNELS, parseAuthSessionSnapshot } from '../shared/auth-contract.cjs'
import type { AuthSessionSnapshot, FocusOnApi } from '../shared/auth-contract.cjs'
import { PERMISSION_CHANNELS } from '../shared/permission-contract.cjs'
import { ONBOARDING_CHANNELS } from '../shared/onboarding-contract.cjs'
import { REPORT_CHANNELS } from '../shared/report-contract.cjs'
import { GOAL_CHANNELS } from '../shared/goal-contract.cjs'
import { SESSION_CHANNELS, isSessionSnapshot } from '../shared/session-contract.cjs'
import { ANALYSIS_CHANNELS } from '../shared/analysis-contract.cjs'
import { SETTINGS_CHANNELS } from '../shared/settings-contract.cjs'
import { INTERVENTION_CHANNELS, isFloatingShow, type FloatingShow } from '../shared/intervention-contract.cjs'

// 포키 창이 구독하기 전에 첫 알림이 도착해도 잃지 않도록 마지막 알림을 보관했다가 구독할 때 다시 전달한다.
let lastShow: FloatingShow | null = null
const showListeners = new Set<(show: FloatingShow | null) => void>()
ipcRenderer.on(INTERVENTION_CHANNELS.show, (_event, payload: unknown) => {
  if (payload !== null && !isFloatingShow(payload)) return
  lastShow = payload
  showListeners.forEach((listener) => listener(payload))
})
let lastLift = 0
const liftListeners = new Set<(px: number) => void>()
ipcRenderer.on(INTERVENTION_CHANNELS.lift, (_event, px: unknown) => {
  if (typeof px !== 'number' || !Number.isFinite(px) || px < 0 || px > 400) return
  lastLift = px
  liftListeners.forEach((listener) => listener(px))
})

// [주의] 채널 문자열이나 ipcRenderer 자체를 Renderer에 넘기면 허용하지 않은 기능도 호출할 수 있다.
const focusOnApi: FocusOnApi = {
  auth: {
    getSession: () => ipcRenderer.invoke(AUTH_CHANNELS.getSession),
    retrySession: () => ipcRenderer.invoke(AUTH_CHANNELS.retrySession),
    startGoogleLogin: () => ipcRenderer.invoke(AUTH_CHANNELS.startGoogleLogin),
    logout: () => ipcRenderer.invoke(AUTH_CHANNELS.logout),
    onSessionChanged(callback: (snapshot: AuthSessionSnapshot) => void): () => void {
      const listener = (_event: Electron.IpcRendererEvent, payload: unknown): void => {
        const snapshot = parseAuthSessionSnapshot(payload)
        if (snapshot) callback(snapshot)
      }

      ipcRenderer.on(AUTH_CHANNELS.sessionChanged, listener)
      // [유지보수] React Effect가 정리될 때 Listener도 제거해 중복 알림을 막는다.
      return () => ipcRenderer.removeListener(AUTH_CHANNELS.sessionChanged, listener)
    },
  },
  permissions: {
    getScreenStatus: () => ipcRenderer.invoke(PERMISSION_CHANNELS.screenStatus),
    openScreenSettings: () => ipcRenderer.invoke(PERMISSION_CHANNELS.screenOpenSettings),
    getCameraStatus: () => ipcRenderer.invoke(PERMISSION_CHANNELS.cameraStatus),
    requestCamera: () => ipcRenderer.invoke(PERMISSION_CHANNELS.cameraRequest),
    openCameraSettings: () => ipcRenderer.invoke(PERMISSION_CHANNELS.cameraOpenSettings),
    getCameraIssue: () => ipcRenderer.invoke(PERMISSION_CHANNELS.cameraIssue),
    clearCameraIssue: () => ipcRenderer.invoke(PERMISSION_CHANNELS.cameraIssueClear),
    showNotification: (input) => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationShow, { titleCode: input.titleCode, bodyCode: input.bodyCode }),
    openNotificationSettings: () => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationOpenSettings),
    getNotificationStatus: () => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationStatus),
    confirmNotification: (input) => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationConfirm, { seen: input.seen }),
    relaunch: () => ipcRenderer.invoke(PERMISSION_CHANNELS.relaunch),
  },
  // [주의] 입력은 허용한 필드만 다시 만들어 보낸다. Main이 한 번 더 검증한다.
  onboarding: {
    getReadiness: () => ipcRenderer.invoke(ONBOARDING_CHANNELS.readiness),
    saveProfile: (input) => ipcRenderer.invoke(ONBOARDING_CHANNELS.saveProfile, { nickname: input.nickname }),
    issuePairingCode: () => ipcRenderer.invoke(ONBOARDING_CHANNELS.issuePairingCode),
    savePermissionsStep: (input) => ipcRenderer.invoke(ONBOARDING_CHANNELS.savePermissionsStep, { consents: input.consents, cameraConsent: input.cameraConsent }),
    saveExcludedDomains: (input) => ipcRenderer.invoke(ONBOARDING_CHANNELS.saveExcludedDomains, { domains: input.domains }),
    getSummary: () => ipcRenderer.invoke(ONBOARDING_CHANNELS.summary),
  },
  reports: {
    getProgress: (input) => ipcRenderer.invoke(REPORT_CHANNELS.progress, { date: input.date }),
    getCalendar: (input) => ipcRenderer.invoke(REPORT_CHANNELS.calendar, { month: input.month }),
    getDaily: (input) => ipcRenderer.invoke(REPORT_CHANNELS.daily, { date: input.date }),
    saveRetrospective: ({ sessionId, rating, reasonCodes, note }) =>
      ipcRenderer.invoke(REPORT_CHANNELS.retrospective, { sessionId, rating, reasonCodes, note }),
    getSessionSummary: (input) => ipcRenderer.invoke(REPORT_CHANNELS.sessionSummary, { sessionId: input.sessionId }),
    getSessionPauses: (input) => ipcRenderer.invoke(REPORT_CHANNELS.sessionPauses, { sessionId: input.sessionId }),
    getLearningSummary: (input) => ipcRenderer.invoke(REPORT_CHANNELS.learningSummary, { sessionId: input.sessionId }),
    listLearningNotes: (input) => ipcRenderer.invoke(REPORT_CHANNELS.learningNotes, { cursor: input.cursor }),
  },
  goals: {
    clarify: (input) => ipcRenderer.invoke(GOAL_CHANNELS.clarify, {
      goalText: input.goalText,
      conversationTurn: input.conversationTurn,
      ...(input.clarificationSessionId ? { clarificationSessionId: input.clarificationSessionId } : {}),
      previousAnswers: input.previousAnswers.map(({ question, answer }) => ({ question, answer })),
    }),
    create: (input) => ipcRenderer.invoke(GOAL_CHANNELS.create, {
      goalText: input.goalText,
      confirmationSource: input.confirmationSource,
      ...(input.clarificationSessionId ? { clarificationSessionId: input.clarificationSessionId } : {}),
      ...(input.goalProfile ? { goalProfile: input.goalProfile } : {}),
      ...(input.idempotencyKey ? { idempotencyKey: input.idempotencyKey } : {}),
    }),
    listOpen: (cursor) => ipcRenderer.invoke(GOAL_CHANNELS.listOpen, cursor ?? null),
    remove: (goalId) => ipcRenderer.invoke(GOAL_CHANNELS.remove, goalId),
    complete: (goalId) => ipcRenderer.invoke(GOAL_CHANNELS.complete, goalId),
    updateProfile: (input) => ipcRenderer.invoke(GOAL_CHANNELS.updateProfile, { goalId: input.goalId, goalProfile: input.goalProfile }),
    regenerateProfile: (goalId) => ipcRenderer.invoke(GOAL_CHANNELS.regenerateProfile, goalId),
  },
  sessions: {
    getFocusSettings: () => ipcRenderer.invoke(SESSION_CHANNELS.focusSettings),
    start: (input) => ipcRenderer.invoke(SESSION_CHANNELS.start, {
      ...(input.goalIds ? { goalIds: [...input.goalIds] } : { goalId: input.goalId }),
      targetDurationMs: input.targetDurationMs,
      timeZone: input.timeZone,
      sessionOptions: {
        interventionLevel: input.sessionOptions.interventionLevel,
        notificationChannels: [...input.sessionOptions.notificationChannels],
        cameraAnalysisEnabled: input.sessionOptions.cameraAnalysisEnabled,
      },
      idempotencyKey: input.idempotencyKey,
      goalText: input.goalText,
    }),
    addGoal: (input) => ipcRenderer.invoke(SESSION_CHANNELS.addGoal, { sessionId: input.sessionId, goalId: input.goalId, goalVersion: input.goalVersion, ...(input.replaceGoalId === undefined ? {} : { replaceGoalId: input.replaceGoalId }) }),
    getCurrent: () => ipcRenderer.invoke(SESSION_CHANNELS.current),
    onChanged(callback) {
      const listener = (_event: Electron.IpcRendererEvent, payload: unknown): void => { callback(isSessionSnapshot(payload) ? payload : null) }
      ipcRenderer.on(SESSION_CHANNELS.changed, listener)
      return () => ipcRenderer.removeListener(SESSION_CHANNELS.changed, listener)
    },
    pause: () => ipcRenderer.invoke(SESSION_CHANNELS.pause),
    resume: () => ipcRenderer.invoke(SESSION_CHANNELS.resume),
    extend: (input) => ipcRenderer.invoke(SESSION_CHANNELS.extend, { minutes: input.minutes }),
    end: () => ipcRenderer.invoke(SESSION_CHANNELS.end),
  },
  analysis: {
    listInstalledApps: () => ipcRenderer.invoke(ANALYSIS_CHANNELS.installedApps),
    getExcludedApps: () => ipcRenderer.invoke(ANALYSIS_CHANNELS.excludedAppsGet),
    saveExcludedApps: (input) => ipcRenderer.invoke(ANALYSIS_CHANNELS.excludedAppsSave, { apps: input.apps.map(({ bundleId, name }) => ({ bundleId, name })) }),
  },
  settings: {
    saveFocusSettings: (input) => ipcRenderer.invoke(SETTINGS_CHANNELS.saveFocusSettings, {
      interventionLevel: input.interventionLevel,
      notificationChannels: [...input.notificationChannels],
      cameraAnalysisEnabled: input.cameraAnalysisEnabled,
      breakSuggestionEnabled: input.breakSuggestionEnabled,
      ...(input.dailyTargetMs === undefined ? {} : { dailyTargetMs: input.dailyTargetMs }),
      ...(input.weeklyTargetMs === undefined ? {} : { weeklyTargetMs: input.weeklyTargetMs }),
      ...(input.retrospectiveEnabled === undefined ? {} : { retrospectiveEnabled: input.retrospectiveEnabled }),
    }),
    getExcludedDomains: () => ipcRenderer.invoke(SETTINGS_CHANNELS.excludedDomainsGet),
    saveExcludedDomains: (input) => ipcRenderer.invoke(SETTINGS_CHANNELS.excludedDomainsSave, { domains: [...input.domains] }),
    getTheme: () => ipcRenderer.invoke(SETTINGS_CHANNELS.themeGet),
    setTheme: (input) => ipcRenderer.invoke(SETTINGS_CHANNELS.themeSet, { theme: input.theme }),
    getConsents: () => ipcRenderer.invoke(SETTINGS_CHANNELS.consentsGet),
    setScreenConsent: (input) => ipcRenderer.invoke(SETTINGS_CHANNELS.screenConsentSet, { granted: input.granted }),
    setCameraConsent: (input) => ipcRenderer.invoke(SETTINGS_CHANNELS.cameraConsentSet, { granted: input.granted }),
    getDevices: () => ipcRenderer.invoke(SETTINGS_CHANNELS.devicesGet),
    unlinkExtension: (input) => ipcRenderer.invoke(SETTINGS_CHANNELS.extensionUnlink, { deviceId: input.deviceId }),
  },
  interventions: {
    onShow(callback) {
      showListeners.add(callback)
      if (lastShow) callback(lastShow)
      return () => { showListeners.delete(callback) }
    },
    act(input) {
      return ipcRenderer.invoke(INTERVENTION_CHANNELS.act, { interventionId: input.interventionId, action: input.action })
    },
    onLift(callback) {
      liftListeners.add(callback)
      callback(lastLift)
      return () => { liftListeners.delete(callback) }
    },
    setInteractive(interactive) {
      ipcRenderer.send(INTERVENTION_CHANNELS.interactive, interactive === true)
    },
  },
}

contextBridge.exposeInMainWorld('focusOn', focusOnApi)
