'use client'

import { useEffect, useState, type FormEvent } from 'react'
import { isLinked } from './extension-auth'
import { describePairingError, submitPairing, type PairingNotice } from './pairing-api'
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

/** onLinked가 있으면 연결 완료 뒤 세션 화면으로 넘어갈 수 있다. initialNotice는 연결이 해제돼 다시 연결해야 할 때의 안내다. */
export function PairingStep({ onLinked, initialNotice = null }: { onLinked?: () => void; initialNotice?: PairingNotice | null } = {}) {
  const [code, setCode] = useState('')
  const [notice, setNotice] = useState<PairingNotice | null>(initialNotice)
  const [submitting, setSubmitting] = useState(false)
  const [linked, setLinked] = useState(false)
  const isValid = isPairingCodeValid(code)

  useEffect(() => {
    // [유지보수] 이미 연결된 브라우저면 입력 화면 대신 연결 완료 화면을 보여 준다(다시 연결 안내 중이면 입력 화면 유지).
    if (initialNotice) return
    void isLinked().then(setLinked).catch(() => setLinked(false))
  }, [initialNotice])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!isValid || submitting) return
    setSubmitting(true)
    setNotice(null)
    const result = await submitPairing(code)
    setSubmitting(false)
    if (result.success) {
      setLinked(true)
      return
    }
    setNotice(describePairingError(result.error))
  }

  return (
    <main className="pairing-stage">
      <section className="pairing-popup" aria-label="FocusOn 연결">
        <header className="p-head">
          <img src="/design/focuson-logo.png" alt="FocusOn" />
          <span className="conn" data-tone={linked ? 'ok' : 'none'}>{linked ? '연결됨' : '연결 전'}</span>
        </header>

        {linked ? (
          <div className="p-body" role="status">
            <div className="center">
              <img className="poki" src="/design/poki-floating-cheer-v7.png" alt="응원하는 포키" />
              <h1>연결됐어요!</h1>
            </div>
            <p className="center muted">데스크톱 앱으로 돌아가 다음 단계를 진행해 주세요.</p>
            {onLinked ? <p className="center"><button className="text-btn" type="button" onClick={onLinked}>집중 상태 보기</button></p> : null}
          </div>
        ) : (
          <div className="p-body">
            <div className="center">
              <img className="poki" src="/design/poki-floating-master-v3.png" alt="포키" />
              <h1>데스크톱 앱과 연결해요</h1>
            </div>
            <p className="center muted">앱의 온보딩 화면에 표시된 코드를 입력해 주세요.</p>

            <form onSubmit={(event) => { void handleSubmit(event) }} noValidate>
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
                  setNotice(null)
                }}
                placeholder="ABC-123"
                aria-invalid={Boolean(notice)}
                aria-describedby={notice ? 'pairing-notice' : undefined}
                disabled={submitting}
              />
              {notice ? (
                <div className="notice is-error" id="pairing-notice" role="alert">
                  <strong>{notice.title}</strong>
                  {notice.lines.map((line) => <span key={line}>{line}</span>)}
                </div>
              ) : null}
              <button className={`btn btn-primary${submitting ? ' is-loading' : ''}`} type="submit" disabled={!isValid || submitting}>
                {submitting ? '연결하는 중' : '연결하기'}
              </button>
            </form>

            <p className="small muted pairing-help">코드는 5분 동안 한 번만 쓸 수 있어요.</p>
          </div>
        )}
      </section>
    </main>
  )
}
