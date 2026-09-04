'use client'

import { useState } from 'react'

export default function Page() {
  const [isStudying, setIsStudying] = useState(false)

  return (
    <main className="focus-on-shell">
      <header className="brand-header">
        <div className="brand-mark" aria-hidden="true">·</div>
        <div>
          <p className="eyebrow">FocusOn</p>
          <h1>집중을 켜는 시간</h1>
        </div>
      </header>

      <section className="study-card" aria-labelledby="study-title">
        <div className="status-row">
          <span className={`status-dot ${isStudying ? 'is-active' : ''}`} />
          <span>{isStudying ? '학습 세션 진행 중' : '학습 세션 준비됨'}</span>
        </div>
        <h2 id="study-title">오늘의 학습 목표를 설정해보자</h2>
        <p className="muted-text">화면과 학습 흐름을 기록해서 집중 습관을 확인할 수 있어.</p>
        <button type="button" className="primary-button" onClick={() => setIsStudying((current) => !current)}>
          {isStudying ? '학습 세션 일시정지' : '학습 세션 시작'}
        </button>
      </section>

      <section className="summary-grid" aria-label="FocusOn 준비 상태">
        <article className="summary-item"><span className="summary-label">목표</span><strong>아직 설정되지 않음</strong></article>
        <article className="summary-item"><span className="summary-label">타이머</span><strong>00:00:00</strong></article>
        <article className="summary-item"><span className="summary-label">분석</span><strong>대기 중</strong></article>
      </section>
    </main>
  )
}
