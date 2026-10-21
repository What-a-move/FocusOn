import type { ReactNode } from 'react'
import Link from 'next/link'
import './onboarding.css'

type OnboardingShellProps = {
  children: ReactNode
  currentStep: number
  prompt: string
  footer: ReactNode
  backHref?: string
  onBack?: () => void
}

const stepCount = 4

export function OnboardingShell({
  children,
  currentStep,
  prompt,
  footer,
  backHref,
  onBack,
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
              <i className={index <= currentStep ? 'active' : ''} key={index} />
            ))}
          </div>
          <span className="step-count" aria-live="polite">{currentStep + 1} / {stepCount}</span>
        </div>

        <div className="body">
          <section className="step" aria-labelledby="onboarding-prompt">
            <div className="speaker">
              <img className="poki" src="/design/poki-floating-logo-base-v1.png" alt="포키" />
              <p className="bubble" id="onboarding-prompt">{prompt}</p>
            </div>
            <div className="panel">{children}</div>
          </section>
        </div>

        <footer className="footer">
          <div className="footer-inner">
            <span className="spacer" />
            {footer}
          </div>
        </footer>
      </div>
    </main>
  )
}
