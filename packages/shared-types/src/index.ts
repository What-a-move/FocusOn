export type FocusState = "FOCUSED" | "DISTRACTED" | "UNCERTAIN";

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
    state: FocusState;
    relevanceScore: number;
    confidence: number;
    reasonCode: AnalysisReasonCode;
    reason: string;
}
