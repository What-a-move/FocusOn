'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import type { ConfirmationSource, CreatedGoal, GoalProfile, PreviousAnswer } from '../../electron/shared/goal-contract.cjs'
import { CONFIRMED_GOAL_KEY, goalApi, readPicked, goalLength, MAX_QUESTIONS, MAX_TODO_PICK, TODO_SAVED_KEY, type ClarifyResult } from '../../shared/api/goal-api'
import { useOpenGoals } from '../../shared/api/useOpenGoals'
import { errorText } from '../../shared/lib/error-text'
import '../../shared/styles/onboarding-window.css'
import './goal.css'

type View = 'entry' | 'questions' | 'candidates' | 'review'
type QuestionMode = 'term' | 'scope' | 'multi'
type Line = { text: string; error?: boolean; typing?: boolean }
type Feedback = { title: string; body: string; info?: boolean } | null
type Review = { goal: string; caption: string; source: ConfirmationSource; profile?: GoalProfile; candidates?: string[]; editableGoal?: boolean }

const STAGE: Record<View, number> = { entry: 0, questions: 1, candidates: 1, review: 2 }
const STAGE_LABEL = ['새 할 일', '다듬기', '확인']
const ASK = '어떤 할 일을 적을까요?'
const same = (a: string, b: string) => a.replace(/\s+/g, '').toLowerCase() === b.replace(/\s+/g, '').toLowerCase()
const POKI = { planning: '/design/poki-main-planning.png', thinking: '/design/poki-thinking.png' }
const TERM_OPTIONS = ['맞아요', '아니에요, 문장을 고칠게요']

function Options({ labels, selected, onChoose, onSubmit, label }: { labels: string[]; selected: number; onChoose: (index: number) => void; onSubmit: () => void; label?: string }) {
  return <div className="options" role="radiogroup" aria-label={label}>
    {labels.map((text, index) => <button className="option" type="button" role="radio" aria-checked={selected === index} key={text}
      onClick={() => onChoose(index)} onDoubleClick={onSubmit}>
      <span className="key" aria-hidden="true" />{text}
    </button>)}
  </div>
}

/** 새 할 일 적기 (FocusOn-design/todo-goals.html?view=add). AI가 다듬고(질문 최대 2회) 저장한 뒤 할 일 고르기로 돌아가 골라 둔다. */
export function GoalSetting() {
  const router = useRouter()
  const { goals: openGoals } = useOpenGoals()
  const [view, setView] = useState<View>('entry')
  const [draft, setDraft] = useState('')
  const [hint, setHint] = useState<{ text: string; error: boolean }>({ text: '2~40자', error: false })
  const [line, setLine] = useState<Line>({ text: ASK })
  const [feedback, setFeedback] = useState<Feedback>(null)
  const [busy, setBusy] = useState(false)
  // 수동 확정 버튼: error = AI 장애·오프라인(입력을 고치면 사라짐), exhausted = 질문 2회 소진
  const [manual, setManual] = useState<'error' | 'exhausted' | null>(null)
  const [asked, setAsked] = useState(0)
  const [answers, setAnswers] = useState<PreviousAnswer[]>([])
  const [clarificationSessionId, setClarificationSessionId] = useState<string | null>(null)
  const [question, setQuestion] = useState<{ mode: QuestionMode; text: string; options: string[] } | null>(null)
  const [candidates, setCandidates] = useState<{ options: string[]; help: string }>({ options: [], help: '' })
  const [selected, setSelected] = useState(-1)
  const [scopeAnswer, setScopeAnswer] = useState('')
  const [review, setReview] = useState<Review | null>(null)
  const partialSave = useRef<CreatedGoal[]>([])
  const saveKeys = useRef(new Map<string, string>())
  const saving = useRef(false)
  const request = useRef(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const sayRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    // [주의] 목표 설정 중 로그인이 만료·해제되면 로그인 화면(/)으로 돌아간다.
    const unsubscribe = window.focusOn?.auth.onSessionChanged((snapshot) => { if (snapshot.status === 'SIGNED_OUT') router.replace('/') })
    return () => unsubscribe?.()
  }, [router])

  useEffect(() => {
    // 화면이 바뀌면 스크린리더가 새 질문을 읽도록 포커스를 옮긴다.
    if (view !== 'entry') sayRef.current?.focus()
  }, [view])

  const go = (next: View, say: Line) => {
    setView(next)
    setLine(say)
    setFeedback(null)
    setSelected(-1)
  }

  const toEntry = (text: string, say: Line = { text: ASK }) => {
    go('entry', say)
    setHint({ text, error: false })
    queueMicrotask(() => inputRef.current?.focus())
  }

  const toReview = (goal: string, caption: string, source: ConfirmationSource, profile?: GoalProfile, candidates?: string[], editableGoal?: boolean) => {
    partialSave.current = []
    const next = { goal, caption, source, profile, candidates, editableGoal }
    setReview(next)
    // 할 일 하나는 확인 없이 바로 저장한다. 나눈 할 일만 확인 화면을 거친다.
    if (!candidates) { void save(next); return }
    go('review', { text: '이렇게 나눠서 저장할까요?' })
  }

  const exhausted = () => {
    setManual('exhausted')
    toEntry('질문을 모두 썼어요. 문장을 고치거나 지금 문구로 바로 정할 수 있어요.')
  }

  const ask = (mode: QuestionMode, text: string, options: string[]) => {
    setAsked((count) => count + 1)
    setQuestion({ mode, text, options })
    setScopeAnswer('')
    go('questions', { text })
  }

  const handle = (result: ClarifyResult, goalText: string, questionsUsed: number) => {
    const canAsk = questionsUsed < MAX_QUESTIONS
    switch (result.decision) {
      case 'CLEAR':
        if (result.goalCandidates.length >= 2) {
          toReview(result.goalCandidates[0], '나눈 할 일', 'MANUAL_CONFIRMED', undefined, result.goalCandidates)
        } else if (!result.normalizedGoalText || goalLength(result.normalizedGoalText) < 2 || goalLength(result.normalizedGoalText) > 40) {
          toReview(goalText, '직접 정한 목표', 'MANUAL_CONFIRMED', result.goalProfileDraft, undefined, true)
        } else {
          toReview(result.normalizedGoalText, '확인한 목표', 'AI_CONFIRMED', result.goalProfileDraft)
        }
        return
      case 'INVALID':
        toEntry('할 일을 구체적으로 다시 적어 주세요.')
        return
      case 'AMBIGUOUS':
        // 추천 목표를 고르는 것은 질문 횟수에 세지 않는다 (AI 분석 계약 3.3).
        if (result.suggestions.length >= 2) {
          setCandidates({ options: result.suggestions, help: '가장 가까운 할 일 하나를 고르거나, 문장을 직접 고쳐도 돼요.' })
          go('candidates', { text: '범위가 조금 넓어요. 이렇게 좁혀 볼까요?' })
        } else if (canAsk && result.question) ask('scope', result.question, [])
        else exhausted()
        return
      case 'MULTI_GOAL':
        if (canAsk && result.question) ask('multi', result.question, result.goalCandidates)
        else exhausted()
        return
      case 'UNKNOWN_TERM':
        if (canAsk && result.question) ask('term', result.question, TERM_OPTIONS)
        else exhausted()
    }
  }

  const clarify = async (goalText: string, previousAnswers: PreviousAnswer[]) => {
    if (busy) return
    const id = ++request.current
    setBusy(true)
    setFeedback(null)
    setLine({ text: '할 일을 살펴보고 있어요', typing: true })
    const result = await goalApi.clarify({ goalText, conversationTurn: asked, previousAnswers, ...(clarificationSessionId ? { clarificationSessionId } : {}) })
    // [주의] 응답을 기다리는 동안 입력이 바뀌었으면(요청 번호가 달라졌으면) 늦게 온 결과를 버린다.
    if (id !== request.current) return
    setBusy(false)
    if (!result.success) {
      const offline = result.error.code === 'LOCAL_NETWORK_UNAVAILABLE'
      setManual('error')
      // [디자인] 실패는 하단 안내에서 한 번만 말한다. 포키 말풍선은 오류색 없이 다음 행동만 권한다.
      toEntry('2~40자', offline ? undefined : { text: '지금은 할 일을 살펴보지 못했어요' })
      setFeedback({ title: '할 일을 살펴보지 못했어요.', body: offline ? errorText(result.error) : '입력한 그대로 저장할 수 있어요.' })
      return
    }
    if (result.data.clarificationSessionId) setClarificationSessionId(result.data.clarificationSessionId)
    setAnswers(previousAnswers)
    handle(result.data, goalText, asked)
  }

  // 고르던 할 일 뒤에 붙여 할 일 고르기에서 골라진 채로 보이게 하고(최대 3개), 한 줄 안내를 남긴다.
  const backToPick = (goals: { goalId: string; goalText: string }[], note: (picked: boolean) => string) => {
    const before = readPicked()
    const merged = [...before, ...goals].filter((goal, index, list) => list.findIndex(({ goalId }) => goalId === goal.goalId) === index).slice(0, MAX_TODO_PICK)
    const picked = goals.some(({ goalId }) => merged.some((goal) => goal.goalId === goalId))
    try {
      sessionStorage.setItem(CONFIRMED_GOAL_KEY, JSON.stringify(merged))
      sessionStorage.setItem(TODO_SAVED_KEY, note(picked))
    } catch { /* 저장소를 못 써도 할 일은 서버에 저장됐다. */ }
    router.push('/todo/')
  }

  const save = async (target = review) => {
    if (!target || saving.current) return
    if (goalLength(target.goal) < 2 || goalLength(target.goal) > 40) {
      setFeedback({ title: '할 일 길이를 확인해 주세요.', body: '할 일은 2~40자로 적어 주세요.' })
      return
    }
    const profile = target.profile
    saving.current = true
    setBusy(true)
    setFeedback(null)
    if (!target.candidates) setLine({ text: '할 일을 저장하고 있어요', typing: true })
    try {
      const terms = target.candidates ?? [target.goal]
      for (const term of terms.slice(partialSave.current.length)) {
        const payload = {
          goalText: term.trim(),
          confirmationSource: target.source,
          ...(clarificationSessionId && !target.candidates && !target.editableGoal ? { clarificationSessionId } : {}),
          ...(profile ? { goalProfile: profile } : {}),
        }
        const fingerprint = JSON.stringify(payload)
        const idempotencyKey = saveKeys.current.get(fingerprint) ?? crypto.randomUUID()
        saveKeys.current.set(fingerprint, idempotencyKey)
        const result = await goalApi.create({ ...payload, idempotencyKey })
        if (!result.success) {
          if (result.error.code === 'GOAL_TEXT_INVALID' && !target.candidates) {
            setDraft(target.goal)
            toEntry('할 일은 2~40자로 적어 주세요.')
            setHint({ text: '할 일은 2~40자로 적어 주세요.', error: true })
          } else {
            if (!target.candidates) setLine({ text: ASK })
            setFeedback({ title: '할 일을 저장하지 못했어요.', body: errorText(result.error) })
          }
          return
        }
        partialSave.current.push(result.data)
      }
      const goals = partialSave.current.map(({ goalId, goalText }) => ({ goalId, goalText }))
      saveKeys.current.clear()
      backToPick(goals, (picked) => goals.length > 1
        ? picked ? `할 일 ${goals.length}개를 저장하고 골라 뒀어요.` : `할 일 ${goals.length}개를 저장했어요.`
        : picked ? `‘${goals[0].goalText}’ 할 일을 저장하고 골라 뒀어요.` : `‘${goals[0].goalText}’ 할 일을 저장했어요. 3개를 다 골라서 더 고르지는 않았어요.`)
    } finally {
      setBusy(false)
      saving.current = false
    }
  }

  const validDraft = () => {
    const length = goalLength(draft)
    if (length >= 2) return true
    setHint({ text: length === 0 ? '할 일을 입력해 주세요.' : '2자 이상 입력해 주세요.', error: true })
    setLine({ text: '한 줄만 적어 주시면 돼요!', error: true })
    inputRef.current?.focus()
    return false
  }

  // 이미 적어 둔 할 일이면 새로 만들지 않고 그 할 일을 골라 둔다.
  const reuse = () => {
    const existing = openGoals.find((goal) => same(goal.goalText, draft.trim()))
    if (!existing) return false
    backToPick([{ goalId: existing.goalId, goalText: existing.goalText }], (picked) => picked
      ? `같은 할 일 ‘${existing.goalText}’이(가) 이미 있어서 그걸 골라 뒀어요.`
      : `같은 할 일 ‘${existing.goalText}’이(가) 이미 있어요. 3개를 다 골라서 더 고르지는 않았어요.`)
    return true
  }

  const ready = view === 'entry' ? goalLength(draft) >= 2
    : view === 'questions' && question?.mode === 'scope' ? goalLength(scopeAnswer) >= 2
    : view === 'questions' || view === 'candidates' ? selected >= 0 : true

  const primary = () => {
    if (busy) return
    if (view === 'entry') { if (validDraft() && !reuse()) void clarify(draft.trim(), answers); return }
    if (view === 'questions' && question) {
      if (question.mode === 'scope') {
        if (goalLength(scopeAnswer) < 2) { setFeedback({ title: '답을 적어 주세요.', body: '이번에 할 부분을 2자 이상으로 적어 주세요.', info: true }); return }
        void clarify(draft.trim(), [...answers, { question: question.text, answer: scopeAnswer.trim() }])
        return
      }
      if (selected < 0) { setFeedback({ title: '하나를 골라 주세요.', body: '가장 가까운 항목 하나를 고르면 다음으로 넘어가요.', info: true }); return }
      if (question.mode === 'multi') { toReview(question.options[selected], '질문으로 고른 목표', 'MANUAL_CONFIRMED'); return }
      if (selected === 0) { void clarify(draft.trim(), [...answers, { question: question.text, answer: '맞아요' }]); return }
      // 아니에요: 문장을 고치게 한다. 이 질문도 횟수에 들어간다.
      setAnswers([...answers, { question: question.text, answer: '아니에요' }])
      if (asked >= MAX_QUESTIONS) { exhausted(); return }
      toEntry('문장을 고친 뒤 다시 ‘다음’을 눌러 주세요.')
      return
    }
    if (view === 'candidates') {
      if (selected < 0) { setFeedback({ title: '하나를 골라 주세요.', body: '가장 가까운 항목 하나를 고르면 다음으로 넘어가요.', info: true }); return }
      toReview(candidates.options[selected], '고른 목표', 'MANUAL_CONFIRMED')
      return
    }
    if (view === 'review') void save()
  }

  const secondaryLabel = view === 'entry' ? (manual === 'exhausted' ? '지금 문구로 저장' : '입력한 그대로 저장')
    : view === 'questions' && asked >= MAX_QUESTIONS ? '지금 문구로 저장' : '문장 수정'
  const showSecondary = !(view === 'entry' && !manual)
  const secondary = () => {
    if (busy) return
    if (view === 'entry') { if (validDraft() && !reuse()) toReview(draft.trim(), '직접 정한 할 일', 'MANUAL_CONFIRMED'); return }
    if (view === 'questions' && asked >= MAX_QUESTIONS) { toReview(draft.trim(), '직접 정한 할 일', 'MANUAL_CONFIRMED'); return }
    toEntry('2~40자')
  }

  const onDraft = (value: string) => {
    request.current += 1 // 진행 중인 확인 응답은 버린다.
    setBusy(false)
    setDraft(value)
    setHint({ text: goalLength(value) >= 40 ? '40자까지 입력할 수 있어요.' : '2~40자', error: false })
    if (manual === 'error') setManual(null)
    setFeedback(null)
  }
  const onEnter = (event: React.KeyboardEvent<HTMLInputElement>) => { if (event.key === 'Enter' && !event.nativeEvent.isComposing) primary() }

  const stage = STAGE[view]
  const primaryLabel = { entry: '저장', questions: '다음', candidates: '이 할 일로', review: '할 일 저장하기' }[view]
  const poki = view === 'questions' || view === 'candidates' ? POKI.thinking : POKI.planning
  const pokiAlt = view === 'questions' || view === 'candidates' ? '생각하는 포키' : '체크리스트를 든 포키'

  return <main className="app-window onboarding-window goal-window">
    <div className="flow">
      <div className="top">
        <Link className="close" href="/todo/" aria-label="닫고 할 일 고르기로">✕</Link>
        <div className="progress" role="progressbar" aria-label="새 할 일 적기 진행" aria-valuemin={1} aria-valuemax={3} aria-valuenow={Math.min(stage + 1, 3)}>
          {[0, 1, 2].map((index) => <i className={index < stage ? 'done' : index === stage ? 'active' : ''} key={index} />)}
        </div>
        <span className="step-count" aria-live="polite">{STAGE_LABEL[stage]}</span>
      </div>

      <div className="body">
        <section className="view" key={view} aria-labelledby="goal-say">
          <div className="speaker">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="poki" src={poki} alt={pokiAlt} />
            <p className={`bubble${line.error ? ' is-error' : ''}`} id="goal-say" tabIndex={-1} ref={sayRef} key={line.text} aria-live="polite">
              {line.text}{line.typing && <span className="typing" aria-hidden="true"><i /><i /><i /></span>}
            </p>
          </div>

          {view === 'entry' && <div className="panel">
            <div className="input-wrap">
              <input className={`goal-input${hint.error ? ' is-error' : ''}`} id="goal-input" ref={inputRef} type="text" maxLength={40} autoComplete="off"
                aria-label="할 일" aria-describedby="goal-hint" aria-invalid={hint.error} value={draft} disabled={busy}
                onChange={(event) => onDraft(event.target.value)} onKeyDown={onEnter} autoFocus />
              <span className={`counter${goalLength(draft) >= 40 ? ' is-full' : ''}`} aria-hidden="true">{goalLength(draft)}/40</span>
            </div>
            <p className={`hint${hint.error ? ' is-error' : ''}`} id="goal-hint" aria-live="polite">{hint.text}</p>
          </div>}

          {view === 'questions' && question && <div className="panel">
            <span className="question-count">{question.mode === 'term' ? '확인 질문' : '질문'} {asked} / {MAX_QUESTIONS}</span>
            {question.mode === 'scope'
              ? <div className="input-wrap"><input className="goal-input" type="text" maxLength={30} autoComplete="off" aria-label="질문에 대한 답"
                  value={scopeAnswer} disabled={busy} onChange={(event) => { setScopeAnswer(event.target.value); setFeedback(null) }} onKeyDown={onEnter} autoFocus /></div>
              : <Options labels={question.options} selected={selected} onChoose={(index) => { setSelected(index); setFeedback(null) }} onSubmit={primary} label={question.text} />}
          </div>}

          {view === 'candidates' && <div className="panel">
            <Options labels={candidates.options} selected={selected} onChoose={(index) => { setSelected(index); setFeedback(null) }} onSubmit={primary} label="할 일 후보" />
            <p className="options-help">{candidates.help}</p>
          </div>}

          {view === 'review' && review?.candidates && <div className="panel">
            <div className="goal-card"><small>{review.caption}</small>
              <ul className="review-goals">{review.candidates.map((goal) => <li key={goal}>{goal}</li>)}</ul>
            </div>
          </div>}
        </section>
      </div>

      <footer className="footer">
        <div className="footer-inner">
          {/* [접근성] 안내용 status 영역은 처음부터 붙여 둬야 내용이 바뀔 때 읽힌다. */}
          <div className="feedback-slot" role="status">
            {feedback?.info && <div className="feedback is-info"><div><strong>{feedback.title}</strong><span>{feedback.body}</span></div></div>}
          </div>
          {feedback && !feedback.info && <div className="feedback" role="alert">
            <span className="feedback-icon" aria-hidden="true">!</span>
            <div><strong>{feedback.title}</strong><span>{feedback.body}</span></div>
          </div>}
          <span className="spacer" />
          {showSecondary && <button className="btn btn-ghost" type="button" onClick={secondary} disabled={busy}>{secondaryLabel}</button>}
          <button className={`btn btn-primary primary${ready ? '' : ' is-blocked'}`} type="button" onClick={primary} disabled={busy}>
            {busy && view === 'review' ? '저장하는 중…' : primaryLabel}
          </button>
        </div>
      </footer>
    </div>
  </main>
}
