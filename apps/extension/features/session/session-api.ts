import { resolveApiBase } from '../pairing/pairing-api'
import { chromeTokenStore, clearTokens, getDeviceId } from '../pairing/extension-auth'
import { createApiClient, type ApiResult } from './api-client'
import { parseActiveSession, type ActiveSession } from './session-model'

const client = createApiClient({ baseUrl: resolveApiBase(process.env.NEXT_PUBLIC_FOCUSON_API_BASE_URL), store: chromeTokenStore })

/** 활성 세션. 없으면 null. */
export async function getActiveSession(): Promise<ApiResult<ActiveSession | null>> {
  const result = await client.authorized('/api/v1/sessions/active', 'GET', 200, parseActiveSession)
  if (!result.success) return result
  return { success: true, data: 'none' in result.data ? null : result.data }
}

/** 이 브라우저 연결 해제. 서버가 이미 해제했어도 로컬 토큰은 지운다. */
export async function unlinkThisBrowser(): Promise<ApiResult<{ unlinked: true }>> {
  const deviceId = await getDeviceId()
  if (deviceId) {
    const result = await client.authorized(`/api/v1/extension-pairings/${encodeURIComponent(deviceId)}`, 'DELETE', 204, () => ({ unlinked: true as const }))
    if (!result.success && result.error.code !== 'RELINK_REQUIRED') return result
  }
  await clearTokens()
  return { success: true, data: { unlinked: true } }
}
