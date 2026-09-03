export type Relevance = "RELATED" | "SUPPORTIVE" | "DISTRACTING" | "UNKNOWN";

export interface PageInfo {
    title: string;
    url: string;
    text?: string;
}

export interface AnalysisResult {
    relevance: Relevance;
    confidence: number;
    reason: string;
}
