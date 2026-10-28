'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { AppSidebar } from './AppSidebar'
import { dashboardApi, dayKey, loadRecentSessions, monthKey, type CalendarMonth, type DailySession } from './dashboard-api'
import './dashboard.css'

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일']

type CalendarView =
  | { status: 'loading' }
  | { status: 'ready'; data: CalendarMonth }
  | { status: 'error' }
  // 오프라인이면 이전에 확인한 값만 그 시각과 함께 보여 준다. 현재 값처럼 단정하지 않는다.
  | { status: 'offline'; cached: { data: CalendarMonth; loadedAt: Date } | null }
type RecentView = { status: 'loading' } | { status: 'ready'; items: DailySession[] } | { status: 'error' }

const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })

function CountUp({ value }: { value: number }) {
  const [shown, setShown] = useState(0)
  useEffect(() => {
    if (value === 0 || matchMedia('(prefers-reduced-motion: reduce)').matches) { queueMicrotask(() => setShown(value)); return }
    const start = performance.now()
    let frame = 0
    const step = (time: number) => {
      const progress = Math.min(1, (time - start) / 700)
      setShown(Math.round(value * (1 - Math.pow(1 - progress, 3))))
      if (progress < 1) frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [value])
  return <b>{shown}</b>
}

function Calendar({ today, month, view, onMove, onRetry }: {
  today: Date; month: Date; view: CalendarView; onMove: (delta: number) => void; onRetry: () => void
}) {
  const data = view.status === 'ready' ? view.data : view.status === 'offline' ? view.cached?.data ?? null : null
  const active = new Set(data?.days.filter((day) => day.hasStudyActivity).map((day) => day.date))
  const isCurrentMonth = monthKey(month) === monthKey(today)
  const offset = (month.getDay() + 6) % 7
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  let order = 0

  return <>
    <div className="fo-panel-head">
      <h2 id="calendar-title">집중 달력</h2>
      <div className="fo-month-controls">
        <button type="button" onClick={() => onMove(-1)} aria-label="이전 달">‹</button>
        <span>{month.getFullYear()}년 {month.getMonth() + 1}월</span>
        <button type="button" onClick={() => onMove(1)} disabled={isCurrentMonth} aria-label="다음 달">›</button>
      </div>
    </div>
    {view.status === 'loading' && <div className="fo-skeleton" aria-hidden="true">{Array.from({ length: 35 }, (_, index) => <i key={index} />)}</div>}
    {view.status !== 'loading' && view.status !== 'error' && <div className="fo-calendar-grid" key={monthKey(month)} aria-label={`${month.getFullYear()}년 ${month.getMonth() + 1}월 집중 달력`}>
      {WEEKDAYS.map((day) => <span className="fo-weekday" key={day}>{day}</span>)}
      {Array.from({ length: offset }, (_, index) => <span key={`blank-${index}`} />)}
      {Array.from({ length: days }, (_, index) => {
        const date = new Date(month.getFullYear(), month.getMonth(), index + 1)
        const key = dayKey(date)
        const future = date > today
        // [주의] 미래 날짜와 확인되지 않은 날에는 포키를 표시하지 않는다.
        const studied = !future && active.has(key)
        const classes = ['fo-calendar-day', key === dayKey(today) ? 'today' : '', future ? 'future' : '', studied ? 'completed' : ''].filter(Boolean).join(' ')
        const style = studied ? { animationDelay: `${0.3 + order++ * 0.07}s` } : undefined
        return <span className={classes} style={style} key={key} aria-label={`${date.getMonth() + 1}월 ${date.getDate()}일${studied ? ', 집중 기록 있음' : ', 기록 없음'}`}>
          {studied ? <span className="fo-poki-face" aria-hidden="true" /> : date.getDate()}
        </span>
      })}
    </div>}
    {view.status === 'loading' && <div className="fo-calendar-state" role="status"><strong>집중 달력을 불러오고 있어요.</strong><span>잠시만 기다려 주세요.</span></div>}
    {view.status === 'error' && <div className="fo-calendar-state is-error" role="alert">
      <strong>집중 달력을 불러오지 못했어요.</strong><span>확인되지 않은 날짜에는 포키를 표시하지 않아요.</span><span>잠시 후 다시 시도해 주세요.</span>
      <button className="fo-button fo-button-ghost fo-retry" type="button" onClick={onRetry}>다시 시도</button>
    </div>}
    {view.status === 'offline' && <div className="fo-calendar-state is-error" role="alert">
      <strong>인터넷에 연결되어 있지 않아요.</strong>
      <span>{view.cached ? `${timeFormat.format(view.cached.loadedAt)}에 마지막으로 확인한 기록만 보여 드려요.` : '확인된 기록이 없어 포키를 표시하지 않아요.'}</span>
      <button className="fo-button fo-button-ghost fo-retry" type="button" onClick={onRetry}>다시 시도</button>
    </div>}
  </>
}

function RecentGoals({ view, onRetry }: { view: RecentView; onRetry: () => void }) {
  return <>
    <div className="fo-panel-head"><h2 id="recent-title">지난 목표</h2></div>
    {view.status === 'loading' && <p className="fo-recent-loading" role="status">기록을 불러오고 있어요.</p>}
    {view.status === 'error' && <div className="fo-calendar-state is-error" role="alert">
      <strong>지난 목표를 불러오지 못했어요.</strong><span>잠시 후 다시 시도해 주세요.</span>
      <button className="fo-button fo-button-ghost fo-retry" type="button" onClick={onRetry}>다시 시도</button>
    </div>}
    {view.status === 'ready' && <ul className="fo-recent-list">
      {view.items.length === 0 && <li className="fo-records-empty"><strong>아직 기록이 없어요</strong><span>첫 집중을 마치면 여기에 나타나요.</span></li>}
      {view.items.map((item) => {
        const ended = new Date(item.endedAt ?? item.startedAt)
        return <li key={item.sessionId}><div className="fo-recent-item">
          <span className="fo-recent-day" aria-hidden="true"><b>{ended.getDate()}</b><small>{ended.getMonth() + 1}월</small></span>
          <div><span className="fo-recent-goal">{item.goal}</span><small className="fo-recent-date">{new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' }).format(ended)}</small></div>
          <span className="fo-recent-time">{Math.round(item.activeMs / 60000)}분</span>
        </div></li>
      })}
    </ul>}
  </>
}

/** D05·D08 홈 (FocusOn-design/main.html). 기록 값은 서버가 확정한 값만 보여 준다. */
export function HomeDashboard({ name, onLogout, busy, message }: { name: string; onLogout: () => void; busy: boolean; message: string | null }) {
  const router = useRouter()
  const [today, setToday] = useState<Date | null>(null)
  const [month, setMonth] = useState<Date | null>(null)
  const [calendar, setCalendar] = useState<CalendarView>({ status: 'loading' })
  const [current, setCurrent] = useState<CalendarMonth | null | 'error'>(null)
  const [recent, setRecent] = useState<RecentView>({ status: 'loading' })
  const cache = useRef(new Map<string, { data: CalendarMonth; loadedAt: Date }>())
  const latestCalendar = useRef(0)
  const latestSummary = useRef(0)
  const shownDay = useRef('')

  useEffect(() => {
    // [유지보수] 정적 빌드 시각과 실행 시각이 달라도 날짜가 어긋나지 않게 실행 후 계산한다.
    // 앱을 켜 둔 채 자정이 지나거나 창으로 돌아왔을 때 날짜가 바뀌었으면 이번 달로 다시 불러온다.
    let timer: ReturnType<typeof setTimeout> | undefined
    const sync = () => {
      const now = new Date()
      if (dayKey(now) !== shownDay.current) {
        shownDay.current = dayKey(now)
        setToday(now)
        setMonth(new Date(now.getFullYear(), now.getMonth(), 1))
      }
      clearTimeout(timer)
      timer = setTimeout(sync, new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime() - now.getTime() + 1000)
    }
    queueMicrotask(sync)
    window.addEventListener('focus', sync)
    return () => { clearTimeout(timer); window.removeEventListener('focus', sync) }
  }, [])

  const loadMonth = useCallback(async (target: Date, now: Date) => {
    const key = monthKey(target)
    const isCurrentMonth = key === monthKey(now)
    // [주의] 늦게 온 이전 응답이 새 화면을 덮지 않게 달력과 이번 달 요약은 각자 마지막 요청만 반영한다.
    const calendarRequest = ++latestCalendar.current
    const summaryRequest = isCurrentMonth ? ++latestSummary.current : 0
    setCalendar({ status: 'loading' })
    const result = await dashboardApi.getCalendar(key)
    if (result.success) cache.current.set(key, { data: result.data, loadedAt: new Date() })
    if (calendarRequest === latestCalendar.current) {
      setCalendar(result.success ? { status: 'ready', data: result.data }
        : result.error.code === 'LOCAL_NETWORK_UNAVAILABLE' ? { status: 'offline', cached: cache.current.get(key) ?? null } : { status: 'error' })
    }
    // 상단 요약과 지난 목표는 이번 달 기준이다. 다른 달로 넘겨도 이번 달 응답은 끝까지 반영한다.
    if (!isCurrentMonth || summaryRequest !== latestSummary.current) return
    if (!result.success) { setCurrent('error'); setRecent({ status: 'error' }); return }
    setCurrent(result.data)
    setRecent({ status: 'loading' })
    const sessions = await loadRecentSessions(result.data, dayKey(now))
    if (summaryRequest !== latestSummary.current) return
    setRecent(sessions.success ? { status: 'ready', items: sessions.data } : { status: 'error' })
  }, [])

  useEffect(() => {
    if (today && month) queueMicrotask(() => { void loadMonth(month, today) })
  }, [today, month, loadMonth])

  const todayMinutes = today && current && current !== 'error'
    ? Math.round((current.days.find((day) => day.date === dayKey(today))?.totalActiveMs ?? 0) / 60000)
    : null
  const monthDays = current && current !== 'error' ? current.studyDayCount : null

  return <main className="fo-home-window">
    <AppSidebar active="home" name={name} onLogout={onLogout} busy={busy} />
    <div className="fo-home-content">
      <header className="fo-topbar">
        <div className="fo-rise"><span>{today ? new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' }).format(today) : '오늘'}</span><p><strong>{name}</strong>님, 안녕하세요</p></div>
        <ul className="fo-rise" style={{ animationDelay: '.1s' }} aria-label="오늘 요약">
          <li><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10" fill="#f2b632"/><circle cx="12" cy="12" r="7" fill="#fff6dc"/><path d="M12 8v4.2l2.8 1.8" fill="none" stroke="#8a5a3b" strokeWidth="2" strokeLinecap="round"/></svg><span>오늘 {todayMinutes === null ? <b>–</b> : <CountUp value={todayMinutes} />}분</span></li>
          <li><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4.5" width="18" height="16" rx="4" fill="#8a5a3b"/><rect x="5" y="9" width="14" height="9.5" rx="2" fill="#fff6dc"/><path d="M9 13.8l2 2 4-4" fill="none" stroke="#e0a52a" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/></svg><span>이번 달 {monthDays === null ? <b>–</b> : <CountUp value={monthDays} />}일</span></li>
        </ul>
      </header>
      <div className="fo-dashboard">
        {message && <div className="fo-notice" role="alert"><strong>요청을 마치지 못했어요.</strong><span>{message}</span></div>}
        <section className="fo-hero" aria-labelledby="focus-title">
          <div className="fo-poki-stage"><Image src="/design/poki-main-planning.png" alt="체크리스트를 든 포키" width={220} height={220} priority /></div>
          <div>
            <h1 className="fo-rise" style={{ animationDelay: '.15s' }} id="focus-title">오늘은 뭐에 <span className="fo-hl">집중</span>할까요?</h1>
            <button className="fo-button fo-button-primary fo-goal-button fo-rise" style={{ animationDelay: '.35s' }} type="button" onClick={() => router.push('/goal/')}>목표 설정하기 <span aria-hidden="true">→</span></button>
          </div>
        </section>
        <div className="fo-dashboard-grid">
          <section className="fo-dashboard-block" aria-labelledby="calendar-title">
            {today && month && <Calendar today={today} month={month} view={calendar}
              onMove={(delta) => setMonth(new Date(month.getFullYear(), month.getMonth() + delta, 1))}
              onRetry={() => { void loadMonth(month, today) }} />}
          </section>
          <section className="fo-dashboard-block" aria-labelledby="recent-title">
            <RecentGoals view={recent} onRetry={() => {
              if (!today || !month) return
              // 지난 목표는 이번 달 달력에서 만든다. 다른 달을 보고 있으면 이번 달로 돌아온다.
              if (monthKey(month) === monthKey(today)) void loadMonth(month, today)
              else setMonth(new Date(today.getFullYear(), today.getMonth(), 1))
            }} />
          </section>
        </div>
      </div>
    </div>
  </main>
}
