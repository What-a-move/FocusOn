'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { flowOf, sessionResultApi, type LearningSummary, type SessionSummary } from './session-result-api'
import '../onboarding/onboarding.css'
import './session-result.css'

const POLL_MS = 5000
const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })
const AUTO_PAUSE_LABEL: Record<string, string> = { REQUIRED_PERMISSION: '권한 꺼짐', EXTENSION_UNLINKED: '연결 끊김', APP_RESTART: '앱 재시작', SYSTEM: '시스템' }
// 학습 정리 상태별 문구(FocusOn-design/session-result.html). 시스템 안내라 존댓말이다.
const REVIEW = {
  QUEUED: { kind: 'pending', icon: 'spinner', heading: '학습 정리를 준비하고 있어요', copy: '' },
  GENERATING: { kind: 'pending', icon: 'spinner', heading: '학습 내용을 정리하고 있어요', copy: '잠시만 기다려 주세요.' },
  READY: { kind: 'ready', icon: '✓', heading: '학습 정리가 준비됐어요', copy: '' },
  INSUFFICIENT_DATA: { kind: 'neutral', icon: '–', heading: '이번에는 학습 기록만 남겼어요', copy: '정리할 내용이 부족했어요.' },
  FAILED: { kind: 'error', icon: '!', heading: '학습 정리를 만들지 못했어요.', copy: '집중 기록은 그대로 남아 있어요.' },
  UNKNOWN: { kind: 'neutral', icon: '–', heading: '학습 정리 상태를 확인하지 못했어요', copy: '잠시 후 다시 확인할게요.' },
} as const

type View =
  | { kind: 'loading' }
  | { kind: 'missing' }
  | { kind: 'error' }
  | { kind: 'ready'; summary: SessionSummary; learning: LearningSummary | null }

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
    if (!summary.success) { setView({ kind: summary.error.code === 'SESSION_NOT_FOUND' ? 'missing' : 'error' }); return }
    setView({ kind: 'ready', summary: summary.data, learning: learning.success ? learning.data : null })
  }, [])

  useEffect(() => {
    // 정적 빌드라 주소의 ?session= 값은 화면이 열린 뒤 읽는다.
    const id = new URLSearchParams(window.location.search).get('session')
    queueMicrotask(() => { setSessionId(id); if (id) void load(id); else setView({ kind: 'missing' }) })
  }, [load])

  // 학습 정리가 만들어지는 동안 5초마다 다시 조회하고, 끝나면 멈춘다(리포트 설계서 2.1).
  const pending = view.kind === 'ready' && (view.learning === null || view.learning.status === 'QUEUED' || view.learning.status === 'GENERATING')
  useEffect(() => {
    if (!pending || !sessionId) return
    const timer = setInterval(() => { void load(sessionId) }, POLL_MS)
    return () => clearInterval(timer)
  }, [pending, sessionId, load])

  const summary = view.kind === 'ready' ? view.summary : null
  const seconds = Math.floor((summary?.accumulatedActiveMs ?? 0) / 1000)
  const shownMinutes = useCountUp(Math.floor(seconds / 60))
  const shownSeconds = useCountUp(seconds % 60)

  if (view.kind === 'loading') return <main className="app-window onboarding-window result-window" aria-busy="true" />
  return <main className="app-window onboarding-window result-window">
    <header className="titlebar"><div className="window-controls" aria-hidden="true"><i /><i /><i /></div></header>
    <div className="scroll">
      <div className="report">
        {view.kind !== 'ready' ? <>
          <header className="hero is-missing">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="poki" src="/design/poki-floating-curious-v8.png" alt="궁금해하는 포키" />
            <div>
              <p className="kicker">집중 리포트</p>
              <h1>{view.kind === 'missing' ? '기록을 찾을 수 없어요' : '기록을 불러오지 못했어요'}</h1>
            </div>
          </header>
          <div className="notice is-error" role="alert">
            {view.kind === 'missing'
              ? <><strong>요청한 집중 기록을 찾지 못했어요.</strong><span>홈에서 다시 확인해 주세요.</span></>
              : <><strong>집중 기록을 불러오지 못했어요.</strong><span>인터넷 연결을 확인한 뒤 다시 시도해 주세요.</span></>}
          </div>
          {view.kind === 'error' && sessionId && <button className="btn btn-ghost retry" type="button" onClick={() => { setView({ kind: 'loading' }); void load(sessionId) }}>다시 시도</button>}
        </> : <ResultBody summary={view.summary} learning={view.learning} shownMinutes={shownMinutes} shownSeconds={shownSeconds} />}

        <div className="actions">
          <Link className="btn btn-primary" href="/">홈으로 <span aria-hidden="true">→</span></Link>
        </div>
      </div>
    </div>
  </main>
}

function ResultBody({ summary, learning, shownMinutes, shownSeconds }: { summary: SessionSummary; learning: LearningSummary | null; shownMinutes: number; shownSeconds: number }) {
  const abandoned = summary.status === 'ABANDONED'
  const flow = flowOf(summary)
  const started = summary.startedAt ? new Date(summary.startedAt) : null
  const today = started !== null && started.toDateString() === new Date().toDateString()
  const extended = summary.extensionCount && summary.extendedDurationMs ? ` · ${minutes(summary.extendedDurationMs)}분 연장` : ''
  const review = REVIEW[learning?.status ?? 'UNKNOWN']
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
      <img className="poki" src={abandoned ? '/design/poki-floating-curious-v8.png' : '/design/poki-floating-cheer-v7.png'} alt={abandoned ? '궁금해하는 포키' : '응원하는 포키'} />
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
        </section>

        <section className="block review-block" aria-labelledby="review-title">
          <div className="block-head"><h2 id="review-title">학습 정리</h2></div>
          <div className={`review-row is-${review.kind}`} role={review.kind === 'error' ? 'alert' : 'status'}>
            <span className="state-icon" aria-hidden="true">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              {review.icon === 'spinner' ? <img className="poki-turn" src="/design/poki-floating-logo-base-v1.png" alt="" /> : review.icon}
            </span>
            <div className="review-text">
              <strong>{review.heading}</strong>
              {(learning?.status === 'READY' ? learning.overview : review.copy) && <span>{learning?.status === 'READY' ? learning.overview : review.copy}</span>}
            </div>
          </div>
        </section>
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
