import type { FocusOnApi } from '../../electron/shared/auth-contract.cjs'

declare global {
  interface Window {
    focusOn?: FocusOnApi
  }
}

export {}
