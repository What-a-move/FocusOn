import { useRef, useState, type RefObject } from 'react'
import { goalApi, goalLength } from '../../shared/api/goal-api'
import { sessionApi, type SessionSnapshot } from '../../shared/api/session-api'
import { errorText } from '../../shared/lib/error-text'
import { useOpenGoals } from './useOpenGoals'

export function AddSessionGoal({ session, onClose, submittingRef }: {
  session: SessionSnapshot; onClose: () => void; submittingRef: RefObject<boolean>
}) {
  const { goals, nextCursor, loading, failed, load } = useOpenGoals()
  const [selected, setSelected] = useState('')
  const [goalText, setGoalText] = useState('')
  const [saved, setSaved] = useState<{ goalId: string; goalText: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState('')
  const createAttempt = useRef<{ goalText: string; key: string } | null>(null)
  const remaining = goals.filter((goal) => !session.goals?.some(({ goalId }) => goalId === goal.goalId))

  const add = async () => {
    const goalVersion = session.goalVersion
    if (submittingRef.current || session.status !== 'RUNNING' || !goalVersion || (session.goals?.length ?? 3) >= 3) return
    const text = goalText.trim()
    if (!selected && !saved && (goalLength(text) < 2 || goalLength(text) > 40)) return

    submittingRef.current = true
    setBusy(true)
    setFailure('')
    try {
      let id = selected || saved?.goalId
      if (!id) {
        if (createAttempt.current?.goalText !== text) createAttempt.current = { goalText: text, key: crypto.randomUUID() }
        const created = await goalApi.create({ goalText: text, confirmationSource: 'MANUAL_CONFIRMED', idempotencyKey: createAttempt.current.key })
        if (!created.success) { setFailure(errorText(created.error)); return }
        setSaved(created.data)
        id = created.data.goalId
      }
      const result = await sessionApi.addGoal({ sessionId: session.sessionId, goalId: id, goalVersion })
      if (result.success) onClose()
      else setFailure(result.error.code === 'SESSION_VERSION_CONFLICT'
        ? '할 일 목록이 바뀌었어요. 확인한 뒤 다시 추가해 주세요.'
        : errorText(result.error))
    } finally {
      setBusy(false)
      submittingRef.current = false
    }
  }

  return <form className="add-goal-form" onSubmit={(event) => { event.preventDefault(); void add() }}>
    <h2 id="dialog-title">세션에 할 일 추가</h2>
    <p>{session.goals?.length ?? 0}/3개를 담았어요.</p>
    {saved ? <p>{saved.goalText}</p> : <>
      <label htmlFor="add-existing-goal">저장한 할 일</label>
      <select id="add-existing-goal" value={selected} disabled={busy || remaining.length === 0}
        onChange={(event) => { setSelected(event.target.value); setFailure('') }}>
        <option value="">선택해 주세요</option>
        {remaining.map((goal) => <option key={goal.goalId} value={goal.goalId}>{goal.goalText}</option>)}
      </select>
      {loading && <p role="status">할 일을 불러오고 있어요.</p>}
      {failed && <p role="alert">할 일 목록을 불러오지 못했어요. <button className="btn btn-ghost" type="button" onClick={() => { void load(nextCursor ?? undefined) }}>다시 시도</button></p>}
      {!failed && nextCursor && <button className="btn btn-ghost" type="button" disabled={loading || busy} onClick={() => { void load(nextCursor) }}>할 일 더 보기</button>}
      <label htmlFor="add-new-goal">새 할 일</label>
      <input id="add-new-goal" type="text" maxLength={40} placeholder="2~40자로 적어 주세요" value={goalText}
        disabled={busy || !!selected} onChange={(event) => { setGoalText(event.target.value); setFailure('') }} />
    </>}
    {failure && <p className="dialog-error" role="alert">{failure}</p>}
    <div className="dialog-actions"><div className="row">
      <button className="btn btn-ghost" type="button" disabled={busy} onClick={onClose}>취소</button>
      <button className="btn btn-primary" type="submit" disabled={busy || session.status !== 'RUNNING'
        || (session.goals?.length ?? 3) >= 3 || (!selected && !saved && goalLength(goalText) < 2)}>{busy ? '추가하는 중…' : '추가하기'}</button>
    </div></div>
  </form>
}
