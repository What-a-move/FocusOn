'use client'

import { useCallback, useEffect, useState } from 'react'
import { getActiveSession, unlinkThisBrowser } from './session-api'
import { displayActiveMs, formatClock, type ActiveSession } from './session-model'
import '../pairing/pairing.css'
import './session-popup.css'

// 팝업이 열려 있는 동안만 서버 상태를 다시 읽는다. 시간 확정·조작은 Desktop이 맡는다(팝업은 표시만 — 팀 확인 전 제안, session-popup-PLAN.md).
const POLL_MS = 10_000
const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })

type View =
  | { kind: 'loading' }
  | { kind: 'none' }
  | { kind: 'session'; session: ActiveSession; fetchedAt: number }
  // 서버에 닿지 않으면 마지막으로 확인한 값만 그 시각과 함께 보여 준다. 지금 값처럼 단정하지 않는다.
  | { kind: 'offline'; last: { session: ActiveSession; at: Date } | null }
  | { kind: 'unlink-confirm' }

function Poki({ name, alt }: { name: string; alt: string }) {
  // eslint-disable-next-line @next/next/no-img-element
  return <img className="poki" src={`/design/poki-floating-${name}.png`} alt={alt} />
}

/** E02 세션 없음 · E03 세션 진행 중(표시 전용) · E06 서버 연결 불가 (FocusOn-design/extension-popup.html). */
export function SessionPopup({ onRelink }: { onRelink: () => void }) {
  const [view, setView] = useState<View>({ kind: 'loading' })
  const [last, setLast] = useState<{ session: ActiveSession; at: Date } | null>(null)
  const [now, setNow] = useState(0)
  const [busy, setBusy] = useState(false)
  const [unlinkError, setUnlinkError] = useState(false)

  const load = useCallback(async () => {
    const result = await getActiveSession()
    const at = Date.now()
    setNow(at)
    if (result.success) {
      if (result.data) setLast({ session: result.data, at: new Date(at) })
      setView((current) => current.kind === 'unlink-confirm' ? current : result.data ? { kind: 'session', session: result.data, fetchedAt: at } : { kind: 'none' })
      return
    }
    if (result.error.code === 'RELINK_REQUIRED') { onRelink(); return }
    setView((current) => current.kind === 'unlink-confirm' ? current : { kind: 'offline', last: null })
  }, [onRelink])

  useEffect(() => {
    queueMicrotask(() => { void load() })
    const poll = setInterval(() => { void load() }, POLL_MS)
    const tick = setInterval(() => setNow(Date.now()), 1000)
    return () => { clearInterval(poll); clearInterval(tick) }
  }, [load])

  const unlink = async () => {
    setBusy(true)
    setUnlinkError(false)
    const result = await unlinkThisBrowser()
    setBusy(false)
    if (result.success) onRelink()
    else setUnlinkError(true)
  }

  const offline = view.kind === 'offline'
  return <main className="pairing-stage">
    <section className="pairing-popup" aria-label="FocusOn">
      <header className="p-head">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/design/focuson-logo.png" alt="FocusOn" />
        <span className="conn" data-tone={offline ? 'bad' : 'ok'}>{offline ? '서버 연결 끊김' : '연결됨'}</span>
      </header>
      <div className="p-body session-body">
        {view.kind === 'loading' && <p className="center muted" role="status">집중 상태를 확인하고 있어요.</p>}

        {view.kind === 'none' && <>
          <div className="center"><Poki name="master-v3" alt="포키" /><h1>지금은 집중 중이 아니에요</h1></div>
          <p className="center muted">데스크톱 앱에서 집중을 시작하면 여기서도 보여요.</p>
          <div className="checks"><span>데스크톱 연결됨</span></div>
          <p className="center"><button className="text-btn" type="button" onClick={() => setView({ kind: 'unlink-confirm' })}>이 브라우저 연결 해제</button></p>
        </>}

        {view.kind === 'unlink-confirm' && <>
          <h1>이 브라우저의 연결을 해제할까요?</h1>
          <ul className="facts">
            <li><span className="mark no" aria-hidden="true">■</span><span><b>멈추는 것</b>이 브라우저의 페이지 분석</span></li>
            <li><span className="mark no" aria-hidden="true">!</span><span><b>세션 중이라면</b>진행 중인 세션은 자동으로 멈춰요</span></li>
            <li><span className="mark yes" aria-hidden="true">↻</span><span><b>다시 쓰려면</b>데스크톱 앱에서 새 코드를 받아 연결해요</span></li>
          </ul>
          {unlinkError && <div className="notice is-error" role="alert"><strong>연결을 해제하지 못했어요.</strong><span>인터넷 연결을 확인한 뒤 다시 눌러 주세요.</span></div>}
          <div className="actions two">
            <button className="btn btn-ghost" type="button" onClick={() => { setUnlinkError(false); void load() }} disabled={busy}>취소</button>
            <button className="btn btn-danger" type="button" onClick={() => { void unlink() }} disabled={busy}>{busy ? '해제하는 중' : '연결 해제'}</button>
          </div>
        </>}

        {view.kind === 'session' && <SessionView session={view.session} activeMs={displayActiveMs(view.session, view.fetchedAt, Math.max(now, view.fetchedAt))} />}

        {view.kind === 'offline' && <>
          <div className="notice is-error" role="alert"><strong>서버에 연결할 수 없어요</strong><span>인터넷 연결을 확인해 주세요.</span><span>타이머는 데스크톱 앱이 계속 재고 있어요.</span></div>
          {last && <div>
            <span className="label">마지막으로 확인한 남은 시간</span>
            <div className="timer-row"><span className="timer is-dim">{formatClock(last.session.targetDurationMs - last.session.accumulatedActiveMs)}</span></div>
            <div className="progress is-dim"><i style={{ width: `${Math.min(100, (last.session.accumulatedActiveMs / last.session.targetDurationMs) * 100)}%` }} /></div>
            <p className="small muted">{timeFormat.format(last.at)} 기준이라 지금과 다를 수 있어요.</p>
          </div>}
          <button className="btn btn-primary" type="button" onClick={() => { void load() }}>다시 연결</button>
        </>}
      </div>
    </section>
  </main>
}

function SessionView({ session, activeMs }: { session: ActiveSession; activeMs: number }) {
  const remaining = Math.max(0, session.targetDurationMs - activeMs)
  const percent = Math.min(100, (activeMs / session.targetDurationMs) * 100)
  const minutes = Math.round(session.targetDurationMs / 60000)
  const paused = session.status !== 'RUNNING'

  if (session.status === 'AWAITING_END_CONFIRMATION') {
    return <>
      <div className="center"><Poki name="master-v3" alt="포키" /><h1>목표한 {minutes}분이 지났어요</h1></div>
      <p className="center muted">데스크톱 앱에서 끝내거나 시간을 늘려 주세요.</p>
    </>
  }

  return <>
    <div className="goal">{session.goalText}</div>
    {session.status === 'AUTO_PAUSED' && <div className="notice is-error" role="alert"><strong>집중이 자동으로 멈췄어요</strong><span>데스크톱 앱에서 확인한 뒤 직접 재개해 주세요.</span></div>}
    <div className="timer-row">
      <div>
        <span className={`timer${paused ? ' is-dim' : ''}`} role="timer" aria-live="off">{formatClock(remaining)}</span>
        <span className="timer-cap">{paused ? '잠시 멈췄어요 · 분석도 쉬는 중' : `${minutes}분 중`}</span>
      </div>
      <Poki name={session.status === 'AUTO_PAUSED' ? 'paused-v2' : paused ? 'paused-v3' : 'master-v3'} alt={paused ? '쉬고 있는 포키' : '함께 집중하는 포키'} />
    </div>
    <div className={`progress${paused ? ' is-dim' : ''}`}><i style={{ width: `${percent}%` }} /></div>
    {/* 멈추기·끝내기는 Desktop에서만 한다. Desktop이 타이머와 서버 신호를 맡기 때문이다(팀 확인 전 제안). */}
    <p className="small muted">멈추기·끝내기는 데스크톱 앱에서 할 수 있어요.</p>
  </>
}
