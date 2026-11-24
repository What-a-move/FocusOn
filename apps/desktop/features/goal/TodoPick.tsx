'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import type { GoalProfile } from '../../electron/shared/goal-contract.cjs'
import type { SessionGoal } from '../../electron/shared/session-contract.cjs'
import { CONFIRMED_GOAL_KEY, MAX_TODO_PICK, readPicked } from '../../shared/api/goal-api'
import { useOpenGoals } from '../../shared/api/useOpenGoals'
import '../../shared/styles/onboarding-window.css'
import './goal.css'
import './todo-pick.css'

/** 할 일 고르기 → 목표 확인 (FocusOn-design/todo-goals.html?view=pick·review). 최대 3개를 골라 범위를 확인한 뒤 집중 준비로 넘긴다. */
export function TodoPick() {
  const router = useRouter()
  const { goals, nextCursor, loading, failed, load } = useOpenGoals()
  const [picked, setPicked] = useState<SessionGoal[]>([])
  const [view, setView] = useState<'pick' | 'review'>('pick')
  const [slide, setSlide] = useState(0)

  useEffect(() => {
    queueMicrotask(() => setPicked(readPicked()))
    const unsubscribe = window.focusOn?.auth.onSessionChanged((snapshot) => { if (snapshot.status === 'SIGNED_OUT') router.replace('/') })
    return () => unsubscribe?.()
  }, [router])

  // 목록에 없는(닫힌) 할 일은 고른 것에서 뺀다. 목록을 다 받기 전에는 그대로 둔다.
  const visible = !loading && !failed && !nextCursor ? picked.filter(({ goalId }) => goals.some((goal) => goal.goalId === goalId)) : picked
  const full = visible.length >= MAX_TODO_PICK
  const toggle = (goal: SessionGoal) => setPicked((current) => current.some(({ goalId }) => goalId === goal.goalId)
    ? current.filter(({ goalId }) => goalId !== goal.goalId)
    : current.length >= MAX_TODO_PICK ? current : [...current, { goalId: goal.goalId, goalText: goal.goalText }])
  const toSetup = () => {
    if (!visible.length) return
    try { sessionStorage.setItem(CONFIRMED_GOAL_KEY, JSON.stringify(visible)) } catch { /* 저장소를 못 쓰면 집중 준비에서 다시 고르게 된다. */ }
    router.push('/session-setup/')
  }
  const review = () => { if (visible.length) { setSlide(0); setView('review') } }
  const profileOf = (goalId: string) => goals.find((goal) => goal.goalId === goalId)?.goalProfile
  const last = visible.length - 1
  const move = (index: number) => setSlide(Math.max(0, Math.min(last, index)))

  useEffect(() => {
    if (view !== 'review') return
    const onKey = (event: KeyboardEvent) => {
      if (event.repeat) return
      if (event.key === 'ArrowRight') setSlide((current) => Math.min(last, current + 1))
      if (event.key === 'ArrowLeft') setSlide((current) => Math.max(0, current - 1))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [view, last])

  const empty = !loading && !failed && goals.length === 0

  return <main className="app-window onboarding-window goal-window todo-window">
    <div className="flow">
      <div className="top">
        <Link className="close" href="/" aria-label="닫고 홈으로">✕</Link>
        <div className="progress" aria-hidden="true"><i className={view === 'pick' ? 'active' : 'done'} /><i className={view === 'review' ? 'active' : ''} /><i /></div>
        <span className="step-count">{view === 'pick' ? '고르기' : '확인'}</span>
      </div>

      <div className="body">
        {view === 'review' ? <div className="review-wrap">
          <div className="review-head">
            <div className="todo-speaker">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img className="poki" src="/design/poki-main-planning.png" alt="" />
              <h1>공부로 볼 범위</h1>
            </div>
            <div className="pager" role="tablist" aria-label="할 일">
              {visible.map((goal, index) => <button type="button" role="tab" aria-selected={index === slide} aria-current={index === slide} data-c={index + 1} key={goal.goalId} onClick={() => move(index)}>
                <span className="badge" aria-hidden="true">{index + 1}</span><span>{goal.goalText}</span>
              </button>)}
            </div>
          </div>
          <div className="center-zone"><div className="stage">
            <button className="arrow-btn" type="button" aria-label="이전 할 일" disabled={slide === 0} onClick={() => move(slide - 1)}>‹</button>
            <div className="viewport"><div className="track" style={{ transform: `translateX(-${slide * 100}%)` }}>
              {visible.map((goal, index) => <ReviewSlide key={goal.goalId} goal={goal} order={index + 1} total={visible.length} profile={profileOf(goal.goalId)} current={index === slide} />)}
            </div></div>
            <button className="arrow-btn" type="button" aria-label="다음 할 일" disabled={slide === last} onClick={() => move(slide + 1)}>›</button>
          </div></div>
        </div> : failed && goals.length === 0 ? <div className="todo-state" role="alert">
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

      {view === 'review' && <footer className="footer">
        <div className="footer-inner">
          <button className="btn btn-ghost" type="button" onClick={() => setView('pick')}>할 일 다시 고르기</button>
          {slide < last
            ? <button className="btn btn-primary" type="button" onClick={() => move(slide + 1)}>다음 할 일<span className="arrow" aria-hidden="true">→</span></button>
            : <button className="btn btn-primary" type="button" onClick={toSetup}>시간 정하기<span className="arrow" aria-hidden="true">→</span></button>}
        </div>
      </footer>}
      {view === 'pick' && !empty && !(failed && goals.length === 0) && <footer className="footer">
        <div className="footer-inner">
          <div className="tray" aria-live="polite">
            {visible.length ? visible.map((goal, index) => <span className="pill" data-c={index + 1} key={goal.goalId}>
              <span className="badge" aria-hidden="true">{index + 1}</span><span>{goal.goalText}</span>
            </span>) : <span className="tray-note">할 일을 골라 주세요.</span>}
          </div>
          <button className="btn btn-primary" type="button" disabled={!visible.length} onClick={review}>
            {visible.length ? `${visible.length}개로 시작` : '시작'}<span className="arrow" aria-hidden="true">→</span>
          </button>
        </div>
      </footer>}
    </div>
  </main>
}

/** 할 일 하나의 범위. 위계: 할 일 이름 > 핵심 주제 > 함께 보는 주제·하게 될 활동. 범위 수정 API가 없어 보기만 한다. */
function ReviewSlide({ goal, order, total, profile, current }: { goal: SessionGoal; order: number; total: number; profile?: GoalProfile; current: boolean }) {
  return <section className="slide" data-c={order} aria-hidden={!current} inert={!current} aria-label={`${order}번 할 일`}>
    <div className="slide-head"><span className="badge" aria-hidden="true">{order}</span><h2>{goal.goalText}</h2><span className="count">{order} / {total}</span></div>
    {profile ? <>
      <div className="core-block">
        <p className="label">핵심 주제</p>
        <ul className="core-list">{profile.coreTopics.map((topic) => <li key={topic}><span>{topic}</span></li>)}</ul>
      </div>
      <div className="sub-block">
        <ProfileList title="함께 보는 주제" items={profile.supportingTopics} />
        <ProfileList title="하게 될 활동" items={profile.expectedActivities} />
      </div>
    </> : <div className="missing"><strong>범위를 만들지 못했어요.</strong><span>할 일 이름만 보고 판단할게요.</span></div>}
  </section>
}

function ProfileList({ title, items }: { title: string; items: string[] }) {
  return <div>
    <p className="label">{title}</p>
    {items.length ? <ul className="items">{items.map((item) => <li key={item}><span>{item}</span></li>)}</ul> : <p className="items-empty">없음</p>}
  </div>
}
