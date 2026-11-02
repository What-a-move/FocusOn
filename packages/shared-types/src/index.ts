// Desktop·Chrome Extension이 함께 쓰는 서버 계약 타입 (Notion AI 분석 계약·API 명세·세션 상태 전이 정책 기준, 2026-10-26).
// [주의] 타입만 둔다. 이 패키지는 빌드 없이 TS 원본을 내보내므로, 앱에서는 `import type`으로만 가져온다.
// 값(배열·함수)을 넣으면 Desktop Main(.cjs)과 Extension `node --test`에서 불러오지 못한다(결정: Desktop 025·Extension 015).

/** 공개 분석 관계. AI 판정은 앞의 3개만 내고, EXCLUDED·PRIVACY_BLOCKED는 클라이언트 Privacy Guard가 AI 호출 전에 정한다. */
export type Relation = 'RELATED' | 'UNRELATED' | 'UNCERTAIN' | 'EXCLUDED' | 'PRIVACY_BLOCKED'

/** 관측을 어디서 어떻게 얻었는지. CHROME_VIEWPORT_OCR은 계약에만 있고 MVP에서는 쓰지 않는다(P1). */
export type ContentSource = 'APP_META' | 'TAB_META' | 'CHROME_DOM' | 'CHROME_VIEWPORT_OCR' | 'DESKTOP_APP_OCR'

/** 제외·민감 화면. 이 값이 있으면 metadata·contentPayload를 보내지 않는다. */
export type GuardStatus = 'EXCLUDED' | 'PRIVACY_BLOCKED'

/** AI가 돌려주는 판정 근거 코드(허용 목록). */
export type ReasonCode = 'SEMANTIC_GOAL_MATCH' | 'SEMANTIC_GOAL_MISMATCH' | 'INSUFFICIENT_CONTEXT' | 'AMBIGUOUS_CONTEXT' | 'ANALYSIS_UNAVAILABLE'

/** 서버 세션 상태(활성 5개 + 종료 2개). */
export type SessionStatus = 'RUNNING' | 'PAUSED' | 'AUTO_PAUSED' | 'AWAITING_END_CONFIRMATION' | 'ENDING' | 'ENDED' | 'ABANDONED'

/** 관측의 최소 메타데이터. Desktop은 appName·windowTitle, Chrome은 appName·pageTitle·urlHost를 쓴다. */
export type AnalysisMetadata = { appName?: string; windowTitle?: string | null; pageTitle?: string; urlHost?: string }

/** `POST /api/v1/sessions/{sessionId}/analysis-runs` 요청 본문. */
export type AnalysisRunRequest = {
  clientRunId: string
  contentSource: ContentSource
  observedAt: string
  /** 같은 세션·contentSource 안에서 1부터 늘어나는 순서 번호 */
  sequence: number
  guardStatus: GuardStatus | null
  metadata?: AnalysisMetadata
  /** 일회성 최소 텍스트. 저장·로그·큐에 남기지 않는다. */
  contentPayload?: { text: string }
}

/** `analysis-runs` 응답 중 클라이언트가 읽는 값. reasonCode는 ReasonCode 값이 오지만, 모르는 값이 와도 응답 자체는 받는다. */
export type AnalysisRunResponse = {
  analysisRunId: string
  relation: Relation
  /** 0~1. 판정이 없으면 null */
  confidence: number | null
  reasonCode: string | null
  observedAt: string
  policyVersion: string
}
