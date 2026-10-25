'use client'

import { useCallback, useEffect, useState } from 'react'
import { isLinked } from '../pairing/extension-auth'
import { PairingStep } from '../pairing/PairingStep'
import { SessionPopup } from './SessionPopup'

const RELINK_NOTICE = { title: '연결이 해제됐어요', lines: ['데스크톱 앱에서 새 코드를 받아', '다시 연결해 주세요.'] }

/** 팝업 첫 화면: 연결 전이면 연결 코드(E01), 연결됐으면 집중 상태(E02·E03·E06). */
export function Popup() {
  const [view, setView] = useState<'checking' | 'pairing' | 'relink' | 'session'>('checking')

  useEffect(() => {
    void isLinked().then((linked) => setView(linked ? 'session' : 'pairing')).catch(() => setView('pairing'))
  }, [])

  const relink = useCallback(() => setView('relink'), [])
  if (view === 'checking') return null
  if (view === 'session') return <SessionPopup onRelink={relink} />
  return <PairingStep key={view} initialNotice={view === 'relink' ? RELINK_NOTICE : null} onLinked={() => setView('session')} />
}
