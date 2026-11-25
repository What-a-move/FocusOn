'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import type { GoalProfile } from '../../electron/shared/goal-contract.cjs'
import type { SessionGoal } from '../../electron/shared/session-contract.cjs'
import { CONFIRMED_GOAL_KEY, goalApi, MAX_TODO_PICK, readPicked, TODO_SAVED_KEY } from '../../shared/api/goal-api'
import { errorText } from '../../shared/lib/error-text'
import { useOpenGoals } from '../../shared/api/useOpenGoals'
import '../../shared/styles/onboarding-window.css'
import './goal.css'
import './todo-pick.css'

const UNDO_MS = 5000

/** 할 일 고르기 → 목표 확인 (FocusOn-design/todo-goals.html?view=pick·review). 최대 3개를 골라 범위를 확인한 뒤 집중 준비로 넘긴다. */
export function TodoPick() {
  const router = useRouter()
  const { goals, nextCursor, loading, failed, load } = useOpenGoals()
  const [picked, setPicked] = useState<SessionGoal[]>([])
  const [view, setView] = useState<'pick' | 'review'>('pick')
  const [slide, setSlide] = useState(0)
  // 지우기: 서버에 되살리기 API가 없어 먼저 숨기고 UNDO_MS 뒤에 DELETE를 보낸다. 그 사이 되돌리기를 누르면 보내지 않는다.
  const [hidden, setHidden] = useState<string[]>([])
  const [saved, setSaved] = useState('')
  const [note, setNote] = useState<{ kind: 'deleted' | 'failed' | 'blocked'; text: string } | null>(null)
  // 목표 확인에서 고친 범위(서버가 돌려준 값). 목록을 다시 받기 전까지 이 값을 보여 준다.
  const [edits, setEdits] = useState<Record<string, GoalProfile>>({})
  const [editing, setEditing] = useState<string | null>(null)
  const [reviewNote, setReviewNote] = useState<{ title: string; body: string } | null>(null)
  const pending = useRef<{ goal: SessionGoal; order: number; timer: ReturnType<typeof setTimeout> } | null>(null)

  useEffect(() => {
    queueMicrotask(() => {
      setPicked(readPicked())
      // 개발 모드는 effect를 두 번 돌리므로, 읽은 값이 있을 때만 바꾼다.
      try { const text = sessionStorage.getItem(TODO_SAVED_KEY); if (text) { setSaved(text); sessionStorage.removeItem(TODO_SAVED_KEY) } } catch { /* 안내만 빠진다. */ }
    })
    const unsubscribe = window.focusOn?.auth.onSessionChanged((snapshot) => { if (snapshot.status === 'SIGNED_OUT') router.replace('/') })
    return () => unsubscribe?.()
  }, [router])

  const shown = goals.filter((goal) => !hidden.includes(goal.goalId))
  // 목록에 없는(닫힌) 할 일은 고른 것에서 뺀다. 목록을 다 받기 전에는 그대로 둔다.
  const visible = !loading && !failed && !nextCursor ? picked.filter(({ goalId }) => shown.some((goal) => goal.goalId === goalId)) : picked

  const commit = async () => {
    const job = pending.current
    if (!job) return
    pending.current = null
    clearTimeout(job.timer)
    const result = await goalApi.remove(job.goal.goalId)
    if (result.success || result.error.code === 'GOAL_NOT_FOUND') { setNote((current) => current?.kind === 'deleted' && current.text === job.goal.goalText ? null : current); return }
    setHidden((current) => current.filter((id) => id !== job.goal.goalId))
    setNote({ kind: result.error.code === 'GOAL_UPDATE_BLOCKED' ? 'blocked' : 'failed', text: job.goal.goalText })
  }
  const remove = (goal: SessionGoal) => {
    void commit()
    const order = picked.findIndex(({ goalId }) => goalId === goal.goalId)
    pending.current = { goal: { goalId: goal.goalId, goalText: goal.goalText }, order, timer: setTimeout(() => { void commit() }, UNDO_MS) }
    setHidden((current) => [...current, goal.goalId])
    setPicked((current) => current.filter(({ goalId }) => goalId !== goal.goalId))
    setNote({ kind: 'deleted', text: goal.goalText })
  }
  const undo = () => {
    const job = pending.current
    if (!job) return
    pending.current = null
    clearTimeout(job.timer)
    setHidden((current) => current.filter((id) => id !== job.goal.goalId))
    if (job.order >= 0) setPicked((current) => current.length >= MAX_TODO_PICK ? current : [...current.slice(0, job.order), job.goal, ...current.slice(job.order)])
    setNote(null)
  }
  // 화면을 떠나도 지우기로 한 할 일은 지운다.
  useEffect(() => () => { const job = pending.current; if (job) { clearTimeout(job.timer); void goalApi.remove(job.goal.goalId) } }, [])

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
  const profileOf = (goalId: string) => edits[goalId] ?? goals.find((goal) => goal.goalId === goalId)?.goalProfile
  // 범위 고치기·다시 만들기. 한 번에 하나만 보내고, 실패하면 화면의 범위는 그대로 둔다(서버도 그대로다).
  const saveProfile = async (goalId: string, run: () => ReturnType<typeof goalApi.regenerateProfile>, regenerate: boolean) => {
    if (editing) return false
    setEditing(goalId)
    setReviewNote(null)
    const result = await run()
    setEditing(null)
    if (result.success) {
      const next = result.data.goalProfile
      if (next) setEdits((current) => ({ ...current, [goalId]: next }))
      else if (regenerate) setReviewNote({ title: '범위를 다시 만들지 못했어요.', body: '잠시 후 다시 시도해 주세요.' })
      return true
    }
    const code = result.error.code
    setReviewNote(code === 'GOAL_UPDATE_BLOCKED' ? { title: '이 할 일은 지금 고칠 수 없어요.', body: '다른 기기에서 이 할 일로 집중하고 있어요.' }
      : regenerate ? { title: '범위를 다시 만들지 못했어요.', body: code === 'GOAL_ANALYSIS_UNAVAILABLE' || code === 'GOAL_ANALYSIS_TIMEOUT' ? '잠시 후 다시 시도해 주세요.' : errorText(result.error) }
        : { title: '바꾼 범위를 저장하지 못했어요.', body: errorText(result.error) })
    return false
  }
  const last = visible.length - 1
  const move = (index: number) => setSlide(Math.max(0, Math.min(last, index)))

  useEffect(() => {
    if (view !== 'review') return
    const onKey = (event: KeyboardEvent) => {
      if (event.repeat || (event.target as HTMLElement | null)?.closest?.('input, textarea')) return
      if (event.key === 'ArrowRight') setSlide((current) => Math.min(last, current + 1))
      if (event.key === 'ArrowLeft') setSlide((current) => Math.max(0, current - 1))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [view, last])

  const empty = !loading && !failed && shown.length === 0

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
          {reviewNote && <div className="todo-note is-error review-note" role="alert">
            <div><strong>{reviewNote.title}</strong><span>{reviewNote.body}</span></div>
            <button className="btn btn-ghost" type="button" onClick={() => setReviewNote(null)}>닫기</button>
          </div>}
          <div className="center-zone"><div className="stage">
            <button className="arrow-btn" type="button" aria-label="이전 할 일" disabled={slide === 0} onClick={() => move(slide - 1)}>‹</button>
            <div className="viewport"><div className="track" style={{ transform: `translateX(-${slide * 100}%)` }}>
              {visible.map((goal, index) => <ReviewSlide key={goal.goalId} goal={goal} order={index + 1} total={visible.length} profile={profileOf(goal.goalId)} current={index === slide}
                busy={editing === goal.goalId} locked={editing !== null}
                onChange={(next) => saveProfile(goal.goalId, () => goalApi.updateProfile(goal.goalId, next), false)}
                onRegenerate={() => { void saveProfile(goal.goalId, () => goalApi.regenerateProfile(goal.goalId), true) }} />)}
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
              {saved ? <p className="saved" role="status">{saved}</p> : full && <p>3개까지 고를 수 있어요.</p>}
            </div>
          </div>
          {note && <div className={`todo-note${note.kind === 'deleted' ? '' : ' is-error'}`} role={note.kind === 'deleted' ? 'status' : 'alert'}>
            <div>
              <strong>{note.kind === 'deleted' ? `‘${note.text}’ 할 일을 지웠어요.` : '할 일을 지우지 못했어요.'}</strong>
              {note.kind === 'blocked' && <span>진행 중인 집중에서 쓰는 할 일이에요.</span>}
              {note.kind === 'failed' && <span>인터넷 연결을 확인한 뒤 다시 시도해 주세요.</span>}
            </div>
            {note.kind === 'deleted'
              ? <button className="btn btn-ghost" type="button" onClick={undo}>되돌리기</button>
              : <button className="btn btn-ghost" type="button" onClick={() => setNote(null)}>닫기</button>}
          </div>}
          <ul className="todo-grid" aria-label="할 일 목록">
            {shown.map((goal, index) => {
              const order = visible.findIndex(({ goalId }) => goalId === goal.goalId) + 1
              const blocked = full && !order
              return <li key={goal.goalId}>
                <button className="todo-card" type="button" role="checkbox" aria-checked={order > 0} aria-disabled={blocked}
                  data-c={order || undefined} style={{ animationDelay: `${Math.min(index, 8) * .04}s` }}
                  onClick={() => { if (!blocked) toggle(goal) }}>
                  <span className="badge" aria-hidden="true">{order || ''}</span>
                  <strong>{goal.goalText}</strong>
                </button>
                <button className="todo-remove" type="button" aria-label={`${goal.goalText} 지우기`} onClick={() => remove(goal)}>✕</button>
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

type ListKey = 'coreTopics' | 'supportingTopics' | 'expectedActivities'
const same = (a: string, b: string) => a.trim().replace(/\s+/g, ' ').toLowerCase() === b.trim().replace(/\s+/g, ' ').toLowerCase()

/** 할 일 하나의 범위. 위계: 할 일 이름 > 핵심 주제 > 함께 보는 주제·하게 될 활동. 항목은 ✕로 빼고 + 추가로 적으면 바로 저장한다. */
function ReviewSlide({ goal, order, total, profile, current, busy, locked, onChange, onRegenerate }: {
  goal: SessionGoal; order: number; total: number; profile?: GoalProfile; current: boolean; busy: boolean; locked: boolean
  onChange: (next: GoalProfile) => Promise<boolean>; onRegenerate: () => void
}) {
  const [adding, setAdding] = useState<ListKey | null>(null)
  const edit = (key: ListKey, items: string[]) => profile ? onChange({ ...profile, [key]: items }) : Promise.resolve(false)
  const list = (key: ListKey) => profile?.[key] ?? []
  const remove = (key: ListKey, index: number) => { if (!locked) void edit(key, list(key).filter((_, i) => i !== index)) }
  const add = (key: ListKey) => adding === key
    ? <AddInput items={list(key)} disabled={locked} onClose={() => setAdding(null)} onAdd={async (text, keepOpen) => {
      const ok = await edit(key, [...list(key), text])
      if (!ok || !keepOpen || list(key).length + 1 >= 5) setAdding(null)
    }} />
    : list(key).length >= 5 ? <p className="add-note">5개까지 넣을 수 있어요.</p>
      : <button className="add-inline" type="button" disabled={locked} onClick={() => setAdding(key)}>+ 추가</button>
  const x = (key: ListKey, index: number, text: string) => <button className="x" type="button" disabled={locked} aria-label={`${text} 빼기`} onClick={() => remove(key, index)}>✕</button>
  return <section className="slide" data-c={order} aria-hidden={!current} inert={!current} aria-label={`${order}번 할 일`} aria-busy={busy}>
    <div className="slide-head"><span className="badge" aria-hidden="true">{order}</span><h2>{goal.goalText}</h2><span className="count">{order} / {total}</span></div>
    {profile ? <>
      <div className="core-block">
        <p className="label">핵심 주제</p>
        <ul className="core-list">
          {profile.coreTopics.map((topic, index) => <li key={topic}><span>{topic}</span>{x('coreTopics', index, topic)}</li>)}
          <li className="add-li">{add('coreTopics')}</li>
        </ul>
      </div>
      <div className="sub-block">
        {([['supportingTopics', '함께 보는 주제'], ['expectedActivities', '하게 될 활동']] as const).map(([key, title]) => <div key={key}>
          <p className="label">{title}</p>
          {list(key).length > 0 && <ul className="items">{list(key).map((item, index) => <li key={item}><span>{item}</span>{x(key, index, item)}</li>)}</ul>}
          {add(key)}
        </div>)}
      </div>
    </> : <div className="missing">
      <div><strong>범위를 만들지 못했어요.</strong><span>할 일 이름만 보고 판단할게요.</span></div>
      <button className="btn btn-ghost" type="button" disabled={locked} onClick={onRegenerate}>{busy ? '만드는 중…' : '다시 만들기'}</button>
    </div>}
  </section>
}

/** 범위 항목 적기. Enter는 저장하고 이어서 적고, Esc·빈 칸은 닫는다. 같은 항목은 저장하지 않고 알린다. */
function AddInput({ items, disabled, onAdd, onClose }: { items: string[]; disabled: boolean; onAdd: (text: string, keepOpen: boolean) => Promise<void>; onClose: () => void }) {
  const [text, setText] = useState('')
  const [note, setNote] = useState('')
  const commit = async (keepOpen: boolean) => {
    const value = text.trim().replace(/\s+/g, ' ')
    if (!value) { onClose(); return }
    if (items.some((item) => same(item, value))) { setNote('같은 항목이 이미 있어요.'); return }
    setText('')
    await onAdd(value, keepOpen)
  }
  return <div>
    <input className="add-input" autoFocus maxLength={100} placeholder="적고 Enter" aria-label="항목 추가" value={text} disabled={disabled}
      onChange={(event) => { setText(event.target.value); setNote('') }}
      onKeyDown={(event) => {
        if (event.key === 'Enter' && !event.nativeEvent.isComposing) { event.preventDefault(); if (!event.repeat) void commit(true) }
        if (event.key === 'Escape') onClose()
      }}
      onBlur={() => { if (!disabled) void commit(false) }} />
    {note && <p className="add-note" aria-live="polite">{note}</p>}
  </div>
}
