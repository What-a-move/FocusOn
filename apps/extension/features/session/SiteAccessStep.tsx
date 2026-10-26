'use client'

import { useEffect, useState } from 'react'
import { requestSiteAccess } from '../../extension/shared/site-access'
import { getActiveSession } from './session-api'
import { formatClock, type ActiveSession } from './session-model'
import '../pairing/pairing.css'
import './session-popup.css'

/**
 * E01b 사이트 접근 허용(안내·거부)과 E06 사이트 접근 꺼짐 (FocusOn-design/extension-popup.html).
 * 집중 중인데 권한이 없으면 꺼짐 화면으로 보여 준다. 세션은 서버가 이미 자동으로 멈췄다.
 */
export function SiteAccessStep({ onGranted }: { onGranted: () => void }) {
  const [denied, setDenied] = useState(false)
  const [session, setSession] = useState<ActiveSession | null>(null)

  useEffect(() => {
    void getActiveSession().then((result) => { if (result.success) setSession(result.data) })
  }, [])

  // [주의] Chrome 권한 창은 클릭 안에서 바로 열어야 한다. await 전에 다른 비동기 작업을 넣지 않는다.
  const request = async () => {
    if (await requestSiteAccess()) onGranted()
    else setDenied(true)
  }

  const revoked = session !== null && !['ENDED', 'ABANDONED'].includes(session.status)
  return <main className="pairing-stage">
    <section className="pairing-popup" aria-label="FocusOn">
      <header className="p-head">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/design/focuson-logo.png" alt="FocusOn" />
        <span className="conn" data-tone={revoked ? 'bad' : 'ok'}>{revoked ? '사이트 접근 꺼짐' : '연결됨'}</span>
      </header>
      <div className="p-body session-body">
        {revoked ? <>
          <div className="notice is-error" role="alert"><strong>사이트 접근 권한이 꺼졌어요</strong><span>그래서 세션이 자동으로 멈췄어요.</span><span>다시 허용한 뒤 직접 재개해 주세요.</span></div>
          <div>
            <span className="label">마지막 확정 남은 시간</span>
            <div className="timer-row">
              <span className="timer is-dim">{formatClock(session.targetDurationMs - session.accumulatedActiveMs)}</span>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img className="poki" src="/design/poki-floating-paused-v2.png" alt="쉬고 있는 포키" />
            </div>
          </div>
          <button className="btn btn-primary" type="button" onClick={() => { void request() }}>사이트 접근 다시 허용</button>
        </> : <>
          <h1>사이트 접근을 허용해 주세요</h1>
          {denied
            ? <div className="notice is-error" role="alert"><strong>사이트 접근을 허용하지 않았어요</strong><span>기기 연결은 그대로예요.</span><span>허용하기 전에는 집중을 시작할 수 없어요.</span></div>
            : <p className="muted">보고 있는 페이지가 목표와 맞는지 확인할 때 필요해요.</p>}
          {denied ? <p className="small muted">집중 중에만 지금 보는 탭 하나를 확인해요.</p> : <>
            <ul className="facts">
              <li><span className="mark yes" aria-hidden="true">✓</span><span><b>확인하는 것</b>집중 중, 지금 보는 탭 하나</span></li>
              <li><span className="mark no" aria-hidden="true">✕</span><span><b>확인하지 않는 것</b>다른 탭·시크릿 창·민감한 화면</span></li>
              <li><span className="mark no" aria-hidden="true">✕</span><span><b>저장하지 않는 것</b>화면 원본과 본문 원문</span></li>
            </ul>
            <p className="small muted">Chrome이 ‘모든 웹사이트 데이터 읽기’ 경고를 띄워요.<br />어느 학습 사이트든 확인하려면 필요한 범위예요.</p>
          </>}
          <button className="btn btn-primary" type="button" onClick={() => { void request() }}>{denied ? '다시 허용하기' : '사이트 접근 허용하기'}</button>
        </>}
      </div>
    </section>
  </main>
}
