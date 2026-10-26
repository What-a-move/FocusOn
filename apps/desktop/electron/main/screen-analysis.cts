// 관측 1건을 보내고, 결과가 애매하면 지금 보는 창의 글자로 한 번 더 보낸다 (화면 분석 5단계, 결정 024).
import type { AnalysisRun } from './analysis-sender.cjs'
import { cleanOcrText } from './ocr-text.cjs'
import type { Observation } from './screen-observer.cjs'

type Deps = {
  send(sessionId: string, observation: Observation): Promise<AnalysisRun | null>
  /** 창 하나를 캡처해 읽은 글자. 권한이 없거나 실패하면 null. */
  ocr(windowId: number): Promise<string | null>
  /** 아직 같은 화면(key)이고 같은 세션이 RUNNING인지. */
  stillShowing(key: string, sessionId: string): boolean
  now(): Date
}

/**
 * 1) 관측을 보낸다. 2) 앱 정보(APP_META) 결과가 관련 없음·판단 보류이고 아직 같은 화면이면 OCR로 다시 보낸다.
 * 서버는 앱 정보만으로 나온 UNRELATED를 이탈로 세지 않으므로(AI 분석 계약 4.4), 이 단계가 있어야 앱에서의 이탈을 판단한다.
 * [주의] 응답·OCR을 기다리는 사이 다른 화면으로 옮겼으면 늦은 결과로 다음 단계를 보내지 않는다(계약 8).
 */
export async function analyzeObservation(deps: Deps, sessionId: string, observation: Observation): Promise<void> {
  const run = await deps.send(sessionId, observation)
  if (!run || observation.guardStatus || observation.contentSource !== 'APP_META' || observation.windowId === null) return
  if (run.relation !== 'UNRELATED' && run.relation !== 'UNCERTAIN') return
  if (!deps.stillShowing(observation.key, sessionId)) return
  const raw = await deps.ocr(observation.windowId)
  if (raw === null || !deps.stillShowing(observation.key, sessionId)) return
  const cleaned = cleanOcrText(raw)
  const observedAt = deps.now().toISOString()
  if (cleaned.sensitive) {
    await deps.send(sessionId, { observedAt, guardStatus: 'PRIVACY_BLOCKED', contentSource: 'DESKTOP_APP_OCR' })
    return
  }
  if (!cleaned.text) return
  await deps.send(sessionId, { observedAt, guardStatus: null, contentSource: 'DESKTOP_APP_OCR', metadata: observation.metadata, text: cleaned.text })
}
