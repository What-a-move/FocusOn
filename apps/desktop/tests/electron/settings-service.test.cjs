const assert = require('node:assert/strict')
const test = require('node:test')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { SettingsService, ThemePreference } = require('../../dist-electron/main/settings-ipc.cjs')
const { parseFocusSettings, isFocusSettingsInput } = require('../../dist-electron/shared/session-contract.cjs')

const settings = { interventionLevel: 'GENTLE', notificationChannels: ['MOLE_UI'], cameraAnalysisEnabled: false, breakSuggestionEnabled: true }

test('집중 설정은 휴식 제안까지 4개 값이 있어야 통과한다', () => {
  assert.deepEqual(parseFocusSettings(settings), settings)
  assert.equal(parseFocusSettings({ ...settings, breakSuggestionEnabled: undefined }), null)
  assert.equal(parseFocusSettings({ ...settings, notificationChannels: [] }), null)
  assert.equal(parseFocusSettings({ ...settings, notificationChannels: ['MOLE_UI', 'MOLE_UI'] }), null)
})

test('저장 입력은 4개 필드만 받는다', () => {
  assert.equal(isFocusSettingsInput(settings), true)
  assert.equal(isFocusSettingsInput({ ...settings, extra: 1 }), false)
  assert.equal(isFocusSettingsInput({ ...settings, interventionLevel: 'LOUD' }), false)
})

test('집중 설정은 전체 객체 PUT, 제외 사이트는 X-Device-Id를 붙인 GET·PUT으로 보내고 readiness는 건드리지 않는다', async () => {
  const requests = []
  const service = new SettingsService({
    authorizedRequest: async (request) => {
      requests.push(request)
      return { success: true, data: request.parse(request.path.endsWith('focus-settings') ? request.body : { domains: request.body?.domains ?? ['youtube.com'] }) }
    },
  })
  assert.deepEqual(await service.saveFocusSettings(settings), { success: true, data: settings })
  assert.deepEqual(await service.getExcludedDomains(), { success: true, data: { domains: ['youtube.com'] } })
  assert.deepEqual(await service.saveExcludedDomains([]), { success: true, data: { domains: [] } })
  assert.deepEqual(requests.map(({ path, method, body, withDeviceId }) => ({ path, method, body, withDeviceId })), [
    { path: '/api/v1/users/me/focus-settings', method: 'PUT', body: settings, withDeviceId: undefined },
    { path: '/api/v1/users/me/analysis-excluded-domains', method: 'GET', body: undefined, withDeviceId: true },
    { path: '/api/v1/users/me/analysis-excluded-domains', method: 'PUT', body: { domains: [] }, withDeviceId: true },
  ])
})

test('서버가 이상한 도메인 목록을 돌려주면 성공으로 보지 않는다', async () => {
  let parse
  const service = new SettingsService({ authorizedRequest: async (request) => { parse = request.parse; return { success: false, error: { code: 'X', retryable: false } } } })
  await service.getExcludedDomains()
  // parse가 null이면 authorizedRequest가 응답 형식 오류로 처리한다.
  assert.equal(parse({ domains: ['not a domain'] }), null)
  assert.equal(parse({ domains: 'youtube.com' }), null)
  assert.deepEqual(parse({ domains: ['youtube.com'] }), { domains: ['youtube.com'] })
})

test('화면 모드는 이 기기에 저장하고, 없거나 깨진 파일이면 macOS 설정을 따른다', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'focuson-theme-'))
  const file = path.join(dir, 'preferences.json')
  const preference = new ThemePreference(file)
  assert.equal(preference.read(), 'system')
  preference.write('dark')
  assert.equal(new ThemePreference(file).read(), 'dark')
  fs.writeFileSync(file, '{broken')
  assert.equal(preference.read(), 'system')
  fs.writeFileSync(file, JSON.stringify({ theme: 'sepia' }))
  assert.equal(preference.read(), 'system')
  fs.rmSync(dir, { recursive: true })
})

function accountFixture(routes) {
  const requests = []
  const statuses = []
  const service = new SettingsService({
    currentDeviceId: () => '10',
    updateOnboardingStatus: (status) => statuses.push(status),
    authorizedRequest: async (request) => {
      requests.push(request)
      const reply = routes[`${request.method} ${request.path}`]
      const raw = typeof reply === 'function' ? reply(request) : reply
      if (raw && raw.fail) return { success: false, error: { code: raw.fail, retryable: false } }
      const data = request.parse(raw)
      return data ? { success: true, data } : { success: false, error: { code: 'INVALID_RESPONSE', retryable: false } }
    },
  })
  return { service, requests, statuses }
}

const consents = (screen) => ({ currentVersion: '2026-09-17', consents: [
  { consentType: 'TERMS_OF_SERVICE', isGranted: true, consentVersion: '2026-09-17', grantedAt: '2026-10-30T10:00:00Z', withdrawnAt: null },
  { consentType: 'SCREEN_ANALYSIS', isGranted: screen, consentVersion: '2026-09-17', grantedAt: '2026-10-30T10:00:00Z', withdrawnAt: screen ? null : '2026-11-02T01:00:00Z' },
] })

test('화면 분석 동의 철회는 서버 현재 버전으로 PUT하고 온보딩 상태를 다시 읽어 반영한다', async () => {
  let granted = true
  const { service, requests, statuses } = accountFixture({
    'GET /api/v1/users/me/consents': () => consents(granted),
    'PUT /api/v1/users/me/consents/SCREEN_ANALYSIS': (request) => { granted = request.body.isGranted; return { consentType: 'SCREEN_ANALYSIS' } },
    'GET /api/v1/users/me/onboarding-readiness': { onboardingStatus: 'INCOMPLETE', currentStep: 'PERMISSIONS_AND_CONSENTS', steps: { profileCompleted: true, extensionLinked: true, screenAnalysisReady: true, requiredConsentsCompleted: false, analysisExcludedDomainsStepCompleted: true } },
  })
  const result = await service.setScreenConsent(false)
  assert.equal(result.data.items.find((item) => item.type === 'SCREEN_ANALYSIS').granted, false)
  assert.deepEqual(requests.find((r) => r.method === 'PUT').body, { isGranted: false, consentVersion: '2026-09-17', consentSource: 'DESKTOP_SETTINGS' })
  assert.deepEqual(statuses, ['INCOMPLETE'])
})

test('기기 목록에서 이 Desktop에 연결된 Extension만 고르고, 다른 기기는 해제하지 않는다', async () => {
  let linked = true
  const { service, requests } = accountFixture({
    'GET /api/v1/devices': () => ({ devices: [
      { deviceId: '10', type: 'DESKTOP', name: 'Mac', lastUsedAt: null, linkedDesktopDeviceId: null },
      { deviceId: '21', type: 'EXTENSION', name: 'Chrome', lastUsedAt: null, linkedDesktopDeviceId: '99' },
      ...(linked ? [{ deviceId: '22', type: 'EXTENSION', name: 'Chrome', lastUsedAt: '2026-11-02T01:00:00Z', linkedDesktopDeviceId: '10' }] : []),
    ] }),
    'DELETE /api/v1/extension-pairings/22': () => { linked = false; return null },
    'GET /api/v1/users/me/onboarding-readiness': { fail: 'SERVICE_UNAVAILABLE' },
  })
  assert.equal((await service.getDevices()).data.extension.deviceId, '22')
  assert.equal((await service.unlinkExtension('21')).error.code, 'DEVICE_NOT_FOUND')
  assert.equal(requests.some((r) => r.method === 'DELETE' && r.path.endsWith('/21')), false)
  const after = await service.unlinkExtension('22')
  assert.deepEqual(after, { success: true, data: { extension: null } }, 'readiness 조회가 실패해도 해제 결과는 돌려준다')
})

test('동의 저장·연결 해제가 끝났으면 다시 읽기가 실패해도 성공으로 돌려준다', async () => {
  let reads = 0
  const { service } = accountFixture({
    'GET /api/v1/users/me/consents': () => (++reads === 1 ? consents(true) : { fail: 'SERVICE_UNAVAILABLE' }),
    'PUT /api/v1/users/me/consents/SCREEN_ANALYSIS': { consentType: 'SCREEN_ANALYSIS' },
    'GET /api/v1/users/me/onboarding-readiness': { fail: 'SERVICE_UNAVAILABLE' },
  })
  const result = await service.setScreenConsent(false)
  assert.equal(result.success, true)
  assert.equal(result.data.items.find((item) => item.type === 'SCREEN_ANALYSIS').granted, false)

  let listed = 0
  const devices = accountFixture({
    'GET /api/v1/devices': () => (++listed === 1 ? { devices: [{ deviceId: '22', type: 'EXTENSION', name: 'Chrome', lastUsedAt: null, linkedDesktopDeviceId: '10' }] } : { fail: 'SERVICE_UNAVAILABLE' }),
    'DELETE /api/v1/extension-pairings/22': null,
    'GET /api/v1/users/me/onboarding-readiness': { fail: 'SERVICE_UNAVAILABLE' },
  })
  assert.deepEqual(await devices.service.unlinkExtension('22'), { success: true, data: { extension: null } })
})
