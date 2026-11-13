'use client'

import { useEffect, useRef, useState } from 'react'
import type { FloatingShow, InterventionAction } from '../../electron/shared/intervention-contract.cjs'
import './floating.css'

// [주의] Renderer는 electron/shared에서 타입만 가져온다(goal-setting-shared-runtime-import-ERROR.md).
// 고개 내민 포키(B안, 2026-11-08 사용자 확정). 6장은 몸 위치가 같아 얼굴만 바뀐다.
const POKI = { GENTLE: 'noticed', NORMAL: 'long-distraction', ACTIVE: 'caught-you', BREAK: 'take-a-break', DISCONNECTED: 'chrome-disconnected' } as const

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
  // 이탈 알림·휴식 제안은 팻말 든 포키 + 누르면 쏙(#246, #253). 연결 끊김만 아래 카드다.
  if (show.kind !== 'DISCONNECTED') return <main className="floating-root">
    <SignAlert key={show.interventionId} show={show} lift={lift} onDone={() => setShow(null)} />
  </main>
  const act = async (action: InterventionAction) => {
    await window.focusOn?.interventions.act({ interventionId: show.interventionId, action })
    setShow(null)
  }

  // F07은 세션이 실제로 멈춘 오류라 시스템 안내(존댓말)·빨간 글자로 보여 준다(결정 031).
  return <main className="floating-root">
    {/* 포키가 먼저 화면 아래에서 고개를 내밀어 표정으로 알리고, 카드는 그 뒤에 포키 옆에 뜬다(2026-11-08 사용자 확정). */}
    <div className="stack" key={show.interventionId} style={{ '--lift': `${lift}px` } as React.CSSProperties}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" {...hover} src={`/design/poki-peek-${POKI.DISCONNECTED}.png`} alt="" />
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

// 강도별 팻말(AI로 글자까지 그린 이미지). 휴식 제안은 강도와 상관없이 휴식 팻말 하나다. 표정은 기존 고개 내민 포키 그대로.
const SIGN = { GENTLE: 'gentle', NORMAL: 'normal', ACTIVE: 'active' } as const
// 처음 한 번은 누르는 방법을 안내한다. 이 기기에만 기억한다.
const HINT_KEY = 'focuson.pokiHintSeen'
const DUCK_MS = 450

/**
 * 이탈 알림 (#246)·휴식 제안 (#253). 포키가 팻말을 들고 나온다. 포키를 누르면 꾹 눌렸다가 굴로 '쏙' 들어간다.
 * - 이탈 알림: 누르면 확인했어(확인 기록). 버튼은 '잠시 쉬기'.
 * - 휴식 제안: 팻말이 '잠깐 쉬어가자'라 누르면 잠시 쉬기(세션 일시정지). 버튼은 '계속할게'(해제 기록).
 * 첫 알림이거나 포키에 마우스를 올리면 안내와 버튼이 보인다. 대사는 화면 읽기용 숨김 글자로 남긴다.
 */
function SignAlert({ show, lift, onDone }: { show: FloatingShow; lift: number; onDone: () => void }) {
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

  const isBreak = show.kind === 'BREAK'
  const duck = () => new Promise((resolve) => setTimeout(resolve, DUCK_MS))
  const sendBack = async () => {
    if (ducking) return
    setDucking(true)
    api?.setInteractive(false)
    if (isBreak) {
      // 일시정지에 실패하면 들어가지 않고 안내를 띄운다(시스템 안내는 존댓말).
      const result = await api?.act({ interventionId: show.interventionId, action: 'REST' })
      if (!result?.success) { setDucking(false); setFailed(true); return }
      await duck()
      onDone()
      return
    }
    await api?.act({ interventionId: show.interventionId, action: 'RECENTER' })
    await duck()
    await api?.act({ interventionId: show.interventionId, action: 'ACK' })
    onDone()
  }
  // 이탈 알림은 잠시 쉬기, 휴식 제안은 계속할게.
  const side = async () => {
    const result = await api?.act({ interventionId: show.interventionId, action: isBreak ? 'CLOSE' : 'REST' })
    if (!isBreak && !result?.success) { setFailed(true); return }
    onDone()
  }

  const level = show.level
  const open = (hover || first || failed) && !ducking
  return <div className={`stack sign-alert${ducking ? ' is-ducking' : ''}${open ? ' is-open' : ''}`} style={{ '--lift': `${lift}px` } as React.CSSProperties}>
    <p className="sr-only" role="status" aria-live="polite">{show.say} {show.sub}</p>
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img className="sign" src={`/design/poki-sign-${isBreak ? 'break' : SIGN[level]}.webp`} alt="" />
    <div className="sign-hint" aria-hidden={!open}>
      <span className="sign-tip">
        {failed ? <><strong>지금은 멈추지 못했어요</strong><small>FocusOn 앱에서 다시 시도해 주세요</small></>
          : isBreak ? <><strong>포키를 톡! 누르면 잠깐 쉬어요</strong><small>세션을 멈추고 쉬는 시간으로 넘어가요</small></>
            : <><strong>포키를 톡! 누르면 돌아가요</strong><small>지금 목표 · {show.goalText}</small></>}
      </span>
      <button className="sign-rest" type="button" tabIndex={open ? 0 : -1} onPointerEnter={enter} onPointerMove={move} onPointerLeave={leave} onClick={() => { void side() }}>{isBreak ? '계속할게' : '잠시 쉬기'}</button>
    </div>
    <button className="poki-hit" type="button" aria-label={`포키: ${show.say} ${isBreak ? '누르면 잠깐 쉬어요' : '누르면 목표로 돌아가요'}`} disabled={ducking}
      onPointerEnter={enter} onPointerMove={move} onPointerLeave={leave} onClick={() => { void sendBack() }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" src={`/design/poki-peek-${isBreak ? POKI.BREAK : POKI[level]}.png`} alt="" />
    </button>
  </div>
}
