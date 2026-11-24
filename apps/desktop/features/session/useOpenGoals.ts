import { useCallback, useEffect, useRef, useState } from 'react'
import type { CreatedGoal } from '../../electron/shared/goal-contract.cjs'
import { goalApi } from '../../shared/api/goal-api'

export function useOpenGoals(enabled = true) {
  const [goals, setGoals] = useState<CreatedGoal[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(enabled)
  const [failed, setFailed] = useState(false)
  const epoch = useRef(0)

  const load = useCallback(async (cursor?: string) => {
    const request = ++epoch.current
    setLoading(true)
    setFailed(false)
    const result = await goalApi.listOpen(cursor)
    if (epoch.current !== request) return
    setLoading(false)
    if (!result.success) { setFailed(true); return }
    const { items, nextCursor: next } = result.data
    setGoals((previous) => cursor ? [...new Map([...previous, ...items].map((goal) => [goal.goalId, goal])).values()] : items)
    setNextCursor(next)
  }, [])

  useEffect(() => {
    if (!enabled) return
    let active = true
    queueMicrotask(() => { if (active) void load() })
    return () => { active = false; epoch.current += 1 }
  }, [enabled, load])

  return { goals, nextCursor, loading, failed, load }
}
