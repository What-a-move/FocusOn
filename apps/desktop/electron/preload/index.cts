import { contextBridge, ipcRenderer } from 'electron'
import { AUTH_CHANNELS, parseAuthSessionSnapshot } from '../shared/auth-contract.cjs'
import type { AuthSessionSnapshot, FocusOnApi } from '../shared/auth-contract.cjs'
import { PERMISSION_CHANNELS } from '../shared/permission-contract.cjs'
import { ONBOARDING_CHANNELS } from '../shared/onboarding-contract.cjs'
import { REPORT_CHANNELS } from '../shared/report-contract.cjs'
import { GOAL_CHANNELS } from '../shared/goal-contract.cjs'
import { SESSION_CHANNELS, isSessionSnapshot } from '../shared/session-contract.cjs'
import { ANALYSIS_CHANNELS } from '../shared/analysis-contract.cjs'

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
    showNotification: (input) => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationShow, { titleCode: input.titleCode, bodyCode: input.bodyCode }),
    openNotificationSettings: () => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationOpenSettings),
    getNotificationStatus: () => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationStatus),
    confirmNotification: (input) => ipcRenderer.invoke(PERMISSION_CHANNELS.notificationConfirm, { seen: input.seen }),
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
    getCalendar: (input) => ipcRenderer.invoke(REPORT_CHANNELS.calendar, { month: input.month }),
    getDaily: (input) => ipcRenderer.invoke(REPORT_CHANNELS.daily, { date: input.date }),
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
    }),
  },
  sessions: {
    getFocusSettings: () => ipcRenderer.invoke(SESSION_CHANNELS.focusSettings),
    start: (input) => ipcRenderer.invoke(SESSION_CHANNELS.start, {
      goalId: input.goalId,
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
}

contextBridge.exposeInMainWorld('focusOn', focusOnApi)
