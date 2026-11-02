'use client'

import { useEffect, useState } from 'react'
import { sessionApi, type SessionSnapshot } from './session-api'

/**
 * 진행 중인(끝나지 않은) 집중. 앱을 다시 켰으면 여기서 서버의 활성 세션을 이어받는다(Main이 APP_RESTART로 멈춘다).
 * 홈·사이드바가 집중 화면으로 돌아갈 길을 보여 줄 때 쓴다.
 */
export function useOngoingSession(): SessionSnapshot | null {
  const [snapshot, setSnapshot] = useState<SessionSnapshot | null>(null)
  useEffect(() => {
    const unsubscribe = sessionApi.onChanged(setSnapshot)
    void sessionApi.getCurrent().then((result) => { if (result.success) setSnapshot(result.data) })
    return unsubscribe
  }, [])
  return snapshot && snapshot.status !== 'ENDED' && snapshot.status !== 'ABANDONED' ? snapshot : null
}
