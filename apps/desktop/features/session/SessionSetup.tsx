'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { CONFIRMED_GOAL_KEY } from '../goal/goal-api'
import {
  formatMinutes, isValidMinutes, MAX_MINUTES, MIN_MINUTES, sessionApi,
  type InterventionLevel, type NotificationChannel,
} from './session-api'
import '../onboarding/onboarding.css'
import './session-setup.css'

// 시간 선택지는 Notion 계약(30분·1시간·2시간·직접)을 따른다. MVP에 종료 없는 세션은 없다.
const DURATIONS = [{ key: '30', label: '30분' }, { key: '60', label: '1시간' }, { key: '120', label: '2시간' }, { key: 'custom', label: '직접' }] as const
export const LEVELS: { key: InterventionLevel; label: string }[] = [{ key: 'GENTLE', label: '약하게' }, { key: 'NORMAL', label: '보통' }, { key: 'ACTIVE', label: '적극적으로' }]
export const CHANNELS: { key: NotificationChannel; label: string }[] = [{ key: 'MOLE_UI', label: '화면 위 포키' }, { key: 'MACOS_NOTIFICATION', label: 'macOS 알림' }, { key: 'SOUND', label: '소리' }]
// 미리보기 기준은 개입 정책 v1(화면 분석 5.2)과 같다.
export const PREVIEW: Record<InterventionLevel, { say: string; poki: string; alt: string; meta: [string, string, string] }> = {
  GENTLE: { say: '목표에서 조금 멀어진 것 같아요.', poki: '/design/poki-floating-master-v3.png', alt: '포키', meta: ['다른 화면이 ', '3분 넘게 이어지거나, 10분 안에 1분 넘게 3번', ' 이어지면 조용히 알려요'] },
  NORMAL: { say: '지금 화면이 목표와 달라 보여요.\n다시 돌아가 볼까요?', poki: '/design/poki-floating-curious-v8.png', alt: '궁금해하는 포키', meta: ['다른 화면이 ', '1분 넘게 이어지거나, 10분 안에 30초 넘게 2번', ' 이어지면 알려요'] },
  ACTIVE: { say: '어? 또 딴 데 가 있네!\n다시 목표로 가요!', poki: '/design/poki-floating-angry-v2.png', alt: '눈을 부릅뜬 포키', meta: ['다른 화면이 ', '30초 넘게 이어지거나, 10분 안에 20초 넘게 2번', ' 이어지면 바로 알려요'] },
}
const DEFAULT_CHANNELS: NotificationChannel[] = ['MOLE_UI', 'MACOS_NOTIFICATION']
const timeFormat = new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' })

type Feedback = { title: string; body: string; info?: boolean } | null

function startError(code: string): Feedback {
  switch (code) {
    case 'SESSION_START_REQUIREMENT_MISSING': return { title: '시작에 필요한 준비가 빠졌어요.', body: '화면 기록·알림 권한과 Chrome 확장 연결을 확인한 뒤 다시 눌러 주세요.' }
    case 'SESSION_ACTIVE_EXISTS': return { title: '진행 중인 집중이 있어요.', body: '진행 중인 집중을 끝낸 뒤 새로 시작할 수 있어요.' }
    case 'LOCAL_NOTIFICATION_NOT_CONFIRMED': return { title: '알림이 보이는지 확인이 필요해요.', body: '시험 알림을 보내 볼게요. 알림이 보이면 ‘보였어요’를 눌러 주세요.' }
    case 'LOCAL_NETWORK_UNAVAILABLE': return { title: '인터넷 연결을 확인해 주세요.', body: '연결된 뒤 다시 누르면 같은 요청으로 이어서 시작해요.' }
    default: return { title: '집중을 시작하지 못했어요.', body: '잠시 후 다시 눌러 주세요.' }
  }
}

function readGoal(): { goalId: string; goalText: string } | null {
  try {
    const saved: unknown = JSON.parse(sessionStorage.getItem(CONFIRMED_GOAL_KEY) ?? 'null')
    if (typeof saved === 'object' && saved !== null && typeof (saved as { goalId?: unknown }).goalId === 'string' && typeof (saved as { goalText?: unknown }).goalText === 'string') {
      return saved as { goalId: string; goalText: string }
    }
  } catch { /* 저장소를 못 읽으면 목표가 없는 것으로 본다. */ }
  return null
}

/** D09 세션 설정 (FocusOn-design/session-setup.html). 기본값은 서버 집중 설정, 시작은 POST /sessions. */
export function SessionSetup() {
  const router = useRouter()
  const [goal, setGoal] = useState<{ goalId: string; goalText: string } | null | undefined>(undefined)
  const [duration, setDuration] = useState<string>('30')
  const [custom, setCustom] = useState('40')
  const [level, setLevel] = useState<InterventionLevel>('NORMAL')
  const [channels, setChannels] = useState<NotificationChannel[]>(DEFAULT_CHANNELS)
  const [camera, setCamera] = useState<boolean | null>(null)
  const [channelError, setChannelError] = useState(false)
  const [feedback, setFeedback] = useState<Feedback>(null)
  const [busy, setBusy] = useState(false)
  const [started, setStarted] = useState(false)
  // 알림 확인: 서버는 알림을 검증하지 않아 Desktop이 시험 알림 + 사용자 확인으로 막는다(결정 022).
  const [notifyCheck, setNotifyCheck] = useState<'ask' | 'sent' | 'denied' | null>(null)
  const [now, setNow] = useState<Date | null>(null)
  // [주의] 같은 선택으로 다시 누르면 같은 Idempotency-Key를 보내 서버가 세션을 두 번 만들지 않게 한다.
  const attempt = useRef<{ fingerprint: string; key: string } | null>(null)
  const customRef = useRef<HTMLInputElement>(null)
  // [주의] 기본값 응답이 늦게 와도 사용자가 이미 바꾼 항목은 덮어쓰지 않는다.
  const touched = useRef({ level: false, channels: false })

  useEffect(() => {
    const unsubscribe = window.focusOn?.auth.onSessionChanged((snapshot) => { if (snapshot.status === 'SIGNED_OUT') router.replace('/') })
    return () => unsubscribe?.()
  }, [router])

  useEffect(() => {
    queueMicrotask(() => { setGoal(readGoal()); setNow(new Date()) })
    const timer = setInterval(() => setNow(new Date()), 30_000)
    void sessionApi.getFocusSettings().then((result) => {
      if (!result.success) return
      if (!touched.current.level) setLevel(result.data.interventionLevel)
      if (!touched.current.channels) setChannels(result.data.notificationChannels)
      setCamera(result.data.cameraAnalysisEnabled)
    })
    return () => clearInterval(timer)
  }, [])

  const minutes = duration === 'custom' ? Number(custom) : Number(duration)
  const validMinutes = isValidMinutes(minutes)
  const preview = PREVIEW[level]

  const moveSegment = <T extends string>(event: React.KeyboardEvent, keys: readonly T[], current: T, choose: (key: T) => void) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    event.preventDefault()
    const index = keys.indexOf(current)
    const next = keys[(index + (event.key === 'ArrowRight' ? 1 : keys.length - 1)) % keys.length]
    choose(next)
    const buttons = (event.currentTarget as HTMLElement).querySelectorAll('button')
    buttons[keys.indexOf(next)]?.focus()
  }

  const chooseDuration = (key: string) => {
    setDuration(key)
    setFeedback(null)
    if (key === 'custom') queueMicrotask(() => customRef.current?.focus())
  }
  const step = (delta: number) => setCustom(String(Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, (Number(custom) || 0) + delta))))
  const toggleChannel = (key: NotificationChannel) => {
    touched.current.channels = true
    const next = channels.includes(key) ? channels.filter((item) => item !== key) : [...channels, key]
    setChannels(next)
    setChannelError(next.length === 0)
    setFeedback(null)
  }

  const start = async () => {
    if (busy || started || !goal) return
    if (!validMinutes) { customRef.current?.focus(); return }
    if (channels.length === 0) { setChannelError(true); return }
    const payload = {
      goalId: goal.goalId,
      goalText: goal.goalText,
      targetDurationMs: minutes * 60_000,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      sessionOptions: { interventionLevel: level, notificationChannels: CHANNELS.map(({ key }) => key).filter((key) => channels.includes(key)), cameraAnalysisEnabled: camera === true },
    }
    const fingerprint = JSON.stringify(payload)
    if (attempt.current?.fingerprint !== fingerprint) attempt.current = { fingerprint, key: crypto.randomUUID() }
    setBusy(true)
    setFeedback(null)
    const result = await sessionApi.start({ ...payload, idempotencyKey: attempt.current.key })
    setBusy(false)
    if (!result.success) {
      setNotifyCheck(result.error.code === 'LOCAL_NOTIFICATION_NOT_CONFIRMED' ? 'ask' : null)
      setFeedback(startError(result.error.code))
      return
    }
    attempt.current = null
    // 시작한 세션은 Main 타이머(SessionRuntime)가 이어 센다.
    setStarted(true)
    router.push('/session/')
  }

  const sendTest = async () => {
    const sent = await sessionApi.sendTestNotification()
    if (!sent.success || !sent.data.shown) { setFeedback({ title: '알림을 보내지 못했어요.', body: '시스템 설정 › 알림에서 FocusOn을 허용한 뒤 다시 보내 주세요.' }); setNotifyCheck('denied'); return }
    setNotifyCheck('sent')
    setFeedback({ title: '방금 보낸 알림이 보였나요?', body: '화면 오른쪽 위에 FocusOn 알림이 떴는지 확인해 주세요.' })
  }
  const answerTest = async (seen: boolean) => {
    const saved = await sessionApi.confirmNotification(seen)
    if (!saved.success) { setFeedback({ title: '알림 확인을 저장하지 못했어요.', body: '다시 눌러 주세요.' }); return }
    if (seen) { setNotifyCheck(null); setFeedback({ title: '알림을 확인했어요.', body: '‘집중 시작하기’를 다시 눌러 주세요.', info: true }); return }
    setNotifyCheck('denied')
    setFeedback({ title: '알림이 꺼져 있는 것 같아요.', body: '시스템 설정 › 알림에서 FocusOn을 허용한 뒤 ‘다시 보내기’를 눌러 주세요.' })
    void sessionApi.openNotificationSettings()
  }

  return <main className="app-window onboarding-window setup-window">
    <header className="titlebar"><div className="window-controls" aria-hidden="true"><i /><i /><i /></div></header>
    <div className="layout">
      <section className="sheet" aria-labelledby="setup-title"><div className="sheet-inner">
        <Link className="back-link" href="/"><span aria-hidden="true">←</span>홈으로</Link>
        <h1 id="setup-title">집중을 준비할게요</h1>
        <div className={`goal-line${goal === null ? ' is-missing' : ''}`}>
          <p>{goal === undefined ? '목표를 불러오고 있어요' : goal ? goal.goalText : '먼저 목표를 정해 주세요.'}</p>
          <Link href="/goal/">{goal ? '수정' : '목표 정하기'}</Link>
        </div>

        <div className="group" role="radiogroup" aria-labelledby="duration-title">
          <div className="group-head"><h2 id="duration-title">집중 시간</h2></div>
          <div className="segmented" onKeyDown={(event) => moveSegment(event, DURATIONS.map(({ key }) => key), duration as typeof DURATIONS[number]['key'], chooseDuration)}>
            {DURATIONS.map(({ key, label }) => <button type="button" role="radio" aria-checked={duration === key} tabIndex={duration === key ? 0 : -1} key={key} onClick={() => chooseDuration(key)}>{label}</button>)}
          </div>
          <div className="duration-row">
            <div className="duration-readout" aria-live="polite">
              <strong>{validMinutes ? formatMinutes(minutes) : '—'}</strong>
              {validMinutes && now && <span>{timeFormat.format(new Date(now.getTime() + minutes * 60_000))}까지</span>}
            </div>
            {duration === 'custom' && <div className="custom-row">
              <button className="stepper" type="button" onClick={() => step(-5)} aria-label="5분 줄이기">−</button>
              <input className={`minutes-input${validMinutes ? '' : ' is-error'}`} ref={customRef} type="number" inputMode="numeric" min={MIN_MINUTES} max={MAX_MINUTES} step={5}
                value={custom} onChange={(event) => { setCustom(event.target.value); setFeedback(null) }} aria-label="직접 입력한 집중 시간(분)" aria-describedby="custom-hint" aria-invalid={!validMinutes} />
              <button className="stepper" type="button" onClick={() => step(5)} aria-label="5분 늘리기">+</button>
              <span className={`custom-hint${validMinutes ? '' : ' is-error'}`} id="custom-hint">{validMinutes ? '5~300분' : '5~300분(5시간) 사이로 입력해 주세요'}</span>
            </div>}
          </div>
        </div>

        <div className="group" role="radiogroup" aria-labelledby="intensity-title">
          <div className="group-head"><h2 id="intensity-title">포키가 알려주는 정도</h2></div>
          <div className="segmented" onKeyDown={(event) => moveSegment(event, LEVELS.map(({ key }) => key), level, (key) => { touched.current.level = true; setLevel(key); setFeedback(null) })}>
            {LEVELS.map(({ key, label }) => <button type="button" role="radio" aria-checked={level === key} tabIndex={level === key ? 0 : -1} key={key} onClick={() => { touched.current.level = true; setLevel(key); setFeedback(null) }}>{label}</button>)}
          </div>
        </div>

        <div className="group">
          <div className="group-head"><h2 id="notify-title">알림 방식</h2></div>
          <div className="checks" role="group" aria-labelledby="notify-title">
            {CHANNELS.map(({ key, label }) => <button className="check-chip" type="button" role="checkbox" aria-checked={channels.includes(key)} key={key} onClick={() => toggleChannel(key)}>{label}</button>)}
          </div>
          {channelError && <p className="group-error" role="alert"><span className="icon-error" aria-hidden="true">!</span>알림 방식을 하나 이상 골라 주세요.</p>}
        </div>

        <div className="start-row">
          {feedback && <div className={`start-feedback${feedback.info ? ' is-info' : ''}`} role={feedback.info ? 'status' : 'alert'}>
            {!feedback.info && <span className="icon-error" aria-hidden="true">!</span>}
            <div>
              <strong>{feedback.title}</strong><span>{feedback.body}</span>
              {notifyCheck && <span className="notify-actions">
                {notifyCheck === 'sent'
                  ? <><button className="btn btn-ghost" type="button" onClick={() => { void answerTest(false) }}>안 보였어요</button><button className="btn btn-primary" type="button" onClick={() => { void answerTest(true) }}>보였어요</button></>
                  : <button className="btn btn-ghost" type="button" onClick={() => { void sendTest() }}>{notifyCheck === 'denied' ? '다시 보내기' : '시험 알림 보내기'}</button>}
              </span>}
            </div>
          </div>}
          <p>카메라 <strong>{camera === null ? '확인하지 못했어요' : camera ? '사용 중' : '사용 안 함'}</strong></p>
          <button className="btn btn-primary" type="button" onClick={() => { void start() }} disabled={busy || started || !goal}>
            {busy ? '시작하는 중…' : started ? '시작했어요' : <>집중 시작하기 <span aria-hidden="true">→</span></>}
          </button>
        </div>
      </div></section>

      <aside className="preview" aria-label="이탈 알림 미리보기">
        <span className="preview-label">이렇게 알려드려요</span>
        <p className="bubble" key={level} aria-live="polite">{preview.say}</p>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <div className="poki-stage"><img className="poki" key={preview.poki} src={preview.poki} alt={preview.alt} /></div>
        {/* [디자인] 기준이 길어 넘쳐 떨어지지 않게, 기준 뒤에서 일부러 줄을 바꾼다. */}
        <p className="preview-meta">{preview.meta[0]}<strong>{preview.meta[1]}</strong><br />{preview.meta[2].trim()}</p>
      </aside>
    </div>
  </main>
}
