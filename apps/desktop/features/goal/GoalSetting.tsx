'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import type { ConfirmationSource, PreviousAnswer } from '../../electron/shared/goal-contract.cjs'
import { CONFIRMED_GOAL_KEY, goalApi, goalLength, MAX_QUESTIONS, type ClarifyResult } from './goal-api'
import '../onboarding/onboarding.css'
import './goal.css'

type View = 'entry' | 'questions' | 'candidates' | 'review' | 'done'
type QuestionMode = 'term' | 'scope' | 'multi'
type Line = { text: string; error?: boolean; typing?: boolean }
type Feedback = { title: string; body: string } | null
type Review = { goal: string; caption: string; source: ConfirmationSource }

const EXAMPLES = ['React Query 캐싱 방식 이해하기', '미적분 적분 문제 풀기', '캡스톤 발표 자료 정리하기']
const STAGE: Record<View, number> = { entry: 0, questions: 1, candidates: 1, review: 2, done: 3 }
const STAGE_LABEL = ['입력', '다듬기', '확인', '완료']
const POKI = { planning: '/design/poki-main-planning.png', curious: '/design/poki-floating-curious-v8.png', cheer: '/design/poki-floating-cheer-v7.png' }
const TERM_OPTIONS = ['맞아요', '아니에요, 문장을 고칠게요']

function Options({ labels, selected, onChoose, onSubmit, label }: { labels: string[]; selected: number; onChoose: (index: number) => void; onSubmit: () => void; label?: string }) {
  return <div className="options" role="radiogroup" aria-label={label}>
    {labels.map((text, index) => <button className="option" type="button" role="radio" aria-checked={selected === index} key={text}
      onClick={() => onChoose(index)} onDoubleClick={onSubmit}>
      <span className="key" aria-hidden="true" />{text}
    </button>)}
  </div>
}

/** D06·D07 목표 설정 (FocusOn-design/goal-setting.html). AI는 판정만 하고, 질문은 최대 2회, 장애 때는 수동 확정한다. */
export function GoalSetting() {
  const router = useRouter()
  const [view, setView] = useState<View>('entry')
  const [draft, setDraft] = useState('')
  const [hint, setHint] = useState<{ text: string; error: boolean }>({ text: '2~40자', error: false })
  const [line, setLine] = useState<Line>({ text: '오늘은 무엇에 집중할까요?' })
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
  const request = useRef(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const sayRef = useRef<HTMLParagraphElement>(null)
  const doneRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    // [주의] 목표 설정 중 로그인이 만료·해제되면 로그인 화면(/)으로 돌아간다.
    const unsubscribe = window.focusOn?.auth.onSessionChanged((snapshot) => { if (snapshot.status === 'SIGNED_OUT') router.replace('/') })
    return () => unsubscribe?.()
  }, [router])

  useEffect(() => {
    // 화면이 바뀌면 스크린리더가 새 질문을 읽도록 포커스를 옮긴다.
    if (view === 'done') doneRef.current?.focus()
    else if (view !== 'entry') sayRef.current?.focus()
  }, [view])

  const go = (next: View, say: Line) => {
    setView(next)
    setLine(say)
    setFeedback(null)
    setSelected(-1)
  }

  const toEntry = (text: string, say: Line = { text: '오늘은 무엇에 집중할까요?' }) => {
    go('entry', say)
    setHint({ text, error: false })
    queueMicrotask(() => inputRef.current?.focus())
  }

  const toReview = (goal: string, caption: string, source: ConfirmationSource) => {
    setReview({ goal, caption, source })
    go('review', { text: '이 목표로 시작할까요?' })
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
        toReview(result.normalizedGoalText ?? goalText, '확인한 목표', 'AI_CONFIRMED')
        return
      case 'AMBIGUOUS':
        // 추천 목표를 고르는 것은 질문 횟수에 세지 않는다 (AI 분석 계약 3.3).
        if (result.suggestions.length >= 2) {
          setCandidates({ options: result.suggestions, help: '가장 가까운 목표 하나를 고르거나, 문장을 직접 고쳐도 돼요.' })
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
    setLine({ text: '목표를 살펴보고 있어요', typing: true })
    const result = await goalApi.clarify({ goalText, conversationTurn: asked, previousAnswers, ...(clarificationSessionId ? { clarificationSessionId } : {}) })
    // [주의] 응답을 기다리는 동안 입력이 바뀌었으면(요청 번호가 달라졌으면) 늦게 온 결과를 버린다.
    if (id !== request.current) return
    setBusy(false)
    if (!result.success) {
      const offline = result.error.code === 'LOCAL_NETWORK_UNAVAILABLE'
      setManual('error')
      toEntry('2~40자', { text: offline ? '인터넷이 끊겼어요.\n지금 문구로 시작해도 괜찮아요!' : '앗, 지금은 확인이 어려워요.\n지금 문구로 시작해도 괜찮아요!', error: true })
      setFeedback({ title: offline ? '인터넷 연결을 확인해 주세요.' : '목표를 확인하지 못했어요.', body: '입력한 문구 그대로 시작할 수 있어요.' })
      return
    }
    if (result.data.clarificationSessionId) setClarificationSessionId(result.data.clarificationSessionId)
    setAnswers(previousAnswers)
    handle(result.data, goalText, asked)
  }

  const save = async () => {
    if (!review || busy) return
    setBusy(true)
    setFeedback(null)
    const result = await goalApi.create({
      goalText: review.goal,
      confirmationSource: review.source,
      // 수동 확정이어도 앞서 AI와 나눈 대화가 있으면 같은 id로 이어 기록한다.
      ...(clarificationSessionId ? { clarificationSessionId } : {}),
    })
    setBusy(false)
    if (!result.success) {
      if (result.error.code === 'GOAL_TEXT_INVALID') { setDraft(review.goal); toEntry('목표는 2~40자로 적어 주세요.'); setHint({ text: '목표는 2~40자로 적어 주세요.', error: true }); return }
      setFeedback({ title: '목표를 저장하지 못했어요.', body: result.error.code === 'LOCAL_NETWORK_UNAVAILABLE' ? '인터넷 연결을 확인한 뒤 다시 눌러 주세요.' : '잠시 후 다시 눌러 주세요.' })
      return
    }
    try { sessionStorage.setItem(CONFIRMED_GOAL_KEY, JSON.stringify({ goalId: result.data.goalId, goalText: result.data.goalText })) } catch { /* 저장소를 못 써도 목표는 서버에 저장됐다. */ }
    setReview({ ...review, goal: result.data.goalText })
    go('done', { text: '' })
  }

  const validDraft = () => {
    const length = goalLength(draft)
    if (length >= 2) return true
    setHint({ text: length === 0 ? '목표를 입력해 주세요.' : '2자 이상 입력해 주세요.', error: true })
    setLine({ text: '한 줄만 적어 주시면 돼요!', error: true })
    inputRef.current?.focus()
    return false
  }

  const ready = view === 'entry' ? goalLength(draft) >= 2
    : view === 'questions' && question?.mode === 'scope' ? goalLength(scopeAnswer) >= 2
    : view === 'questions' || view === 'candidates' ? selected >= 0 : true

  const primary = () => {
    if (busy) return
    if (view === 'entry') { if (validDraft()) void clarify(draft.trim(), answers); return }
    if (view === 'questions' && question) {
      if (question.mode === 'scope') {
        if (goalLength(scopeAnswer) < 2) { setFeedback({ title: '답을 적어 주세요.', body: '이번에 할 부분을 2자 이상으로 적어 주세요.' }); return }
        void clarify(draft.trim(), [...answers, { question: question.text, answer: scopeAnswer.trim() }])
        return
      }
      if (selected < 0) { setFeedback({ title: '하나를 골라 주세요.', body: '가장 가까운 항목 하나를 고르면 다음으로 넘어가요.' }); return }
      if (question.mode === 'multi') { toReview(question.options[selected], '질문으로 고른 목표', 'AI_CONFIRMED'); return }
      if (selected === 0) { void clarify(draft.trim(), [...answers, { question: question.text, answer: '맞아요' }]); return }
      // 아니에요: 문장을 고치게 한다. 이 질문도 횟수에 들어간다.
      setAnswers([...answers, { question: question.text, answer: '아니에요' }])
      if (asked >= MAX_QUESTIONS) { exhausted(); return }
      toEntry('문장을 고친 뒤 다시 ‘다음’을 눌러 주세요.')
      return
    }
    if (view === 'candidates') {
      if (selected < 0) { setFeedback({ title: '하나를 골라 주세요.', body: '가장 가까운 항목 하나를 고르면 다음으로 넘어가요.' }); return }
      toReview(candidates.options[selected], '고른 목표', 'AI_CONFIRMED')
      return
    }
    if (view === 'review') { void save(); return }
    router.push('/session-setup/')
  }

  const secondaryLabel = view === 'entry' ? (manual === 'exhausted' ? '지금 문구로 정하기' : '입력한 그대로 시작')
    : view === 'questions' && asked >= MAX_QUESTIONS ? '지금 문구로 정하기' : '문장 수정'
  const showSecondary = view !== 'done' && !(view === 'entry' && !manual)
  const secondary = () => {
    if (busy) return
    if (view === 'entry') { if (validDraft()) toReview(draft.trim(), '직접 정한 목표', 'MANUAL_CONFIRMED'); return }
    if (view === 'questions' && asked >= MAX_QUESTIONS) { toReview(draft.trim(), '직접 정한 목표', 'MANUAL_CONFIRMED'); return }
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
  const primaryLabel = { entry: '다음', questions: '다음', candidates: '이 목표로', review: '이 목표로 정하기', done: '집중 시간 정하기' }[view]
  const poki = view === 'questions' || view === 'candidates' ? POKI.curious : POKI.planning
  const pokiAlt = view === 'questions' || view === 'candidates' ? '궁금해하는 포키' : '체크리스트를 든 포키'

  return <main className="app-window onboarding-window goal-window">
    <header className="titlebar"><div className="window-controls" aria-hidden="true"><i /><i /><i /></div></header>
    <div className="flow">
      <div className="top">
        <Link className="close" href="/" aria-label="목표 설정 닫고 홈으로">✕</Link>
        <div className="progress" role="progressbar" aria-label="목표 설정 진행" aria-valuemin={1} aria-valuemax={3} aria-valuenow={Math.min(stage + 1, 3)}>
          {[0, 1, 2].map((index) => <i className={index < stage ? 'done' : index === stage ? 'active' : ''} key={index} />)}
        </div>
        <span className="step-count" aria-live="polite">{STAGE_LABEL[stage]}</span>
      </div>

      <div className="body">
        {view === 'done' && review ? <section className="view done-view" aria-labelledby="done-goal">
          <div>
            <p className="done-label">이번 집중 목표</p>
            <h2 id="done-goal" tabIndex={-1} ref={doneRef}>{review.goal}</h2>
            <p className="done-ok"><i aria-hidden="true"><svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round"><path d="M3.5 8.5l3 3 6-7" /></svg></i>목표가 정해졌어요. 이제 시간만 정하면 돼요</p>
          </div>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="poki" src={POKI.cheer} alt="응원하는 포키" />
        </section> : <section className="view" key={view} aria-labelledby="goal-say">
          <div className="speaker">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="poki" src={poki} alt={pokiAlt} />
            <p className={`bubble${line.error ? ' is-error' : ''}`} id="goal-say" tabIndex={-1} ref={sayRef} key={line.text} aria-live="polite">
              {line.text}{line.typing && <span className="typing" aria-hidden="true"><i /><i /><i /></span>}
            </p>
          </div>

          {view === 'entry' && <div className="panel">
            <label className="field-label" htmlFor="goal-input">이번에 끝낼 일 하나</label>
            <div className="input-wrap">
              <input className={`goal-input${hint.error ? ' is-error' : ''}`} id="goal-input" ref={inputRef} type="text" maxLength={40} autoComplete="off"
                placeholder="예: 미적분 적분 문제 풀기" aria-describedby="goal-hint" aria-invalid={hint.error} value={draft} disabled={busy}
                onChange={(event) => onDraft(event.target.value)} onKeyDown={onEnter} autoFocus />
              <span className={`counter${goalLength(draft) >= 40 ? ' is-full' : ''}`} aria-hidden="true">{goalLength(draft)}/40</span>
            </div>
            <p className={`hint${hint.error ? ' is-error' : ''}`} id="goal-hint" aria-live="polite">{hint.text}</p>
            <p className="chips-label">예시</p>
            <div className="chips">{EXAMPLES.map((example) => <button className="chip" type="button" key={example} disabled={busy}
              onClick={() => { onDraft(example); inputRef.current?.focus() }}>{example}</button>)}</div>
          </div>}

          {view === 'questions' && question && <div className="panel">
            <span className="question-count">{question.mode === 'term' ? '확인 질문' : '질문'} {asked} / {MAX_QUESTIONS}</span>
            {question.mode === 'scope'
              ? <div className="input-wrap"><input className="goal-input" type="text" maxLength={30} autoComplete="off" placeholder="예: 캡스톤 중간 발표" aria-label="질문에 대한 답"
                  value={scopeAnswer} disabled={busy} onChange={(event) => { setScopeAnswer(event.target.value); setFeedback(null) }} onKeyDown={onEnter} autoFocus /></div>
              : <Options labels={question.options} selected={selected} onChoose={(index) => { setSelected(index); setFeedback(null) }} onSubmit={primary} label={question.text} />}
          </div>}

          {view === 'candidates' && <div className="panel">
            <Options labels={candidates.options} selected={selected} onChoose={(index) => { setSelected(index); setFeedback(null) }} onSubmit={primary} label="목표 후보" />
            <p className="options-help">{candidates.help}</p>
          </div>}

          {view === 'review' && review && <div className="panel">
            <div className="goal-card"><small>{review.caption}</small><p>{review.goal}</p></div>
            <ul className="rules"><li>한 세션에 목표 하나</li><li>완료 여부는 판단하지 않아요</li></ul>
          </div>}
        </section>}
      </div>

      <footer className="footer">
        <div className="footer-inner">
          {feedback && <div className="feedback" role="alert">
            <span className="feedback-icon" aria-hidden="true">!</span>
            <div><strong>{feedback.title}</strong><span>{feedback.body}</span></div>
          </div>}
          {showSecondary && <button className="btn btn-ghost" type="button" onClick={secondary} disabled={busy}>{secondaryLabel}</button>}
          <span className="spacer" />
          <button className={`btn btn-primary primary${ready ? '' : ' is-blocked'}`} type="button" onClick={primary} disabled={busy}>
            {busy && view === 'review' ? '저장하는 중…' : primaryLabel}
          </button>
        </div>
      </footer>
    </div>
  </main>
}
