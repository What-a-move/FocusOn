'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { formatMinutes, MAX_MINUTES, MIN_MINUTES, sessionApi, type SessionSnapshot } from './session-api'
import '../onboarding/onboarding.css'
import './focus-session.css'

const CIRCUMFERENCE = 2 * Math.PI * 90
const EXTEND_OPTIONS = [10, 25, 50]
const POKI = { focus: '/design/poki-floating-master-v3.png', paused: '/design/poki-floating-paused-v3.png', curious: '/design/poki-floating-curious-v8.png', cheer: '/design/poki-floating-cheer-v7.png' }

function formatClock(ms: number): string {
  const seconds = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(seconds / 3600)
  const mm = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')
  const ss = String(seconds % 60).padStart(2, '0')
  return hours ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
}

function errorText(code: string): string {
  switch (code) {
    case 'LOCAL_SCREEN_PERMISSION_REVOKED': return '화면 기록 권한이 꺼져 있어요. 시스템 설정에서 켠 뒤 다시 눌러 주세요.'
    case 'LOCAL_NOTIFICATION_NOT_CONFIRMED': return '알림이 보이는지 확인이 필요해요. 세션 설정에서 시험 알림을 확인한 뒤 다시 눌러 주세요.'
    case 'SESSION_START_REQUIREMENT_MISSING': return '화면 기록 권한과 Chrome 확장 연결을 확인한 뒤 다시 눌러 주세요.'
    case 'SESSION_VERSION_CONFLICT': return '다른 곳에서 상태가 바뀌어 최신 상태로 맞췄어요. 확인한 뒤 다시 눌러 주세요.'
    case 'LOCAL_NETWORK_UNAVAILABLE': return '인터넷 연결을 확인한 뒤 다시 눌러 주세요.'
    default: return '요청을 마치지 못했어요. 잠시 후 다시 눌러 주세요.'
  }
}

/** D10·D11·D15·D16 집중 화면 (FocusOn-design/session.html). 시간은 Main SessionRuntime이 센 값만 그린다. */
export function FocusSession() {
  const router = useRouter()
  // [주의] Main이 보낸 누적 시간에, 받은 뒤 흐른 시간만 더한다(화면용). 확정값은 언제나 Main의 값이다.
  const [view, setView] = useState<{ snapshot: SessionSnapshot | null | undefined; receivedAt: number }>({ snapshot: undefined, receivedAt: 0 })
  const snapshot = view.snapshot
  const [loadError, setLoadError] = useState(false)
  const [now, setNow] = useState(0)
  const [dialog, setDialog] = useState<'finish' | 'timeup' | 'extend' | null>(null)
  const [custom, setCustom] = useState('')
  const [customError, setCustomError] = useState(false)
  const dialogRef = useRef<HTMLDialogElement>(null)

  const apply = (next: SessionSnapshot | null) => { const at = performance.now(); setView({ snapshot: next, receivedAt: at }); setNow(at) }

  useEffect(() => {
    const unsubscribeAuth = window.focusOn?.auth.onSessionChanged((auth) => { if (auth.status === 'SIGNED_OUT') router.replace('/') })
    const unsubscribe = sessionApi.onChanged(apply)
    void sessionApi.getCurrent().then((result) => { if (result.success) apply(result.data); else setLoadError(true) })
    const timer = setInterval(() => setNow(performance.now()), 1000)
    return () => { unsubscribe(); unsubscribeAuth?.(); clearInterval(timer) }
  }, [router])

  const status = snapshot?.status
  const awaiting = status === 'AWAITING_END_CONFIRMATION'
  // 시간이 끝나면 확인 창을 연다. 사용자가 고르기 전에는 닫을 수 없다.
  const activeDialog = awaiting ? (dialog === 'extend' ? 'extend' : 'timeup') : dialog
  useEffect(() => {
    const element = dialogRef.current
    if (!element) return
    if (activeDialog && !element.open) element.showModal()
    if (!activeDialog && element.open) element.close()
  }, [activeDialog])

  if (snapshot === undefined && !loadError) return <main className="app-window onboarding-window focus-window" aria-busy="true" />

  if (!snapshot) {
    return <main className="app-window onboarding-window focus-window">
      <header className="titlebar"><div className="window-controls" aria-hidden="true"><i /><i /><i /></div></header>
      <section className="finished">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className="poki" src={POKI.curious} alt="궁금해하는 포키" />
        <h1>{loadError ? '집중 상태를 불러오지 못했어요' : '진행 중인 집중이 없어요'}</h1>
        <p>{loadError ? '인터넷 연결을 확인한 뒤 다시 열어 주세요.' : '홈에서 목표를 정하고 집중을 시작해 주세요.'}</p>
        <Link className="btn btn-primary" href="/">홈으로</Link>
      </section>
    </main>
  }

  const running = status === 'RUNNING'
  const elapsed = Math.min(snapshot.targetDurationMs, snapshot.accumulatedActiveMs + (running ? Math.max(0, now - view.receivedAt) : 0))
  const remaining = Math.max(0, snapshot.targetDurationMs - elapsed)
  const paused = status === 'PAUSED' || status === 'AUTO_PAUSED' || awaiting
  const finished = status === 'ENDED' || status === 'ABANDONED'
  // 세션 중 화면 기록 권한이 꺼져 자동으로 멈춘 상태(session-states.html `권한 꺼짐`)
  const permissionLost = status === 'AUTO_PAUSED' && snapshot.error === 'LOCAL_SCREEN_PERMISSION_REVOKED'
  const caption = permissionLost ? '자동으로 멈춤 · 마지막 확정 시간' : awaiting ? '설정한 시간이 끝났어요' : status === 'AUTO_PAUSED' ? '앱을 다시 열어 멈춰 있어요' : paused ? '잠시 멈췄어요' : `${formatMinutes(Math.round(snapshot.targetDurationMs / 60000))} 중`

  const togglePause = () => { void (running ? sessionApi.pause() : sessionApi.resume()) }
  const end = async () => { const result = await sessionApi.end(); if (result.success) setDialog(null) }
  const extend = async (minutes: number) => {
    const result = await sessionApi.extend(minutes)
    if (result.success) { setDialog(null); setCustom(''); setCustomError(false) }
  }
  const submitCustom = (event: React.FormEvent) => {
    event.preventDefault()
    const value = Number(custom)
    const valid = Number.isInteger(value) && value >= MIN_MINUTES && value <= MAX_MINUTES
    setCustomError(!valid)
    if (valid) void extend(value)
  }

  if (finished) {
    const abandoned = status === 'ABANDONED'
    return <main className="app-window onboarding-window focus-window">
      <header className="titlebar"><div className="window-controls" aria-hidden="true"><i /><i /><i /></div></header>
      <section className="finished" aria-labelledby="finished-title">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className="poki" src={abandoned ? POKI.curious : POKI.cheer} alt={abandoned ? '궁금해하는 포키' : '응원하는 포키'} />
        <h1 id="finished-title">{abandoned ? '집중이 자동으로 끝났어요' : '집중을 마쳤어요'}</h1>
        <p>{abandoned ? `앱과 3분 넘게 연결되지 않아 마지막으로 확인한 ${Math.floor(snapshot.accumulatedActiveMs / 60000)}분까지 기록했어요.` : `집중한 시간 ${Math.floor(snapshot.accumulatedActiveMs / 60000)}분`}</p>
        {/* 회차 리포트(D16·D18)는 리포트 Issue에서 연결한다. */}
        <Link className="btn btn-primary" href="/">홈으로</Link>
      </section>
    </main>
  }

  return <main className={`app-window onboarding-window focus-window${paused ? ' is-paused' : ''}`}>
    <header className="titlebar"><div className="window-controls" aria-hidden="true"><i /><i /><i /></div></header>
    {/* 집중 중에도 홈으로 나갈 수 있다. 타이머는 Main에서 계속 센다. */}
    <Link className="home-link" href="/"><span aria-hidden="true">←</span>홈</Link>
    <section className="stage" aria-labelledby="goal-text">
      <div className="center">
        <div className="ring-wrap">
          <svg className="timer-ring" viewBox="0 0 200 200" aria-hidden="true">
            <circle className="track" cx="100" cy="100" r="90" />
            <circle className="fill" cx="100" cy="100" r="90" style={{ strokeDasharray: CIRCUMFERENCE, strokeDashoffset: CIRCUMFERENCE * (1 - Math.min(1, elapsed / snapshot.targetDurationMs)) }} />
          </svg>
          <div className="ring-center">
            <div className="clock" role="timer" aria-live="off">{formatClock(Math.ceil(remaining / 1000) * 1000)}</div>
            <div className={`clock-caption${permissionLost ? ' is-error' : ''}`}>{caption}</div>
          </div>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="poki" key={permissionLost ? 'curious' : paused ? 'paused' : 'focus'} src={permissionLost ? POKI.curious : paused ? POKI.paused : POKI.focus} alt={permissionLost ? '궁금해하는 포키' : paused ? '쉬고 있는 포키' : '함께 집중하는 포키'} />
        </div>
        <p className="goal" id="goal-text">{snapshot.goalText}</p>
        <div className="controls">
          <div className="control">
            <button className={`round${paused && !awaiting ? ' is-play' : ''}`} type="button" onClick={togglePause} disabled={snapshot.busy || awaiting} aria-label={running ? '잠시 멈추기' : '다시 집중하기'}>
              {running
                ? <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="6" y="4" width="4" height="16" rx="1.5" /><rect x="14" y="4" width="4" height="16" rx="1.5" /></svg>
                : <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5Z" /></svg>}
            </button>
            <span className="label">{running ? '멈추기' : '이어 하기'}</span>
          </div>
          <div className="control">
            <button className="round" type="button" onClick={() => setDialog('finish')} disabled={snapshot.busy} aria-label="집중 끝내기">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="6" y="6" width="12" height="12" rx="2.5" /></svg>
            </button>
            <span className="label">끝내기</span>
          </div>
        </div>
        {permissionLost && !activeDialog && <div className="halt-notice" role="alert">
          <p><strong>화면 분석 권한이 꺼졌어요.</strong>그래서 타이머와 분석을 멈췄어요.<br />권한을 켠 뒤 직접 이어 가 주세요.</p>
          <button className="btn btn-danger" type="button" onClick={() => { void window.focusOn?.permissions.openScreenSettings() }}>권한 확인</button>
        </div>}
        {snapshot.error && !permissionLost && !activeDialog && <p className="session-error" role="alert"><span className="icon-error" aria-hidden="true">!</span>{errorText(snapshot.error)}</p>}
      </div>
    </section>

    <dialog ref={dialogRef} aria-labelledby="dialog-title" onCancel={(event) => { event.preventDefault(); if (!awaiting) setDialog(null) }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" src={activeDialog === 'finish' ? POKI.curious : POKI.focus} alt="" />
      <h2 id="dialog-title">{activeDialog === 'finish' ? '집중을 끝낼까요?' : '목표한 시간이 지났어요'}</h2>
      <p>{activeDialog === 'finish' ? '지금까지 집중한 시간을 기록하고 이번 회차를 마칩니다.'
        : activeDialog === 'extend' ? '얼마나 더 할까요? 같은 회차로 이어져요.' : '타이머와 분석은 멈췄어요.\n끝내거나 같은 목표로 더 공부할 수 있어요.'}</p>
      {activeDialog === 'timeup' && <div className="time-facts"><div><span>집중한 시간</span> <b>{Math.floor(elapsed / 60000)}분</b></div></div>}
      {snapshot.error && activeDialog && <p className="dialog-error" role="alert">{errorText(snapshot.error)}</p>}
      {activeDialog === 'finish' && <div className="dialog-actions"><div className="row">
        <button className="btn btn-ghost" type="button" onClick={() => setDialog(null)}>계속하기</button>
        <button className="btn btn-primary" type="button" onClick={() => { void end() }} disabled={snapshot.busy}>{snapshot.busy ? '끝내는 중…' : '끝내기'}</button>
      </div></div>}
      {activeDialog === 'timeup' && <div className="dialog-actions"><div className="row">
        <button className="btn btn-ghost" type="button" onClick={() => setDialog('extend')}>더 공부하기</button>
        <button className="btn btn-primary" type="button" onClick={() => { void end() }} disabled={snapshot.busy}>{snapshot.busy ? '끝내는 중…' : '세션 종료'}</button>
      </div></div>}
      {activeDialog === 'extend' && <div className="dialog-actions">
        <div className="row three">{EXTEND_OPTIONS.map((minutes) => <button className="btn btn-ghost" type="button" key={minutes} onClick={() => { void extend(minutes) }} disabled={snapshot.busy}>{minutes}분</button>)}</div>
        <form className="extend-custom" onSubmit={submitCustom} noValidate>
          <label className="sr-only" htmlFor="extend-input">직접 입력 (분)</label>
          <input id="extend-input" type="number" inputMode="numeric" min={MIN_MINUTES} max={MAX_MINUTES} placeholder="직접 입력 (분)" value={custom} aria-invalid={customError} onChange={(event) => { setCustom(event.target.value); setCustomError(false) }} />
          <button className="btn btn-ghost" type="submit" disabled={snapshot.busy}>연장</button>
        </form>
        {customError && <p className="dialog-error" role="alert">5~300분(5시간) 사이로 입력해 주세요.</p>}
        <button className="btn btn-ghost" type="button" onClick={() => setDialog('timeup')}>뒤로</button>
      </div>}
    </dialog>
  </main>
}
