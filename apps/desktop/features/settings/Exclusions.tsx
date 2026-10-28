'use client'

import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { DOMAIN, MAX_SITES, normalizeDomain } from '../onboarding/ExcludedSitesStep'
import { analysisApi, type ExcludedApp, type InstalledApp } from '../onboarding/onboarding-api'
import { SettingsShell, ToastView, failText, useToast, type Toast } from './SettingsShell'
import { settingsApi } from './settings-api'

const MAX_APPS = 50
const SUGGESTED = ['youtube.com', 'instagram.com', 'netflix.com', 'x.com']
const DEFAULT_HINT = { kind: '' as '' | 'error' | 'success', text: '하위 도메인까지 함께 제외돼요' }
type Kind = 'sites' | 'apps'
type Load<T> = { status: 'loading' } | { status: 'error'; code: string } | { status: 'ready'; data: T }
type Item = { id: string; label: string }

/**
 * 분석 제외 목록 (FocusOn-design/exclusions.html). 사이트는 서버 전체 목록(PUT 전체 교체),
 * 앱은 이 기기 목록(결정 024)에 바꿀 때마다 바로 저장한다. 실패하면 목록을 바꾸지 않는다.
 */
export function Exclusions() {
  const [kind, setKind] = useState<Kind>('sites')
  const [sites, setSites] = useState<Load<string[]>>({ status: 'loading' })
  const [apps, setApps] = useState<Load<ExcludedApp[]>>({ status: 'loading' })
  const [saving, setSaving] = useState(false)
  const [justAdded, setJustAdded] = useState('')
  const [toast, showToast, hideToast] = useToast()

  const loadSites = useCallback(async () => {
    setSites({ status: 'loading' })
    const result = await settingsApi.getExcludedDomains()
    setSites(result.success ? { status: 'ready', data: result.data.domains } : { status: 'error', code: result.error.code })
  }, [])
  const loadApps = useCallback(async () => {
    setApps({ status: 'loading' })
    const result = await analysisApi.getExcludedApps()
    setApps(result.success ? { status: 'ready', data: result.data } : { status: 'error', code: result.error.code })
  }, [])
  useEffect(() => { queueMicrotask(() => { void loadSites(); void loadApps() }) }, [loadSites, loadApps])

  /** 저장이 성공했을 때만 화면 목록을 바꾼다. 실패 문구를 돌려준다. */
  async function saveSites(next: string[]): Promise<string | null> {
    setSaving(true)
    const result = await settingsApi.saveExcludedDomains(next)
    setSaving(false)
    if (!result.success) return failText(result.error.code)
    setSites({ status: 'ready', data: result.data.domains })
    return null
  }
  async function saveApps(next: ExcludedApp[]): Promise<string | null> {
    setSaving(true)
    const result = await analysisApi.saveExcludedApps(next)
    setSaving(false)
    if (!result.success) return failText(result.error.code)
    setApps({ status: 'ready', data: result.data })
    return null
  }

  async function remove(target: Kind, id: string, label: string) {
    if (target === 'sites' && sites.status === 'ready') {
      const before = sites.data
      const error = await saveSites(before.filter((item) => item !== id))
      showToast(error ? { text: error } : { text: `제외를 풀었어요 · ${label}`, undo: () => { void saveSites(before).then((undoError) => { if (undoError) showToast({ text: undoError }) }) } })
    }
    if (target === 'apps' && apps.status === 'ready') {
      const before = apps.data
      const error = await saveApps(before.filter((item) => item.bundleId !== id))
      showToast(error ? { text: error } : { text: `제외를 풀었어요 · ${label}`, undo: () => { void saveApps(before).then((undoError) => { if (undoError) showToast({ text: undoError }) }) } })
    }
  }

  const count = (view: Load<unknown[]>) => (view.status === 'ready' ? view.data.length : '–')
  return <SettingsShell>
    <div className="fo-settings-page fo-exclusions">
      <Link className="fo-back-link" href="/settings/#analysis">← 설정</Link>
      <h1>분석 제외 목록</h1>
      <p className="fo-intro">여기 있는 곳은 분석하지 않고 알림도 보내지 않아요. 타이머는 그대로 흘러가요.</p>
      <UnderlineTabs kind={kind} onChange={setKind} counts={{ sites: count(sites), apps: count(apps) }} />
      {kind === 'sites'
        ? <section className="fo-ex-panel" id="sites-panel" role="tabpanel" aria-labelledby="tab-sites" key="sites">
          <SiteForm sites={sites.status === 'ready' ? sites.data : null} saving={saving}
            onAdd={async (domain) => { const error = await saveSites([domain, ...(sites.status === 'ready' ? sites.data : [])]); if (!error) setJustAdded(domain); return error }} />
          <ExclusionList view={sites} saving={saving} justAdded={justAdded} title="제외한 사이트" sub="하위 도메인 포함" empty="아직 제외한 사이트가 없어요"
            toItems={(data) => data.map((domain) => ({ id: domain, label: domain }))} onRetry={() => { void loadSites() }} onRemove={(item) => remove('sites', item.id, item.label)} />
        </section>
        : <section className="fo-ex-panel" id="apps-panel" role="tabpanel" aria-labelledby="tab-apps" key="apps">
          <AppPicker apps={apps.status === 'ready' ? apps.data : null} saving={saving} showToast={showToast}
            onAdd={async (app) => { if (apps.status !== 'ready') return; const error = await saveApps([app, ...apps.data]); if (error) showToast({ text: error }); else { setJustAdded(app.bundleId); showToast({ text: `제외했어요 · ${app.name}` }) } }} />
          <ExclusionList view={apps} saving={saving} justAdded={justAdded} title="제외한 앱" sub="macOS 앱" empty="아직 제외한 앱이 없어요"
            toItems={(data) => data.map((app) => ({ id: app.bundleId, label: app.name }))} onRetry={() => { void loadApps() }} onRemove={(item) => remove('apps', item.id, item.label)} />
        </section>}
    </div>
    <ToastView toast={toast} onHide={hideToast} />
  </SettingsShell>
}

function UnderlineTabs({ kind, onChange, counts }: { kind: Kind; onChange: (kind: Kind) => void; counts: Record<Kind, number | string> }) {
  const refs = useRef<Partial<Record<Kind, HTMLButtonElement | null>>>({})
  const inkRef = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    const move = () => {
      const button = refs.current[kind]
      if (!button || !inkRef.current) return
      inkRef.current.style.width = `${button.offsetWidth}px`
      inkRef.current.style.transform = `translateX(${button.offsetLeft}px)`
    }
    move()
    addEventListener('resize', move)
    return () => removeEventListener('resize', move)
  }, [kind, counts])
  const tabs: { id: Kind; label: string }[] = [{ id: 'sites', label: '사이트' }, { id: 'apps', label: '앱' }]
  return <div className="fo-ex-tabs" role="tablist" aria-label="제외 대상 종류">
    {tabs.map((tab, index) => <button type="button" role="tab" key={tab.id} id={`tab-${tab.id}`} aria-controls={`${tab.id}-panel`}
      aria-selected={kind === tab.id} tabIndex={kind === tab.id ? 0 : -1} ref={(element) => { refs.current[tab.id] = element }}
      onClick={() => onChange(tab.id)}
      onKeyDown={(event) => {
        if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return
        const next = tabs[(index + 1) % tabs.length]
        onChange(next.id)
        refs.current[next.id]?.focus()
      }}>{tab.label}<span className="fo-count">{counts[tab.id]}</span></button>)}
    <span className="fo-ex-ink" ref={inkRef} aria-hidden="true" />
  </div>
}

function SiteForm({ sites, saving, onAdd }: { sites: string[] | null; saving: boolean; onAdd: (domain: string) => Promise<string | null> }) {
  const [input, setInput] = useState('')
  const [hint, setHint] = useState(DEFAULT_HINT)

  async function add(raw: string) {
    if (!sites) return
    const typed = raw.trim().toLowerCase()
    const value = normalizeDomain(typed)
    if (!typed) return setHint({ kind: 'error', text: '추가할 사이트를 입력해 주세요.' })
    if (!DOMAIN.test(value)) return setHint({ kind: 'error', text: '도메인 형식을 확인해 주세요. 예: youtube.com' })
    if (sites.includes(value)) return setHint({ kind: 'error', text: `이미 제외된 사이트예요 · ${value}` })
    if (sites.length >= MAX_SITES) return setHint({ kind: 'error', text: `제외 사이트는 ${MAX_SITES}개까지 추가할 수 있어요.` })
    const error = await onAdd(value)
    if (error) return setHint({ kind: 'error', text: error })
    setInput('')
    setHint({ kind: 'success', text: value !== typed ? `주소에서 도메인만 남겼어요 · ${value}` : `추가했어요 · ${value}` })
  }

  const disabled = saving || !sites
  return <>
    <form className="fo-add-row" noValidate onSubmit={(event: FormEvent) => { event.preventDefault(); void add(input) }}>
      <div className="fo-input-wrap">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z" /></svg>
        <input className={`fo-text-input${hint.kind === 'error' ? ' is-error' : ''}`} type="text" inputMode="url" autoComplete="off" spellCheck={false}
          placeholder="youtube.com 또는 주소 붙여 넣기" aria-label="제외할 사이트" aria-describedby="domain-hint" value={input}
          onChange={(event) => { setInput(event.target.value); setHint(DEFAULT_HINT) }} />
      </div>
      <button className="fo-button fo-button-primary" type="submit" disabled={disabled}>{saving ? '저장 중…' : '추가'}</button>
    </form>
    <p className={`fo-hint${hint.kind ? ` is-${hint.kind}` : ''}`} id="domain-hint" aria-live="polite">{hint.text}</p>
    {sites && <div className="fo-suggest"><span>자주 추가해요</span>
      {SUGGESTED.filter((domain) => !sites.includes(domain)).map((domain) => <button type="button" key={domain} disabled={disabled} onClick={() => { void add(domain) }}>{domain}</button>)}
    </div>}
  </>
}

/** 앱 추가: 시안의 예시 목록 대신 이 Mac에 설치된 앱(실행 중인 앱 먼저)을 보여 준다(Issue #54와 같음). */
function AppPicker({ apps, saving, showToast, onAdd }: { apps: ExcludedApp[] | null; saving: boolean; showToast: (toast: Toast) => void; onAdd: (app: ExcludedApp) => Promise<void> }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [installed, setInstalled] = useState<InstalledApp[] | 'loading' | 'error'>('loading')

  async function load() {
    setInstalled('loading')
    const result = await analysisApi.listInstalledApps()
    setInstalled(result.success ? result.data : 'error')
  }

  const full = (apps?.length ?? 0) >= MAX_APPS
  return <>
    <div className="fo-app-add">
      <p>메시지·메일 앱은 처음부터 분석하지 않아요</p>
      <button className="fo-button fo-button-primary" type="button" disabled={!apps || saving}
        onClick={() => { if (full) return showToast({ text: `제외 앱은 ${MAX_APPS}개까지 추가할 수 있어요.` }); dialogRef.current?.showModal(); void load() }}>앱 추가</button>
    </div>
    <dialog ref={dialogRef} className="fo-dialog" aria-labelledby="app-dialog-title">
      <h2 id="app-dialog-title">제외할 앱 고르기</h2>
      <p>설치된 앱 중에서 골라 주세요.</p>
      {installed === 'loading' && <p role="status">설치된 앱을 확인하고 있어요.</p>}
      {installed === 'error' && <div className="fo-app-error" role="alert">
        <p>설치된 앱을 불러오지 못했어요. 다시 시도해 주세요.</p>
        <button className="fo-small-btn" type="button" onClick={() => { void load() }}>다시 시도</button>
      </div>}
      {Array.isArray(installed) && <div className="fo-app-list">
        {installed.map((app) => {
          const added = apps?.some((item) => item.bundleId === app.bundleId) ?? false
          return <button className="fo-app-option" type="button" key={app.bundleId} disabled={added || saving}
            onClick={() => { dialogRef.current?.close(); void onAdd({ bundleId: app.bundleId, name: app.name }) }}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {app.icon ? <img className="fo-item-icon" src={app.icon} alt="" /> : <span className="fo-item-icon" aria-hidden="true">{[...app.name][0]}</span>}
            {app.name}
            {added ? <small>추가됨</small> : app.running ? <small>실행 중</small> : null}
          </button>
        })}
      </div>}
      <div className="fo-dialog-actions single"><button className="fo-button fo-button-ghost" type="button" onClick={() => dialogRef.current?.close()}>닫기</button></div>
    </dialog>
  </>
}

function ExclusionList<T>({ view, saving, justAdded, title, sub, empty, toItems, onRetry, onRemove }: {
  view: Load<T>; saving: boolean; justAdded: string; title: string; sub: string; empty: string
  toItems: (data: T) => Item[]; onRetry: () => void; onRemove: (item: Item) => Promise<void>
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [pending, setPending] = useState<Item | null>(null)

  if (view.status === 'loading') return <p className="fo-settings-status" role="status">목록을 불러오고 있어요.</p>
  if (view.status === 'error') return <div className="fo-settings-load-error">
    <div className="fo-notice" role="alert"><strong>{failText(view.code, '목록을 불러오지')}</strong></div>
    <button className="fo-small-btn" type="button" onClick={onRetry}>다시 시도</button>
  </div>

  const items = toItems(view.data)
  if (items.length === 0) return <div className="fo-ex-empty">
    <Image src="/design/poki-floating-excluded-v2.png" alt="" width={96} height={96} />{empty}
  </div>

  return <>
    <div className="fo-list-head"><strong>{title}</strong></div>
    <ul className="fo-ex-list">
      {items.map((item, index) => <li className={`fo-ex-item${item.id === justAdded ? ' is-new' : ''}`} key={item.id} style={{ animationDelay: `${index * 40}ms` }}>
        <span className="fo-item-icon" aria-hidden="true">{[...item.label][0]}</span>
        <div className="fo-item-copy"><strong>{item.label}</strong><small>{sub}</small></div>
        <button className="fo-remove" type="button" disabled={saving} aria-label={`${item.label} 제외 풀기`}
          onClick={() => { setPending(item); dialogRef.current?.showModal() }}>제외 풀기</button>
      </li>)}
    </ul>
    <dialog ref={dialogRef} className="fo-dialog" aria-labelledby="remove-title" aria-describedby="remove-copy" onClose={() => setPending(null)}>
      <h2 id="remove-title">제외를 풀까요?</h2>
      <p id="remove-copy">다음 확인부터 {pending?.label} 화면도 분석해요.</p>
      <div className="fo-dialog-actions">
        <button className="fo-button fo-button-ghost" type="button" onClick={() => dialogRef.current?.close()}>그대로 두기</button>
        <button className="fo-button fo-button-primary" type="button" onClick={() => { const item = pending; dialogRef.current?.close(); if (item) void onRemove(item) }}>제외 풀기</button>
      </div>
    </dialog>
  </>
}
