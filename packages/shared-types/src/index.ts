/**
 * Notion API 명세와 동일한 공개 분석 관계 상태다.
 *
 * 관련성(RELATED/UNRELATED)과 제외·개인정보 차단(EXCLUDED/PRIVACY_BLOCKED),
 * 판단 보류(UNCERTAIN)를 하나의 상태로 섞지 않고 명시적으로 구분한다.
 */
export type FocusState =
  | "RELATED"
  | "UNRELATED"
  | "UNCERTAIN"
  | "EXCLUDED"
  | "PRIVACY_BLOCKED";

export type AnalysisReasonCode =
    | "GOAL_RELATED"
    | "GOAL_UNRELATED"
    | "INSUFFICIENT_CONTEXT"
    | "AMBIGUOUS_CONTEXT";

export interface PageInfo {
    title: string;
    url: string;
    text?: string;
}

export interface AnalysisResult {
    relation: FocusState;
    relevanceScore: number;
    confidence: number;
    reasonCode: AnalysisReasonCode;
    reason: string;
}
