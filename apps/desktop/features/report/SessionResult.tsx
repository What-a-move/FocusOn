'use client'

import { useCallback, useEffect, useState, type CSSProperties } from 'react'
import Link from 'next/link'
import { firstOffTaskAt, flowOf, pauseTimelinePositions, sessionResultApi, type LearningSummary, type OffTaskReport, type OffTaskShare, type SessionPause, type SessionSummary } from '../../shared/api/session-result-api'
import { errorText } from '../../shared/lib/error-text'
import '../../shared/styles/onboarding-window.css'
import './session-result.css'

const POLL_MS = 5000
const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })
const pauseTimeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit', second: '2-digit' })
const offTaskPercent = new Intl.NumberFormat('ko-KR', { style: 'percent', maximumFractionDigits: 1 })
const OFF_TASK_STYLE: Record<OffTaskShare['category'], { label: string; color: string; icon: string }> = {
  VIDEO: { label: '영상', color: '#d59072', icon: 'M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM10 8.5l5 3.5-5 3.5z' },
  SNS: { label: 'SNS', color: '#b59bdc', icon: 'M10 3L6 21M18 3l-4 18M4 9h17M3 15h17' },
  COMMUNITY: { label: '커뮤니티', color: '#72b5aa', icon: 'M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-4-4M16 4a3.5 3.5 0 0 1 0 7m-4-3.5a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z' },
  SHOPPING: { label: '쇼핑', color: '#d6ad60', icon: 'M4 8h16l1 13H3L4 8Zm4 2V7a4 4 0 1 1 8 0v3' },
  GAME: { label: '게임', color: '#8daada', icon: 'M7 7h10a4 4 0 0 1 4 3.5l1 5.5a2.5 2.5 0 0 1-4.2 2.2L15.5 16h-7l-2.3 2.2A2.5 2.5 0 0 1 2 16l1-5.5A4 4 0 0 1 7 7Zm1 3v4m-2-2h4m6 0h.01M18 10h.01' },
  MESSENGER: { label: '메신저', color: '#d092b5', icon: 'M5 4h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-9l-5 3v-3a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm3 7h.01m4 0h.01m4 0h.01' },
  NEWS: { label: '뉴스', color: '#9db378', icon: 'M5 4h15v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8h2m0-4v14m4-9h7m-7 4h7m-7 4h4' },
  OTHER: { label: '기타', color: '#a79d92', icon: 'M5 12h.01m7 0h.01m7 0h.01' },
}
const AUTO_PAUSE_LABEL: Record<string, string> = { REQUIRED_PERMISSION: '권한 꺼짐', EXTENSION_UNLINKED: '연결 끊김', APP_RESTART: '앱 재시작', SYSTEM: '시스템' }
// 학습 정리 상태별 한 줄(FocusOn-design/session-report.html `learningHtml`). 시스템 안내라 존댓말이다.
// AI 정리 실패는 요청 오류가 아니라 서버가 정한 결과라 기본색으로 보여 준다(결정 031).
const LEARN_LINE = {
  QUEUED: '정리 준비 중이에요.',
  GENERATING: '학습 내용을 정리하고 있어요.',
  INSUFFICIENT_DATA: '이번엔 정리할 내용이 부족했어요.',
  FAILED: '학습 정리를 만들지 못했어요.',
  // 서버 404(NOT_FOUND)는 이 회차에 정리 기록이 없다는 뜻이다(학습 정리 API 계약).
  NONE: '이번에는 학습 정리가 없어요.',
  UNKNOWN: '학습 정리 상태를 확인하지 못했어요.',
} as const

type View =
  | { kind: 'loading' }
  | { kind: 'missing' }
  | { kind: 'error'; code: string }
  | { kind: 'ready'; summary: SessionSummary; learning: LearningSummary | null; retryLearning: boolean; noLearning: boolean }

const minutes = (ms: number) => Math.round(ms / 60000)

/** 숫자가 0에서 목표값까지 올라간다. 동작 줄이기 설정이면 바로 보여 준다. */
function useCountUp(target: number): number {
  const [value, setValue] = useState(0)
  useEffect(() => {
    const duration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 900
    let frame = 0
    const start = performance.now()
    const step = (time: number) => {
      const progress = duration === 0 ? 1 : Math.min(1, (time - start) / duration)
      setValue(Math.round(target * (1 - Math.pow(1 - progress, 3))))
      if (progress < 1) frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [target])
  return value
}

/** D16·D18 집중 리포트 (FocusOn-design/session-result.html). 점수 없이 시간·흐름·기록·학습 정리 상태를 보여 준다. */
export function SessionResult() {
  const [sessionId, setSessionId] = useState<string | null | undefined>(undefined)
  const [view, setView] = useState<View>({ kind: 'loading' })

  const load = useCallback(async (id: string) => {
    const [summary, learning] = await Promise.all([sessionResultApi.getSummary(id), sessionResultApi.getLearningSummary(id)])
    if (!summary.success) { setView(summary.error.code === 'SESSION_NOT_FOUND' ? { kind: 'missing' } : { kind: 'error', code: summary.error.code }); return }
    setView({ kind: 'ready', summary: summary.data, learning: learning.success ? learning.data : null, retryLearning: !learning.success && learning.error.retryable, noLearning: !learning.success && learning.error.code === 'NOT_FOUND' })
  }, [])

  // 다시 조회할 때는 학습 정리만 바꾼다. 일시 실패면 화면을 그대로 두고 다음에 다시 묻고, 다시 해도 안 되는 실패면 멈춘다.
  const reloadLearning = useCallback(async (id: string) => {
    const learning = await sessionResultApi.getLearningSummary(id)
    setView((current) => current.kind !== 'ready' ? current
      : learning.success ? { ...current, learning: learning.data, retryLearning: false }
      : learning.error.retryable ? current : { ...current, retryLearning: false })
  }, [])

  useEffect(() => {
    // 정적 빌드라 주소의 ?session= 값은 화면이 열린 뒤 읽는다.
    const id = new URLSearchParams(window.location.search).get('session')
    queueMicrotask(() => { setSessionId(id); if (id) void load(id); else setView({ kind: 'missing' }) })
  }, [load])

  // 학습 정리가 만들어지는 동안 5초마다 다시 조회하고, 끝나면 멈춘다(리포트 설계서 2.1).
  const pending = view.kind === 'ready' && (view.learning === null ? view.retryLearning : view.learning.status === 'QUEUED' || view.learning.status === 'GENERATING')
  useEffect(() => {
    if (!pending || !sessionId) return
    const timer = setInterval(() => { void reloadLearning(sessionId) }, POLL_MS)
    return () => clearInterval(timer)
  }, [pending, sessionId, reloadLearning])

  const summary = view.kind === 'ready' ? view.summary : null
  const seconds = Math.floor((summary?.accumulatedActiveMs ?? 0) / 1000)
  const shownMinutes = useCountUp(Math.floor(seconds / 60))
  const shownSeconds = useCountUp(seconds % 60)

  return <main className="app-window onboarding-window result-window">
    <div className="scroll" aria-busy={view.kind === 'loading'}>
      {view.kind === 'loading' ? null : <div className={`report${view.kind !== 'ready' ? ' is-empty' : ''}`}>
        {view.kind !== 'ready' ? (
          <header className="hero is-missing">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="poki" src={view.kind === 'missing' ? '/design/poki-looking.png' : '/design/poki-worried.png'} alt="" />
            <div>
              <p className="kicker">집중 리포트</p>
              <h1>{view.kind === 'missing' ? '기록을 찾을 수 없어요' : '기록을 불러오지 못했어요'}</h1>
              {/* [디자인] 제목이 이미 상황을 말하므로 같은 뜻의 상자를 겹쳐 두지 않고 할 일만 한 줄로 둔다. */}
              <p className="hero-sub" role={view.kind === 'error' ? 'alert' : undefined}>{view.kind === 'error' ? errorText(view) : '홈에서 집중 기록을 다시 열어 주세요.'}</p>
            </div>
          </header>
        ) : <ResultBody summary={view.summary} learning={view.learning} noLearning={view.noLearning} shownMinutes={shownMinutes} shownSeconds={shownSeconds} />}
      </div>}
    </div>
    <footer className="footer">
      <div className="footer-inner actions">
        {view.kind === 'error' && sessionId && <button className="btn btn-ghost" type="button" onClick={() => { setView({ kind: 'loading' }); void load(sessionId) }}>다시 시도</button>}
        <Link className="btn btn-primary" href="/">홈으로 <span aria-hidden="true">→</span></Link>
      </div>
    </footer>
  </main>
}

function ResultBody({ summary, learning, noLearning, shownMinutes, shownSeconds }: { summary: SessionSummary; learning: LearningSummary | null; noLearning: boolean; shownMinutes: number; shownSeconds: number }) {
  const abandoned = summary.status === 'ABANDONED'
  const flow = flowOf(summary)
  const { pauses, retryPauses } = useReportPauses(summary.sessionId)
  const started = summary.startedAt ? new Date(summary.startedAt) : null
  const today = started !== null && started.toDateString() === new Date().toDateString()
  const extended = summary.extensionCount && summary.extendedDurationMs ? ` · ${minutes(summary.extendedDurationMs)}분 연장` : ''
  const autoPause = summary.autoPauseCount === null ? null
    : summary.autoPauseCount === 0 ? '없음'
      : `${summary.autoPauseCount}회${summary.autoPauseReasons.length ? ` · ${[...new Set(summary.autoPauseReasons.map((reason) => AUTO_PAUSE_LABEL[reason] ?? '시스템'))].join(', ')}` : ''}`
  const pause = summary.pauseCount === null ? null : `${summary.pauseCount}회${summary.totalPausedMs !== null ? ` · ${minutes(summary.totalPausedMs)}분` : ''}`
  const meta: Array<[string, string | null]> = [
    ['시간', summary.startedAt && summary.endedAt ? `${timeFormat.format(new Date(summary.startedAt))} – ${timeFormat.format(new Date(summary.endedAt))}` : null],
    ['멈춤', pause],
    ['자동 멈춤', autoPause],
    ['이탈 알림', summary.interventionCount === null ? null : `${summary.interventionCount}회`],
  ]

  return <>
    <header className="hero">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img className="poki" src={abandoned ? '/design/poki-thinking.png' : '/design/poki-celebrate.png'} alt={abandoned ? '생각하는 포키' : '축하하는 포키'} />
      <div>
        <p className="kicker">집중 리포트</p>
        <h1>{abandoned ? '집중이 자동으로 끝났어요' : '이번 집중을 마쳤어요'}</h1>
        <p className="goal">{summary.goalText}</p>
        {abandoned && <p className="abandoned-note">앱과 3분 넘게 연결되지 않아 마지막으로 확인한 시간까지 기록했어요.</p>}
        {/* 달력 기준과 같게 1분 이상만 도장을 찍는다. */}
        {started && summary.accumulatedActiveMs >= 60_000 && <p className="stamp"><span className="stamp-face" aria-hidden="true"><i /></span><span><b>{today ? '오늘' : `${started.getMonth() + 1}월 ${started.getDate()}일`}</b> 집중 달력에 포키가 찍혔어요</span></p>}
      </div>
      <div className="time-col">
        <span className="big-time" aria-label={`집중한 시간 ${Math.floor(summary.accumulatedActiveMs / 60000)}분 ${Math.floor(summary.accumulatedActiveMs / 1000) % 60}초`}><b>{shownMinutes}</b><small>분</small><b>{String(shownSeconds).padStart(2, '0')}</b><small>초</small></span>
        <span className="time-note">{minutes(summary.targetDurationMs - (summary.extendedDurationMs ?? 0))}분 계획{extended}</span>
      </div>
    </header>

    <div className="columns">
      <div className="col">
        <section className="block flow-block" aria-labelledby="flow-title">
          <div className="block-head"><h2 id="flow-title">이번 시간의 흐름</h2></div>
          {flow && flow.related + flow.unrelated + flow.uncertain > 0 ? <>
            <div className="track" role="img" aria-label={`목표와 연결된 활동 ${minutes(flow.related)}분, 다른 활동 ${minutes(flow.unrelated)}분, 판단 보류 ${minutes(flow.uncertain)}분, 분석 안 됨 ${minutes(flow.unavailable)}분`}>
              <span className="c-related" style={{ flex: flow.related }} /><span className="c-unrelated" style={{ flex: flow.unrelated }} /><span className="c-uncertain" style={{ flex: flow.uncertain }} /><span className="c-unavailable" style={{ flex: flow.unavailable }} />
            </div>
            <ul className="legend">
              <li><i className="c-related" />목표 활동<strong>{minutes(flow.related)}분</strong></li>
              <li><i className="c-unrelated" />다른 활동<strong>{minutes(flow.unrelated)}분</strong></li>
              <li><i className="c-uncertain" />판단 보류<strong>{minutes(flow.uncertain)}분</strong></li>
              <li><i className="c-unavailable" />분석 안 됨<strong>{minutes(flow.unavailable)}분</strong></li>
            </ul>
            {flow.rate === null
              ? <p className="rate"><small>분석된 구간이 짧아 목표 활동 비율은 표시하지 않았어요.</small></p>
              : <p className="rate" title="판단 보류·분석 안 됨 시간은 빼고 계산해요">목표 활동 비율 <strong>{flow.rate}%</strong></p>}
          </> : <p className="muted-note">아직 분석된 흐름이 없어요.</p>}
          <PauseTimeline summary={summary} pauses={pauses} retry={retryPauses} />
        </section>

        <OffTaskBreakdown key={summary.sessionId} summary={summary} pauses={pauses} />
        <LearningBlock sessionId={summary.sessionId} learning={learning} noLearning={noLearning} />
      </div>
      <div className="col">
        <section className="block meta-block" aria-labelledby="meta-title">
          <div className="block-head"><h2 id="meta-title">기록</h2></div>
          <dl className="meta">
            {meta.map(([label, value]) => <div key={label}><dt>{label}</dt><dd className={value === null ? 'is-empty' : ''}>{value ?? '기록 없음'}</dd></div>)}
          </dl>
        </section>
      </div>
    </div>
  </>
}

// 범례가 스크롤 영역에 처음 들어올 때만 나타낸다.
function observeOffTaskChart(element: HTMLDivElement | null) {
  if (!element || !('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  element.dataset.reveal = 'pending'
  const observer = new IntersectionObserver(([entry]) => {
    if (!entry.isIntersecting) return
    element.dataset.reveal = 'visible'
    observer.disconnect()
  }, { threshold: 0.1 })
  observer.observe(element)
  return () => observer.disconnect()
}

function OffTaskBreakdown({ summary, pauses }: { summary: SessionSummary; pauses: SessionPause[] | 'error' | null }) {
  const sessionId = summary.sessionId
  const [breakdown, setBreakdown] = useState<OffTaskReport | 'error' | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    void sessionResultApi.getOffTaskBreakdown(sessionId).then((result) => {
      if (!cancelled) setBreakdown(result.success ? result.data : 'error')
    })
    return () => { cancelled = true }
  }, [sessionId, attempt])

  const first = breakdown && breakdown !== 'error' && Array.isArray(pauses) ? firstOffTaskAt(breakdown, summary, pauses) : null
  return <section className="block" aria-labelledby="off-task-title">
    <div className="block-head"><h2 id="off-task-title">딴짓 종류 비율</h2></div>
    {first && <dl className="meta">
      <div><dt>첫 이탈 시각</dt><dd><time dateTime={first}>{pauseTimeFormat.format(new Date(first))}</time></dd></div>
    </dl>}
    {breakdown === null ? <p className="muted-note" role="status">딴짓 종류를 불러오고 있어요.</p>
      : breakdown === 'error' ? <>
        <p className="muted-note" role="status">딴짓 종류를 불러오지 못했어요.</p>
        <button className="btn btn-ghost report-retry" type="button" onClick={() => {
          setBreakdown(null)
          setAttempt((value) => value + 1)
        }}>딴짓 종류 다시 시도</button>
      </> : breakdown.categories.length === 0 ? <p className="muted-note">분류된 딴짓 기록이 없어요.</p>
        : <div className="off-task-chart" ref={observeOffTaskChart}>
          <p className="muted-note">분류된 기록 건수 기준이에요. 판단을 보류한 기록은 제외해요.</p>
          <div className="track" aria-hidden="true">
            {breakdown.categories.map(({ category, share }) => <span key={category} style={{ flex: share, background: OFF_TASK_STYLE[category].color }} />)}
          </div>
          <ul className="off-task-legend" role="list">
            {breakdown.categories.map(({ category, share }) => {
              const { label, color, icon } = OFF_TASK_STYLE[category]
              return <li key={category} style={{ '--category-color': color } as CSSProperties}>
                <span className="off-task-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d={icon} /></svg>
                </span>
                <span>{label}</span><strong>{offTaskPercent.format(share)}</strong>
              </li>
            })}
          </ul>
        </div>}
  </section>
}

function useReportPauses(sessionId: string) {
  const [pauses, setPauses] = useState<SessionPause[] | 'error' | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let cancelled = false
    void sessionResultApi.getPauses(sessionId).then((result) => {
      if (!cancelled) setPauses(result.success ? result.data : 'error')
    })
    return () => { cancelled = true }
  }, [sessionId, attempt])

  return { pauses, retryPauses: () => { setPauses(null); setAttempt((value) => value + 1) } }
}

function PauseTimeline({ summary, pauses, retry }: { summary: SessionSummary; pauses: SessionPause[] | 'error' | null; retry: () => void }) {
  if (pauses === 'error') return <div className="pause-timeline">
    <p className="muted-note" role="status">일시정지 구간을 불러오지 못했어요.</p>
    <button className="btn btn-ghost report-retry" type="button" onClick={retry}>일시정지 구간 다시 시도</button>
  </div>
  if (!pauses?.length) return null

  const positions = pauseTimelinePositions(pauses, summary)
  const activeSeconds = Math.floor(summary.accumulatedActiveMs / 1000)
  return <figure className="pause-timeline">
    <figcaption>일시정지 구간</figcaption>
    {positions.length > 0 && <div aria-hidden="true">
      <div className="pause-axis">
        {positions.map((position, index) => <span key={pauses[index].pausedAt + index} style={{ left: `${position}%` }}>Ⅱ</span>)}
      </div>
      <div className="pause-axis-label"><span>0분</span><span>집중 {Math.floor(activeSeconds / 60)}분 {activeSeconds % 60}초</span></div>
    </div>}
    <ul className="pause-times" aria-label="멈춤 및 재개 시각">
      {pauses.map(({ pausedAt, resumedAt, pauseReason }, index) => {
        const end = resumedAt ?? summary.endedAt
        const reason = Object.hasOwn(AUTO_PAUSE_LABEL, pauseReason) ? AUTO_PAUSE_LABEL[pauseReason] : null
        return <li key={pausedAt + index}>
          <time dateTime={pausedAt}>{pauseTimeFormat.format(new Date(pausedAt))}</time>
          {end && <>부터 <time dateTime={end}>{pauseTimeFormat.format(new Date(end))}</time>까지</>}
          {!resumedAt && (end ? ' · 재개 없이 종료' : ' · 재개 기록 없음')}
          {reason && ` · ${reason}`}
        </li>
      })}
    </ul>
  </figure>
}

/** 학습 정리: 정리가 끝났으면 요약 본문 + 다음 시작 지점 + 정리 보기·복사, 아니면 상태 한 줄. */
function LearningBlock({ sessionId, learning, noLearning }: { sessionId: string; learning: LearningSummary | null; noLearning: boolean }) {
  const [copied, setCopied] = useState<'' | '복사했어요' | '복사하지 못했어요'>('')
  const ready = learning?.status === 'READY' && learning.overview ? learning : null
  const waiting = learning?.status === 'QUEUED' || learning?.status === 'GENERATING'
  const copy = async () => {
    if (!ready) return
    try { await navigator.clipboard.writeText([ready.overview, ready.nextStartPrompt && `다음 시작 지점: ${ready.nextStartPrompt}`].filter(Boolean).join('\n\n')); setCopied('복사했어요') }
    catch { setCopied('복사하지 못했어요') }
    setTimeout(() => setCopied(''), 1800)
  }
  return <section className="block learn" aria-labelledby="learn-title">
    <h2 id="learn-title">학습 정리</h2>
    {ready ? <>
      <span className="learn-acts">
        <Link className="text-btn" href={`/notes/?session=${encodeURIComponent(sessionId)}`}>정리 보기</Link>
        <button className="text-btn" type="button" onClick={() => { void copy() }}>{copied || '복사'}</button>
      </span>
      <p className="learn-overview">{ready.overview}</p>
      {ready.nextStartPrompt && <p className="learn-next"><b>다음 시작 지점</b><span>{ready.nextStartPrompt}</span></p>}
    </> : <p className="learn-line" role="status">
      {waiting && <span className="learn-writing" aria-hidden="true"><span /><span /><span /></span>}
      <span>{LEARN_LINE[learning?.status === 'READY' ? 'UNKNOWN' : learning?.status ?? (noLearning ? 'NONE' : 'UNKNOWN')]}</span>
    </p>}
  </section>
}
