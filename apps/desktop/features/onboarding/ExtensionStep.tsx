import { OnboardingShell } from './OnboardingShell'

type ExtensionStepProps = {
  code?: string
  expiresAt?: string
  status?: 'unavailable' | 'waiting' | 'connected' | 'error'
}

export function ExtensionStep({
  code,
  expiresAt,
  status = 'unavailable',
}: ExtensionStepProps) {
  const characters = (code?.replace('-', '') ?? '').padEnd(6, ' ').slice(0, 6).split('')

  return (
    <OnboardingShell
      currentStep={1}
      prompt={status === 'connected' ? '연결됐어요! 잘하셨어요.' : 'Chrome이랑 연결해 볼까요?'}
      backHref="/onboarding/"
      footer={<button className="btn btn-primary next" type="button" disabled>다음</button>}
    >
      <p className="field-label">연결 코드</p>
      <div className={`code-tiles${status === 'connected' ? ' is-connected' : ''}${status === 'error' ? ' is-expired' : ''}`} aria-live="polite" aria-label="연결 코드">
        {characters.map((character, index) => (
          <span key={index}>{character.trim()}</span>
        )).flatMap((tile, index) => index === 2 ? [tile, <span className="dash" key="dash">-</span>] : [tile])}
      </div>

      <div className="code-meta">
        <div className="timer" aria-hidden="true"><i style={{ width: status === 'waiting' ? '100%' : status === 'connected' ? '100%' : '0%' }} /></div>
        <span className="countdown" aria-live="polite">
          {status === 'connected' ? '완료' : expiresAt ?? '--:--'}
        </span>
        <button className="link-button" type="button" disabled={status === 'unavailable'}>
          새 코드 받기
        </button>
      </div>

      {status === 'error' ? (
        <p className="hint is-error" role="alert">연결 상태를 확인하지 못했어. 잠시 후 다시 시도해줘.</p>
      ) : null}
      {status === 'connected' ? (
        <p className="hint is-success" role="status">Chrome Extension이 연결됐어.</p>
      ) : null}

      <ol className="steps-guide" aria-label="확장 프로그램 연결 방법">
        <li>
          <span className="step-number" aria-hidden="true">1</span>
          <span className="step-copy">
            <strong>FocusOn 아이콘 열기</strong>
            <span>Chrome 오른쪽 위에서 <b>FocusOn 아이콘</b>을 눌러.</span>
          </span>
        </li>
        <li>
          <span className="step-number" aria-hidden="true">2</span>
          <span className="step-copy">
            <strong>연결 코드 입력하기</strong>
            <span>위 코드를 <code>ABC-123</code> 형식 그대로 입력해.</span>
          </span>
        </li>
      </ol>
    </OnboardingShell>
  )
}
