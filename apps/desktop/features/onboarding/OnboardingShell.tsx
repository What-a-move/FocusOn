import type { ReactNode } from 'react'
import Link from 'next/link'
import type { Notice } from './onboarding-api'
import './onboarding.css'

type OnboardingShellProps = {
  children: ReactNode
  /** 진행 바 칸 번호: 0 닉네임 · 1 Chrome 연결 · 2 권한·동의 · 3 제외 사이트 */
  currentStep: 0 | 1 | 2 | 3
  prompt: string
  promptError?: boolean
  poki?: string
  backHref?: string
  onBack?: () => void
  feedback?: Notice | null
  next: { label: string; blocked: boolean; busy?: boolean; onClick: () => void }
}

const stepCount = 4

export function OnboardingShell({
  children,
  currentStep,
  prompt,
  promptError = false,
  poki = '/design/poki-floating-logo-base-v1.png',
  backHref,
  onBack,
  feedback,
  next,
}: OnboardingShellProps) {
  return (
    <main className="app-window onboarding-window">
      <header className="titlebar">
        <div className="window-controls" aria-hidden="true"><i /><i /><i /></div>
      </header>

      <div className="flow">
        <div className="top">
          {onBack ? (
            <button className="back" type="button" aria-label="이전 화면" onClick={onBack}>←</button>
          ) : backHref ? (
            <Link className="back" href={backHref} aria-label="이전 단계">←</Link>
          ) : (
            <button className="back" type="button" aria-label="이전 단계" disabled>←</button>
          )}
          <div className="progress" role="progressbar" aria-label="온보딩 진행" aria-valuemin={1} aria-valuemax={stepCount} aria-valuenow={currentStep + 1}>
            {Array.from({ length: stepCount }, (_, index) => (
              <i className={index < currentStep ? 'done' : index === currentStep ? 'active' : ''} key={index} />
            ))}
          </div>
          <span className="step-count" aria-live="polite">{currentStep + 1} / {stepCount}</span>
        </div>

        <div className="body">
          <section className="step" aria-labelledby="onboarding-prompt">
            <div className="speaker">
              <img className="poki" src={poki} alt="포키" />
              {/* [디자인] 대사가 바뀔 때마다 말풍선을 다시 그려 살짝 떠오르게 한다. */}
              <p className={`bubble${promptError ? ' is-error' : ''}`} id="onboarding-prompt" key={prompt}>{prompt}</p>
            </div>
            <div className="panel">{children}</div>
          </section>
        </div>

        <footer className="footer">
          <div className="footer-inner">
            {feedback ? (
              <div className="feedback" role="alert" key={feedback.title}>
                <span className="feedback-icon" aria-hidden="true">!</span>
                <div><strong>{feedback.title}</strong><span>{feedback.body}</span></div>
              </div>
            ) : null}
            <span className="spacer" />
            {/* [유지보수] 조건이 부족해도 버튼은 눌리게 두고, 막힌 이유는 하단 바에서 알려 준다 (원본 HTML 동작). */}
            <button className={`btn btn-primary next${next.blocked ? ' is-blocked' : ''}`} type="button" onClick={next.onClick} disabled={next.busy}>
              {next.busy ? '저장하는 중…' : next.label}
            </button>
          </div>
        </footer>
      </div>
    </main>
  )
}
