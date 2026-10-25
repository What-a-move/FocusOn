const assert = require('node:assert/strict')
const test = require('node:test')
const os = require('node:os')
const path = require('node:path')
const { existsSync, mkdtempSync, readFileSync } = require('node:fs')
const { ScreenObserver, parseFrontmost } = require('../../dist-electron/main/screen-observer.cjs')
const { ExcludedApps } = require('../../dist-electron/main/excluded-apps.cjs')
const { isExcludedAppList } = require('../../dist-electron/shared/analysis-contract.cjs')
const { toInstalledApps } = require('../../dist-electron/main/analysis-ipc.cjs')

const AT = new Date('2026-10-25T10:00:15Z')
function setup(excluded = []) {
  let clock = 0
  const observer = new ScreenObserver({ selfPid: 1, isExcluded: (id) => excluded.includes(id), now: () => clock, wallClock: () => AT })
  // 1초 간격으로 같은 앱을 seconds초 동안 넣고, 만들어진 관측만 모은다.
  const hold = (front, seconds) => {
    const made = []
    for (let i = 0; i <= seconds; i += 1) { const o = observer.observe(front); if (o) made.push(o); clock += 1000 }
    return made
  }
  return { observer, hold }
}
const xcode = { bundleId: 'com.apple.dt.Xcode', appName: 'Xcode', pid: 50, windowTitle: 'FocusOn — main.swift' }

test('같은 화면이 15초 유지되면 APP_META 관측을 한 번만 만든다', () => {
  const { hold } = setup()
  assert.deepEqual(hold(xcode, 14), [], '15초 전에는 만들지 않음')
  const made = hold(xcode, 30)
  assert.equal(made.length, 1, '같은 화면에서는 다시 만들지 않음')
  assert.deepEqual(made[0], { observedAt: AT.toISOString(), guardStatus: null, contentSource: 'APP_META', metadata: { appName: 'Xcode', windowTitle: 'FocusOn — main.swift' } })
})

test('15초 안에 창 제목이 바뀌면 처음부터 다시 센다', () => {
  const { hold } = setup()
  hold(xcode, 10)
  assert.deepEqual(hold({ ...xcode, windowTitle: 'README.md' }, 14), [])
  assert.equal(hold({ ...xcode, windowTitle: 'README.md' }, 2).length, 1)
})

test('맨 앞 앱을 확인할 수 없는 순간은 이탈로 보지 않고 15초 계산도 끊지 않는다', () => {
  const { observer, hold } = setup()
  hold(xcode, 8)
  assert.equal(observer.observe(null), null)
  assert.equal(hold(xcode, 7).length, 1)
})

test('Chrome·번들 ID 없는 앱은 관측하지 않는다', () => {
  const { hold } = setup()
  assert.deepEqual(hold({ ...xcode, bundleId: 'com.google.Chrome', appName: 'Google Chrome' }, 20), [], 'Chrome은 Extension 담당')
  assert.deepEqual(hold({ ...xcode, bundleId: null }, 20), [])
})

test('제외 앱은 EXCLUDED, 민감 앱·창 제목은 PRIVACY_BLOCKED로 메타데이터 없이 만든다', () => {
  const { hold } = setup(['com.apple.dt.Xcode'])
  assert.deepEqual(hold(xcode, 15), [{ observedAt: AT.toISOString(), guardStatus: 'EXCLUDED' }])
  assert.deepEqual(hold({ bundleId: 'com.kakao.KakaoTalkMac', appName: '카카오톡', pid: 60, windowTitle: '친구' }, 15), [{ observedAt: AT.toISOString(), guardStatus: 'PRIVACY_BLOCKED' }])
  assert.deepEqual(hold({ bundleId: 'com.apple.Notes', appName: '메모', pid: 61, windowTitle: '은행 비밀번호 정리' }, 15), [{ observedAt: AT.toISOString(), guardStatus: 'PRIVACY_BLOCKED' }])
})

test('지원하지 않는 브라우저와 FocusOn 자기 창은 내용 없이 EXCLUDED로만 알려 직전 판정을 끊는다', () => {
  const { hold } = setup()
  const excluded = [{ observedAt: AT.toISOString(), guardStatus: 'EXCLUDED' }]
  assert.deepEqual(hold({ bundleId: 'com.apple.Safari', appName: 'Safari', pid: 70, windowTitle: 'React Query 문서' }, 15), excluded, 'Safari')
  assert.deepEqual(hold({ bundleId: 'company.thebrowser.Browser', appName: 'Arc', pid: 71, windowTitle: null }, 15), excluded, 'Arc')
  assert.deepEqual(hold({ ...xcode, bundleId: 'com.github.Electron', pid: 1 }, 15), excluded, 'FocusOn 창')
})

test('reset 뒤에는 같은 화면이어도 15초를 다시 센다', () => {
  const { observer, hold } = setup()
  hold(xcode, 15)
  observer.reset()
  assert.equal(hold(xcode, 15).length, 1)
})

test('도우미 응답은 허용 필드만 읽고 형식이 다르면 확인 불가로 본다', () => {
  assert.deepEqual(parseFrontmost({ ok: true, app: { bundleId: 'com.apple.dt.Xcode', appName: 'Xcode', pid: 50, windowTitle: null, extra: 'x' } }),
    { bundleId: 'com.apple.dt.Xcode', appName: 'Xcode', pid: 50, windowTitle: null })
  assert.equal(parseFrontmost({ ok: true, app: null }), null)
  assert.equal(parseFrontmost({ ok: true, app: { bundleId: 'a' } }), null)
  assert.equal(parseFrontmost(null), null)
})

test('제외 앱은 로컬 파일에만 저장하고, 잘못된 목록은 받지 않는다', () => {
  const file = path.join(mkdtempSync(path.join(os.tmpdir(), 'focuson-')), 'excluded-apps.json')
  const store = new ExcludedApps(file)
  assert.deepEqual(store.list(), [], '파일이 없으면 빈 목록')
  store.save([{ bundleId: 'com.valvesoftware.steam', name: ' Steam ' }])
  assert.equal(store.has('com.valvesoftware.steam'), true)
  assert.deepEqual(JSON.parse(readFileSync(file, 'utf8')).apps, [{ bundleId: 'com.valvesoftware.steam', name: 'Steam' }])
  assert.deepEqual(new ExcludedApps(file).list(), [{ bundleId: 'com.valvesoftware.steam', name: 'Steam' }], '다시 열어도 유지')
  assert.equal(existsSync(`${file}.tmp`), false, '임시 파일은 남기지 않음')
  assert.equal(isExcludedAppList([{ bundleId: 'a.b', name: 'A' }, { bundleId: 'a.b', name: 'B' }]), false, '중복')
  assert.equal(isExcludedAppList([{ bundleId: 'a b', name: 'A' }]), false, '번들 ID 형식')
  assert.equal(isExcludedAppList([{ bundleId: 'a.b', name: 'A', path: '/x' }]), false, '허용 필드만')
  assert.equal(isExcludedAppList(Array.from({ length: 51 }, (_, i) => ({ bundleId: `a.b${i}`, name: 'A' }))), false, '최대 50개')
})

test('설치된 앱 목록은 실행 중인 앱을 먼저, 그다음 이름 순으로 정렬한다', () => {
  const apps = toInstalledApps({ apps: [
    { bundleId: 'c', name: '캘린더', path: '/c' }, { bundleId: 'b', name: 'Books', path: '/b' }, { bundleId: 'x', name: 1 },
    { bundleId: 'unity.My Game_1', name: 'Game', path: '/g' }, { bundleId: 'e', name: '  ', path: '/e' },
  ], running: ['c'] })
  assert.deepEqual(apps.map((app) => [app.bundleId, app.running]), [['c', true], ['b', false]], '저장할 수 없는 번들 ID·빈 이름은 뺌')
})
