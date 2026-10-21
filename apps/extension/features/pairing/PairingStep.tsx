'use client'

import { useState, type FormEvent } from 'react'
import './pairing.css'

function formatPairingCode(value: string) {
  const normalized = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6)
  return normalized.length > 3
    ? `${normalized.slice(0, 3)}-${normalized.slice(3)}`
    : normalized
}

function isPairingCodeValid(value: string) {
  return /^[A-Z0-9]{3}-[A-Z0-9]{3}$/u.test(value)
}

export function PairingStep() {
  const [code, setCode] = useState('')
  const [message, setMessage] = useState('')
  const [hasError, setHasError] = useState(false)
  const isValid = isPairingCodeValid(code)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!isValid) {
      setHasError(true)
      setMessage('ABC-123 형식으로 코드를 모두 입력해 주세요.')
      return
    }

    setHasError(false)
    setMessage('코드 형식이 맞아요.')
  }

  return (
    <main className="pairing-stage">
      <section className="pairing-popup" aria-label="FocusOn 연결">
        <header className="p-head">
          <img src="/design/focuson-logo.png" alt="FocusOn" />
          <span className="conn" data-tone="none">연결 전</span>
        </header>

        <div className="p-body">
          <div className="center">
            <img className="poki" src="/design/poki-floating-master-v3.png" alt="포키" />
            <h1>데스크톱 앱과 연결해요</h1>
          </div>
          <p className="center muted">앱의 온보딩 화면에 표시된 코드를 입력해 주세요.</p>

          <form onSubmit={handleSubmit} noValidate>
            <label className="sr-only" htmlFor="pairing-code">연결 코드</label>
            <input
              className="code"
              id="pairing-code"
              name="pairingCode"
              inputMode="text"
              autoComplete="one-time-code"
              autoCapitalize="characters"
              maxLength={7}
              value={code}
              onChange={(event) => {
                setCode(formatPairingCode(event.target.value))
                setMessage('')
                setHasError(false)
              }}
              placeholder="ABC-123"
              aria-invalid={hasError}
              aria-describedby="pairing-message"
            />
            <p className={`pairing-message${hasError ? ' is-error' : ''}`} id="pairing-message" aria-live="polite">
              {message}
            </p>
            <button className="btn btn-primary" type="submit" disabled={!isValid}>연결하기</button>
          </form>

          <p className="small muted pairing-help">코드는 5분 동안 한 번만 쓸 수 있어요.</p>
        </div>

        <footer className="p-foot">
          <span className="text-btn">FocusOn 앱에서 연결 코드를 확인해 주세요 ↗</span>
        </footer>
      </section>
    </main>
  )
}
