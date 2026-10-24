import { contextBridge, ipcRenderer } from 'electron'
import { AUTH_CHANNELS, parseAuthSessionSnapshot } from '../shared/auth-contract.cjs'
import type { AuthSessionSnapshot, FocusOnApi } from '../shared/auth-contract.cjs'
import { PERMISSION_CHANNELS } from '../shared/permission-contract.cjs'
import { ONBOARDING_CHANNELS } from '../shared/onboarding-contract.cjs'
import { REPORT_CHANNELS } from '../shared/report-contract.cjs'

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
}

contextBridge.exposeInMainWorld('focusOn', focusOnApi)
