'use client'

import { useEffect, useState } from 'react'
import type { FloatingShow, InterventionAction } from '../../electron/shared/intervention-contract.cjs'
import './floating.css'

// [주의] Renderer는 electron/shared에서 타입만 가져온다(goal-setting-shared-runtime-import-ERROR.md).
const POKI = { GENTLE: 'master-v3', NORMAL: 'curious-v8', ACTIVE: 'angry-v2', BREAK: 'curious-v8', RECENTER: 'master-v3' } as const

type View = { show: FloatingShow; step: 'say' | 'recenter' }

/** 화면 위 포키 F04·F05 (FocusOn-design/floating-poki.html). 문구는 Main이 정해 보낸다. 판정·타이머는 바꾸지 않는다. */
export function FloatingPoki() {
  const [view, setView] = useState<View | null>(null)

  useEffect(() => window.focusOn?.interventions.onShow((show) => setView({ show, step: 'say' })), [])

  if (!view) return <main className="floating-root" />
  const { show, step } = view
  const act = (action: InterventionAction) => {
    void window.focusOn?.interventions.act({ interventionId: show.interventionId, action })
    if (action === 'RECENTER') setView({ show, step: 'recenter' })
    else setView(null)
  }
  const recenter = step === 'recenter'
  const poki = recenter ? POKI.RECENTER : show.kind === 'BREAK' ? POKI.BREAK : POKI[show.level]
  const actions: Array<[string, InterventionAction, boolean]> = recenter
    ? [['확인했어', 'ACK', true]]
    : show.kind === 'BREAK' ? [['잠시 쉬기', 'REST', true], ['계속할게', 'CLOSE', false]] : [['조금 더 보기', 'RECENTER', true], ['잠시 쉬기', 'REST', false]]

  return <main className="floating-root">
    <div className="pop" key={step}>
      <div className="bubble" role="status" aria-live="polite">
        <span>{recenter ? '지금 목표는 이거야.' : show.say}</span>
        <small>{recenter ? show.goalText : show.sub}</small>
        <div className="say-actions">
          {actions.map(([label, action, primary]) => <button key={action} type="button" className={primary ? 'primary' : ''} onClick={() => act(action)}>{label}</button>)}
        </div>
        {/* 말꼬리: 말풍선 오른쪽 아래에서 나와 포키 얼굴 쪽으로 휘어진다. 바탕을 말풍선 테두리 위에 겹쳐 이어 붙인다. */}
        <svg className="tail" viewBox="0 0 36 30" aria-hidden="true">
          <path className="tail-fill" d="M0 0C9 3 19 10 34 26C20 22 9 21 0 18Z" />
          <path className="tail-line" d="M0 0C9 3 19 10 34 26C20 22 9 21 0 18" />
        </svg>
        <button className="close" type="button" aria-label="포키 닫기" onClick={() => act(recenter ? 'ACK' : 'CLOSE')}>
          <svg viewBox="0 0 12 12" aria-hidden="true"><path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
        </button>
      </div>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" src={`/design/poki-floating-${poki}.png`} alt="" />
    </div>
  </main>
}
