'use client'

import { useState } from 'react'
import { chromeLogStore } from './logger'

/**
 * 팝업 하단 `로그 복사` (FocusOn-design/extension-popup.html의 `.p-foot`·`.text-btn`).
 * 최근 기록(최대 500건)을 JSON으로 클립보드에 넣는다. 문제가 생기면 화면 설명 대신 이 기록을 공유한다.
 */
export function LogCopy() {
  const [result, setResult] = useState<'' | 'ok' | 'fail'>('')

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(JSON.stringify(await chromeLogStore.read(), null, 2))
      setResult('ok')
    } catch {
      setResult('fail')
    }
    setTimeout(() => setResult(''), 3000)
  }

  return <footer className="p-foot">
    <button className="text-btn" type="button" onClick={() => { void copy() }}>로그 복사</button>
    <span className={`small ${result === 'fail' ? 'is-fail' : 'muted'}`} role="status">
      {result === 'ok' ? '로그를 복사했어요' : result === 'fail' ? '로그를 복사하지 못했어요' : ''}
    </span>
  </footer>
}
