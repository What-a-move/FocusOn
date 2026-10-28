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
