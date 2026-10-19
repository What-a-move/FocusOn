import { execFile } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import type { AcquisitionContext, AcquisitionDecision, DesktopObservation, DesktopSource, DesktopTarget } from '../shared/screen-acquisition.js';

type NativeData = { target?: DesktopTarget; text?: string; confidence?: number; imageHash?: string; screen?: boolean; accessibility?: boolean };
type NativeRequest = { operation: 'metadata' | 'ocr' | 'permissions'; expected?: DesktopTarget; previousImageHash?: string | null; excludedBundleIds?: string[]; ownBundleIds?: string[] };
type NativeCall = (request: NativeRequest, signal?: AbortSignal) => Promise<NativeData>;
const fallbackReasons = new Set(['METADATA_INSUFFICIENT', 'CONFLICTING_METADATA', 'EXTRACTION_FAILED', 'UNSUPPORTED']);
const sensitive = (text: string) => /[\w.+-]+@[\w.-]+\.[a-z]{2,}|\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9_]{16,}|eyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+)|\bBearer\s+\S+|(?:api[_ -]?key|password|비밀번호|인증\s*코드|OTP)\s*[:=]\s*\S+|\b(?:\d[ -]?){13,19}\b|\b01[016789][ -]?\d{3,4}[ -]?\d{4}\b/i.test(text);
export class ScreenAcquisitionError extends Error {
  constructor(public readonly code: string) { super(code); }
}
const nativePath = fileURLToPath(new URL('../native/focuson-screen-capture', import.meta.url));
const policyKey = (context: AcquisitionContext | null) => context === null ? '' : JSON.stringify({ ...context, validUntil: 0 });
const sameTarget = (a: DesktopTarget, b: DesktopTarget) => (['pid', 'bundleId', 'appName', 'windowTitle', 'x', 'y', 'width', 'height'] as const).every(key => a[key] === b[key]);
export const callNative: NativeCall = (request, signal) => new Promise((resolve, reject) => {
  if (process.platform !== 'darwin') { reject(new ScreenAcquisitionError('UNSUPPORTED_PLATFORM')); return; }
  const child = execFile(nativePath, [], { signal, timeout: 30_000, maxBuffer: 128_000, encoding: 'utf8' }, (error, stdout) => {
    if (error) { reject(new ScreenAcquisitionError('CAPTURE_UNAVAILABLE')); return; }
    try {
      const response = JSON.parse(stdout);
      if (!response.success) throw new ScreenAcquisitionError(/^[A-Z_]{1,64}$/.test(response.error?.code) ? response.error.code : 'CAPTURE_UNAVAILABLE');
      resolve(response.data);
    } catch (error) { reject(error instanceof ScreenAcquisitionError ? error : new ScreenAcquisitionError('CAPTURE_UNAVAILABLE')); }
  });
  child.stdin?.end(JSON.stringify(request));
});

export class DesktopScreenAcquisition {
  private epoch = 0;
  private busy = false;
  private context: AcquisitionContext | null = null;
  private last: { event: Pick<DesktopObservation, 'clientRunId' | 'navigationId' | 'contentSource'>; target: DesktopTarget } | null = null;
  private sequences: Partial<Record<DesktopSource, number>> = {};
  private imageHash: string | null = null;
  private cancellation: AbortController | null = null;
  constructor(private readonly native: NativeCall = callNative, private readonly now = Date.now) {}

  setContext(context: AcquisitionContext | null): void {
    if (policyKey(context) !== policyKey(this.context)) { this.epoch += 1; this.last = null; this.cancellation?.abort(); }
    if (this.context?.sessionId !== context?.sessionId) { this.sequences = {}; this.imageHash = null; }
    this.context = context === null ? null : structuredClone(context);
  }
  private policy(): AcquisitionContext {
    const context = this.context;
    if (!context || context.sessionStatus !== 'RUNNING' || context.resumeRequired !== false) throw new ScreenAcquisitionError('SESSION_INACTIVE');
    if (!context.sessionId || !Number.isSafeInteger(context.version) || context.version < 1 || !Number.isFinite(context.validUntil) || context.validUntil <= this.now() || context.validUntil > this.now() + 60_000 ||
        !Array.isArray(context.excludedBundleIds) || !Array.isArray(context.ownBundleIds) || context.ownBundleIds.length === 0 ||
        ![...context.excludedBundleIds, ...context.ownBundleIds].every(id => typeof id === 'string' && id.length > 0 && id.length <= 256)) throw new ScreenAcquisitionError('POLICY_UNAVAILABLE');
    if (context.privacyBlocked) throw new ScreenAcquisitionError('PRIVACY_BLOCKED');
    return context;
  }
  private target(data: NativeData, policy: AcquisitionContext): DesktopTarget {
    const target = data.target;
    if (!target || !Number.isInteger(target.pid) || target.pid <= 0 || !target.bundleId || typeof target.appName !== 'string' || typeof target.windowTitle !== 'string' ||
        ![target.x, target.y, target.width, target.height].every(Number.isFinite) || target.width <= 0 || target.height <= 0) throw new ScreenAcquisitionError('TARGET_UNAVAILABLE');
    if (policy.ownBundleIds.includes(target.bundleId)) throw new ScreenAcquisitionError('FOCUSON_UI_EXCLUDED');
    if (policy.excludedBundleIds.includes(target.bundleId)) throw new ScreenAcquisitionError('EXCLUDED');
    if (target.bundleId.startsWith('com.google.Chrome')) throw new ScreenAcquisitionError('CHROME_HANDLED_BY_EXTENSION');
    if (sensitive(target.appName + '\n' + target.windowTitle)) throw new ScreenAcquisitionError('PRIVACY_BLOCKED');
    return target;
  }
  private check(epoch: number): AcquisitionContext {
    const policy = this.policy();
    if (epoch !== this.epoch) throw new ScreenAcquisitionError('STALE_EVENT');
    return policy;
  }
  private async exclusive<T>(work: () => Promise<T>): Promise<T> {
    if (this.busy) throw new ScreenAcquisitionError('CAPTURE_BUSY');
    this.busy = true;
    this.cancellation = new AbortController();
    try { return await work(); }
    catch (error) { this.last = null; throw error; }
    finally { this.cancellation = null; this.busy = false; }
  }
  private signal(context: AcquisitionContext): AbortSignal {
    return AbortSignal.any([this.cancellation!.signal, AbortSignal.timeout(Math.max(1, context.validUntil - this.now()))]);
  }
  async observeMetadata(): Promise<DesktopObservation> {
    return this.exclusive(async () => {
      const epoch = this.epoch, policy = this.policy();
      const observedAt = new Date(this.now()).toISOString();
      const target = this.target(await this.native({ operation: 'metadata', excludedBundleIds: policy.excludedBundleIds, ownBundleIds: policy.ownBundleIds }, this.signal(policy)), policy);
      this.check(epoch);
      return this.observation(target, 'APP_META', '', randomUUID(), observedAt);
    });
  }
  async collectNext(decision: AcquisitionDecision): Promise<DesktopObservation | null> {
    return this.exclusive(async () => {
      const epoch = this.epoch, policy = this.policy(), last = this.last;
      if (!last) throw new ScreenAcquisitionError('METADATA_STAGE_REQUIRED');
      if (decision.clientRunId !== last.event.clientRunId || decision.navigationId !== last.event.navigationId || decision.contentSource !== last.event.contentSource) throw new ScreenAcquisitionError('STALE_DECISION');
      if (last.event.contentSource !== 'APP_META' || decision.relation !== 'UNCERTAIN' || !fallbackReasons.has(decision.reasonCode)) { this.last = null; return null; }
      const observedAt = new Date(this.now()).toISOString();
      const data = await this.native({ operation: 'ocr', excludedBundleIds: policy.excludedBundleIds,
        ownBundleIds: policy.ownBundleIds, expected: last.target, previousImageHash: this.imageHash }, this.signal(policy));
      this.check(epoch);
      const target = this.target(data, policy);
      if (!sameTarget(target, last.target)) throw new ScreenAcquisitionError('STALE_EVENT');
      if (typeof data.text !== 'string' || data.text.length > 6000 || !data.text.trim() || !Number.isFinite(data.confidence) || data.confidence! < 0 || data.confidence! > 1 || typeof data.imageHash !== 'string') throw new ScreenAcquisitionError('INVALID_OCR_RESULT');
      if (sensitive(data.text)) throw new ScreenAcquisitionError('PRIVACY_BLOCKED');
      this.imageHash = data.imageHash;
      return this.observation(target, 'DESKTOP_APP_OCR', data.text, last.event.navigationId, observedAt, data.confidence);
    });
  }
  private observation(target: DesktopTarget, contentSource: DesktopSource, text: string, navigationId: string, observedAt: string, confidence?: number): DesktopObservation {
    const context = this.policy();
    const sequence = (this.sequences[contentSource] ?? 0) + 1;
    this.sequences[contentSource] = sequence;
    const observation: DesktopObservation = { sessionId: context.sessionId, clientRunId: randomUUID(), sequence, navigationId,
      observedAt, isActiveTarget: true, contentSource, contentType: 'DOCUMENT',
      appName: target.appName, windowTitle: target.windowTitle, pageTitle: '', urlHost: '', text,
      extractionStatus: 'SUCCESS', isSensitive: false, isExcluded: false,
      ...(confidence === undefined ? {} : { ocr: { confidence, captureStatus: 'SUCCESS' as const } }) };
    this.last = { event: { clientRunId: observation.clientRunId, navigationId, contentSource }, target };
    return observation;
  }
}
