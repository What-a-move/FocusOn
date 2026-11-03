import { errorText } from './error-text'

// 원인별 아이콘. 포키는 한 화면에 한 번만 나오도록 오류 화면에는 쓰지 않는다.
const ICONS = {
  // 인터넷 끊김
  offline: <path d="M2 8.8a15 15 0 0 1 4.2-2.6M9.9 5.1A15 15 0 0 1 22 8.8M5 12.6a10 10 0 0 1 3.6-2.2M15 10.4a10 10 0 0 1 4 2.2M8.5 16.4a5 5 0 0 1 7 0M12 20h.01M3 3l18 18" />,
  // 아직 준비 중(서버에 없는 기능)
  pending: <><path d="M6 3h12M6 21h12M7 3v3.5a5 5 0 0 0 2.4 4.3L12 12l2.6 1.2A5 5 0 0 1 17 17.5V21M17 3v3.5a5 5 0 0 1-2.4 4.3M7 21v-3.5a5 5 0 0 1 2.4-4.3" /></>,
  // 그 밖의 실패
  failed: <><circle cx="12" cy="12" r="9" /><path d="M12 7.5v5M12 16h.01" /></>,
}
const iconOf = (code: string) => (code === 'LOCAL_NETWORK_UNAVAILABLE' ? ICONS.offline : code === 'NOT_FOUND' ? ICONS.pending : ICONS.failed)

/**
 * 불러오기 실패 화면. 빨간 상자 대신 원인 아이콘 + 무엇을 못 불러왔는지 + 원인·할 일 + 다시 시도를 가운데에 쌓는다.
 * compact는 홈의 한 칸처럼 좁은 자리에 쓰는 작은 크기다. 부모가 공간을 주면 그 가운데에 놓인다.
 */
export function LoadError({ code, what, onRetry, compact = false }: { code: string; what: string; onRetry: () => void; compact?: boolean }) {
  return <div className={`fo-error-state${compact ? ' is-compact' : ''}`} role="alert">
    <span className="fo-error-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{iconOf(code)}</svg>
    </span>
    <strong>{what} 불러오지 못했어요</strong>
    <span className="fo-error-copy">{errorText({ code })}</span>
    <button className="fo-error-retry" type="button" onClick={onRetry}>다시 시도</button>
  </div>
}
