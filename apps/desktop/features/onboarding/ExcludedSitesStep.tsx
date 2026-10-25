'use client'

import { useEffect, useState, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import { OnboardingShell } from './OnboardingShell'
import { ExcludedAppsField } from './ExcludedAppsField'
import { analysisApi, describeError, onboardingApi, type ExcludedApp, type Notice } from './onboarding-api'

const MAX_SITES = 50
const defaultHint = { kind: '' as '' | 'error' | 'success', text: '도메인만 적어 주세요. 하위 도메인도 함께 제외돼요.' }
const DOMAIN = /^(?=.{4,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{0,61}[a-z0-9]?$/u

// [주의] 주소 전체가 아니라 정규화한 host만 남긴다 (API_CONTRACT 분석 제외 도메인).
export function normalizeDomain(raw: string): string {
  let value = raw.trim().toLowerCase()
  if (/[/:]/u.test(value)) {
    try { value = new URL(value.includes('://') ? value : `https://${value}`).hostname } catch { return '' }
  }
  return value.replace(/^www\./u, '').replace(/\.$/u, '')
}

export function ExcludedSitesStep() {
  const router = useRouter()
  const [input, setInput] = useState('')
  const [sites, setSites] = useState<string[]>([])
  const [hint, setHint] = useState(defaultHint)
  const [feedback, setFeedback] = useState<Notice | null>(null)
  const [saving, setSaving] = useState(false)
  const [apps, setApps] = useState<ExcludedApp[]>([])

  // 이전에 이 기기에서 고른 제외 앱을 보여 준다. 없거나 못 읽으면 빈 목록이다.
  useEffect(() => { void analysisApi.getExcludedApps().then((result) => { if (result.success) setApps(result.data) }) }, [])

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
    setSaving(true)
    // 제외 앱은 이 기기에만 저장한다. 서버에는 보내지 않는다(결정 024).
    const savedApps = await analysisApi.saveExcludedApps(apps)
    if (!savedApps.success) {
      setSaving(false)
      setFeedback({ title: '제외 앱을 저장하지 못했어요.', body: '잠시 후 다시 눌러 주세요.' })
      return
    }
    const result = await onboardingApi.saveExcludedSites(sites)
    setSaving(false)
    if (!result.success) {
      setFeedback(describeError(result.error))
      return
    }
    router.push('/onboarding/done/')
  }

  return (
    <OnboardingShell
      currentStep={3}
      prompt="분석하지 않을 사이트가 있나요?"
      poki="/design/poki-floating-excluded-v2.png"
      backHref="/onboarding/permissions/"
      feedback={feedback}
      next={{ label: sites.length || apps.length ? '다음' : '건너뛰기', blocked: false, busy: saving, onClick: () => { void finish() } }}
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
      <p className="exclude-note">여기 있는 사이트와 앱에서는 화면 분석만 멈추고, 집중 타이머는 계속돼요.<br />지금 건너뛰어도 설정에서 언제든 추가할 수 있어요.</p>
    </OnboardingShell>
  )
}
