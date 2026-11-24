'use client'

import { useEffect, useRef, useState } from 'react'
import type { FloatingShow, InterventionAction } from '../../electron/shared/intervention-contract.cjs'
import { OffTaskAlert } from './OffTaskAlert'
import './floating.css'

// [주의] Renderer는 electron/shared에서 타입만 가져온다(goal-setting-shared-runtime-import-ERROR.md).

/** 화면 위 포키 F04·F05·F07 (FocusOn-design/floating-poki.html). 문구는 Main이 정해 보낸다. 판정·타이머는 바꾸지 않는다. */
export function FloatingPoki() {
  const [show, setShow] = useState<FloatingShow | null>(null)
  const [lift, setLift] = useState(0)

  useEffect(() => window.focusOn?.interventions.onShow(setShow), [])
  useEffect(() => window.focusOn?.interventions.onLift(setLift), [])
  // 카드·포키 위에서만 클릭을 받고, 나머지 투명한 곳은 아래 앱으로 통과시킨다(#225). 닫히면 다시 통과.
  useEffect(() => { if (!show) window.focusOn?.interventions.setInteractive(false) }, [show])
  const hover = { onPointerEnter: () => window.focusOn?.interventions.setInteractive(true), onPointerLeave: () => window.focusOn?.interventions.setInteractive(false) }

  if (!show) return <main className="floating-root" />
  if (show.kind === 'OFF_TASK') return <main className="floating-root">
    {show.presentation && <OffTaskAlert key={show.interventionId} show={show} presentation={show.presentation} lift={lift} />}
  </main>
  if (show.kind === 'BREAK') return <main className="floating-root">
    <BreakAlert key={show.interventionId} show={show} lift={lift} />
  </main>
  const act = async (action: InterventionAction) => {
    return window.focusOn?.interventions.act({ interventionId: show.interventionId, action })
  }

  if (show.kind === 'GOAL_ADD') return <main className="floating-root">
    <GoalAddAlert key={show.interventionId} show={show} lift={lift} />
  </main>

  // F07은 세션이 실제로 멈춘 오류라 시스템 안내(존댓말)·빨간 글자로 보여 준다(결정 031).
  return <main className="floating-root">
    {/* 포키가 먼저 화면 아래에서 고개를 내밀어 표정으로 알리고, 카드는 그 뒤에 포키 옆에 뜬다(2026-11-08 사용자 확정). */}
    <div className="stack" key={show.interventionId} style={{ '--lift': `${lift}px` } as React.CSSProperties}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" {...hover} src="/design/poki-peek-chrome-disconnected.png" alt="" />
      <div {...hover} className="card is-error" role="alert" aria-live="assertive">
        <div className="card-body">
          <em className="who">FocusOn</em>
          <span>{show.say}</span>
          <small>{show.sub}</small>
          <div className="say-actions">
            <button type="button" className="primary" onClick={() => { void act('OPEN_APP') }}>앱 열기</button>
          </div>
        </div>
        <button className="close" type="button" aria-label="포키 닫기" onClick={() => { void act('CLOSE') }}>
          <svg viewBox="0 0 12 12" aria-hidden="true"><path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
        </button>
      </div>
    </div>
  </main>
}

function GoalAddAlert({ show, lift }: { show: FloatingShow; lift: number }) {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  const api = window.focusOn?.interventions
  const hover = { onPointerEnter: () => api?.setInteractive(true), onPointerLeave: () => api?.setInteractive(false) }
  const open = async () => {
    if (busy) return
    setBusy(true)
    setFailed(false)
    try {
      const result = await api?.act({ interventionId: show.interventionId, action: 'OPEN_APP' })
      setFailed(result?.success !== true)
    } catch {
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }
  return <div className="stack" style={{ '--lift': `${lift}px` } as React.CSSProperties}>
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img className="poki" {...hover} src="/design/poki-peek-chrome-disconnected.png" alt="" />
    <div {...hover} className="card" role="status">
      <div className="card-body">
        <span>{show.say}</span>
        <small>{show.sub}</small>
        {failed && <small role="alert">세션을 열지 못했어요. 다시 시도해 주세요.</small>}
        <div className="say-actions">
          <button type="button" className="primary" disabled={busy} onClick={() => { void open() }}>할 일 추가</button>
          <button type="button" disabled={busy} onClick={() => { void api?.act({ interventionId: show.interventionId, action: 'CLOSE' }) }}>지금은 괜찮아요</button>
        </div>
      </div>
    </div>
  </div>
}

// 처음 한 번은 누르는 방법을 안내한다. 이 기기에만 기억한다.
const HINT_KEY = 'focuson.pokiHintSeen'

/** 휴식 제안은 팻말을 누르면 세션을 잠시 멈춘다(#253). */
function BreakAlert({ show, lift }: { show: FloatingShow; lift: number }) {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.interventions
  const [ducking, setDucking] = useState(false)
  const [hover, setHover] = useState(false)
  const [failed, setFailed] = useState(false)
  const [first] = useState(() => { try { return localStorage.getItem(HINT_KEY) !== '1' } catch { return true } })
  const hideTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  useEffect(() => { if (first) { try { localStorage.setItem(HINT_KEY, '1') } catch { /* 다음에 다시 안내해도 된다 */ } } }, [first])
  useEffect(() => () => clearTimeout(hideTimer.current), [])

  // 포키·버튼 위에서만 클릭을 받는다(#225). 포키에서 버튼으로 옮겨 가는 사이(약 230px) 안내가 꺼지지 않게 0.9초 기다린다.
  const enter = () => { clearTimeout(hideTimer.current); setHover(true); api?.setInteractive(true) }
  // 다른 앱이 커서를 되돌릴 수 있어, 포키·버튼 위에서 움직이는 동안 0.15초마다 손가락 커서를 다시 요청한다.
  const lastMove = useRef(0)
  const move = () => { const now = performance.now(); if (now - lastMove.current > 150) { lastMove.current = now; api?.setInteractive(true) } }
  const leave = () => { api?.setInteractive(false); hideTimer.current = setTimeout(() => setHover(false), 900) }

  const sendBack = async () => {
    if (ducking) return
    setDucking(true)
    api?.setInteractive(false)
    const result = await api?.act({ interventionId: show.interventionId, action: 'REST' })
    // 일시정지에 실패하면 포키는 내려가지 않고 안내한다.
    if (!result?.success) { setDucking(false); setFailed(true) }
  }

  const open = (hover || first || failed) && !ducking
  return <div className={`stack sign-alert${ducking ? ' is-ducking' : ''}${open ? ' is-open' : ''}`} style={{ '--lift': `${lift}px` } as React.CSSProperties}>
    <p className="sr-only" role="status" aria-live="polite">{show.say} {show.sub}</p>
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img className="sign" src="/design/poki-sign-break.webp" alt="" />
    <div className="sign-hint" aria-hidden={!open}>
      <span className="sign-tip">
        {failed ? <><strong>지금은 멈추지 못했어요</strong><small>FocusOn 앱에서 다시 시도해 주세요</small></>
          : <><strong>포키를 톡! 누르면 잠깐 쉬어요</strong><small>세션을 멈추고 쉬는 시간으로 넘어가요</small></>}
      </span>
      <button className="sign-rest" type="button" tabIndex={open ? 0 : -1} onPointerEnter={enter} onPointerMove={move} onPointerLeave={leave} onClick={() => { void api?.act({ interventionId: show.interventionId, action: 'CLOSE' }) }}>계속할게</button>
    </div>
    <button className="poki-hit" type="button" aria-label={`포키: ${show.say} 누르면 잠깐 쉬어요`} disabled={ducking}
      onPointerEnter={enter} onPointerMove={move} onPointerLeave={leave} onClick={() => { void sendBack() }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" src="/design/poki-peek-take-a-break.png" alt="" />
    </button>
  </div>
}
