'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { dashboardApi, dayKey, monthKey, type DailySession } from '../dashboard/dashboard-api'
import type { DailyReport } from '../../electron/shared/report-contract.cjs'
import { LoadError, SettingsShell } from '../settings/SettingsShell'
import './records.css'

// 디자인 원본: FocusOn-design/records.html. 시안의 `기록 시안 보기`·`예시` 태그, 하루 흐름 막대, 회차 `n회 연장`은
// 미리보기 요소이거나 일간 리포트 응답에 없는 값이라 넣지 않는다(records-PLAN.md).
type Load<T> = { status: 'loading' } | { status: 'ready'; data: T } | { status: 'error'; code: string }

const WEEKDAYS = '월화수목금토일'
const fromKey = (key: string) => new Date(`${key}T12:00:00`)
const addDays = (date: Date, days: number) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + days, 12)
const weekStart = (key: string) => { const date = fromKey(key); return addDays(date, -((date.getDay() + 6) % 7)) }
const isDayKey = (value: string | null): value is string => !!value && /^\d{4}-\d{2}-\d{2}$/u.test(value) && dayKey(fromKey(value)) === value
const minutesText = (minutes: number) => minutes >= 60 ? `${Math.floor(minutes / 60)}시간${minutes % 60 ? ` ${minutes % 60}분` : ''}` : `${minutes}분`
const clock = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
const longDate = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })
const shortDate = (date: Date) => `${date.getMonth() + 1}월 ${date.getDate()}일`

/** 집중 기록: 고른 날이 속한 주(월~일)의 하루별 집중 시간과, 고른 날의 회차 목록. */
export function Records() {
  const [today, setToday] = useState<string | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  // 기록 있음(서버 hasStudyActivity)인 날만 분으로 담는다.
  const [week, setWeek] = useState<Load<Record<string, number>>>({ status: 'loading' })
  const [day, setDay] = useState<Load<DailyReport>>({ status: 'loading' })
  const weekRequest = useRef(0)
  const dayRequest = useRef(0)
  const shownWeek = useRef('')

  useEffect(() => {
    // 정적 빌드라 주소의 ?date= 값은 화면이 열린 뒤 읽는다. 잘못됐거나 미래면 오늘이다.
    const now = dayKey(new Date())
    const requested = new URLSearchParams(window.location.search).get('date')
    queueMicrotask(() => { setToday(now); setSelected(isDayKey(requested) && requested <= now ? requested : now) })
  }, [])

  const loadWeek = useCallback(async (startKey: string) => {
    const id = ++weekRequest.current
    setWeek({ status: 'loading' })
    const start = fromKey(startKey)
    // 한 주가 두 달에 걸치면 두 달을 모두 부른다.
    const months = [...new Set([monthKey(start), monthKey(addDays(start, 6))])]
    const results = await Promise.all(months.map((month) => dashboardApi.getCalendar(month)))
    if (id !== weekRequest.current) return
    const failed = results.find((result) => !result.success)
    if (failed && !failed.success) { setWeek({ status: 'error', code: failed.error.code }); return }
    const minutes: Record<string, number> = {}
    for (const result of results) if (result.success) for (const item of result.data.days) if (item.hasStudyActivity) minutes[item.date] = Math.floor(item.totalActiveMs / 60000)
    setWeek({ status: 'ready', data: minutes })
  }, [])

  const loadDay = useCallback(async (key: string) => {
    const id = ++dayRequest.current
    setDay({ status: 'loading' })
    const result = await dashboardApi.getDaily(key)
    if (id !== dayRequest.current) return
    setDay(result.success
      ? { status: 'ready', data: { ...result.data, sessions: [...result.data.sessions].sort((a, b) => a.startedAt.localeCompare(b.startedAt)) } }
      : { status: 'error', code: result.error.code })
  }, [])

  useEffect(() => {
    if (!selected) return
    window.history.replaceState(null, '', `?date=${selected}`)
    const startKey = dayKey(weekStart(selected))
    // 같은 주 안에서 날짜만 바꾸면 주간 막대는 다시 부르지 않는다.
    queueMicrotask(() => {
      if (shownWeek.current !== startKey) { shownWeek.current = startKey; void loadWeek(startKey) }
      void loadDay(selected)
    })
  }, [selected, loadWeek, loadDay])

  if (!today || !selected) return <SettingsShell active="records"><div className="fo-records" aria-busy="true" /></SettingsShell>

  const start = weekStart(selected)
  const end = addDays(start, 6)
  const days = Array.from({ length: 7 }, (_, index) => {
    const key = dayKey(addDays(start, index))
    return { key, index, minutes: week.status === 'ready' ? week.data[key] ?? 0 : 0 }
  })
  const scale = Math.max(60, ...days.map((item) => item.minutes))
  const weekSum = days.reduce((sum, item) => sum + item.minutes, 0)
  const error = week.status === 'error' ? week : day.status === 'error' ? day : null
  const pick = (key: string) => setSelected(key > today ? today : key)
  const shift = (delta: number) => pick(dayKey(addDays(fromKey(selected), delta)))
  const retry = () => { shownWeek.current = dayKey(start); void loadWeek(dayKey(start)); void loadDay(selected) }

  return <SettingsShell active="records">
    <div className="fo-records">
      <header className="fo-records-head">
        <h1>집중 기록</h1>
        <div className="fo-records-tools">
          <button className="fo-icon-tool" type="button" aria-label="이전 주" onClick={() => shift(-7)}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg></button>
          <span className="fo-week-range" aria-live="polite">{shortDate(start)} – {start.getMonth() === end.getMonth() ? `${end.getDate()}일` : shortDate(end)}</span>
          <button className="fo-icon-tool" type="button" aria-label="다음 주" onClick={() => shift(7)} disabled={dayKey(addDays(start, 7)) > today}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" /></svg></button>
          <label className="fo-icon-tool fo-date-picker" title="날짜 선택">
            <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="5" width="17" height="15" rx="3" /><path d="M3.5 10h17M8 3v4M16 3v4" /></svg>
            <input type="date" aria-label="날짜 선택" max={today} value={selected} onChange={(event) => { if (isDayKey(event.target.value)) pick(event.target.value) }} />
          </label>
          <button className="fo-text-tool" type="button" onClick={() => pick(today)} disabled={selected === today}>오늘</button>
        </div>
      </header>

      {/* 불러오지 못하면 빈 막대 자리를 남기지 않고 아래 오류 안내만 둔다. */}
      {week.status !== 'error' && <div className="fo-week-wrap">
        {/* 불러오는 중·실패면 합계를 비워 둔다. 자리는 남겨 막대 위치가 흔들리지 않게 한다. */}
        <div className="fo-week-total">{week.status === 'ready' && <>
          <span>{dayKey(start) === dayKey(weekStart(today)) ? '이번 주' : '이 주'}</span>
          <strong>{minutesText(weekSum)}</strong>
          {weekSum > 0 && <em>{days.filter((item) => item.minutes).length}일 집중</em>}
        </>}</div>
        <div className="fo-week" role="group" aria-label="주간 날짜">
          {days.map(({ key, index, minutes }) => <button
            className={`fo-day${key === today ? ' is-today' : ''}${minutes ? ' has-record' : ''}`}
            type="button" key={key} disabled={key > today} aria-pressed={key === selected} onClick={() => pick(key)}
            aria-label={`${shortDate(fromKey(key))}${minutes ? `, ${minutesText(minutes)} 집중` : ''}`}>
            <span className="fo-bar-area">
              <span className="fo-bar-val">{minutes ? minutesText(minutes) : ''}</span>
              <span className="fo-bar" style={{ height: minutes ? `calc(${(minutes / scale) * 100}% - 18px)` : undefined, animationDelay: `${index * 0.04}s` }} />
            </span>
            <span className="fo-day-name">{key === today ? '오늘' : WEEKDAYS[index]}</span>
            <span className="fo-day-num">{fromKey(key).getDate()}</span>
          </button>)}
        </div>
      </div>}

      {week.status === 'error' ? <LoadError code={week.code} what="기록을" onRetry={retry} /> : <section className="fo-day-section" aria-label={`${longDate.format(fromKey(selected))} 기록`}>
        {error ? <LoadError code={error.code} what="기록을" onRetry={retry} />
          : day.status === 'loading' ? <div className="fo-records-skeleton" aria-hidden="true"><i /><i /><i /></div>
          : day.status === 'ready' && <DayRecords date={selected} today={today} report={day.data} />}
      </section>}
    </div>
  </SettingsShell>
}

function DayRecords({ date, today, report }: { date: string; today: string; report: DailyReport }) {
  // [주의] 하루 총합은 서버가 종료 확정 세션만으로 계산한 값을 쓴다. 진행 중 회차의 현재 시간은 목록에만 보인다(리포트 설계서 2.2).
  const { sessions } = report
  const total = Math.floor(report.totalActiveMs / 60000)
  return <div className="fo-swap" key={date}>
    <div className="fo-day-summary">
      <span className="fo-day-date">{longDate.format(fromKey(date))}{date === today ? ' · 오늘' : ''}</span>
      <span className="fo-day-time" aria-label="이날 집중한 시간">
        {total >= 60 ? <><b>{Math.floor(total / 60)}</b><small>시간</small><b>{total % 60}</b><small>분</small></> : <><b>{total}</b><small>분</small></>}
      </span>
      <span className="fo-day-count">{sessions.filter((item) => item.endedAt).length}회</span>
    </div>
    {sessions.length === 0 ? <div className="fo-records-empty">
      <Image src="/design/poki-looking.png" alt="두리번거리는 포키" width={240} height={240} />
      <h2>이날은 기록이 없어요</h2>
      <Link className="fo-button fo-button-primary" href="/goal/">집중 시작하기</Link>
    </div> : <ol className="fo-timeline" aria-label="이날의 집중 회차">
      {sessions.map((item) => {
        const minutes = Math.floor(item.activeMs / 60000)
        // 끝나지 않은 회차는 결과가 없으니 집중 화면으로 보낸다.
        return <li key={item.sessionId}>
          <span className="fo-clock">{clock.format(new Date(item.startedAt))}</span>
          <span className="fo-dot" />
          <Link className="fo-entry" href={item.endedAt ? `/session-result/?session=${encodeURIComponent(item.sessionId)}` : '/session/'}
            aria-label={`${item.goal}, ${minutes}분 집중, ${item.endedAt ? '회차 리포트 보기' : '진행 중인 집중 보기'}`}>
            <span className="fo-entry-main">
              <span className="fo-entry-goal">{item.goal}</span>
              <span className="fo-entry-sub">{item.endedAt ? `~ ${clock.format(new Date(item.endedAt))}` : '진행 중'}</span>
            </span>
            <span className="fo-entry-time">{minutes}분</span>
            <span className="fo-entry-arrow" aria-hidden="true">›</span>
          </Link>
        </li>
      })}
    </ol>}
  </div>
}
