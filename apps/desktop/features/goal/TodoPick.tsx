'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import type { SessionGoal } from '../../electron/shared/session-contract.cjs'
import { CONFIRMED_GOAL_KEY, MAX_TODO_PICK, readPicked } from '../../shared/api/goal-api'
import { useOpenGoals } from '../../shared/api/useOpenGoals'
import '../../shared/styles/onboarding-window.css'
import './goal.css'
import './todo-pick.css'

/** 할 일 고르기 (FocusOn-design/todo-goals.html?view=pick). 열린 할 일에서 최대 3개를 골라 집중 준비로 넘긴다. */
export function TodoPick() {
  const router = useRouter()
  const { goals, nextCursor, loading, failed, load } = useOpenGoals()
  const [picked, setPicked] = useState<SessionGoal[]>([])

  useEffect(() => { queueMicrotask(() => setPicked(readPicked())) }, [])

  // 목록에 없는(닫힌) 할 일은 고른 것에서 뺀다. 목록을 다 받기 전에는 그대로 둔다.
  const visible = !loading && !failed && !nextCursor ? picked.filter(({ goalId }) => goals.some((goal) => goal.goalId === goalId)) : picked
  const full = visible.length >= MAX_TODO_PICK
  const toggle = (goal: SessionGoal) => setPicked((current) => current.some(({ goalId }) => goalId === goal.goalId)
    ? current.filter(({ goalId }) => goalId !== goal.goalId)
    : current.length >= MAX_TODO_PICK ? current : [...current, { goalId: goal.goalId, goalText: goal.goalText }])
  const start = () => {
    if (!visible.length) return
    try { sessionStorage.setItem(CONFIRMED_GOAL_KEY, JSON.stringify(visible)) } catch { /* 저장소를 못 쓰면 집중 준비에서 다시 고르게 된다. */ }
    router.push('/session-setup/')
  }

  const empty = !loading && !failed && goals.length === 0

  return <main className="app-window onboarding-window goal-window todo-window">
    <div className="flow">
      <div className="top">
        <Link className="close" href="/" aria-label="닫고 홈으로">✕</Link>
        <div className="progress" aria-hidden="true"><i className="active" /><i /><i /></div>
        <span className="step-count">고르기</span>
      </div>

      <div className="body">
        {failed && goals.length === 0 ? <div className="todo-state" role="alert">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/design/poki-main-planning.png" alt="" />
          <strong className="is-error">할 일 목록을 불러오지 못했어요.</strong>
          <span>인터넷 연결을 확인한 뒤 다시 시도해 주세요.</span>
          <button className="btn btn-ghost" type="button" onClick={() => { void load() }}>다시 시도</button>
        </div> : empty ? <div className="todo-state">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/design/poki-empty-note.png" alt="" />
          <strong>아직 적어 둔 할 일이 없어요</strong>
          <Link className="btn btn-primary" href="/goal/">할 일 적기</Link>
        </div> : <div className="todo-wrap">
          <div className="todo-speaker">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="poki" src="/design/poki-main-planning.png" alt="체크리스트를 든 포키" />
            <div>
              <h1>오늘은 뭐부터 <span className="hl">할까요</span>?</h1>
              {full && <p>3개까지 고를 수 있어요.</p>}
            </div>
          </div>
          <ul className="todo-grid" aria-label="할 일 목록">
            {goals.map((goal, index) => {
              const order = visible.findIndex(({ goalId }) => goalId === goal.goalId) + 1
              const blocked = full && !order
              return <li key={goal.goalId}>
                <button className="todo-card" type="button" role="checkbox" aria-checked={order > 0} aria-disabled={blocked}
                  data-c={order || undefined} style={{ animationDelay: `${Math.min(index, 8) * .04}s` }}
                  onClick={() => { if (!blocked) toggle(goal) }}>
                  <span className="badge" aria-hidden="true">{order || ''}</span>
                  <strong>{goal.goalText}</strong>
                </button>
              </li>
            })}
            <li><Link className="add-card" href="/goal/"><b aria-hidden="true">+</b>새 할 일 적기</Link></li>
          </ul>
          {loading && <p className="todo-more" role="status">할 일을 불러오고 있어요.</p>}
          {failed && <p className="todo-more" role="alert">더 불러오지 못했어요. <button className="btn btn-ghost" type="button" onClick={() => { void load(nextCursor ?? undefined) }}>다시 시도</button></p>}
          {!loading && !failed && nextCursor && <p className="todo-more"><button className="btn btn-ghost" type="button" onClick={() => { void load(nextCursor) }}>할 일 더 보기</button></p>}
        </div>}
      </div>

      {!empty && !(failed && goals.length === 0) && <footer className="footer">
        <div className="footer-inner">
          <div className="tray" aria-live="polite">
            {visible.length ? visible.map((goal, index) => <span className="pill" data-c={index + 1} key={goal.goalId}>
              <span className="badge" aria-hidden="true">{index + 1}</span><span>{goal.goalText}</span>
            </span>) : <span className="tray-note">할 일을 골라 주세요.</span>}
          </div>
          <button className="btn btn-primary" type="button" disabled={!visible.length} onClick={start}>
            {visible.length ? `${visible.length}개로 시작` : '시작'}<span className="arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </footer>}
    </div>
  </main>
}
