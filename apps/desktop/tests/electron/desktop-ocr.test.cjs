const assert = require('node:assert/strict')
const test = require('node:test')
const os = require('node:os')
const path = require('node:path')
const { mkdtempSync, writeFileSync } = require('node:fs')
const { cleanOcrText } = require('../../dist-electron/main/ocr-text.cjs')
const { analyzeObservation } = require('../../dist-electron/main/screen-analysis.cjs')
const { AnalysisSender } = require('../../dist-electron/main/analysis-sender.cjs')

const AT = new Date('2026-10-26T10:00:30Z')
const meta = { observedAt: '2026-10-26T10:00:15Z', guardStatus: null, contentSource: 'APP_META', metadata: { appName: 'Preview', windowTitle: 'notes.pdf' }, key: 'com.apple.Preview\nnotes.pdf', windowId: 42 }

function setup({ relation = 'UNRELATED', ocr = '미분 계수 정의와 극한', showing = [true, true] } = {}) {
  const sent = []; const ocrCalls = []
  const deps = {
    send: async (_sessionId, observation) => { sent.push(observation); return { analysisRunId: String(sent.length), relation: sent.length === 1 ? relation : 'RELATED', confidence: 0.9, reasonCode: null, observedAt: observation.observedAt, policyVersion: 'v1' } },
    ocr: async (windowId) => { ocrCalls.push(windowId); return ocr },
    stillShowing: () => showing.shift() ?? true,
    now: () => AT,
  }
  return { deps, sent, ocrCalls }
}

test('앱 정보 결과가 관련 없음·판단 보류면 그 창의 글자로 한 번 더 보낸다', async () => {
  for (const relation of ['UNRELATED', 'UNCERTAIN']) {
    const t = setup({ relation })
    await analyzeObservation(t.deps, '901', meta)
    assert.deepEqual(t.ocrCalls, [42], relation)
    assert.deepEqual(t.sent[1], { observedAt: AT.toISOString(), guardStatus: null, contentSource: 'DESKTOP_APP_OCR', metadata: meta.metadata, text: '미분 계수 정의와 극한' })
  }
})

test('관련 있음이거나 제외·민감 관측이면 OCR하지 않는다', async () => {
  const related = setup({ relation: 'RELATED' })
  await analyzeObservation(related.deps, '901', meta)
  const guarded = setup()
  await analyzeObservation(guarded.deps, '901', { observedAt: meta.observedAt, guardStatus: 'EXCLUDED' })
  const noWindow = setup()
  await analyzeObservation(noWindow.deps, '901', { ...meta, windowId: null })
  assert.deepEqual([related.ocrCalls, guarded.ocrCalls, noWindow.ocrCalls], [[], [], []])
})

test('응답이나 OCR을 기다리는 사이 다른 화면으로 옮겼으면 늦은 결과로 보내지 않는다', async () => {
  const beforeOcr = setup({ showing: [false] })
  await analyzeObservation(beforeOcr.deps, '901', meta)
  assert.deepEqual([beforeOcr.ocrCalls.length, beforeOcr.sent.length], [0, 1])
  const afterOcr = setup({ showing: [true, false] })
  await analyzeObservation(afterOcr.deps, '901', meta)
  assert.deepEqual([afterOcr.ocrCalls.length, afterOcr.sent.length], [1, 1])
})

test('민감한 글자면 글자 대신 PRIVACY_BLOCKED만, OCR 실패나 빈 글자면 보내지 않는다', async () => {
  const sensitive = setup({ ocr: '새 비밀번호를 입력하세요' })
  await analyzeObservation(sensitive.deps, '901', meta)
  assert.deepEqual(sensitive.sent[1], { observedAt: AT.toISOString(), guardStatus: 'PRIVACY_BLOCKED', contentSource: 'DESKTOP_APP_OCR' })
  for (const ocr of [null, '   ']) {
    const t = setup({ ocr })
    await analyzeObservation(t.deps, '901', meta)
    assert.equal(t.sent.length, 1)
  }
})

test('OCR 글자는 주소·이메일·전화·긴 토큰을 지우고 2,000자로 자른다', () => {
  const { text, sensitive } = cleanOcrText(`극한 정의\n문의 me@example.com 010-1234-5678 https://x.io/a abcd1234efgh5678ijkl9012mnop ${'가'.repeat(3000)}`)
  assert.equal(sensitive, false)
  assert.ok(text.startsWith('극한 정의 문의'))
  for (const leaked of ['@', '010', 'https', 'abcd1234']) assert.equal(text.includes(leaked), false, leaked)
  assert.equal(text.length, 2000)
  assert.equal(cleanOcrText('카드 4111 1111 1111 1111').sensitive, true)
})

function sender(file) {
  const calls = []
  const auth = { authorizedRequest: async (request) => { calls.push(request); return { success: true, data: request.parse({ analysisRunId: '1', relation: 'RELATED', confidence: 1, reasonCode: null, observedAt: 'x', policyVersion: 'v1' }) } } }
  return { sender: new AnalysisSender(auth, file, () => 0), calls }
}

test('순서 번호는 contentSource별로 세고, OCR 글자는 contentPayload로 보낸다', async () => {
  const file = path.join(mkdtempSync(path.join(os.tmpdir(), 'focuson-')), 'analysis-sequence.json')
  const { sender: s, calls } = sender(file)
  await s.send('901', meta)
  await s.send('901', { observedAt: 'x', guardStatus: null, contentSource: 'DESKTOP_APP_OCR', metadata: meta.metadata, text: '극한' })
  await s.send('901', meta)
  assert.deepEqual(calls.map((call) => [call.body.contentSource, call.body.sequence]), [['APP_META', 1], ['DESKTOP_APP_OCR', 1], ['APP_META', 2]])
  assert.deepEqual(calls[1].body.contentPayload, { text: '극한' })
  for (const leaked of ['key', 'windowId', 'text']) assert.equal(leaked in calls[0].body, false, `${leaked}는 보내지 않음`)
})

test('예전 저장 형식의 순서 번호는 APP_META로 이어 센다', async () => {
  const file = path.join(mkdtempSync(path.join(os.tmpdir(), 'focuson-')), 'analysis-sequence.json')
  writeFileSync(file, JSON.stringify({ sessionId: '901', sequence: 7 }))
  const { sender: s, calls } = sender(file)
  await s.send('901', meta)
  assert.equal(calls[0].body.sequence, 8)
})
