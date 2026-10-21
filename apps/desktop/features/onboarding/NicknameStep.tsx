'use client'

import { useState, type FormEvent } from 'react'
import { OnboardingShell } from './OnboardingShell'

function getNicknameError(nickname: string): string | null {
  if (!nickname) return '닉네임을 입력해줘.'
  if (nickname.length < 2 || nickname.length > 12) return '닉네임은 2~12자로 입력해줘.'
  if (!/^[가-힣A-Za-z0-9]+$/u.test(nickname)) return '한글, 영문, 숫자만 사용할 수 있어.'
  return null
}

export function NicknameStep() {
  const [nickname, setNickname] = useState('')
  const [message, setMessage] = useState('')
  const [hasError, setHasError] = useState(false)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const error = getNicknameError(nickname)

    if (error) {
      setHasError(true)
      setMessage(error)
      return
    }

    setHasError(false)
    setMessage('닉네임 입력 형식이 맞아.')
  }

  function handleAvailabilityCheck() {
    const error = getNicknameError(nickname)
    if (error) {
      setHasError(true)
      setMessage(error)
      return
    }

    setHasError(false)
    setMessage('입력 형식은 괜찮아. 중복 여부는 아직 확인하지 않았어.')
  }

  return (
    <OnboardingShell
      currentStep={0}
      prompt="어떻게 불러드릴까요?"
      footer={<button className="btn btn-primary next" type="submit" form="nickname-form" disabled>다음</button>}
    >
      <form id="nickname-form" onSubmit={handleSubmit} noValidate>
        <label className="field-label" htmlFor="nickname">닉네임</label>
        <div className="name-row">
          <div className="input-wrap">
            <input
              className={`text-input${hasError ? ' is-error' : ''}`}
              id="nickname"
              name="nickname"
              type="text"
              autoComplete="nickname"
              value={nickname}
              maxLength={12}
              aria-describedby="nickname-hint"
              aria-invalid={hasError}
              onChange={(event) => {
                setNickname(event.target.value)
                setMessage('')
                setHasError(false)
              }}
              placeholder="2~12자"
            />
            <span className="counter" aria-hidden="true">{nickname.length}/12</span>
          </div>
          <button className="btn btn-ghost check-button" type="button" onClick={handleAvailabilityCheck}>중복 확인</button>
        </div>
        <p
          className={`hint${hasError ? ' is-error' : message ? ' is-success' : ''}`}
          id="nickname-hint"
          aria-live="polite"
        >
          {message || '2~12자 · 특수문자 제외'}
        </p>
      </form>
    </OnboardingShell>
  )
}
