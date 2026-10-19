export type DesktopTarget = {
  pid: number; bundleId: string; appName: string; windowTitle: string;
  x: number; y: number; width: number; height: number;
};
export type AcquisitionContext = {
  sessionId: string; sessionStatus: string; resumeRequired: boolean;
  version: number; validUntil: number; privacyBlocked: boolean;
  excludedBundleIds: string[]; ownBundleIds: string[];
};
export type DesktopSource = 'APP_META' | 'DESKTOP_APP_OCR';
export type DesktopObservation = {
  sessionId: string; clientRunId: string; sequence: number; navigationId: string;
  observedAt: string; isActiveTarget: true; contentSource: DesktopSource;
  contentType: 'DOCUMENT'; appName: string; windowTitle: string; pageTitle: string;
  urlHost: ''; text: string; extractionStatus: 'SUCCESS'; isSensitive: false; isExcluded: false;
  ocr?: { confidence: number; captureStatus: 'SUCCESS' };
};
export type AcquisitionDecision = {
  clientRunId: string; navigationId: string; contentSource: string;
  relation: string; reasonCode: string;
};
