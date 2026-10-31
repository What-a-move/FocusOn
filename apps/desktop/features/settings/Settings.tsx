'use client'

import { useCallback, useEffect, useRef, useState, useSyncExternalStore, type CSSProperties, type KeyboardEvent } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { CHANNELS, LEVELS, PREVIEW } from '../session/SessionSetup'
import { sessionApi, type NotificationChannel } from '../session/session-api'
import { onboardingApi } from '../onboarding/onboarding-api'
import { LoadError, SettingsShell, ToastView, failText, useToast } from './SettingsShell'
import { settingsApi, type FocusSettings, type ThemeSource } from './settings-api'

// 시안의 계정·기기 연결 탭은 다음 Issue에서 만든다(settings-PLAN.md).
const TABS = [
  { id: 'focus', label: '집중', poki: 'poki-floating-master-v3.png' },
  { id: 'analysis', label: '분석·권한', poki: 'poki-floating-curious-v8.png' },
  { id: 'general', label: '화면', poki: 'poki-floating-sleepy-v7.png' },
] as const
type TabId = (typeof TABS)[number]['id']
const THEMES: { value: ThemeSource; label: string }[] = [{ value: 'light', label: '라이트' }, { value: 'dark', label: '다크' }, { value: 'system', label: 'macOS 설정 따르기' }]

// 고른 탭은 주소 #focus·#analysis·#general로 남긴다. 정적 화면을 만들 때는 주소가 없어 첫 탭이다.
const subscribeHash = (callback: () => void) => { addEventListener('hashchange', callback); return () => removeEventListener('hashchange', callback) }
const readTab = (): TabId => TABS.find((tab) => `#${tab.id}` === location.hash)?.id ?? 'focus'
function openTab(id: TabId) {
  history.replaceState(null, '', `#${id}`)
  dispatchEvent(new HashChangeEvent('hashchange'))
}

type Load<T> = { status: 'loading' } | { status: 'error'; code: string } | { status: 'ready'; data: T }

export function Settings() {
  const tab = useSyncExternalStore(subscribeHash, readTab, () => 'focus' as const)
  const [toast, showToast, hideToast] = useToast()
  const tabRefs = useRef<Partial<Record<TabId, HTMLButtonElement | null>>>({})
  const inkRef = useRef<HTMLSpanElement>(null)
  const current = TABS.find((item) => item.id === tab) ?? TABS[0]

  // 갈색 알약이 고른 탭으로 미끄러져 간다. 크기만 재서 바로 옮기므로 상태로 두지 않는다.
  useEffect(() => {
    const move = () => {
      const button = tabRefs.current[tab]
      if (!button || !inkRef.current) return
      inkRef.current.style.width = `${button.offsetWidth}px`
      inkRef.current.style.transform = `translateX(${button.offsetLeft}px)`
    }
    move()
    addEventListener('resize', move)
    return () => removeEventListener('resize', move)
  }, [tab])

  function onTabKey(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const step = ({ ArrowRight: 1, ArrowLeft: -1 } as Record<string, number>)[event.key]
    if (!step) return
    const next = TABS[(index + step + TABS.length) % TABS.length]
    openTab(next.id)
    tabRefs.current[next.id]?.focus()
  }

  return <SettingsShell>
    <div className="fo-settings-page">
      <div className="fo-settings-head">
        <div>
          <h1>설정</h1>
          <div className="fo-settings-tabs" role="tablist" aria-label="설정 묶음">
            {TABS.map((item, index) => <button
              type="button" role="tab" key={item.id} id={`tab-${item.id}`} aria-controls={item.id}
              aria-selected={item.id === tab} tabIndex={item.id === tab ? 0 : -1}
              ref={(element) => { tabRefs.current[item.id] = element }}
              onClick={() => openTab(item.id)} onKeyDown={(event) => onTabKey(event, index)}
            >{item.label}</button>)}
            <span className="fo-tab-ink" ref={inkRef} aria-hidden="true" />
          </div>
        </div>
        {/* 탭마다 포키 모습이 바뀐다. key로 다시 그려 등장 모션을 한 번 더 보여 준다. */}
        <Image className="fo-settings-poki" key={current.poki} src={`/design/${current.poki}`} alt="" width={240} height={240} priority />
      </div>
      <section className="fo-settings-panel" id={tab} role="tabpanel" aria-labelledby={`tab-${tab}`} key={tab}>
        {tab === 'focus' && <FocusPanel showToast={showToast} />}
        {tab === 'analysis' && <AnalysisPanel showToast={showToast} />}
        {tab === 'general' && <GeneralPanel showToast={showToast} />}
      </section>
    </div>
    <ToastView toast={toast} onHide={hideToast} />
  </SettingsShell>
}

type ShowToast = (toast: { text: string }) => void

/** 집중 기본값 (focus-settings). 바꿀 때마다 전체 객체를 저장하고, 실패하면 이전 값으로 되돌린다. */
function FocusPanel({ showToast }: { showToast: ShowToast }) {
  const [view, setView] = useState<Load<FocusSettings>>({ status: 'loading' })
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setView({ status: 'loading' })
    const result = await sessionApi.getFocusSettings()
    setView(result.success ? { status: 'ready', data: result.data } : { status: 'error', code: result.error.code })
  }, [])
  useEffect(() => { queueMicrotask(() => { void load() }) }, [load])

  if (view.status === 'loading') return <p className="fo-settings-status" role="status">집중 설정을 불러오고 있어요.</p>
  // [주의] 읽지 못했을 때 기본값을 보여 주거나 저장하지 않는다. 서버 값을 덮어쓸 수 있다.
  if (view.status === 'error') return <LoadError code={view.code} what="집중 설정을" onRetry={() => { void load() }} />

  const settings = view.data
  async function save(next: FocusSettings) {
    setView({ status: 'ready', data: next })
    setSaving(true)
    const result = await settingsApi.saveFocusSettings(next)
    setSaving(false)
    if (result.success) {
      setView({ status: 'ready', data: result.data })
      showToast({ text: '저장했어요' })
    } else {
      setView({ status: 'ready', data: settings })
      showToast({ text: failText(result.error.code) })
    }
  }

  function toggleChannel(key: NotificationChannel) {
    const on = settings.notificationChannels.includes(key)
    if (on && settings.notificationChannels.length === 1) return showToast({ text: '알림 방식은 하나 이상 필요해요' })
    const next = CHANNELS.map((item) => item.key).filter((item) => (item === key ? !on : settings.notificationChannels.includes(item)))
    void save({ ...settings, notificationChannels: next })
  }

  return <>
    <div className="fo-field" style={{ '--n': 0 } as CSSProperties}>
      <strong className="fo-field-title" id="level-title">포키가 알려주는 정도</strong>
      <small className="fo-field-desc">{PREVIEW[settings.interventionLevel].meta.join('')}</small>
      <div className="fo-segmented" role="radiogroup" aria-labelledby="level-title">
        {LEVELS.map(({ key, label }) => <button type="button" role="radio" key={key} aria-checked={settings.interventionLevel === key} disabled={saving}
          onClick={() => { if (settings.interventionLevel !== key) void save({ ...settings, interventionLevel: key }) }}>{label}</button>)}
      </div>
    </div>
    <div className="fo-field" style={{ '--n': 1 } as CSSProperties}>
      <strong className="fo-field-title" id="channel-title">알림 방식</strong>
      <div className="fo-chips" role="group" aria-labelledby="channel-title">
        {CHANNELS.map(({ key, label }) => <button className="fo-chip" type="button" role="checkbox" key={key} disabled={saving}
          aria-checked={settings.notificationChannels.includes(key)} onClick={() => toggleChannel(key)}>{label}</button>)}
      </div>
    </div>
    <div className="fo-row" style={{ '--n': 2 } as CSSProperties}>
      <div className="fo-row-copy"><strong id="break-title">휴식 제안</strong><small>자주 흐트러지면 쉬어 갈지 물어봐요</small></div>
      <button className="fo-switch" type="button" role="switch" aria-labelledby="break-title" aria-checked={settings.breakSuggestionEnabled} disabled={saving}
        onClick={() => { void save({ ...settings, breakSuggestionEnabled: !settings.breakSuggestionEnabled }) }} />
    </div>
    <p className="fo-foot-note" style={{ '--n': 3 } as CSSProperties}>바꾼 설정은 다음 집중부터 적용돼요.</p>
  </>
}

const CHECK = <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3.5 8.5l3 3 6-7" /></svg>
const OFF = <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true"><path d="M8 4v5M8 12h.01" /></svg>

/**
 * 필수 권한 상태 (D22). Desktop이 직접 확인할 수 있는 화면 기록·알림만 보여 준다.
 * 알림은 macOS 허용 상태를 읽을 수 없어 시험 알림 + 사용자 확인 기록을 쓴다(결정 018).
 */
function AnalysisPanel({ showToast }: { showToast: ShowToast }) {
  const [screen, setScreen] = useState<boolean | null>(null)
  const [screenStep, setScreenStep] = useState<'idle' | 'opened' | 'stuck'>('idle')
  const [notification, setNotification] = useState<boolean | null>(null)
  const [notificationSent, setNotificationSent] = useState(false)

  const check = useCallback(async () => {
    const [screenResult, notificationResult] = await Promise.all([onboardingApi.checkScreenRecording(), sessionApi.getNotificationStatus()])
    setScreen(screenResult.success ? screenResult.data.granted : null)
    setNotification(notificationResult.success ? notificationResult.data.confirmed : null)
    return screenResult.success && screenResult.data.granted
  }, [])
  // 시스템 설정에서 돌아오면 다시 확인한다.
  useEffect(() => {
    queueMicrotask(() => { void check() })
    const onFocus = () => { void check() }
    addEventListener('focus', onFocus)
    return () => removeEventListener('focus', onFocus)
  }, [check])

  async function screenAction() {
    if (screenStep === 'idle') {
      const result = await onboardingApi.openScreenRecordingSettings()
      if (!result.success) return showToast({ text: '시스템 설정을 열지 못했어요. 직접 열어 주세요.' })
      setScreenStep('opened')
      return
    }
    const granted = await check()
    setScreenStep(granted ? 'idle' : 'stuck')
    if (granted) showToast({ text: '화면 기록 권한을 확인했어요' })
  }

  async function notificationAction() {
    if (!notificationSent) {
      const result = await sessionApi.sendTestNotification()
      if (!result.success || !result.data.shown) return showToast({ text: '시험 알림을 보내지 못했어요. 시스템 설정에서 알림을 켜 주세요.' })
      setNotificationSent(true)
      return
    }
    const result = await sessionApi.confirmNotification(true)
    if (!result.success) return showToast({ text: failText(result.error.code) })
    setNotification(result.data.confirmed)
    setNotificationSent(false)
    if (result.data.confirmed) showToast({ text: '알림을 확인했어요' })
  }

  const off = [screen, notification].filter((value) => value === false).length
  const unknown = screen === null || notification === null
  return <>
    <div style={{ '--n': 0 } as CSSProperties}>
      <div className="fo-perm-head">
        <strong>필수 권한</strong>
        {!unknown && <span className={`fo-badge ${off ? 'bad' : 'ok'}`}>{off ? `${off}개 꺼져 있음` : '모두 허용됨'}</span>}
      </div>
      {off > 0 && <div className="fo-notice is-info fo-perm-alert" role="status"><strong>필수 권한을 켜야 집중을 시작할 수 있어요.</strong><span>아래에서 꺼진 권한의 버튼을 눌러 주세요.</span></div>}
      <div className="fo-perm-list">
        <PermissionRow name="화면 기록" desc="필요할 때만 화면 글자 읽기" granted={screen}
          error={screenStep === 'stuck' ? '아직 꺼져 있어요. 켰다면 앱을 다시 열어 주세요.' : null}
          action={screen === false ? { label: screenStep === 'idle' ? '시스템 설정 열기' : '다시 확인', run: screenAction } : null} />
        <PermissionRow name="알림" desc="흐트러졌을 때 알려 주기" granted={notification}
          error={notificationSent ? '알림이 보였다면 ‘보였어요’를 눌러 주세요.' : null}
          action={notification === false ? { label: notificationSent ? '보였어요' : '시험 알림 보내기', run: notificationAction } : null} />
      </div>
    </div>
    <div className="fo-row" style={{ '--n': 1 } as CSSProperties}>
      <div className="fo-row-copy"><strong>분석 제외 목록</strong><small>여기 있는 사이트·앱은 분석하지 않아요</small></div>
      <Link className="fo-small-btn" href="/settings/exclusions/">관리</Link>
    </div>
    <p className="fo-foot-note" style={{ '--n': 2 } as CSSProperties}>화면 원문·캡처·카메라 영상은 저장하지 않아요. 비밀번호·결제·메시지 화면은 분석하지 않아요.</p>
  </>
}

function PermissionRow({ name, desc, granted, error, action }: {
  name: string; desc: string; granted: boolean | null; error: string | null; action: { label: string; run: () => Promise<void> } | null
}) {
  const [busy, setBusy] = useState(false)
  const state = granted === null ? '확인하지 못했어요' : granted ? desc : '꺼져 있어요'
  return <div className={`fo-perm${granted === false ? ' is-off' : ''}`}>
    <span className="fo-perm-icon">{granted === false ? OFF : CHECK}</span>
    <div aria-label={`${name}, ${granted === null ? '확인하지 못함' : granted ? '허용됨' : '꺼져 있음'}`}>
      <b>{name}</b>
      <small className={error ? 'fo-note-line' : undefined}>{error ?? state}</small>
    </div>
    {action ? <button className="fo-small-btn" type="button" disabled={busy} onClick={async () => { setBusy(true); await action.run(); setBusy(false) }}>{action.label}</button> : <span />}
  </div>
}

/** 화면 모드 (D20). Main이 nativeTheme에 적용하고 이 기기에 저장한다. 언어는 한국어만 있다. */
function GeneralPanel({ showToast }: { showToast: ShowToast }) {
  const [theme, setTheme] = useState<ThemeSource | null>(null)
  useEffect(() => {
    void settingsApi.getTheme().then((result) => { if (result.success) setTheme(result.data.theme) })
  }, [])

  async function choose(value: ThemeSource) {
    if (value === theme) return
    const result = await settingsApi.setTheme(value)
    if (!result.success) return showToast({ text: failText(result.error.code) })
    setTheme(result.data.theme)
    showToast({ text: '저장했어요' })
  }

  const shot = (mode: 'light' | 'dark') => <span className={`fo-shot-half ${mode}`}><i className="side" /><i className="bar1" /><i className="bar2" /><i className="btn-mini" /></span>
  return <div className="fo-field" style={{ '--n': 0 } as CSSProperties}>
    <strong className="fo-field-title" id="theme-title">화면 모드</strong>
    <div className="fo-theme-tiles" role="radiogroup" aria-labelledby="theme-title">
      {THEMES.map(({ value, label }) => <button className="fo-theme-tile" type="button" role="radio" key={value} aria-checked={theme === value} onClick={() => { void choose(value) }}>
        <span className={`fo-shot ${value}`}>{value === 'system' ? <>{shot('light')}{shot('dark')}</> : shot(value)}</span>
        {label}
      </button>)}
    </div>
  </div>
}
