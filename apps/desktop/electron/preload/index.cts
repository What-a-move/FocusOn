import { contextBridge } from 'electron'

// [주의] Renderer에는 기능별 허용 API만 추가한다. Electron 전체 객체나 범용 IPC는 노출하지 않는다.
contextBridge.exposeInMainWorld('focusOnDesktop', Object.freeze({ platform: 'macos' }))
