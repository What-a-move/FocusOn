import { useRef, useState, type RefObject } from 'react'
import { goalApi, goalLength } from '../../shared/api/goal-api'
import { sessionApi, type SessionSnapshot } from '../../shared/api/session-api'
import { errorText } from '../../shared/lib/error-text'
import { useOpenGoals } from '../../shared/api/useOpenGoals'

// 디자인 원본: FocusOn-design/session.html `#add-dialog`
const norm = (text: string) => text.replace(/\s+/g, '').toLowerCase()
type Failure = { title: string; body?: string }

export function AddSessionGoal({ session, onClose, onAdded, submittingRef }: {
  session: SessionSnapshot; onClose: () => void; onAdded: () => void; submittingRef: RefObject<boolean>
}) {
  const { goals: openGoals } = useOpenGoals()
  const [text, setText] = useState('')
  const [replace, setReplace] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<Failure | null>(null)
  // 할 일은 만들었는데 세션에 붙이지 못했을 때, 다시 시도하면 같은 할 일을 다시 만들지 않는다.
  const saved = useRef<{ goalText: string; goalId: string; key: string } | null>(null)
  const current = session.goals ?? []
  const full = current.length >= 3

  const add = async () => {
    const goalVersion = session.goalVersion
    if (submittingRef.current || full || session.status !== 'RUNNING' || !goalVersion) return
    const value = text.trim()
    if (goalLength(value) < 2) return setFailure({ title: '2자 이상 적어 주세요.' })
    if (current.some((goal) => norm(goal.goalText) === norm(value))) return setFailure({ title: '이미 고른 할 일이에요.' })

    submittingRef.current = true
    setBusy(true)
    setFailure(null)
    try {
      // 적어 둔 할 일과 같은 글이면 새로 만들지 않고 그 할 일을 붙인다.
      let goalId = openGoals.find((goal) => norm(goal.goalText) === norm(value))?.goalId
      if (!goalId) {
        if (saved.current?.goalText !== value) saved.current = { goalText: value, goalId: '', key: crypto.randomUUID() }
        if (!saved.current.goalId) {
          const created = await goalApi.create({ goalText: value, confirmationSource: 'MANUAL_CONFIRMED', idempotencyKey: saved.current.key })
          if (!created.success) {
            return setFailure(created.error.code === 'GOAL_TEXT_INVALID'
              ? { title: '2~40자로 적어 주세요.' }
              : { title: '할 일을 추가하지 못했어요.', body: errorText(created.error) })
          }
          saved.current.goalId = created.data.goalId
        }
        goalId = saved.current.goalId
      }
      const result = await sessionApi.addGoal({ sessionId: session.sessionId, goalId, goalVersion })
      if (result.success) { onAdded(); return }
      setFailure({ title: '할 일을 추가하지 못했어요.', body: result.error.code === 'SESSION_VERSION_CONFLICT'
        ? '할 일 목록이 바뀌었어요. 확인한 뒤 다시 추가해 주세요.'
        : errorText(result.error) })
    } finally {
      setBusy(false)
      submittingRef.current = false
    }
  }

  const invalid = !!failure && !failure.body
  return <form className="add-goal-form" noValidate onSubmit={(event) => { event.preventDefault(); void add() }}>
    {/* eslint-disable-next-line @next/next/no-img-element */}
    <img className="poki" src="/design/poki-main-planning.png" alt="" />
    <h2 id="dialog-title">지금 하는 일을 할 일로 추가할까요?</h2>
    <div className="add-field">
      <label htmlFor="add-input">할 일</label>
      <input id="add-input" className="add-input" type="text" maxLength={40} autoComplete="off" autoFocus placeholder="예: 자료구조 과제 정리하기"
        value={text} disabled={busy} aria-invalid={invalid} aria-describedby={failure ? 'add-error' : undefined}
        onChange={(event) => { setText(event.target.value); setFailure(null) }}
        onKeyDown={(event) => { if (event.key === 'Enter' && (event.repeat || event.nativeEvent.isComposing)) event.preventDefault() }} />
      <span className="add-count">{goalLength(text)}/40</span>
    </div>
    {full && <fieldset className="add-field replace-field">
      <legend className="replace-head">할 일은 3개까지라 하나를 바꿔요</legend>
      <ul className="replace-list">
        {current.map((goal) => <li key={goal.goalId}><label>
          <input type="radio" name="replace" value={goal.goalId} checked={replace === goal.goalId} onChange={() => setReplace(goal.goalId)} />
          <span title={goal.goalText}>{goal.goalText}</span>
        </label></li>)}
      </ul>
      {/* shortcut: 서버에 세션 할 일 바꾸기 API가 없어 보기 전용, API가 생기면 바꾸기를 연결 */}
      <p className="replace-note">할 일 바꾸기는 아직 준비 중이에요.</p>
    </fieldset>}
    {failure && <div className="add-error" id="add-error" role="alert"><strong>{failure.title}</strong>{failure.body && <span>{failure.body}</span>}</div>}
    <div className="dialog-actions"><div className="row">
      <button className="btn btn-ghost" type="button" disabled={busy} onClick={onClose}>닫기</button>
      <button className="btn btn-primary" type="submit" disabled={busy || full || session.status !== 'RUNNING' || !text.trim()}>
        {busy ? '추가하는 중…' : full ? '바꾸기' : '추가'}
      </button>
    </div></div>
  </form>
}
