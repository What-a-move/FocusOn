'use client'

import { useCallback, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { OnboardingShell } from './OnboardingShell'
import { describeError, onboardingApi, type Notice } from './onboarding-api'

const CODE_TTL_MS = 5 * 60 * 1000
const defaultHint = '5분 동안 유효 · 3번까지 입력할 수 있어요'

type Status = 'loading' | 'waiting' | 'expired' | 'error' | 'connected'

export function ExtensionStep() {
  const router = useRouter()
  const [status, setStatus] = useState<Status>('loading')
  const [code, setCode] = useState('')
  const [deadline, setDeadline] = useState(0)
  const [now, setNow] = useState(0)
  const [hint, setHint] = useState<{ kind: '' | 'error' | 'success'; text: string }>({ kind: '', text: defaultHint })
  const [feedback, setFeedback] = useState<Notice | null>(null)

  const issue = useCallback(async () => {
    setStatus('loading')
    setFeedback(null)
    const result = await onboardingApi.issuePairingCode()
    if (!result.success) {
      setCode('')
      setStatus('error')
      setHint({ kind: 'error', text: '연결 코드를 받지 못했어요. ‘새 코드 받기’를 눌러 다시 시도해 주세요.' })
      setFeedback(describeError(result.error))
      return
    }
    setCode(result.data.code.replace('-', ''))
    setDeadline(Date.parse(result.data.expiresAt))
    setNow(Date.now())
    setStatus('waiting')
    setHint({ kind: '', text: defaultHint })
  }, [])

  useEffect(() => {
    // [유지보수] 화면에 들어올 때 코드를 한 번 받는다. 화면을 떠나면 state와 함께 버려진다.
    queueMicrotask(() => { void issue() })
  }, [issue])

  useEffect(() => {
    if (status !== 'waiting') return
    const timer = setInterval(() => {
      const current = Date.now()
      setNow(current)
      if (current >= deadline) {
        setStatus('expired')
        setHint({ kind: 'error', text: '시간이 지나 코드가 만료됐어요.' })
      }
    }, 1000)
    return () => clearInterval(timer)
  }, [status, deadline])

  // ponytail: Extension 입력 결과(연결 완료·코드 틀림)는 서버 pairing 상태 구독이 생기면 status로 반영한다.

  const left = status === 'waiting' ? Math.max(0, deadline - now) : 0
  const seconds = Math.ceil(left / 1000)
  const characters = [...code.padEnd(6, ' ').slice(0, 6)]
  const prompt = status === 'connected'
    ? { text: '연결됐어요! 잘하셨어요.', error: false }
    : status === 'expired'
      ? { text: '코드가 만료됐어요. 새 코드를 받아 주세요.', error: true }
      : { text: 'Chrome이랑 연결해 볼까요?', error: false }

  function goNext() {
    if (status !== 'connected') {
      setFeedback(status === 'expired'
        ? { title: '연결 코드가 만료됐어요.', body: '‘새 코드 받기’를 눌러 다시 연결해 주세요.' }
        : { title: 'Chrome 연결이 필요해요.', body: '확장 프로그램에 연결 코드를 입력해 주세요.' })
      return
    }
    router.push('/onboarding/permissions/')
  }

  return (
    <OnboardingShell
      currentStep={1}
      prompt={prompt.text}
      promptError={prompt.error}
      backHref="/onboarding/"
      feedback={feedback}
      next={{ label: '다음', blocked: status !== 'connected', onClick: goNext }}
    >
      <p className="field-label">연결 코드</p>
      <div className={`code-tiles${status === 'connected' ? ' is-connected' : ''}${status === 'expired' ? ' is-expired' : ''}`} aria-live="polite" aria-label="연결 코드">
        {characters.slice(0, 3).map((character, index) => <span key={index}>{character.trim()}</span>)}
        <span className="dash">-</span>
        {characters.slice(3).map((character, index) => <span key={index + 3}>{character.trim()}</span>)}
      </div>

      <div className="code-meta">
        <div className="timer" aria-hidden="true"><i style={{ width: status === 'connected' ? '100%' : `${(left / CODE_TTL_MS) * 100}%` }} /></div>
        <span className={`countdown${status === 'waiting' && seconds <= 60 ? ' is-low' : ''}`}>
          {status === 'connected' ? '완료' : status === 'waiting' ? `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : '0:00'}
        </span>
        <button className="link-button" type="button" disabled={status === 'loading' || status === 'connected'} onClick={() => { void issue() }}>
          새 코드 받기
        </button>
      </div>
      <p className={`hint${hint.kind ? ` is-${hint.kind}` : ''}`} aria-live="polite">{hint.text}</p>

      <ol className="steps-guide">
        <li>Chrome 오른쪽 위의 <b>FocusOn 아이콘</b>을 눌러 주세요.</li>
        <li>위 코드를 <code>ABC-123</code> 형식 그대로 입력해 주세요.</li>
      </ol>
    </OnboardingShell>
  )
}
