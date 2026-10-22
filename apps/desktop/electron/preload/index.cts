import { contextBridge, ipcRenderer } from 'electron'
import { AUTH_CHANNELS, parseAuthSessionSnapshot } from '../shared/auth-contract.cjs'
import type { AuthSessionSnapshot, FocusOnApi } from '../shared/auth-contract.cjs'

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
}

contextBridge.exposeInMainWorld('focusOn', focusOnApi)
