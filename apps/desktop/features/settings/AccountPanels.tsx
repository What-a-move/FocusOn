'use client'

import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useRouter } from 'next/navigation'
import { errorText } from '../../app/lib/error-text'
import { requiredConsents } from '../onboarding/consent-docs'
import { ConsentDocument } from '../onboarding/ConsentDocument'
import { onboardingApi } from '../onboarding/onboarding-api'
import { LoadError, failText } from './SettingsShell'
import { settingsApi, type ConsentList, type DeviceStatus } from './settings-api'

// 디자인 원본: FocusOn-design/settings.html 계정(D25)·기기 연결(D24) 패널. 철회·해제·삭제는 오류가 아니므로 빨간색을 쓰지 않는다(결정 031).
type Load<T> = { status: 'loading' } | { status: 'error'; code: string } | { status: 'ready'; data: T }
type ShowToast = (toast: { text: string }) => void
const NICKNAME = /^[가-힣A-Za-z0-9]{2,12}$/u
const dateText = (value: string) => new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' }).format(new Date(value))
const timeText = (value: string) => new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(value))

/** 확인 창 하나를 여러 동작이 함께 쓴다. 고른 동작은 확인을 누를 때만 실행한다. */
type Confirm = { title: string; body: string; action: string; run: () => Promise<void> }
function useConfirm(): [(confirm: Confirm) => void, ReactNode] {
  const ref = useRef<HTMLDialogElement>(null)
  const [pending, setPending] = useState<Confirm | null>(null)
  const open = (confirm: Confirm) => { setPending(confirm); ref.current?.showModal() }
  const dialog = <dialog ref={ref} className="fo-dialog" aria-labelledby="confirm-title" aria-describedby="confirm-copy" onClose={() => setPending(null)}>
    <h2 id="confirm-title">{pending?.title}</h2>
    <p id="confirm-copy">{pending?.body}</p>
    <div className="fo-dialog-actions">
      <button className="fo-button fo-button-ghost" type="button" onClick={() => ref.current?.close()}>취소</button>
      <button className="fo-button fo-button-primary" type="button" onClick={() => { const item = pending; ref.current?.close(); if (item) void item.run() }}>{pending?.action}</button>
    </div>
  </dialog>
  return [open, dialog]
}

/** 계정: 닉네임 · 동의 내역 · 화면 분석 동의 · 로그아웃 · 계정 삭제. */
export function AccountPanel({ showToast }: { showToast: ShowToast }) {
  const router = useRouter()
  const [nickname, setNickname] = useState('FocusOn 사용자')
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [nameError, setNameError] = useState('')
  const [saving, setSaving] = useState(false)
  const [consents, setConsents] = useState<Load<ConsentList>>({ status: 'loading' })
  const docsRef = useRef<HTMLDialogElement>(null)
  const [confirm, confirmDialog] = useConfirm()

  useEffect(() => {
    const auth = window.focusOn?.auth
    if (!auth) return
    void auth.getSession().then((result) => { if (result.success && result.data.user?.nickname) setNickname(result.data.user.nickname) })
    return auth.onSessionChanged((snapshot) => { if (snapshot.user?.nickname) setNickname(snapshot.user.nickname) })
  }, [])

  const load = useCallback(async () => {
    setConsents({ status: 'loading' })
    const result = await settingsApi.getConsents()
    setConsents(result.success ? { status: 'ready', data: result.data } : { status: 'error', code: result.error.code })
  }, [])
  useEffect(() => { queueMicrotask(() => { void load() }) }, [load])

  async function saveName() {
    const value = draft.trim()
    if (!NICKNAME.test(value)) { setNameError(value.length < 2 ? '2자 이상 입력해 주세요.' : '2~12자의 한글·영문·숫자만 쓸 수 있어요.'); return }
    if (value === nickname) { setEditing(false); return }
    setSaving(true)
    const result = await onboardingApi.saveNickname(value)
    setSaving(false)
    if (!result.success) {
      setNameError(result.error.code === 'PROFILE_NICKNAME_DUPLICATE' ? '이미 사용 중인 닉네임이에요. 다른 이름을 적어 주세요.' : `저장하지 못했어요. ${errorText(result.error)}`)
      return
    }
    setEditing(false)
    showToast({ text: '닉네임을 바꿨어요' })
  }

  async function setScreenConsent(granted: boolean) {
    const result = await settingsApi.setScreenConsent(granted)
    if (!result.success) return showToast({ text: failText(result.error.code, granted ? '동의하지' : '철회하지') })
    setConsents({ status: 'ready', data: result.data })
    showToast({ text: granted ? '화면 분석에 다시 동의했어요' : '화면 분석 동의를 철회했어요' })
  }

  async function logout() {
    const result = await window.focusOn?.auth.logout()
    // 서버 로그아웃이 실패하면 로그인이 남아 있으므로 첫 화면으로 보내지 않는다.
    if (!result?.success) return showToast({ text: failText(result?.error.code ?? 'SERVICE_UNAVAILABLE', '로그아웃하지') })
    router.replace('/')
  }

  const screen = consents.status === 'ready' ? consents.data.items.find((item) => item.type === 'SCREEN_ANALYSIS') : undefined

  return <>
    <div className="fo-account-hero">
      <span className="fo-account-avatar" aria-hidden="true" />
      <div className="fo-row-copy">
        {editing ? <>
          <input className={`fo-text-input${nameError ? ' is-error' : ''}`} type="text" maxLength={12} autoComplete="off" autoFocus aria-label="새 닉네임" aria-describedby="name-hint" value={draft}
            onChange={(event) => { setDraft(event.target.value); setNameError('') }} onKeyDown={(event) => { if (event.key === 'Enter') void saveName(); if (event.key === 'Escape') setEditing(false) }} />
          <small id="name-hint" className={nameError ? 'fo-hint is-error' : ''} role={nameError ? 'alert' : undefined}>{nameError || '2~12자 · 한글·영문·숫자'}</small>
        </> : <><strong className="fo-account-name">{nickname}</strong><small>Google 계정</small></>}
      </div>
      {editing ? <div className="fo-account-actions">
        <button className="fo-small-btn" type="button" onClick={() => setEditing(false)} disabled={saving}>취소</button>
        <button className="fo-small-btn" type="button" onClick={() => { void saveName() }} disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
      </div> : <button className="fo-small-btn" type="button" onClick={() => { setDraft(nickname); setNameError(''); setEditing(true) }}>닉네임 변경</button>}
    </div>

    {consents.status === 'error' ? <LoadError compact code={consents.code} what="동의 내역을" onRetry={() => { void load() }} /> : <div>
      <div className="fo-row">
        <div className="fo-row-copy"><strong>동의 내역</strong><small>{consents.status === 'loading' ? '불러오고 있어요' : `필수 3개 · ${screen?.granted ? '모두 동의함' : '화면 분석 철회함'}`}</small></div>
        <button className="fo-small-btn" type="button" onClick={() => { docsRef.current?.showModal(); docsRef.current?.scrollTo(0, 0) }}>전문 보기</button>
      </div>
      <div className="fo-row">
        {screen?.granted === false ? <>
          <div className="fo-row-copy"><strong>화면 분석 동의</strong><small>{screen.withdrawnAt ? `${dateText(screen.withdrawnAt)}에 철회했어요 · ` : ''}다시 동의해야 새 집중을 시작할 수 있어요</small></div>
          <button className="fo-small-btn" type="button" onClick={() => confirm({ title: '화면 분석에 다시 동의할까요?', body: '집중 중에 보는 앱·페이지가 목표와 관련 있는지만 확인해요. 화면 원문·캡처는 저장하지 않아요.', action: '동의하기', run: () => setScreenConsent(true) })}>다시 동의</button>
        </> : <>
          <div className="fo-row-copy"><strong>화면 분석 동의 철회</strong><small>철회하면 새 집중을 시작할 수 없어요</small></div>
          <button className="fo-small-btn" type="button" disabled={consents.status !== 'ready'} onClick={() => confirm({ title: '화면 분석 동의를 철회할까요?', body: '다시 동의할 때까지 새 집중을 시작할 수 없고, 홈 대신 동의 화면이 열려요. 이미 남은 집중 기록은 그대로예요.', action: '철회하기', run: () => setScreenConsent(false) })}>철회</button>
        </>}
      </div>
      <div className="fo-row">
        <div className="fo-row-copy"><strong>로그아웃</strong><small>이 Mac에서 로그인 정보를 지워요</small></div>
        <button className="fo-small-btn" type="button" onClick={() => confirm({ title: '로그아웃할까요?', body: '다시 쓰려면 Google로 로그인해야 해요.', action: '로그아웃', run: logout })}>로그아웃</button>
      </div>
    </div>}

    <div className="fo-danger-zone">
      <p className="fo-danger-label">되돌릴 수 없어요</p>
      <div className="fo-row">
        {/* 서버에 계정 삭제 API가 아직 없어 버튼을 막아 둔다(PM 결정, 2026-11-02). */}
        <div className="fo-row-copy"><strong>계정 삭제</strong><small>모든 기록과 동의 내역이 함께 삭제돼요 · 아직 준비 중이에요</small></div>
        <button className="fo-small-btn" type="button" disabled>계정 삭제</button>
      </div>
    </div>

    <dialog ref={docsRef} className="fo-dialog fo-docs-dialog" aria-labelledby="docs-title">
      <ConsentDocument docs={requiredConsents.map(({ detail }) => detail)} titleId="docs-title" />
      <div className="fo-dialog-actions single"><button className="fo-button fo-button-ghost" type="button" onClick={() => docsRef.current?.close()}>닫기</button></div>
    </dialog>
    {confirmDialog}
  </>
}

const POLL_MS = 3000

/** 기기 연결: 이 Mac에 연결된 Chrome 확장 상태 · 새 연결 코드 · 연결 해제. */
export function DevicesPanel({ showToast }: { showToast: ShowToast }) {
  const [view, setView] = useState<Load<DeviceStatus>>({ status: 'loading' })
  const [code, setCode] = useState<{ value: string; expiresAt: number } | null>(null)
  const [issuing, setIssuing] = useState(false)
  const [now, setNow] = useState(0)
  const [confirm, confirmDialog] = useConfirm()

  const load = useCallback(async () => {
    const result = await settingsApi.getDevices()
    setView(result.success ? { status: 'ready', data: result.data } : { status: 'error', code: result.error.code })
    return result
  }, [])
  useEffect(() => { queueMicrotask(() => { void load() }) }, [load])

  // 코드를 보여 주는 동안 3초마다 연결됐는지 확인한다. 연결되면 코드를 닫는다.
  // ponytail: 실시간 알림 채널이 생기면 polling 대신 구독으로 바꾼다.
  useEffect(() => {
    if (!code) return
    // 이 코드에 대한 확인만 반영한다. 코드를 닫거나 새로 받으면 늦게 온 결과는 버리고, 확인 요청은 겹치지 않게 한다.
    let active = true
    let checking = false
    const tick = setInterval(() => {
      const current = Date.now()
      setNow(current)
      if (current >= code.expiresAt) setCode(null)
    }, 1000)
    const poll = setInterval(() => {
      if (checking) return
      checking = true
      void settingsApi.getDevices().then((result) => {
        checking = false
        if (!active || !result.success || !result.data.extension) return
        setView({ status: 'ready', data: result.data })
        setCode(null)
        showToast({ text: 'Chrome 확장과 연결됐어요' })
        // 해제로 미완료가 됐던 온보딩 상태를 서버 기준으로 다시 맞춘다. 그래야 홈이 동의·연결 화면으로 가지 않는다.
        void onboardingApi.getReadiness()
      })
    }, POLL_MS)
    return () => { active = false; clearInterval(tick); clearInterval(poll) }
  }, [code, showToast])

  async function issue() {
    setIssuing(true)
    const result = await onboardingApi.issuePairingCode()
    setIssuing(false)
    if (!result.success) return showToast({ text: failText(result.error.code, '연결 코드를 받지') })
    setNow(Date.now())
    setCode({ value: result.data.code, expiresAt: Date.parse(result.data.expiresAt) })
  }

  async function unlink(deviceId: string) {
    const result = await settingsApi.unlinkExtension(deviceId)
    if (!result.success) return showToast({ text: failText(result.error.code, '연결을 해제하지') })
    setView({ status: 'ready', data: result.data })
    showToast({ text: 'Chrome 확장 연결을 해제했어요' })
  }

  if (view.status === 'error') return <LoadError poki={false} code={view.code} what="기기 연결을" onRetry={() => { setView({ status: 'loading' }); void load() }} />
  const extension = view.status === 'ready' ? view.data.extension : null
  const left = code ? Math.max(0, Math.ceil((code.expiresAt - now) / 1000)) : 0

  return <>
    <div>
      <div className="fo-device">
        <span className="fo-device-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"><path d="M10 4.5a2 2 0 0 1 4 0V6h3.5a1 1 0 0 1 1 1v3.5H17a2 2 0 0 0 0 4h1.5V18a1 1 0 0 1-1 1h-3.5v-1.5a2 2 0 0 0-4 0V19H6.5a1 1 0 0 1-1-1v-3.5H7a2 2 0 0 0 0-4H5.5V7a1 1 0 0 1 1-1H10z" /></svg></span>
        <div className="fo-row-copy">
          <strong>Chrome 확장 프로그램</strong>
          <small>{view.status === 'loading' ? '확인하고 있어요' : extension ? (extension.lastUsedAt ? `마지막 확인 ${timeText(extension.lastUsedAt)}` : '이 Mac과 연결돼 있어요') : '연결된 확장이 없어요'}</small>
        </div>
        {view.status === 'ready' && <span className={`fo-badge ${extension ? 'ok' : 'bad'}`}>{extension ? '연결됨' : '연결 안 됨'}</span>}
      </div>
      {code && <div className="fo-link-code" role="status">
        <b>{code.value}</b>
        <div className="fo-row-copy"><small>Chrome 툴바의 FocusOn 아이콘을 눌러 입력해 주세요</small><small>{Math.floor(left / 60)}:{String(left % 60).padStart(2, '0')} 남음 · 한 번만 쓸 수 있어요</small></div>
        <button className="fo-small-btn" type="button" onClick={() => setCode(null)}>닫기</button>
      </div>}
    </div>
    <div>
      <div className="fo-row">
        <div className="fo-row-copy"><strong>새 연결 코드</strong><small>5분 동안 한 번 쓸 수 있어요</small></div>
        <button className="fo-small-btn" type="button" onClick={() => { void issue() }} disabled={issuing || view.status !== 'ready'}>{issuing ? '받는 중…' : code ? '새 코드 받기' : '코드 받기'}</button>
      </div>
      {extension && <div className="fo-row">
        <div className="fo-row-copy"><strong>연결 해제</strong><small>다시 연결할 때까지 새 집중을 시작할 수 없어요</small></div>
        <button className="fo-small-btn" type="button" onClick={() => confirm({ title: 'Chrome 확장 연결을 해제할까요?', body: '해제하면 다시 연결할 때까지 새 집중을 시작할 수 없어요. 새 연결 코드로 언제든 다시 연결할 수 있어요.', action: '해제하기', run: () => unlink(extension.deviceId) })}>해제</button>
      </div>}
    </div>
    {confirmDialog}
  </>
}
