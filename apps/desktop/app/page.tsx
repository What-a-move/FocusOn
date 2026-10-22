'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import type { AuthSessionSnapshot, IpcError } from '../electron/shared/auth-contract.cjs'
import { selectAuthView } from './lib/auth-view'
import { onboardingApi, STEP_ROUTES } from '../features/onboarding/onboarding-api'

const signedOut: AuthSessionSnapshot = {
  revision: 0, status: 'SIGNED_OUT', user: null, onboardingStatus: null, error: null,
}

function errorMessage(error: IpcError | null): string | null {
  if (!error) return null
  switch (error.code) {
    case 'LOCAL_NETWORK_UNAVAILABLE': return '인터넷 연결을 확인하고 다시 시도해 주세요.'
    case 'AUTH_RELOGIN_REQUIRED':
    case 'AUTH_TOKEN_REUSE_DETECTED':
    case 'AUTH_OAUTH_REAUTH_REQUIRED': return '로그인 정보가 만료됐어요. Google로 다시 로그인해 주세요.'
    case 'AUTH_DEVICE_LIMIT_EXCEEDED': return '로그인 가능한 Desktop 3대를 모두 사용 중이에요. 기존 기기를 해제한 뒤 다시 시도해 주세요.'
    case 'SERVICE_UNAVAILABLE': return '로그인 서비스를 사용할 수 없어요. 잠시 후 다시 시도해 주세요.'
    default: return error.retryable ? '요청을 마치지 못했어요. 잠시 후 다시 시도해 주세요.' : '로그인을 마치지 못했어요. 다시 시도해 주세요.'
  }
}

function Logo({ className = '' }: { className?: string }) {
  return <span className={`fo-logo ${className}`}>
    <Image className="fo-logo-light" src="/design/focuson-logo.png" alt="FocusOn" width={2172} height={724} priority />
    <Image className="fo-logo-dark" src="/design/focuson-logo-dark.png" alt="FocusOn" width={2172} height={724} priority />
  </span>
}

function Alert({ message }: { message: string | null }) {
  if (!message) return null
  return <div className="fo-notice" role="alert"><strong>요청을 마치지 못했어요.</strong><span>{message}</span></div>
}

function LoadingScreen() {
  return <main className="fo-loading" role="status" aria-label="로그인 상태 확인 중">
    <Image src="/design/poki-floating-logo-base-v1.png" alt="" width={1254} height={1254} priority />
    <Logo /><p>로그인 상태를 확인하고 있어요.</p>
  </main>
}

function LoginScreen({ onLogin, onRetry, busy, authenticating, message, available, offline }: {
  onLogin: () => void; onRetry: () => void; busy: boolean; authenticating: boolean; message: string | null; available: boolean; offline: boolean
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
          {offline ? <div className="fo-notice" role="alert"><strong>인터넷에 연결되어 있지 않아요.</strong><span>{message ?? '연결을 확인한 뒤 다시 시도해 주세요.'}</span></div> : <Alert message={message} />}
          {(busy || authenticating) && <div className="fo-notice" role="status"><strong>Google 계정을 연결하고 있어요.</strong><span>브라우저에서 인증을 마친 뒤 앱으로 돌아와 주세요.</span></div>}
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

const navigation = [
  { label: '홈', asset: 'nav-home.png' },
  { label: '집중 시작', asset: 'nav-start.svg' },
  { label: '집중 기록', asset: 'nav-records.png' },
  { label: '학습 노트', asset: 'nav-notes.svg' },
  { label: '설정', asset: 'nav-settings.png' },
] as const

function Calendar({ today }: { today: Date | null }) {
  const [monthOffset, setMonthOffset] = useState(0)
  if (!today) return <div className="fo-calendar-empty" role="status">달력을 준비하고 있어요.</div>
  const month = new Date(today.getFullYear(), today.getMonth() + monthOffset, 1)
  const offset = (month.getDay() + 6) % 7
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  return <>
    <div className="fo-month-controls"><button type="button" onClick={() => setMonthOffset((value) => value - 1)} aria-label="이전 달">‹</button><span>{month.getFullYear()}년 {month.getMonth() + 1}월</span><button type="button" onClick={() => setMonthOffset((value) => value + 1)} disabled={monthOffset >= 0} aria-label="다음 달">›</button></div>
    <div className="fo-calendar-grid" aria-label={`${month.getFullYear()}년 ${month.getMonth() + 1}월 집중 달력`}>
      {['월', '화', '수', '목', '금', '토', '일'].map((day) => <span className="fo-weekday" key={day}>{day}</span>)}
      {Array.from({ length: offset }, (_, index) => <span key={`blank-${index}`} />)}
      {Array.from({ length: days }, (_, index) => {
        const day = index + 1
        const isToday = monthOffset === 0 && day === today.getDate()
        return <span className={`fo-calendar-day${isToday ? ' today' : ''}`} key={day} aria-label={`${month.getMonth() + 1}월 ${day}일, 기록 없음`}>{day}</span>
      })}
    </div>
  </>
}

function HomeScreen({ name, onLogout, busy, message }: { name: string; onLogout: () => void; busy: boolean; message: string | null }) {
  const [today, setToday] = useState<Date | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  useEffect(() => {
    // [유지보수] 정적 HTML 빌드 시각과 앱 실행 시각이 달라도 날짜가 어긋나지 않게 실행 후 계산한다.
    queueMicrotask(() => setToday(new Date()))
  }, [])

  return <main className="fo-home-window">
    <aside className="fo-sidebar" aria-label="주 메뉴">
      <Logo className="fo-sidebar-logo" />
      <nav className="fo-nav">{navigation.map(({ label, asset }, index) => <span className={`fo-nav-item${index === 0 ? ' active' : ''}`} aria-current={index === 0 ? 'page' : undefined} key={label}><Image src={`/design/${asset}`} alt="" width={28} height={28} /><span>{label}</span></span>)}</nav>
      <div className="fo-sidebar-spacer" />
      <div className="fo-profile"><span className="fo-profile-avatar" aria-hidden="true" /><div><strong>{name}</strong><small>Google 계정</small></div></div>
      <button className="fo-sidebar-logout" type="button" onClick={onLogout} disabled={busy}>{busy ? '로그아웃 중…' : '로그아웃'}</button>
    </aside>
    <div className="fo-home-content">
      <header className="fo-topbar"><div><span>{today ? new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' }).format(today) : '오늘'}</span><p><strong>{name}</strong>님, 안녕하세요</p></div><ul aria-label="오늘 요약"><li><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="#f2b632"/><circle cx="12" cy="12" r="7" fill="#fff6dc"/><path d="M12 8v4.2l2.8 1.8" fill="none" stroke="#8a5a3b" strokeWidth="2" strokeLinecap="round"/></svg><span>오늘 <strong>0</strong>분</span></li><li><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4.5" width="18" height="16" rx="4" fill="#8a5a3b"/><rect x="5" y="9" width="14" height="9.5" rx="2" fill="#fff6dc"/><path d="M9 13.8l2 2 4-4" fill="none" stroke="#e0a52a" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/></svg><span>이번 달 <strong>0</strong>일</span></li></ul></header>
      <div className="fo-dashboard"><Alert message={message} />
        <section className="fo-hero" aria-labelledby="focus-title"><div className="fo-poki-stage"><Image src="/design/poki-main-planning.png" alt="체크리스트를 든 포키" width={220} height={220} priority /></div><div><h1 id="focus-title">오늘은 뭐에 <span>집중</span>할까요?</h1><button className="fo-button fo-button-primary fo-goal-button" type="button" onClick={() => setNotice('목표 설정은 다음 작업에서 연결할게요.')}>목표 설정하기 <span aria-hidden="true">→</span></button>{notice && <p className="fo-home-notice" role="status">{notice}</p>}</div></section>
        <div className="fo-dashboard-grid"><section className="fo-dashboard-block" aria-labelledby="calendar-title"><div className="fo-panel-head"><h2 id="calendar-title">집중 달력</h2></div><Calendar today={today} /></section><section className="fo-dashboard-block" aria-labelledby="recent-title"><div className="fo-panel-head"><h2 id="recent-title">지난 목표</h2></div><div className="fo-records-empty"><strong>아직 기록이 없어요</strong><span>첫 집중을 마치면 여기에 나타나요.</span></div></section></div>
      </div>
    </div>
  </main>
}

export default function Page() {
  const [snapshot, setSnapshot] = useState<AuthSessionSnapshot | null>(null)
  const [requestError, setRequestError] = useState<IpcError | null>(null)
  const [busy, setBusy] = useState(false)
  const [available, setAvailable] = useState(true)

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
    }
    const unsubscribe = auth.onSessionChanged(applySnapshot)
    void auth.getSession().then((result) => {
      if (!active) return
      if (result.success) applySnapshot(result.data)
      else setSnapshot({ ...signedOut, error: result.error })
    }).catch(() => { if (active) setSnapshot({ ...signedOut, error: { code: 'SERVICE_UNAVAILABLE', retryable: false } }) })
    return () => { active = false; unsubscribe() }
  }, [])

  const login = async () => {
    const auth = window.focusOn?.auth
    if (!auth || busy) return
    setBusy(true); setRequestError(null)
    try { const result = await auth.startGoogleLogin(); if (!result.success) setRequestError(result.error) }
    catch { setRequestError({ code: 'SERVICE_UNAVAILABLE', retryable: false }) }
    finally { setBusy(false) }
  }
  const retry = async () => {
    const auth = window.focusOn?.auth
    if (!auth || busy) return
    setBusy(true); setRequestError(null)
    try {
      const result = await auth.retrySession()
      if (result.success) setSnapshot((current) => !current || result.data.revision > current.revision ? result.data : current)
      else setRequestError(result.error)
    } catch { setRequestError({ code: 'SERVICE_UNAVAILABLE', retryable: false }) }
    finally { setBusy(false) }
  }
  const logout = async () => {
    const auth = window.focusOn?.auth
    if (!auth || busy) return
    setBusy(true); setRequestError(null)
    try { const result = await auth.logout(); if (!result.success) setRequestError(result.error) }
    catch { setRequestError({ code: 'SERVICE_UNAVAILABLE', retryable: false }) }
    finally { setBusy(false) }
  }

  const view = selectAuthView(snapshot)
  const message = errorMessage(requestError ?? snapshot?.error ?? null)
  if (view === 'loading') return <LoadingScreen />
  if (view === 'onboarding' && snapshot?.user) return <OnboardingRedirect reconnect={snapshot.status === 'LINK_REQUIRED'} />
  if (view === 'home' && snapshot?.user) return <HomeScreen name={snapshot.user.nickname || 'FocusOn 사용자'} onLogout={() => { void logout() }} busy={busy} message={message} />
  return <LoginScreen onLogin={() => { void login() }} onRetry={() => { void retry() }} busy={busy} authenticating={snapshot?.status === 'AUTHENTICATING'} message={message} available={available} offline={view === 'offline'} />
}
