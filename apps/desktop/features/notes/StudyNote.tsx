'use client'

import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { sessionResultApi, type LearningSummary, type SessionSummary } from '../report/session-result-api'
import { LoadError, SettingsShell } from '../settings/SettingsShell'
import './notes.css'

// 디자인 원본: FocusOn-design/study-review.html · fo-notes.js. 시안의 상태 미리보기 전환은 넣지 않는다.
// `이어서 집중하기`는 세션 요약에 goalId가 없어 같은 목표로 바로 시작할 수 없으므로 넣지 않는다.
const POLL_MS = 5000
type View =
  | { kind: 'loading' }
  | { kind: 'missing' }
  | { kind: 'list' }
  | { kind: 'error'; code: string }
  | { kind: 'ready'; summary: SessionSummary; learning: LearningSummary }

// 정리 전·실패 상태. 시스템 안내라 존댓말이며, 정리 실패는 요청 오류가 아니므로 빨간색을 쓰지 않는다(결정 031).
const STATUS = {
  QUEUED: { title: '학습 정리를 준비하고 있어요', copy: '곧 정리를 시작해요. 이 화면을 열어 두면 다 되는 대로 보여 드려요.', waiting: true },
  GENERATING: { title: '학습 내용을 정리하고 있어요', copy: '다 되면 이 화면에 바로 보여 드려요.', waiting: true },
  INSUFFICIENT_DATA: { title: '이번에는 학습 기록만 남겼어요', copy: '노트로 정리할 만큼 확인된 학습 내용이 부족했어요. 목표와 집중 시간은 기록됐어요.', waiting: false },
  FAILED: { title: '학습 정리를 만들지 못했어요', copy: '집중 기록과 흐름은 집중 리포트에서 그대로 볼 수 있어요.', waiting: false },
} as const

const dateFormat = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' })
const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })

function metaOf(summary: SessionSummary): string {
  const minutes = `${Math.floor(summary.accumulatedActiveMs / 60000)}분`
  if (!summary.startedAt) return minutes
  const start = new Date(summary.startedAt)
  const range = summary.endedAt ? ` · ${timeFormat.format(start)} – ${timeFormat.format(new Date(summary.endedAt))}` : ''
  return `${dateFormat.format(start)}${range} · ${minutes}`
}

/** 복사용 글. 화면과 같은 순서로 쓴다. */
function asText(summary: SessionSummary, learning: LearningSummary): string {
  return [
    summary.goalText, metaOf(summary), '',
    '학습 내용 한눈에 보기', learning.overview ?? '', '',
    '핵심 개념', ...learning.keyConcepts.flatMap((item) => [`• ${item.name}`, `  ${item.briefExplanation}`, ...(item.sessionEvidence ? [`  세션에서 확인한 내용: ${item.sessionEvidence}`] : [])]),
    ...(learning.nextStartPrompt ? ['', '다음 시작 지점', learning.nextStartPrompt] : []),
  ].join('\n')
}

/**
 * 회차 하나의 학습 노트 (`/notes/?session=ID`). 집중 리포트의 `정리 보기`에서 연다.
 * 사이드바로 회차 없이 들어오면 목록 자리에 준비 중 안내를 둔다. 목록은 서버 `GET /learning-summaries` 이후 만든다.
 */
export function StudyNote() {
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [view, setView] = useState<View>({ kind: 'loading' })

  const load = useCallback(async (id: string) => {
    const [summary, learning] = await Promise.all([sessionResultApi.getSummary(id), sessionResultApi.getLearningSummary(id)])
    if (!summary.success) { setView(summary.error.code === 'SESSION_NOT_FOUND' ? { kind: 'missing' } : { kind: 'error', code: summary.error.code }); return }
    if (!learning.success) { setView(learning.error.code === 'SESSION_NOT_FOUND' ? { kind: 'missing' } : { kind: 'error', code: learning.error.code }); return }
    setView({ kind: 'ready', summary: summary.data, learning: learning.data })
  }, [])

  useEffect(() => {
    // 정적 빌드라 주소의 ?session= 값은 화면이 열린 뒤 읽는다.
    const id = new URLSearchParams(window.location.search).get('session')
    queueMicrotask(() => { setSessionId(id); if (id) void load(id); else setView({ kind: 'list' }) })
  }, [load])

  // 정리 중이면 5초마다 다시 묻는다. 완료·근거 부족·실패가 되면 멈춘다(리포트 설계서 2.1).
  const waiting = view.kind === 'ready' && (view.learning.status === 'QUEUED' || view.learning.status === 'GENERATING')
  // 요청은 하나씩만 보내고, 화면을 떠난 뒤 온 응답은 버린다. 다시 해도 안 되는 실패(기록 없음 등)면 멈추고 알린다.
  useEffect(() => {
    if (!waiting || !sessionId) return
    let active = true
    let checking = false
    const timer = setInterval(() => {
      if (checking) return
      checking = true
      void sessionResultApi.getLearningSummary(sessionId).then((result) => {
        checking = false
        if (!active) return
        if (result.success) setView((current) => current.kind === 'ready' ? { ...current, learning: result.data } : current)
        else if (!result.error.retryable) setView(result.error.code === 'SESSION_NOT_FOUND' ? { kind: 'missing' } : { kind: 'error', code: result.error.code })
      })
    }, POLL_MS)
    return () => { active = false; clearInterval(timer) }
  }, [waiting, sessionId])

  const back = sessionId ? `/session-result/?session=${encodeURIComponent(sessionId)}` : '/'
  return <SettingsShell active="notes">
    <div className="fo-note-page">
      {sessionId && <Link className="fo-back-link" href={back}>← 집중 리포트</Link>}
      {view.kind === 'loading' && <div className="fo-notebook fo-note-status" aria-busy="true" />}
      {view.kind === 'error' && <LoadError code={view.code} what="학습 노트를" onRetry={() => { if (sessionId) { setView({ kind: 'loading' }); void load(sessionId) } }} />}
      {view.kind === 'list' && <section className="fo-notebook fo-note-status">
        <Image src="/design/poki-floating-sleepy-v7.png" alt="" width={240} height={240} />
        <h2>학습 노트 모아 보기는 준비 중이에요</h2>
        <p>지금은 집중 리포트의 <b>정리 보기</b>에서 회차별 노트를 열 수 있어요.</p>
        <Link className="fo-button fo-button-ghost" href="/records/">집중 기록으로</Link>
      </section>}
      {view.kind === 'missing' && <section className="fo-notebook fo-note-status">
        <Image src="/design/poki-floating-curious-v8.png" alt="궁금해하는 포키" width={240} height={240} />
        <h2>노트를 찾을 수 없어요</h2>
        <p>집중 기록에서 회차를 다시 열어 주세요.</p>
        <Link className="fo-button fo-button-ghost" href="/records/">집중 기록으로</Link>
      </section>}
      {view.kind === 'ready' && (view.learning.status === 'READY'
        ? <Notebook summary={view.summary} learning={view.learning} />
        : <section className="fo-notebook fo-note-status" aria-live="polite">
          <Image className={view.learning.status === 'GENERATING' ? 'is-turning' : undefined} src="/design/poki-records-journal.png" alt="" width={240} height={240} />
          <h2>{STATUS[view.learning.status].title}</h2>
          <p>{STATUS[view.learning.status].copy}</p>
          {STATUS[view.learning.status].waiting && <span className="fo-dots" aria-hidden="true"><i /><i /><i /></span>}
        </section>)}
    </div>
  </SettingsShell>
}

function Notebook({ summary, learning }: { summary: SessionSummary; learning: LearningSummary }) {
  const [tip, setTip] = useState<'복사' | '복사했어요' | '복사하지 못했어요'>('복사')
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  useEffect(() => () => clearTimeout(timer.current), [])

  async function copy() {
    try { await navigator.clipboard.writeText(asText(summary, learning)); setTip('복사했어요') }
    catch { setTip('복사하지 못했어요') }
    clearTimeout(timer.current)
    timer.current = setTimeout(() => setTip('복사'), 1800)
  }

  // 줄마다 차례로 써 내려가는 모션 순서
  let order = 0
  const write = (className = '') => ({ className: `${className} fo-nb-write`.trim(), style: { '--i': order++ } as CSSProperties })
  return <article className="fo-notebook">
    <header className="fo-nb-head">
      <div><p className="fo-nb-meta">{metaOf(summary)}</p><h1 {...write('fo-nb-title')}>{summary.goalText}</h1></div>
      <button className={`fo-nb-copy${tip !== '복사' ? ' is-done' : ''}`} type="button" data-tip={tip} aria-label={tip === '복사' ? '노트 복사' : tip} onClick={() => { void copy() }}>
        {tip !== '복사했어요'
          ? <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M5 15V6a2 2 0 0 1 2-2h9" /></svg>
          : <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>}
      </button>
    </header>
    {learning.overview && <><h2 {...write('fo-nb-label')}>학습 내용 한눈에 보기</h2><p {...write('fo-nb-text')}>{learning.overview}</p></>}
    {learning.keyConcepts.length > 0 && <><h2 {...write('fo-nb-label')}>핵심 개념</h2>
      <ol className="fo-nb-concepts">{learning.keyConcepts.map((item) => <li key={item.name} {...write()}>
        <b>{item.name}</b>
        <span className="fo-nb-def">{item.briefExplanation}</span>
        {item.sessionEvidence && <span className="fo-nb-seen"><em>세션에서 확인</em>{item.sessionEvidence}</span>}
      </li>)}</ol></>}
    {learning.nextStartPrompt && <><h2 {...write('fo-nb-label')}>다음 시작 지점</h2><p {...write('fo-nb-next')}><mark>{learning.nextStartPrompt}</mark></p></>}
  </article>
}
