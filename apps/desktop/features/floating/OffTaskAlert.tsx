'use client'

import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import type { FloatingShow, OffTaskPresentation } from '../../electron/shared/intervention-contract.cjs'
import { createGentleTimer } from './gentle-timer'

const RISE_MS = { gentle: 700, peek: 600, normal: 1300, active: 1300 }
const ACTIVE_COPY = ['언제까지 놀기만 할래?', '공부 좀 하자..', '집중해!!']
const STUDY_TOAST = '공부로 표시했어요.'
// Renderer에서는 Electron 계약을 타입으로만 참조한다.
const CAMERA_ID: typeof import('../../electron/shared/intervention-contract.cjs').CAMERA_ID = 'local-camera'

type Dismissal = 'return' | 'study' | 'timeout'

export function OffTaskAlert({ show, presentation, lift }: { show: FloatingShow; presentation: OffTaskPresentation; lift: number }) {
  const { stage } = presentation
  const canMarkStudy = show.interventionId !== CAMERA_ID
  const isCorner = stage === 'gentle' || stage === 'peek'
  const [up, setUp] = useState(false)
  const [entered, setEntered] = useState(false)
  const [count, setCount] = useState(5)
  const [dismissal, setDismissal] = useState<Dismissal | null>(null)
  const dismissed = useRef(false)
  const gentleTimer = useRef<ReturnType<typeof createGentleTimer> | null>(null)
  const lastMove = useRef(0)
  const ready = !dismissal && (isCorner || (entered && (stage === 'normal' || count === 0)))

  const dismiss = useCallback((reason: Dismissal) => {
    if (dismissed.current) return
    dismissed.current = true
    gentleTimer.current?.cancel()
    setDismissal(reason)
    void window.focusOn?.interventions.act({
      interventionId: show.interventionId,
      action: reason === 'return' ? 'RECENTER' : reason === 'study' ? 'NOT_OFF_TASK' : 'CLOSE',
    })
  }, [show.interventionId])

  useEffect(() => {
    const noMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const startDelay = noMotion ? 0 : 50
    const riseMs = noMotion ? 0 : RISE_MS[stage]
    const gentle = stage === 'gentle' ? createGentleTimer(() => dismiss('timeout')) : null
    gentleTimer.current = gentle
    let countdown: ReturnType<typeof setInterval> | undefined
    const start = setTimeout(() => {
      gentle?.start(riseMs)
      setUp(true)
    }, startDelay)
    const settle = setTimeout(() => {
      setEntered(true)
      if (stage !== 'active') return
      const deadline = performance.now() + 5000
      countdown = setInterval(() => {
        const remaining = Math.max(0, Math.ceil((deadline - performance.now()) / 1000))
        setCount(remaining)
        if (remaining === 0) clearInterval(countdown)
      }, 100)
    }, startDelay + riseMs)
    return () => {
      clearTimeout(start)
      clearTimeout(settle)
      clearInterval(countdown)
      gentle?.cancel()
    }
  }, [dismiss, stage])

  useEffect(() => {
    if (!dismissal) return
    const noMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const timer = setTimeout(() => {
      void window.focusOn?.interventions.act({ interventionId: show.interventionId, action: 'ACK' })
    }, dismissal === 'study' ? 2400 : noMotion ? 0 : RISE_MS[stage])
    return () => clearTimeout(timer)
  }, [dismissal, show.interventionId, stage])

  const pointer = {
    onPointerEnter: () => {
      if (!ready) return
      gentleTimer.current?.enter()
      window.focusOn?.interventions.setInteractive(true)
    },
    onPointerMove: () => {
      if (!ready || performance.now() - lastMove.current < 150) return
      lastMove.current = performance.now()
      window.focusOn?.interventions.setInteractive(true)
    },
    onPointerLeave: () => {
      if (!ready) return
      gentleTimer.current?.leave()
      window.focusOn?.interventions.setInteractive(false)
    },
  }
  const returnToStudy = () => { if (ready) dismiss('return') }
  const shown = up && !dismissal
  const flags = `${shown ? ' is-up' : ''}${ready ? ' is-ready' : ''}${dismissal ? ' is-down' : ''}`

  /* eslint-disable @next/next/no-img-element -- 로컬 그림을 CSS의 시안 비율대로 배치한다. */
  if ('side' in presentation) {
    const isGentle = stage === 'gentle'
    return <div className="off-task-corner" style={{ '--lift': `${lift}px`, '--screen-width': `${window.screen.width}px` } as CSSProperties}>
      {isGentle
        ? <button className={`gentle-sign ${presentation.side}${flags}`} type="button" aria-label="잠깐! 알림 닫기" disabled={!!dismissal}
          {...pointer} onClick={returnToStudy}>
          <img src="/design/poki-sign-gentle.webp" alt="" />
        </button>
        : <div className={`peek-alert ${presentation.side}${flags}`}>
          <button className="peek-sign" type="button" aria-label="딴짓 감지! 공부로 돌아가기" tabIndex={-1} disabled={!!dismissal} {...pointer} onClick={returnToStudy}>
            <img src="/design/poki-sign-normal.webp" alt="" />
          </button>
          <button className="peek-poki" type="button" aria-label="딴짓 감지! 공부로 돌아가기" disabled={!!dismissal} {...pointer} onClick={returnToStudy}>
            <img src="/design/poki-peek-long-distraction.png" alt="" />
          </button>
        </div>}
    </div>
  }

  const active = presentation.stage === 'active'
  const art = active ? `poki-active-sign-${presentation.art}` : 'poki-normal-sign-1'
  const caption = active ? ACTIVE_COPY[presentation.art - 1] : '어? 또??'
  return <>
    <div className={`off-task-backdrop ${stage}${shown ? ' is-up' : ''}`} />
    <div className={`off-task-stage ${stage}${flags}`}>
      <div className="off-task-art">
        <img src={`/design/${art}.webp`} alt={caption} />
      </div>
      <button className="stage-poki" type="button" tabIndex={-1} aria-label="공부로 돌아가기" disabled={!ready} {...pointer} onClick={returnToStudy} />
      <button className="stage-sign" type="button" aria-label={`${caption} 공부로 돌아가기`} disabled={!ready} {...pointer} onClick={returnToStudy} />
      <div className="off-task-actions">
        {active && <span className="off-task-count" aria-live="polite" aria-hidden={count === 0}>{count === 0 ? '' : count}</span>}
        {canMarkStudy && <button className="off-task-study" type="button" disabled={!ready} {...pointer} onClick={() => { if (ready) dismiss('study') }}>이것도 공부야</button>}
      </div>
    </div>
    {canMarkStudy && <div role="status" className={`off-task-toast${dismissal === 'study' ? ' is-up' : ''}`}>{dismissal === 'study' ? STUDY_TOAST : ''}</div>}
  </>
  /* eslint-enable @next/next/no-img-element */
}
