'use client'

import { useRef, useState } from 'react'
import { analysisApi, type ExcludedApp, type InstalledApp } from './onboarding-api'

const MAX_APPS = 50

/**
 * 분석 제외 앱 고르기 (FocusOn-design/exclusions.html 앱 탭·`제외할 앱 고르기`). 목록은 이 기기에만 저장한다(결정 024).
 * 시안의 예시 앱 목록 대신 실제 설치된 앱(실행 중인 앱 먼저)을 보여 준다.
 */
export function ExcludedAppsField({ apps, onChange }: { apps: ExcludedApp[]; onChange: (apps: ExcludedApp[]) => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [installed, setInstalled] = useState<InstalledApp[] | 'loading' | 'error'>('loading')
  const [hint, setHint] = useState('')

  async function load() {
    setInstalled('loading')
    const result = await analysisApi.listInstalledApps()
    setInstalled(result.success ? result.data : 'error')
  }

  function open() {
    dialogRef.current?.showModal()
    void load()
  }

  function add(app: InstalledApp) {
    onChange([{ bundleId: app.bundleId, name: app.name }, ...apps])
    setHint(`제외했어요 · ${app.name}`)
    dialogRef.current?.close()
  }

  const full = apps.length >= MAX_APPS
  return <div className="app-field">
    <p className="field-label" id="apps-label">분석 제외 앱 <span className="tag">선택</span></p>
    <div className="app-add">
      <p>메시지·메일 앱은 처음부터 분석하지 않아요.</p>
      <button className="btn btn-ghost check-button" type="button" onClick={open}>앱 추가</button>
    </div>
    <p className={`hint${hint ? ' is-success' : ''}`} aria-live="polite">{hint}</p>
    {apps.length > 0 && <ul className="site-list" aria-labelledby="apps-label">
      {apps.map((app) => (
        <li key={app.bundleId}>
          {app.name}
          <button type="button" aria-label={`${app.name} 빼기`} onClick={() => { onChange(apps.filter((item) => item.bundleId !== app.bundleId)); setHint('') }}>✕</button>
        </li>
      ))}
    </ul>}

    <dialog ref={dialogRef} className="consent-dialog app-dialog" aria-labelledby="app-dialog-title">
      <h2 id="app-dialog-title">제외할 앱 고르기</h2>
      <p>설치된 앱 중에서 골라 주세요.{full ? ` 제외 앱은 ${MAX_APPS}개까지 추가할 수 있어요.` : ''}</p>
      {installed === 'loading' && <p role="status">설치된 앱을 확인하고 있어요.</p>}
      {installed === 'error' && <div className="app-error" role="alert">
        <p>설치된 앱을 불러오지 못했어요. 다시 시도해 주세요.</p>
        <button className="btn btn-ghost" type="button" onClick={() => { void load() }}>다시 시도</button>
      </div>}
      {Array.isArray(installed) && <div className="app-list">
        {installed.map((app) => {
          const added = apps.some((item) => item.bundleId === app.bundleId)
          return <button className="app-option" type="button" key={app.bundleId} disabled={added || full} onClick={() => add(app)}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {app.icon ? <img className="item-icon" src={app.icon} alt="" /> : <span className="item-icon" aria-hidden="true">{[...app.name][0]}</span>}
            {app.name}
            {added ? <small>추가됨</small> : app.running ? <small>실행 중</small> : null}
          </button>
        })}
      </div>}
      <button className="btn btn-ghost" type="button" onClick={() => dialogRef.current?.close()}>닫기</button>
    </dialog>
  </div>
}
