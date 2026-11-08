import Image from 'next/image'
import { errorText } from '../lib/error-text'

// 원인별 포키. 같은 포키가 모든 오류에 반복되지 않게 원인마다 다르게 쓴다. 아이콘은 쓰지 않는다.
// 찾지 못함은 두리번, 그 밖의 실패(오프라인 포함)는 걱정 포키.
const pokiOf = (code: string) => code === 'NOT_FOUND' ? 'poki-looking.png' : 'poki-worried.png'

/**
 * 불러오기 실패 화면. 포키 + 무엇을 못 불러왔는지 + 원인·할 일 + 다시 시도를 가운데에 쌓는다.
 * 한 화면에 포키는 하나만 둔다. 이미 포키가 있는 화면(홈 카드·설정 머리)과 좁은 칸(compact)에서는 포키 없이 글자만 둔다.
 */
export function LoadError({ code, what, onRetry, compact = false, poki = !compact }: { code: string; what: string; onRetry: () => void; compact?: boolean; poki?: boolean }) {
  return <div className={`fo-error-state${compact ? ' is-compact' : ''}`} role="alert">
    {poki && <Image className="fo-error-poki" src={`/design/${pokiOf(code)}`} alt="" width={160} height={160} />}
    <strong>{what} 불러오지 못했어요.</strong>
    <span className="fo-error-copy">{errorText({ code })}</span>
    <button className="fo-error-retry" type="button" onClick={onRetry}>다시 시도</button>
  </div>
}
