'use client'

import { useEffect, useState } from 'react'
import type { FloatingShow, InterventionAction } from '../../electron/shared/intervention-contract.cjs'
import './floating.css'

// [주의] Renderer는 electron/shared에서 타입만 가져온다(goal-setting-shared-runtime-import-ERROR.md).
// 고개 내민 포키(B안, 2026-11-08 사용자 확정). 6장은 몸 위치가 같아 얼굴만 바뀐다.
const POKI = { GENTLE: 'noticed', NORMAL: 'long-distraction', ACTIVE: 'caught-you', BREAK: 'take-a-break', RECENTER: 'back-to-study', DISCONNECTED: 'chrome-disconnected' } as const

type View = { show: FloatingShow; step: 'say' | 'recenter'; failed?: boolean }

/** 화면 위 포키 F04·F05·F07 (FocusOn-design/floating-poki.html). 문구는 Main이 정해 보낸다. 판정·타이머는 바꾸지 않는다. */
export function FloatingPoki() {
  const [view, setView] = useState<View | null>(null)
  const [lift, setLift] = useState(0)

  useEffect(() => window.focusOn?.interventions.onShow((show) => setView({ show, step: 'say' })), [])
  useEffect(() => window.focusOn?.interventions.onLift(setLift), [])
  // 카드·포키 위에서만 클릭을 받고, 나머지 투명한 곳은 아래 앱으로 통과시킨다(#225). 닫히면 다시 통과.
  useEffect(() => { if (!view) window.focusOn?.interventions.setInteractive(false) }, [view])
  const hover = { onPointerEnter: () => window.focusOn?.interventions.setInteractive(true), onPointerLeave: () => window.focusOn?.interventions.setInteractive(false) }

  if (!view) return <main className="floating-root" />
  const { show, step } = view
  const act = async (action: InterventionAction) => {
    if (action === 'RECENTER') setView({ show, step: 'recenter' })
    const result = await window.focusOn?.interventions.act({ interventionId: show.interventionId, action })
    // 잠시 쉬기에서 일시정지하지 못했으면 창을 닫지 않고 안내한다(시스템 안내는 존댓말).
    if (action === 'REST' && !result?.success) { setView({ show, step, failed: true }); return }
    if (action !== 'RECENTER') setView(null)
  }
  const recenter = step === 'recenter'
  // F07은 세션이 실제로 멈춘 오류라 시스템 안내(존댓말)·빨간 글자로 보여 준다(결정 031).
  const disconnected = show.kind === 'DISCONNECTED'
  const poki = recenter ? POKI.RECENTER : show.kind === 'OFF_TASK' ? POKI[show.level] : POKI[show.kind]
  const actions: Array<[string, InterventionAction, boolean]> = recenter
    ? [['알겠어', 'ACK', true]]
    : disconnected ? [['앱 열기', 'OPEN_APP', true]]
      : show.kind === 'BREAK' ? [['잠시 쉬기', 'REST', true], ['계속할게', 'CLOSE', false]] : [['확인했어', 'RECENTER', true], ['잠시 쉬기', 'REST', false]]


  return <main className="floating-root">
    {/* 포키가 먼저 화면 아래에서 고개를 내밀어 표정으로 알리고, 카드는 그 뒤에 포키 옆에 뜬다(2026-11-08 사용자 확정).
        새 알림이면 둘 다 다시 나오고, 목표 안내로 넘어갈 때는 카드 내용과 표정만 바뀐다. */}
    <div className="stack" key={show.interventionId} style={{ '--lift': `${lift}px` } as React.CSSProperties}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" {...hover} src={`/design/poki-peek-${poki}.png`} alt="" />
      <div {...hover} className={`card${disconnected ? ' is-error' : ''}`} role={disconnected ? 'alert' : 'status'} aria-live={disconnected ? 'assertive' : 'polite'}>
        <div key={step} className="card-body">
          <em className="who">{disconnected ? 'FocusOn' : '포키'}</em>
          <span>{recenter ? '지금 목표는 이거야.' : show.say}</span>
          <small>{recenter ? show.goalText : show.sub}</small>
          {view.failed && <p className="fail" role="alert">지금은 멈추지 못했어요. FocusOn 앱에서 다시 시도해 주세요.</p>}
          <div className="say-actions">
            {actions.map(([label, action, primary]) => <button key={action} type="button" className={primary ? 'primary' : ''} onClick={() => { void act(action) }}>{label}</button>)}
          </div>
        </div>
        <button className="close" type="button" aria-label="포키 닫기" onClick={() => { void act(recenter ? 'ACK' : 'CLOSE') }}>
          <svg viewBox="0 0 12 12" aria-hidden="true"><path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
        </button>
      </div>
    </div>
  </main>
}
