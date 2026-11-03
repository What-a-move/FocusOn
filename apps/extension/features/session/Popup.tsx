'use client'

import { useCallback, useEffect, useState } from 'react'
import { hasSiteAccess } from '../../extension/shared/site-access'
import { isLinked } from '../pairing/extension-auth'
import { PairingStep } from '../pairing/PairingStep'
import { SessionPopup } from './SessionPopup'
import { SiteAccessStep } from './SiteAccessStep'

const RELINK_NOTICE = { title: '연결이 해제됐어요', lines: ['데스크톱 앱에서 새 코드를 받아', '다시 연결해 주세요.'] }

/** 팝업 첫 화면: 연결 전이면 연결 코드(E01), 사이트 접근이 없으면 허용(E01b·E06), 모두 되면 집중 상태(E02·E03·E06). */
export function Popup() {
  const [view, setView] = useState<'checking' | 'pairing' | 'relink' | 'site' | 'session'>('checking')
  const afterLinked = useCallback(() => { void hasSiteAccess().then((granted) => setView(granted ? 'session' : 'site')) }, [])

  useEffect(() => {
    void isLinked().then((linked) => { if (linked) afterLinked(); else setView('pairing') }).catch(() => setView('pairing'))
  }, [afterLinked])

  const relink = useCallback(() => setView('relink'), [])
  if (view === 'checking') return null
  if (view === 'session') return <SessionPopup onRelink={relink} />
  if (view === 'site') return <SiteAccessStep onGranted={() => setView('session')} />
  return <PairingStep key={view} initialNotice={view === 'relink' ? RELINK_NOTICE : null} onLinked={afterLinked} />
}
