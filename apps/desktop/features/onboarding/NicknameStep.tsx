'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { OnboardingShell } from './OnboardingShell'
import { describeError, onboardingApi, type Notice } from './onboarding-api'

// field: false면 입력값 문제가 아니라 서버·연결 문제라 입력칸은 빨갛게 하지 않는다
type Hint = { kind: '' | 'error' | 'success'; text: string; field?: boolean }

const defaultHint: Hint = { kind: '', text: '2~12자 · 특수문자 제외' }

export function NicknameStep() {
  const router = useRouter()
  const [nickname, setNickname] = useState('')
  const [hint, setHint] = useState<Hint>(defaultHint)
  const [checked, setChecked] = useState(false)
  const [checking, setChecking] = useState(false)
  const [prompt, setPrompt] = useState({ text: '어떻게 불러드릴까요?', error: false })
  const [feedback, setFeedback] = useState<Notice | null>(null)

  async function check() {
    const value = nickname.trim()
    if (!/^[가-힣A-Za-z0-9]{2,12}$/u.test(value)) {
      setHint({ kind: 'error', text: value.length < 2 ? '2자 이상 입력해 주세요.' : '2~12자의 한글·영문·숫자만 쓸 수 있어요.' })
      setPrompt({ text: '음… 다시 한번 적어 주실래요?', error: true })
      return
    }

    // [유지보수] 서버에 중복 확인 API가 따로 없어 저장 요청의 결과(성공·409 중복)로 확인한다. 성공하면 이미 저장된 상태다.
    setChecking(true)
    const result = await onboardingApi.saveNickname(value)
    setChecking(false)
    if (result.success) {
      setChecked(true)
      setHint({ kind: 'success', text: '사용할 수 있는 닉네임이에요.' })
      setPrompt({ text: `반가워요, ${value}님!`, error: false })
      setFeedback(null)
      return
    }

    const notice = describeError(result.error)
    const duplicate = result.error.code === 'PROFILE_NICKNAME_DUPLICATE'
    setHint({ kind: 'error', text: duplicate ? '이미 사용 중인 닉네임이에요. 다른 이름을 적어 주세요.' : notice.title, field: duplicate })
    setPrompt(duplicate ? { text: '앗, 누가 벌써 쓰고 있어요!', error: true } : { text: '어떻게 불러드릴까요?', error: false })
    setFeedback(notice)
  }

  async function goNext() {
    if (!checked) {
      setFeedback({ title: '닉네임 중복 확인이 필요해요.', body: '닉네임을 입력하고 ‘중복 확인’을 눌러 주세요.', info: true })
      return
    }
    router.push('/onboarding/extension/')
  }

  return (
    <OnboardingShell
      currentStep={0}
      prompt={prompt.text}
      promptError={prompt.error}
      feedback={feedback}
      next={{ label: '다음', blocked: !checked, onClick: () => { void goNext() } }}
    >
      <label className="field-label" htmlFor="nickname">닉네임</label>
      <div className="name-row">
        <div className="input-wrap">
          <input
            className={`text-input${hint.kind && hint.field !== false ? ` is-${hint.kind}` : ''}`}
            id="nickname"
            type="text"
            autoComplete="off"
            maxLength={12}
            placeholder="2~12자"
            value={nickname}
            aria-describedby="nickname-hint"
            aria-invalid={hint.kind === 'error' && hint.field !== false}
            onChange={(event) => {
              const value = event.target.value
              setNickname(value)
              setChecked(false)
              setFeedback(null)
              setHint(/[^가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]/u.test(value) ? { kind: 'error', text: '특수문자와 공백은 쓸 수 없어요.' } : defaultHint)
            }}
            onKeyDown={(event) => {
              if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
              if (checked) void goNext()
              else void check()
            }}
          />
          <span className="counter" aria-hidden="true">{nickname.length}/12</span>
        </div>
        <button className="btn btn-ghost check-button" type="button" disabled={checking} onClick={() => { void check() }}>
          {checking ? '확인 중…' : '중복 확인'}
        </button>
      </div>
      <p className={`hint${hint.kind ? ` is-${hint.kind}` : ''}`} id="nickname-hint" aria-live="polite">{hint.text}</p>
    </OnboardingShell>
  )
}
