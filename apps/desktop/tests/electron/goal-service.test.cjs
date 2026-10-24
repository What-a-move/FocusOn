const assert = require('node:assert/strict')
const test = require('node:test')
const { GoalService } = require('../../dist-electron/main/goal-ipc.cjs')
const { parseClarifyResult, parseCreatedGoal, isValidClarifyInput, isValidCreateGoalInput } = require('../../dist-electron/shared/goal-contract.cjs')

const base = { normalizedGoalText: null, question: null, suggestions: [], goalCandidates: [], conversationTurn: 0 }

test('명확성 응답은 결정마다 필요한 값이 있어야 통과한다', () => {
  assert.equal(parseClarifyResult({ ...base, decision: 'CLEAR', normalizedGoalText: 'React Query 캐싱 이해하기' }).normalizedGoalText, 'React Query 캐싱 이해하기')
  assert.equal(parseClarifyResult({ ...base, decision: 'UNKNOWN_TERM' }), null, '확인 질문이 없으면 거부')
  assert.equal(parseClarifyResult({ ...base, decision: 'MULTI_GOAL', question: '어떤 걸 할까요?', goalCandidates: ['영어 단어'] }), null, '후보가 하나뿐이면 거부')
  assert.equal(parseClarifyResult({ ...base, decision: 'AMBIGUOUS' }), null, '추천도 질문도 없으면 거부')
  assert.equal(parseClarifyResult({ ...base, decision: 'DONE' }), null)
  const ambiguous = parseClarifyResult({ ...base, decision: 'AMBIGUOUS', suggestions: ['미적분 개념 정리하기', '미적분 예제 풀기', '미적분 강의 듣기', '네 번째'], clarificationSessionId: '701' })
  assert.equal(ambiguous.suggestions.length, 3)
  assert.equal(ambiguous.clarificationSessionId, '701')
})

test('Renderer 입력은 목표 2~40자·질문 2회·허용 필드만 받는다', () => {
  assert.equal(isValidClarifyInput({ goalText: 'rq 캐싱', conversationTurn: 0, previousAnswers: [] }), true)
  assert.equal(isValidClarifyInput({ goalText: '가', conversationTurn: 0, previousAnswers: [] }), false)
  assert.equal(isValidClarifyInput({ goalText: 'rq 캐싱', conversationTurn: 3, previousAnswers: [] }), false)
  assert.equal(isValidClarifyInput({ goalText: 'rq 캐싱', conversationTurn: 1, previousAnswers: [{ question: 'q', answer: '맞아요', extra: 1 }] }), false)
  assert.equal(isValidClarifyInput({ goalText: 'rq 캐싱', conversationTurn: 0, previousAnswers: [], userId: '1' }), false)
  assert.equal(isValidCreateGoalInput({ goalText: '미적분 풀기', confirmationSource: 'MANUAL_CONFIRMED' }), true)
  assert.equal(isValidCreateGoalInput({ goalText: '미적분 풀기', confirmationSource: 'ADMIN' }), false)
  assert.equal(parseCreatedGoal({ goalId: '12', goalText: '미적분 풀기', confirmationSource: 'AI_CONFIRMED' }).goalId, '12')
})

test('목표 요청은 POST로 보내고 빈 clarificationSessionId는 보내지 않는다', async () => {
  const requests = []
  const service = new GoalService({ authorizedRequest: async (request) => { requests.push(request); return { success: false, error: { code: 'GOAL_ANALYSIS_UNAVAILABLE', retryable: true } } } })
  await service.clarify({ goalText: ' rq 캐싱 ', conversationTurn: 1, previousAnswers: [{ question: 'q', answer: '맞아요' }], clarificationSessionId: '701' })
  await service.create({ goalText: '미적분 풀기', confirmationSource: 'MANUAL_CONFIRMED' })
  assert.deepEqual(requests.map(({ path, method, expectedStatus, body }) => ({ path, method, expectedStatus, body })), [
    { path: '/api/v1/goals/clarify', method: 'POST', expectedStatus: 200, body: { goalText: 'rq 캐싱', conversationTurn: 1, previousAnswers: [{ question: 'q', answer: '맞아요' }], clarificationSessionId: '701' } },
    { path: '/api/v1/goals', method: 'POST', expectedStatus: 201, body: { goalText: '미적분 풀기', confirmationSource: 'MANUAL_CONFIRMED' } },
  ])
})
