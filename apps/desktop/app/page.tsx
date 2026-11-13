'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import type { AuthSessionSnapshot, IpcError } from '../electron/shared/auth-contract.cjs'
import { selectAuthView } from '../shared/lib/auth-view'
import { errorText } from '../shared/lib/error-text'
import { onboardingApi, STEP_ROUTES } from '../shared/api/onboarding-api'
import { HomeDashboard } from '../features/dashboard/HomeDashboard'

const signedOut: AuthSessionSnapshot = {
  revision: 0, status: 'SIGNED_OUT', user: null, onboardingStatus: null, error: null,
}

function Logo({ className = '' }: { className?: string }) {
  return <span className={`fo-logo ${className}`}>
    <Image className="fo-logo-light" src="/design/focuson-logo.png" alt="FocusOn" width={2172} height={724} priority />
    <Image className="fo-logo-dark" src="/design/focuson-logo-dark.png" alt="FocusOn" width={2172} height={724} priority />
  </span>
}

type AuthAction = 'login' | 'retry' | 'logout'
// 오류 제목은 그 오류가 생길 때 진행 중이던 동작 기준이다. 로그아웃 정리가 실패해도 로그인 화면으로 오기 때문이다.
// 동작과 상관없이(만료 등) 생긴 오류는 로그인 기준으로 본다.
const FAILED_TITLE: Record<AuthAction, string> = {
  login: '로그인하지 못했어요.',
  retry: '로그인 상태를 확인하지 못했어요.',
  logout: '로그아웃하지 못했어요.',
}

function Alert({ message, action }: { message: string | null; action: AuthAction }) {
  if (!message) return null
  return <div className="fo-notice" role="alert"><strong>{FAILED_TITLE[action]}</strong><span>{message}</span></div>
}

// [유지보수] 앱을 켤 때만 로고 화면을 잠깐 보여 준다. 로그인 확인이 빨리 끝나도 이 시간은 채운다(#244).
// [주의] 홈으로 다시 올 때 페이지를 새로 불러오면 모듈 변수가 초기화된다. 창이 살아 있는 동안 남는 sessionStorage에 기억하고,
// 앱을 다시 켜면(새 창) 다시 보인다.
const INTRO_MS = 1800
const INTRO_KEY = 'focuson.introShown'

function introAlreadyShown(): boolean {
  try { return sessionStorage.getItem(INTRO_KEY) === '1' } catch { return false }
}

function LoadingScreen() {
  return <main className="fo-loading" role="status" aria-label="FocusOn 시작 중">
    <Image src="/design/poki-floating-logo-base-v1.png" alt="" width={1254} height={1254} priority />
    <Logo />
  </main>
}

function LoginScreen({ onLogin, onRetry, busy, authenticating, message, action, available, offline }: {
  onLogin: () => void; onRetry: () => void; busy: boolean; authenticating: boolean; message: string | null; action: AuthAction; available: boolean; offline: boolean
}) {
  return <main className="fo-auth-window">
    <div className="fo-auth-brand"><Logo /></div>
    <div className="fo-auth-content">
      <section className="fo-auth-copy" aria-labelledby="login-title">
        <h1 id="login-title">오늘도 함께<br />집중해 볼까요?</h1>
        <p className="fo-auth-sub">목표 하나만 정하면 포키가 곁에서 도와드려요.</p>
        <button className="fo-google-button" type="button" onClick={offline ? onRetry : onLogin} disabled={busy || !available}>
          <Image src="/design/google-mark.svg" alt="" width={22} height={22} />
          <span>{busy ? '연결하는 중…' : offline ? '연결 다시 확인하기' : authenticating ? 'Google 로그인 다시 열기' : 'Google로 계속하기'}</span>
        </button>
        <div className="fo-login-state" aria-live="polite">
          {offline ? <div className="fo-notice" role="alert"><strong>로그인 상태를 확인하지 못했어요.</strong><span>{message ?? errorText({ code: 'LOCAL_NETWORK_UNAVAILABLE' })}</span></div> : <Alert message={message} action={action} />}
          {(busy || authenticating) && <div className="fo-notice is-info" role="status"><strong>Google 계정을 연결하고 있어요.</strong><span>브라우저에서 인증을 마친 뒤 앱으로 돌아와 주세요.</span></div>}
          {!available && <p className="fo-app-only">Google 로그인은 FocusOn 데스크톱 앱에서 사용할 수 있어요.</p>}
        </div>
        <p className="fo-legal"><span>이용약관 · 개인정보 처리방침</span><span>화면 원본과 카메라 영상은 저장하지 않아요.</span></p>
      </section>
      <div className="fo-auth-poki"><Image src="/design/poki-login-wave.png" alt="손을 흔들어 인사하는 포키" width={1402} height={1122} priority /></div>
    </div>
  </main>
}

// [유지보수] 온보딩 화면은 /onboarding/ 경로에 있다. 서버 onboarding-readiness의 currentStep으로 이어갈 단계를 정한다.
// 조회에 실패하면 첫 단계(재연결이면 Extension 단계)로 보낸다. 각 단계 저장은 서버가 다시 검증한다.
function OnboardingRedirect({ reconnect }: { reconnect: boolean }) {
  const router = useRouter()
  useEffect(() => {
    void onboardingApi.getReadiness().then((result) => {
      router.replace(result.success ? STEP_ROUTES[result.data.currentStep] : reconnect ? '/onboarding/extension/' : '/onboarding/')
    })
  }, [reconnect, router])
  return <LoadingScreen />
}

export default function Page() {
  const [snapshot, setSnapshot] = useState<AuthSessionSnapshot | null>(null)
  const [requestError, setRequestError] = useState<IpcError | null>(null)
  const [busy, setBusy] = useState(false)
  // 진행 중인 동작. 그동안 도착한 Snapshot 오류와 요청 오류에 이 동작을 붙인다.
  const running = useRef<AuthAction | null>(null)
  const [action, setAction] = useState<AuthAction>('login')
  const [available, setAvailable] = useState(true)
  // null: 아직 모름(첫 그림은 빈 화면) · true: 로고를 보여 주는 중 · false: 로고 없이 진행
  const [intro, setIntro] = useState<boolean | null>(null)

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    queueMicrotask(() => {
      if (introAlreadyShown()) { setIntro(false); return }
      try { sessionStorage.setItem(INTRO_KEY, '1') } catch { /* 저장 못 해도 이번에는 보여 준다 */ }
      setIntro(true)
      timer = setTimeout(() => setIntro(false), INTRO_MS)
    })
    return () => clearTimeout(timer)
  }, [])

  useEffect(() => {
    let active = true
    const auth = window.focusOn?.auth
    if (!auth) {
      queueMicrotask(() => { if (active) { setAvailable(false); setSnapshot(signedOut) } })
      return () => { active = false }
    }
    // [주의] Event를 먼저 구독하고 Snapshot을 조회한다. 늦게 온 이전 revision은 버린다.
    const applySnapshot = (next: AuthSessionSnapshot) => {
      if (!active) return
      setSnapshot((current) => !current || next.revision > current.revision ? next : current)
      setRequestError(null)
      setAction(running.current ?? 'login')
    }
    const unsubscribe = auth.onSessionChanged(applySnapshot)
    void auth.getSession().then((result) => {
      if (!active) return
      if (result.success) applySnapshot(result.data)
      else setSnapshot({ ...signedOut, error: result.error })
    }).catch(() => { if (active) setSnapshot({ ...signedOut, error: { code: 'LOCAL_APP_ERROR', retryable: false } }) })
    return () => { active = false; unsubscribe() }
  }, [])

  const login = async () => {
    const auth = window.focusOn?.auth
    if (!auth || busy) return
    setBusy(true); setRequestError(null); setAction('login'); running.current = 'login'
    try { const result = await auth.startGoogleLogin(); if (!result.success) setRequestError(result.error) }
    catch { setRequestError({ code: 'LOCAL_APP_ERROR', retryable: false }) }
    finally { setBusy(false); running.current = null }
  }
  const retry = async () => {
    const auth = window.focusOn?.auth
    if (!auth || busy) return
    setBusy(true); setRequestError(null); setAction('retry'); running.current = 'retry'
    try {
      const result = await auth.retrySession()
      if (result.success) setSnapshot((current) => !current || result.data.revision > current.revision ? result.data : current)
      else setRequestError(result.error)
    } catch { setRequestError({ code: 'LOCAL_APP_ERROR', retryable: false }) }
    finally { setBusy(false); running.current = null }
  }
  const logout = async () => {
    const auth = window.focusOn?.auth
    if (!auth || busy) return
    setBusy(true); setRequestError(null); setAction('logout'); running.current = 'logout'
    try { const result = await auth.logout(); if (!result.success) setRequestError(result.error) }
    catch { setRequestError({ code: 'LOCAL_APP_ERROR', retryable: false }) }
    finally { setBusy(false); running.current = null }
  }

  const view = selectAuthView(snapshot)
  const shownError = requestError ?? snapshot?.error ?? null
  const message = shownError ? errorText(shownError) : null
  if (intro) return <LoadingScreen />
  // 두 번째부터는 로그인 확인(보통 수십 ms) 동안 로고 없이 빈 화면으로 기다린다.
  if (intro === null || view === 'loading') return <main className="fo-loading" aria-busy="true" />
  if (view === 'onboarding' && snapshot?.user) return <OnboardingRedirect reconnect={snapshot.status === 'LINK_REQUIRED'} />
  if (view === 'home' && snapshot?.user) return <HomeDashboard name={snapshot.user.nickname || 'FocusOn 사용자'} onLogout={() => { void logout() }} busy={busy} message={message} />
  return <LoginScreen onLogin={() => { void login() }} onRetry={() => { void retry() }} busy={busy} authenticating={snapshot?.status === 'AUTHENTICATING'} message={message} action={action} available={available} offline={view === 'offline'} />
}
