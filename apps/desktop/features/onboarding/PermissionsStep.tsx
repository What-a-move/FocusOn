'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { OnboardingShell } from './OnboardingShell'
import { describeError, onboardingApi, type Notice } from './onboarding-api'

type Detail = { title: string; rows: [string, string][] }
type Consent = { id: string; title: string; summary?: string; detail: Detail }

const requiredConsents: Consent[] = [
  {
    id: 'terms',
    title: '서비스 이용약관',
    detail: { title: '서비스 이용약관', rows: [
      ['목적', '서비스 이용 조건과 책임 범위 안내'],
      ['처리 항목', '동의 여부와 동의 시각'],
      ['보유 기간', '철회하거나 계정을 삭제한 뒤 3년 (동의 이력만)'],
      ['거부 권리', '동의하지 않을 수 있어요.'],
      ['거부 시 영향', 'FocusOn을 이용할 수 없어요.'],
      ['버전', 'v1.0'],
    ] },
  },
  {
    id: 'privacy',
    title: '개인정보 처리방침',
    summary: 'Google 계정 식별자, 닉네임, 목표, 집중 기록',
    detail: { title: '개인정보 처리방침', rows: [
      ['목적', '계정 관리, 집중 기록과 리포트 제공'],
      ['처리 항목', 'Google 계정 식별자, 닉네임, 목표, 세션 시간·기록, 연결한 기기 정보'],
      ['보유 기간', '집중 기록·리포트는 세션 종료 후 1년. 계정을 삭제하면 30일 안에 삭제해요.'],
      ['거부 권리', '동의하지 않을 수 있어요.'],
      ['거부 시 영향', '계정을 만들 수 없어요.'],
      ['버전', 'v1.0'],
    ] },
  },
  {
    id: 'screen-analysis',
    title: '화면 분석',
    summary: '지금 보는 화면이 목표와 관련 있는지만 확인해요',
    detail: { title: '화면 분석', rows: [
      ['목적', '집중 중에 보는 앱·페이지가 목표와 관련 있는지 확인'],
      ['처리 항목', '앱 이름, 탭 제목·도메인, 필요할 때만 보이는 화면의 글자. 판단 결과만 저장하고 화면 원문·캡처는 저장하지 않아요.'],
      ['보유 기간', '분석 결과는 세션 종료 후 90일 (그 뒤엔 합계만 남겨요)'],
      ['거부 권리', '동의하지 않을 수 있어요.'],
      ['거부 시 영향', '집중 세션을 시작할 수 없어요.'],
      ['버전', 'v1.0'],
    ] },
  },
]

const cameraDetail: Detail = { title: '카메라 분석', rows: [
  ['목적', '화면만으로 알기 어려운, 오래 자리를 비운 순간을 보조로 알아차리기'],
  ['처리 항목', '얼굴이 보이는지, 시선이 화면 쪽인지 같은 상태 신호. 영상·사진은 저장하거나 보내지 않아요.'],
  ['보유 기간', '상태 기록은 세션 종료 후 1년'],
  ['거부 권리', '선택 항목이라 켜지 않아도 돼요.'],
  ['거부 시 영향', '화면 분석만으로 정상 이용할 수 있어요. 설정에서 언제든 켜고 끌 수 있어요.'],
  ['버전', 'v1.0'],
] }

type PermName = 'screen' | 'camera' | 'notify'
type PermState = { state: 'idle' | 'opened' | 'granted' | 'denied' | 'requested'; error: string | null }

const permCopy: Record<PermName, { title: string; required: boolean; idle: string; action: string }> = {
  screen: { title: '화면 기록', required: true, idle: '보고 있는 화면이 목표와 맞는지 확인할 때만 써요', action: '설정 열기' },
  camera: { title: '카메라', required: false, idle: '동의한 카메라 분석에 필요해요', action: '허용하기' },
  notify: { title: '알림', required: true, idle: '집중이 흐트러졌을 때 알려 드려요', action: '허용하기' },
}

const idle: PermState = { state: 'idle', error: null }
// macOS가 설정 창 열기를 거절한 경우다. 서버 문제가 아니므로 직접 여는 길을 알려 준다.
const SETTINGS_NOT_OPENED: Record<'screen' | 'notify', Notice> = {
  screen: { title: '시스템 설정을 열지 못했어요.', body: '시스템 설정 › 개인정보 보호 및 보안 › 화면 기록에서 직접 켜 주세요.' },
  notify: { title: '시스템 설정을 열지 못했어요.', body: '시스템 설정 › 알림에서 FocusOn을 직접 켜 주세요.' },
}
const NOTIFY_BLOCKED: Record<PermState['state'], string> = {
  idle: '‘허용하기’로 시험 알림을 받은 뒤 ‘보였어요’를 눌러 주세요.',
  requested: '시험 알림이 보였다면 ‘보였어요’를 눌러 주세요.',
  denied: '‘설정 열기’로 알림을 켠 뒤 ‘다시 보내기’를 눌러 주세요.',
  opened: '알림을 허용한 뒤 ‘다시 보내기’를 눌러 주세요.',
  granted: '',
}

// [유지보수] 화면 기록은 macOS가 앱 안에서 허용 창을 띄우지 않는다. 시스템 설정을 연 뒤 돌아와서 다시 확인한다.
function permText(name: PermName, perm: PermState): string {
  if (perm.error) return perm.error
  if (name === 'screen' && perm.state === 'opened') return '시스템 설정 › 개인정보 보호 및 보안 › 화면 기록에서 FocusOn을 켜 주세요'
  if (name === 'screen' && perm.state === 'denied') return '아직 꺼져 있어요. 켰다면 FocusOn을 다시 열어 주세요.'
  // [유지보수] macOS가 알림 허용 여부를 알려 주지 않아 시험 알림을 보내고 사용자에게 보였는지 묻는다.
  if (name === 'notify' && perm.state === 'requested') return '방금 보낸 알림이 보였나요?'
  if (name === 'notify' && perm.state === 'denied') return '시스템 설정 › 알림에서 FocusOn을 허용해 주세요.'
  if (name === 'notify' && perm.state === 'opened') return '허용한 뒤 ‘다시 보내기’를 눌러 주세요.'
  if (perm.state === 'denied') return '시스템 설정에서 언제든 허용할 수 있어요.'
  return permCopy[name].idle
}

export function PermissionsStep() {
  const router = useRouter()
  const [view, setView] = useState<'consents' | 'permission'>('consents')
  const [accepted, setAccepted] = useState<Set<string>>(new Set())
  const [cameraConsent, setCameraConsent] = useState(false)
  const [perms, setPerms] = useState<Record<PermName, PermState>>({ screen: idle, camera: idle, notify: idle })
  const [detail, setDetail] = useState<Detail | null>(null)
  const [prompt, setPrompt] = useState({ text: '시작하려면 동의가 필요해요!', error: false })
  const [feedback, setFeedback] = useState<Notice | null>(null)
  const [saving, setSaving] = useState(false)
  const dialogRef = useRef<HTMLDialogElement>(null)

  const allAccepted = requiredConsents.every(({ id }) => accepted.has(id))
  const screenReady = perms.screen.state === 'granted'
  const notifyReady = perms.notify.state === 'granted'
  const setPerm = (name: PermName, next: PermState) => setPerms((current) => ({ ...current, [name]: next }))

  function updateConsents(next: Set<string>) {
    setAccepted(next)
    setFeedback(null)
    const ready = requiredConsents.every(({ id }) => next.has(id))
    setPrompt({ text: ready ? '좋아요, 다 됐어요!' : '시작하려면 동의가 필요해요!', error: false })
  }

  function showDetail(next: Detail) {
    setDetail(next)
    dialogRef.current?.showModal()
  }

  function failPerm(name: PermName, state: PermState['state'], notice: Notice) {
    setPerm(name, { state, error: `${notice.title} ${notice.body}` })
    setPrompt({ text: '권한을 확인하지 못했어요.', error: true })
  }

  // [유지보수] 시스템 설정에서 돌아오면(창 포커스) 화면 기록 상태를 조용히 다시 확인한다.
  const recheckScreen = useCallback(async (silent: boolean) => {
    const result = await onboardingApi.checkScreenRecording()
    const setScreen = (next: PermState) => setPerms((current) => ({ ...current, screen: next }))
    if (!result.success) {
      if (!silent) {
        const notice = describeError(result.error, '화면 기록 권한을 확인하지 못했어요.')
        setScreen({ state: 'opened', error: `${notice.title} ${notice.body}` })
        setPrompt({ text: '권한을 확인하지 못했어요.', error: true })
      }
      return
    }
    if (result.data.granted) {
      setScreen({ state: 'granted', error: null })
      setPrompt({ text: '좋아요, 준비됐어요!', error: false })
    } else if (!silent) {
      setScreen({ state: 'denied', error: null })
      setPrompt({ text: '아직 꺼져 있는 것 같아요.', error: false })
    }
  }, [])

  useEffect(() => {
    if (view !== 'permission') return
    queueMicrotask(() => { void recheckScreen(true) })
    const onFocus = () => { void recheckScreen(true) }
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [view, recheckScreen])

  async function runPerm(name: PermName) {
    setFeedback(null)
    const current = perms[name]
    if (name === 'screen' && current.state === 'idle') {
      const result = await onboardingApi.openScreenRecordingSettings()
      if (!result.success || !result.data.opened) return failPerm(name, 'idle', result.success ? SETTINGS_NOT_OPENED.screen : describeError(result.error, '시스템 설정을 열지 못했어요.'))
      setPerm(name, { state: 'opened', error: null })
      setPrompt({ text: '설정에서 켜고 돌아와 주세요!', error: false })
      return
    }
    if (name === 'screen') return recheckScreen(false)
    if (name === 'camera') {
      const result = await onboardingApi.requestCamera()
      if (!result.success) return failPerm(name, current.state, describeError(result.error, '카메라 권한을 요청하지 못했어요.'))
      setPerm(name, { state: result.data.granted ? 'granted' : 'denied', error: null })
      if (result.data.granted) setPrompt({ text: '카메라도 준비됐어요!', error: false })
      return
    }
    if (current.state === 'denied') {
      const result = await onboardingApi.openNotificationSettings()
      if (!result.success || !result.data.opened) return failPerm(name, 'denied', result.success ? SETTINGS_NOT_OPENED.notify : describeError(result.error, '시스템 설정을 열지 못했어요.'))
      setPerm(name, { state: 'opened', error: null })
      setPrompt({ text: '설정에서 켜고 돌아와 주세요!', error: false })
      return
    }
    const result = await onboardingApi.requestNotifications()
    if (!result.success) return failPerm(name, current.state, describeError(result.error, '시험 알림을 보내지 못했어요.'))
    setPerm(name, { state: result.data.requested ? 'requested' : 'denied', error: null })
    setPrompt(result.data.requested ? { text: '알림이 왔는지 봐 주세요!', error: false } : { text: '알림을 보내지 못했어요.', error: true })
  }

  async function answerNotification(seen: boolean) {
    setFeedback(null)
    const saved = await onboardingApi.confirmNotification(seen)
    if (!saved.success && seen) {
      setFeedback({ title: '알림 확인을 저장하지 못했어요.', body: '‘보였어요’를 다시 눌러 주세요.' })
      return
    }
    setPerm('notify', { state: seen ? 'granted' : 'denied', error: null })
    setPrompt(seen ? { text: '알림도 준비됐어요!', error: false } : { text: '설정에서 알림을 켜 주세요.', error: false })
  }

  async function goNext() {
    if (view === 'consents') {
      if (!allAccepted) {
        setFeedback({ title: '필수 항목에 동의해 주세요.', body: '필수 항목 3개에 모두 동의해야 시작할 수 있어요.', info: true })
        return
      }
      setView('permission')
      setFeedback(null)
      setPrompt({ text: '권한 두 개만 켜 주시면 돼요', error: false })
      return
    }
    if (!screenReady) {
      setFeedback({ title: '화면 기록 권한이 필요해요.', body: perms.screen.state === 'idle' ? '‘설정 열기’로 시스템 설정을 열어 화면 기록을 켜 주세요.' : '시스템 설정에서 화면 기록을 켠 뒤 ‘다시 확인’을 눌러 주세요.', info: true })
      return
    }
    if (!notifyReady) {
      setFeedback({ title: '알림 권한이 필요해요.', body: NOTIFY_BLOCKED[perms.notify.state], info: true })
      return
    }
    setSaving(true)
    const result = await onboardingApi.savePermissionsStep({ consents: [...accepted], cameraConsent })
    setSaving(false)
    if (!result.success) {
      setFeedback(describeError(result.error, '동의와 권한을 저장하지 못했어요.'))
      return
    }
    router.push('/onboarding/sites/')
  }

  const visiblePerms: PermName[] = cameraConsent ? ['screen', 'camera', 'notify'] : ['screen', 'notify']

  return (
    <OnboardingShell
      currentStep={2}
      prompt={prompt.text}
      promptError={prompt.error}
      backHref={view === 'consents' ? '/onboarding/extension/' : undefined}
      onBack={view === 'permission' ? () => { setView('consents'); setFeedback(null); setPrompt({ text: '시작하려면 동의가 필요해요!', error: false }) } : undefined}
      feedback={feedback}
      next={{ label: '다음', blocked: view === 'consents' ? !allAccepted : !(screenReady && notifyReady), busy: saving, onClick: () => { void goNext() } }}
    >
      {view === 'consents' ? (
        <div className="consents">
          <p className="substep">필수 동의 <span>1 / 2</span></p>
          <ul className="choice-list">
            <li>
              <button className="choice all" type="button" role="checkbox" aria-checked={allAccepted} onClick={() => updateConsents(allAccepted ? new Set() : new Set(requiredConsents.map(({ id }) => id)))}>
                <span className="check" aria-hidden="true">✓</span>
                <span className="choice-text"><strong>모두 동의하기</strong></span>
              </button>
            </li>
            <li className="divider" aria-hidden="true" />
            {requiredConsents.map((consent) => {
              const checked = accepted.has(consent.id)
              return (
                <li key={consent.id}>
                  <button
                    className="choice"
                    type="button"
                    role="checkbox"
                    aria-checked={checked}
                    onClick={() => {
                      const next = new Set(accepted)
                      if (checked) next.delete(consent.id)
                      else next.add(consent.id)
                      updateConsents(next)
                    }}
                  >
                    <span className="check" aria-hidden="true">✓</span>
                    <span className="choice-text"><strong>{consent.title}</strong>{consent.summary ? <small>{consent.summary}</small> : null}</span>
                    <span className="tag required">필수</span>
                  </button>
                  <button className="detail-link" type="button" onClick={() => showDetail(consent.detail)}>자세히</button>
                </li>
              )
            })}
            <li className="optional-label">선택 동의</li>
            <li>
              <button
                className="choice"
                type="button"
                role="checkbox"
                aria-checked={cameraConsent}
                onClick={() => {
                  const on = !cameraConsent
                  setCameraConsent(on)
                  if (!on) setPerm('camera', idle)
                  setPrompt({ text: on ? '카메라도 함께 볼게요.\n다음에서 권한을 물어볼게요.' : '카메라는 안 켜도 괜찮아요!', error: false })
                }}
              >
                <span className="check" aria-hidden="true">✓</span>
                <span className="choice-text"><strong>카메라 분석</strong><small>얼굴·시선을 기기 안에서만 분석해요. 영상은 저장하지 않아요.</small></span>
                <span className="tag">선택</span>
              </button>
              <button className="detail-link" type="button" onClick={() => showDetail(cameraDetail)}>자세히</button>
            </li>
          </ul>
        </div>
      ) : (
        <>
          <p className="substep">권한 확인 <span>2 / 2</span></p>
          <ul className="perm-list">
            {visiblePerms.map((name) => {
              const perm = perms[name]
              const copy = permCopy[name]
              const action = name === 'screen' && perm.state !== 'idle' ? '다시 확인'
                : name === 'notify' && perm.state === 'denied' ? '설정 열기'
                  : name === 'notify' && perm.state === 'opened' ? '다시 보내기' : copy.action
              return (
                <li className="perm" data-state={perm.state} key={name}>
                  <span className="perm-state" aria-hidden="true">{perm.state === 'denied' ? '!' : '✓'}</span>
                  <span className="choice-text">
                    <strong>{copy.title} <span className={`tag${copy.required ? ' required' : ''}`}>{copy.required ? '필수' : '선택'}</span></strong>
                    <small className={`perm-desc${perm.error ? ' is-error' : ''}`} aria-live="polite">{permText(name, perm)}</small>
                  </span>
                  {perm.state === 'granted'
                    ? <span className="perm-done">허용됨</span>
                    : name === 'notify' && perm.state === 'requested'
                      ? (
                        <span className="perm-answer">
                          <button className="btn btn-ghost perm-action" type="button" onClick={() => { void answerNotification(false) }}>안 보였어요</button>
                          <button className="btn btn-primary perm-action" type="button" onClick={() => { void answerNotification(true) }}>보였어요</button>
                        </span>
                      )
                      : <button className="btn btn-ghost perm-action" type="button" onClick={() => { void runPerm(name) }}>{action}</button>}
                </li>
              )
            })}
          </ul>
          <p className="perm-note">화면 원본과 캡처는 저장하지 않아요.</p>
        </>
      )}

      <dialog className="consent-dialog" ref={dialogRef} aria-labelledby="consent-dialog-title" onClose={() => setDetail(null)}>
        {detail ? (
          <>
            <h2 id="consent-dialog-title">{detail.title}</h2>
            <dl>{detail.rows.map(([term, value]) => [<dt key={`${term}-t`}>{term}</dt>, <dd key={`${term}-d`}>{value}</dd>])}</dl>
            <form method="dialog"><button className="btn btn-primary" type="submit">확인</button></form>
          </>
        ) : null}
      </dialog>
    </OnboardingShell>
  )
}
