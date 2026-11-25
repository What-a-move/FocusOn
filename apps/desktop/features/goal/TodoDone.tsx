'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import type { SessionGoal } from '../../electron/shared/session-contract.cjs'
import { ENDED_GOALS_KEY, goalApi } from '../../shared/api/goal-api'
import '../../shared/styles/onboarding-window.css'
import './goal.css'
import './todo-pick.css'

const TICK = <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3.5 8.5l3 3 6-7" /></svg>

function readEnded(sessionId: string | null): SessionGoal[] {
  try {
    const saved = JSON.parse(sessionStorage.getItem(ENDED_GOALS_KEY) ?? 'null') as { sessionId?: unknown; goals?: unknown } | null
    if (!sessionId || saved?.sessionId !== sessionId || !Array.isArray(saved.goals)) return []
    return saved.goals.filter((goal): goal is SessionGoal => typeof goal?.goalId === 'string' && typeof goal?.goalText === 'string').slice(0, 3)
  } catch {
    return []
  }
}

/** 세션 끝 「다 했어요」 (FocusOn-design/todo-goals.html?view=done). 끝낸 할 일을 DONE으로 바꾸고 결과로 넘어간다. */
export function TodoDone() {
  const router = useRouter()
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [goals, setGoals] = useState<SessionGoal[]>([])
  const [finished, setFinished] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  // 저장에 한 번 실패하면 안내하고, 그다음엔 결과로 그냥 넘어갈 수 있다(저장 실패가 결과를 막지 않는다).
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    queueMicrotask(() => {
      const id = new URLSearchParams(window.location.search).get('session')
      const ended = readEnded(id)
      if (!id) { router.replace('/'); return }
      if (!ended.length) { router.replace(`/session-result/?session=${encodeURIComponent(id)}`); return }
      setSessionId(id)
      setGoals(ended)
    })
  }, [router])

  const toResult = () => {
    try { sessionStorage.removeItem(ENDED_GOALS_KEY) } catch { /* 다음 세션이 덮어쓴다. */ }
    if (sessionId) router.replace(`/session-result/?session=${encodeURIComponent(sessionId)}`)
  }
  const save = async () => {
    if (busy) return
    setBusy(true)
    setFailed(false)
    const results = await Promise.all(finished.map((goalId) => goalApi.complete(goalId)))
    setBusy(false)
    // 이미 지운 할 일(GOAL_NOT_FOUND)은 다시 시도해도 소용없으니 성공으로 본다.
    const left = finished.filter((_, index) => { const result = results[index]; return !result.success && result.error.code !== 'GOAL_NOT_FOUND' })
    if (!left.length) { toResult(); return }
    setFinished(left)
    setFailed(true)
  }
  const next = () => { if (failed || !finished.length) toResult(); else void save() }
  const toggle = (goalId: string) => setFinished((current) => current.includes(goalId) ? current.filter((id) => id !== goalId) : [...current, goalId])

  if (!goals.length) return <main className="app-window onboarding-window goal-window todo-window" aria-busy="true" />

  return <main className="app-window onboarding-window goal-window todo-window">
    <div className="flow">
      <div className="body">
        <div className="todo-wrap done-wrap">
          <div className="todo-speaker">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="poki" src="/design/poki-celebrate.png" alt="축하하는 포키" />
            <h1>수고했어요!<br />끝낸 할 일이 <span className="hl">있나요</span>?</h1>
          </div>
          <ul className="done-list" aria-label="이번 집중의 할 일">
            {goals.map((goal, index) => <li className="done-row" data-c={index + 1} key={goal.goalId} style={{ animationDelay: `${index * .06}s` }}>
              <span className="badge" aria-hidden="true">{index + 1}</span>
              <strong>{goal.goalText}</strong>
              <button className="toggle" type="button" aria-pressed={finished.includes(goal.goalId)} disabled={busy} onClick={() => toggle(goal.goalId)}>
                <span className="tick">{TICK}</span>다 했어요
              </button>
            </li>)}
          </ul>
          {failed && <div className="todo-note is-error" role="alert">
            <div><strong>‘다 했어요’를 저장하지 못했어요.</strong><span>할 일 목록에는 그대로 남아 있어요.</span></div>
            <button className="btn btn-ghost" type="button" disabled={busy} onClick={() => { void save() }}>다시 시도</button>
          </div>}
        </div>
      </div>
      <footer className="footer">
        <div className="footer-inner">
          <span className="spacer" />
          <button className="btn btn-primary" type="button" disabled={busy} onClick={next}>
            {busy ? '저장하는 중…' : failed ? '그냥 결과 보기' : '결과 보기'}<span className="arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </footer>
    </div>
  </main>
}
