import { useState } from 'react'
import './App.css'

function App() {
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
          <span>{isStudying ? '브라우저 학습 분석 중' : '브라우저 학습 준비됨'}</span>
        </div>
        <h2 id="study-title">현재 페이지를 학습 기록에 연결해보자</h2>
        <p className="muted-text">
          페이지 이동과 체류 흐름을 기록해서 집중 습관을 확인할 수 있어.
        </p>
        <button
          type="button"
          className="primary-button"
          onClick={() => setIsStudying((current) => !current)}
        >
          {isStudying ? '분석 일시정지' : '분석 시작'}
        </button>
      </section>

      <section className="summary-grid" aria-label="FocusOn 브라우저 상태">
        <article className="summary-item">
          <span className="summary-label">현재 페이지</span>
          <strong>확인 대기 중</strong>
        </article>
        <article className="summary-item">
          <span className="summary-label">학습 목표</span>
          <strong>설정되지 않음</strong>
        </article>
        <article className="summary-item">
          <span className="summary-label">분석 상태</span>
          <strong>{isStudying ? '진행 중' : '대기 중'}</strong>
        </article>
      </section>
    </main>
  )
}

export default App
