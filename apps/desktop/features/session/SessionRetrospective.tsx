'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import type { RetrospectiveInput } from '../../electron/shared/report-contract.cjs'
import { sessionResultApi } from '../../shared/api/session-result-api'
import { errorText } from '../../shared/lib/error-text'
import '../../shared/styles/onboarding-window.css'
import './session-retrospective.css'

const RATINGS = { GOOD: '좋았어', OKAY: '그럭저럭', BAD: '별로였어' } as const
const REASONS = {
  '내 상태': [['SELF_TIRED', '피곤했어요'], ['SELF_NOISE', '시끄러웠어요'], ['SELF_TOO_MANY_GOALS', '할 일이 너무 많았어요']],
  '앱 동작': [['APP_FALSE_ALERT', '잘못된 알림'], ['APP_TOO_FREQUENT', '알림이 잦았어요'], ['APP_LATE_ALERT', '알림이 늦었어요'], ['APP_BLUR_ANNOYING', '흐림이 불편했어요']],
} as const
type Reason = NonNullable<RetrospectiveInput['reasonCodes']>[number]

export function SessionRetrospective() {
  const router = useRouter()
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [rating, setRating] = useState<RetrospectiveInput['rating'] | null>(null)
  const [reasons, setReasons] = useState<Reason[]>([])
  const [note, setNote] = useState('')
  const [failure, setFailure] = useState({ message: '', sensitive: false })
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)

  useEffect(() => {
    const id = new URLSearchParams(window.location.search).get('session')
    if (!id) { router.replace('/'); return }
    queueMicrotask(() => setSessionId(id))
  }, [router])

  function next() {
    if (sessionId) router.replace(`/todo-done/?session=${encodeURIComponent(sessionId)}`)
  }

  async function save() {
    if (saving.current || !sessionId || !rating) return
    saving.current = true
    setBusy(true)
    setFailure({ message: '', sensitive: false })
    const result = await sessionResultApi.saveRetrospective({
      sessionId, rating, ...(rating === 'BAD' ? { reasonCodes: reasons, note: note.trim() } : {}),
    })
    if (result.success) { next(); return }
    const sensitive = result.error.code === 'RETROSPECTIVE_NOTE_SENSITIVE'
    setFailure({ sensitive, message: sensitive ? '개인정보를 지운 뒤 다시 저장해 주세요.' : errorText(result.error) })
    saving.current = false
    setBusy(false)
  }

  return <main className="app-window onboarding-window retrospective-window">
    <form className="flow" aria-labelledby="retrospective-heading" onSubmit={(event) => { event.preventDefault(); void save() }}>
      <div className="body">
        <div className="retro-content">
          <header><Image src="/design/poki-celebrate.png" width={88} height={88} alt="" /><h1 id="retrospective-heading">이번 집중은 어땠나요?</h1></header>
          <fieldset disabled={busy} className="retro-ratings">
            <legend className="retro-visually-hidden">이번 집중 평가</legend>
            {(Object.keys(RATINGS) as Array<keyof typeof RATINGS>).map((value) =>
              <label key={value}><input type="radio" name="rating" value={value} checked={rating === value} onChange={() => { setRating(value); setFailure({ message: '', sensitive: false }) }} /><span>{RATINGS[value]}</span></label>)}
          </fieldset>
          {rating === 'BAD' && <div className="retro-reasons">
            <h2>어떤 점이 아쉬웠나요?</h2>
            {Object.entries(REASONS).map(([group, choices]) => <fieldset disabled={busy} key={group}>
              <legend>{group}</legend>
              {choices.map(([code, label]) => <label key={code}>
                <input type="checkbox" checked={reasons.includes(code)} onChange={(event) => {
                  const checked = event.target.checked
                  setReasons((current) => checked ? [...current, code] : current.filter((reason) => reason !== code))
                }} />{label}
              </label>)}
            </fieldset>)}
            <label htmlFor="retrospective-note">더 남길 말 (선택)</label>
            <textarea id="retrospective-note" maxLength={100} disabled={busy} value={note}
              aria-invalid={failure.sensitive} aria-describedby={failure.sensitive ? 'retrospective-error' : undefined}
              onChange={(event) => { setNote(event.target.value); setFailure({ message: '', sensitive: false }) }} />
            <small>{note.length}/100</small>
          </div>}
          {failure.message && <p id="retrospective-error" role="alert">회고를 저장하지 못했어요. {failure.message}</p>}
        </div>
      </div>
      <footer className="footer"><div className="footer-inner">
        <button className="btn btn-ghost" type="button" disabled={!sessionId || busy} onClick={next}>건너뛰기</button>
        <button className="btn btn-primary" type="submit" disabled={!sessionId || !rating || busy}>{busy ? '저장 중…' : failure.message ? '다시 저장' : '다음'}</button>
      </div></footer>
    </form>
  </main>
}
