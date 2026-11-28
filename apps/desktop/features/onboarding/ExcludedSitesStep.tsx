'use client'

import { useEffect, useState, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import { OnboardingShell } from './OnboardingShell'
import { ExcludedAppsField } from './ExcludedAppsField'
import { analysisApi, describeError, onboardingApi, type ExcludedApp, type Notice } from '../../shared/api/onboarding-api'
import { sessionApi } from '../../shared/api/session-api'
import { coreFocusSettings, MESSENGER_LABELS, settingsApi } from '../../shared/api/settings-api'
import type { LearningMessenger } from '../../electron/shared/session-contract.cjs'
import { DOMAIN, MAX_SITES, normalizeDomain } from '../../shared/lib/domain'

const defaultHint = { kind: '' as '' | 'error' | 'success', text: '' }

export function ExcludedSitesStep() {
  const router = useRouter()
  const [input, setInput] = useState('')
  const [sites, setSites] = useState<string[]>([])
  const [hint, setHint] = useState(defaultHint)
  const [feedback, setFeedback] = useState<Notice | null>(null)
  const [saving, setSaving] = useState(false)
  const [apps, setApps] = useState<ExcludedApp[]>([])
  const [appsLoaded, setAppsLoaded] = useState(false)
  const [savedMessengers, setSavedMessengers] = useState<LearningMessenger[] | null>(null)
  const [messengers, setMessengers] = useState<LearningMessenger[]>([])
  const [messengerLoadFailed, setMessengerLoadFailed] = useState(false)

  async function loadMessengers() {
    setMessengerLoadFailed(false)
    const result = await sessionApi.getFocusSettings()
    if (!result.success) { setMessengerLoadFailed(true); return }
    const selected = result.data.learningMessengers ?? []
    setSavedMessengers(selected)
    setMessengers(selected)
  }

  // 이전에 이 기기에서 고른 제외 앱을 보여 준다. 없거나 못 읽으면 빈 목록이다.
  useEffect(() => {
    queueMicrotask(() => { void loadMessengers() })
    void analysisApi.getExcludedApps().then((result) => {
      if (result.success) setApps(result.data)
      setAppsLoaded(true)
    })
  }, [])

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const raw = input.trim().toLowerCase()
    const value = normalizeDomain(raw)
    if (!raw) return setHint({ kind: 'error', text: '추가할 사이트를 입력해 주세요.' })
    if (!DOMAIN.test(value)) return setHint({ kind: 'error', text: '도메인 형식을 확인해 주세요. 예: youtube.com' })
    if (sites.includes(value)) return setHint({ kind: 'error', text: `이미 추가한 사이트예요 · ${value}` })
    if (sites.length >= MAX_SITES) return setHint({ kind: 'error', text: `제외 사이트는 ${MAX_SITES}개까지 추가할 수 있어요.` })
    setSites([...sites, value])
    setInput('')
    setFeedback(null)
    setHint({ kind: 'success', text: value !== raw ? `주소에서 도메인만 남겨 추가했어요 · ${value}` : `추가했어요 · ${value}` })
  }

  // [주의] 저장하거나 '건너뛰기'를 눌렀을 때만 단계 완료로 기록한다. 방문만으로는 완료가 아니다.
  async function finish() {
    // [주의] 저장된 제외 앱을 읽기 전에 저장하면 빈 목록으로 덮어쓴다.
    if (!appsLoaded || saving) return
    setSaving(true)
    setFeedback(null)
    try {
      const changed = savedMessengers && (savedMessengers.length !== messengers.length || messengers.some((item) => !savedMessengers.includes(item)))
      if (changed) {
        const current = await sessionApi.getFocusSettings()
        if (!current.success) {
          setFeedback(describeError(current.error, '학습용 메신저를 저장하지 못했어요.'))
          return
        }
        const saved = await settingsApi.saveFocusSettings({ ...coreFocusSettings(current.data), learningMessengers: messengers })
        if (!saved.success) {
          setFeedback(describeError(saved.error, '학습용 메신저를 저장하지 못했어요.'))
          return
        }
        setSavedMessengers(saved.data.learningMessengers ?? [])
      }
      // 제외 앱은 이 기기에만 저장한다. 마지막 서버 호출에서 제외 사이트 단계의 완료를 기록한다.
      const savedApps = await analysisApi.saveExcludedApps(apps)
      if (!savedApps.success) {
        setFeedback(describeError(savedApps.error, '제외 앱을 저장하지 못했어요.'))
        return
      }
      const result = await onboardingApi.saveExcludedSites(sites)
      if (!result.success) {
        setFeedback(describeError(result.error, '제외 사이트를 저장하지 못했어요.'))
        return
      }
      router.push('/onboarding/done/')
    } finally {
      setSaving(false)
    }
  }

  return (
    <OnboardingShell
      currentStep={3}
      prompt="빼 둘 사이트랑 공부 메신저를 골라 줘"
      poki="/design/poki-floating-excluded-v2.png"
      backHref="/onboarding/permissions/"
      feedback={feedback}
      next={{ label: sites.length || apps.length || messengers.length ? '다음' : '건너뛰기', blocked: false, busy: saving || !appsLoaded, onClick: () => { void finish() } }}
    >
      <label className="field-label" htmlFor="site-input">분석 제외 사이트 <span className="tag">선택</span></label>
      <form className="name-row" onSubmit={add} noValidate>
        <div className="input-wrap">
          <input
            className={`text-input no-counter${hint.kind === 'error' ? ' is-error' : ''}`}
            id="site-input"
            type="text"
            autoComplete="off"
            placeholder="예: youtube.com"
            value={input}
            aria-describedby="site-hint"
            onChange={(event) => { setInput(event.target.value); setHint(defaultHint) }}
          />
        </div>
        <button className="btn btn-ghost check-button" type="submit">추가</button>
      </form>
      <p className={`hint${hint.kind ? ` is-${hint.kind}` : ''}`} id="site-hint" aria-live="polite">{hint.text}</p>
      <ul className="site-list" aria-label="제외한 사이트">
        {sites.map((site) => (
          <li key={site}>
            {site}
            <button type="button" aria-label={`${site} 빼기`} onClick={() => { setSites(sites.filter((item) => item !== site)); setHint(defaultHint) }}>✕</button>
          </li>
        ))}
      </ul>
      <ExcludedAppsField apps={apps} onChange={(next) => { setApps(next); setFeedback(null) }} />
      <fieldset className="messenger-field" disabled={saving}>
        <legend className="field-label">학습에 쓰는 메신저 <span className="tag">선택</span></legend>
        {savedMessengers === null ? messengerLoadFailed
          ? <div className="messenger-load-failed"><p role="status">학습용 메신저를 불러오지 못했어요.</p>
            <button className="btn btn-ghost" type="button" onClick={() => { void loadMessengers() }}>다시 시도</button></div>
          : <p role="status">학습용 메신저를 불러오고 있어요.</p>
          : <div className="messenger-chips">
            {(Object.keys(MESSENGER_LABELS) as LearningMessenger[]).map((item) => <label key={item}>
              <input type="checkbox" checked={messengers.includes(item)} onChange={(event) => {
                const selected = event.target.checked
                setMessengers((current) => selected ? [...current, item] : current.filter((value) => value !== item))
                setFeedback(null)
              }} />
              {MESSENGER_LABELS[item]}
            </label>)}
          </div>}
      </fieldset>
    </OnboardingShell>
  )
}
