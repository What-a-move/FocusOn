/**
 * Desktop이 정한 전경 context(#182) 중 이 Extension이 맡은 것만 고른다.
 * `GET /sessions/{id}/observation-context` 응답이 EXTENSION 담당이고 담당 기기가 이 기기일 때만 ID를 돌려준다.
 * 204(context 없음)·다른 기기 담당·형식 오류면 null이라 분석 요청에 붙이지 않는다.
 */
export function pickContextId(value: unknown, deviceId: string | null): string | null {
  if (!deviceId || typeof value !== 'object' || value === null) return null
  const { observationContextId, observer, observerDeviceId } = value as Record<string, unknown>
  if (observer !== 'EXTENSION' || observerDeviceId !== deviceId || typeof observationContextId !== 'string') return null
  return observationContextId
}
