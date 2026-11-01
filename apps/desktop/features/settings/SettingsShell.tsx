'use client'

import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useRouter } from 'next/navigation'
import { AppSidebar } from '../dashboard/AppSidebar'
import { errorText } from '../../app/lib/error-text'
import './settings.css'

/** 설정 하위 화면의 틀. 홈과 같은 사이드바를 두고, 로그인이 풀리면 첫 화면(로그인)으로 돌려보낸다. */
export function SettingsShell({ children }: { children: ReactNode }) {
  const router = useRouter()
  const [name, setName] = useState('FocusOn 사용자')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const auth = window.focusOn?.auth
    if (!auth) return
    const apply = (status: string, nickname: string | null | undefined) => {
      // [주의] 오프라인·재연결 필요일 때는 설정을 계속 보여 준다. 로그아웃·만료일 때만 로그인 화면으로 보낸다.
      if (status === 'SIGNED_OUT' || status === 'EXPIRED') router.replace('/')
      else if (nickname) setName(nickname)
    }
    void auth.getSession().then((result) => { if (result.success) apply(result.data.status, result.data.user?.nickname) })
    return auth.onSessionChanged((snapshot) => apply(snapshot.status, snapshot.user?.nickname))
  }, [router])

  async function logout() {
    const auth = window.focusOn?.auth
    if (!auth) return
    setBusy(true)
    await auth.logout()
    setBusy(false)
    router.replace('/')
  }

  return <main className="fo-home-window">
    <AppSidebar active="settings" name={name} onLogout={() => { void logout() }} busy={busy} />
    <div className="fo-settings">{children}</div>
  </main>
}

export type Toast = { text: string; undo?: () => void }

/** 아래쪽 안내 한 줄. 되돌리기가 있으면 5초, 없으면 2초 보인다 (exclusions.html 토스트). */
export function useToast(): [Toast | null, (toast: Toast) => void, () => void] {
  const [toast, setToast] = useState<Toast | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const hide = useCallback(() => { clearTimeout(timer.current); setToast(null) }, [])
  const show = useCallback((next: Toast) => {
    clearTimeout(timer.current)
    setToast(next)
    timer.current = setTimeout(() => setToast(null), next.undo ? 5000 : 2000)
  }, [])
  useEffect(() => () => clearTimeout(timer.current), [])
  return [toast, show, hide]
}

export function ToastView({ toast, onHide }: { toast: Toast | null; onHide: () => void }) {
  return <div className={`fo-toast${toast ? ' is-visible' : ''}`} role="status" aria-live="polite">
    <span>{toast?.text}</span>
    {toast?.undo && <button type="button" onClick={() => { toast.undo?.(); onHide() }}>되돌리기</button>}
  </div>
}

/** 처음 불러오기 실패. 제목·할 일·다시 시도를 한 상자에 둔다 (홈 달력 오류와 같은 모양). */
export function LoadError({ code, what, onRetry }: { code: string; what: string; onRetry: () => void }) {
  return <div className="fo-notice fo-load-error" role="alert">
    <strong>{what} 불러오지 못했어요.</strong>
    <span>{errorText({ code })}</span>
    <button className="fo-small-btn" type="button" onClick={onRetry}>다시 시도</button>
  </div>
}

/** 저장 실패 토스트. 무엇을 못 했는지 + 원인·할 일. */
export function failText(code: string, action = '저장하지'): string {
  return `${action} 못했어요. ${errorText({ code })}`
}
