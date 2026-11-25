const assert = require('node:assert/strict')
const test = require('node:test')
const { GoalService } = require('../../dist-electron/main/goal-ipc.cjs')
const { parseClarifyResult, parseCreatedGoal, parseOpenGoalList, isValidClarifyInput, isValidCreateGoalInput } = require('../../dist-electron/shared/goal-contract.cjs')

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

test('보여 줄 수 없는 선택지를 빼고 나면 개수가 모자란 응답은 거부한다', () => {
  assert.equal(parseClarifyResult({ ...base, decision: 'MULTI_GOAL', question: '어떤 걸 할까요?', goalCandidates: ['영어 단어 외우기', ' '] }), null)
  assert.equal(parseClarifyResult({ ...base, decision: 'AMBIGUOUS', suggestions: ['가', '미적분 예제 풀기'] }), null)
})

test('목표 후보를 공백 없이 비교한 뒤 최대 세 개를 남긴다', () => {
  const result = parseClarifyResult({
    ...base, decision: 'CLEAR', goalCandidates: ['React 학습', ' React 학습 ', 'Vue 실습', 'Svelte 실습'],
  })
  assert.deepEqual(result.goalCandidates, ['React 학습', 'Vue 실습', 'Svelte 실습'])
})

test('서버 CLEAR의 목표 프로필을 확인하고 사용자 편집값과 할 일 페이지를 전달한다', async () => {
  const profile = { mainTopic: '미적분', purpose: 'PRACTICE', coreTopics: ['적분'], supportingTopics: [], expectedActivities: ['예제 풀기'] }
  const draft = parseClarifyResult({ ...base, clarityStatus: 'CLEAR', goalProfileDraft: profile, normalizedGoalText: '미적분 풀기' })
  assert.deepEqual(draft.goalProfileDraft, profile)
  assert.equal(parseClarifyResult({ ...base, clarityStatus: 'CLEAR', goalCandidates: ['영어 단어', '미적분 예제 풀기'] }).goalCandidates.length, 2)
  const input = { goalText: '미적분 풀기', confirmationSource: 'AI_CONFIRMED', goalProfile: profile }
  assert.equal(isValidCreateGoalInput(input), true)
  assert.equal(isValidCreateGoalInput({ ...input, goalProfile: { ...profile, coreTopics: ['CSS', 'css '] } }), false)
  const requests = []
  const service = new GoalService({ authorizedRequest: async (request) => { requests.push(request); return { success: true, data: {} } } })
  await service.create(input)
  await service.listOpen('page+2')
  assert.deepEqual(requests[0].body.goalProfile, profile)
  assert.equal(requests[1].path, '/api/v1/goals?todoStatus=OPEN&cursor=page%2B2')
})

test('40자를 넘긴 Claude 확정 문구도 수동 확인 화면에서 편집할 수 있도록 받는다', () => {
  const normalizedGoalText = '가나다라마바사'.repeat(6)
  const result = parseClarifyResult({ ...base, clarityStatus: 'CLEAR', normalizedGoalText })
  assert.equal(result.normalizedGoalText, normalizedGoalText)
  assert.equal(isValidCreateGoalInput({ goalText: normalizedGoalText, confirmationSource: 'MANUAL_CONFIRMED' }), false)
})

test('목표 저장 재시도 키는 UUID v4만 받아서 HTTP 헤더로 전달한다', async () => {
  const idempotencyKey = '3f2b8a52-7c1e-4d6a-9b0f-1e2d3c4b5a69'
  const body = { goalText: '미적분 풀기', confirmationSource: 'MANUAL_CONFIRMED' }
  const input = { ...body, idempotencyKey }
  assert.equal(isValidCreateGoalInput(input), true)
  assert.equal(isValidCreateGoalInput({ ...input, idempotencyKey: 'invalid' }), false)

  const requests = []
  const service = new GoalService({ authorizedRequest: async (request) => {
    requests.push(request)
    return { success: false, error: { code: 'LOCAL_NETWORK_UNAVAILABLE', retryable: true } }
  } })
  await service.create(input)
  await service.create(input)
  for (const request of requests) {
    assert.deepEqual(request.headers, { 'Idempotency-Key': idempotencyKey })
    assert.deepEqual(request.body, body)
  }
})

test('할 일 목록은 범위(goalProfile)를 같이 읽고, 형식이 어긋난 범위는 버리되 할 일은 남긴다', () => {
  const profile = { mainTopic: 'React', purpose: 'CONCEPT_LEARNING', coreTopics: ['커스텀 훅'], supportingTopics: ['useEffect'], expectedActivities: ['예제 작성'] }
  const list = parseOpenGoalList({ items: [
    { goalId: '1', goalText: '리액트 훅 정리', confirmationSource: 'AI_CONFIRMED', todoStatus: 'OPEN', goalProfile: profile },
    { goalId: '2', goalText: '로그인 오류 고치기', confirmationSource: 'MANUAL_CONFIRMED', todoStatus: 'OPEN', goalProfile: null },
    { goalId: '3', goalText: '발표 자료 정리', confirmationSource: 'AI_CONFIRMED', todoStatus: 'OPEN', goalProfile: { mainTopic: '' } },
  ], nextCursor: null })
  assert.deepEqual(list.items.map((item) => item.goalProfile ?? null), [profile, null, null])
})

test('할 일 지우기는 DELETE /goals/{id}를 204로 보내고, 잘못된 id는 IPC에서 막는다', async () => {
  const { isGoalId } = require('../../dist-electron/shared/goal-contract.cjs')
  const requests = []
  const service = new GoalService({ authorizedRequest: async (request) => { requests.push(request); return { success: true, data: {} } } })
  await service.remove('g_12')
  assert.equal(requests[0].path, '/api/v1/goals/g_12')
  assert.equal(requests[0].method, 'DELETE')
  assert.equal(requests[0].expectedStatus, 204)
  assert.equal(isGoalId('../sessions'), false)
  assert.equal(isGoalId('g_12'), true)
})

test('다 했어요는 PATCH /goals/{id}에 todoStatus DONE만 보낸다', async () => {
  const requests = []
  const service = new GoalService({ authorizedRequest: async (request) => { requests.push(request); return { success: true, data: {} } } })
  await service.complete('g_12')
  assert.deepEqual([requests[0].path, requests[0].method, requests[0].body, requests[0].expectedStatus], ['/api/v1/goals/g_12', 'PATCH', { todoStatus: 'DONE' }, 200])
})

test('범위 수정은 PATCH로 goalProfile만, 다시 만들기는 POST /goals/{id}/profile로 보내고 형식이 다른 범위는 IPC에서 막는다', async () => {
  const { isProfileUpdate } = require('../../dist-electron/shared/goal-contract.cjs')
  const profile = { mainTopic: '미적분', purpose: 'PRACTICE', coreTopics: ['극한'], supportingTopics: [], expectedActivities: ['문제 풀이'] }
  const requests = []
  const service = new GoalService({ authorizedRequest: async (request) => { requests.push(request); return { success: true, data: {} } } })
  await service.updateProfile('g_12', profile)
  await service.regenerateProfile('g_12')
  assert.deepEqual(requests.map((r) => [r.path, r.method, r.body ?? null, r.expectedStatus]), [
    ['/api/v1/goals/g_12', 'PATCH', { goalProfile: profile }, 200],
    ['/api/v1/goals/g_12/profile', 'POST', null, 200],
  ])
  assert.equal(isProfileUpdate({ goalId: 'g_12', goalProfile: profile }), true)
  assert.equal(isProfileUpdate({ goalId: 'g_12', goalProfile: { ...profile, coreTopics: ['극한', '극한'] } }), false, '중복 항목')
  assert.equal(isProfileUpdate({ goalId: 'g_12', goalProfile: { ...profile, coreTopics: ['1', '2', '3', '4', '5', '6'] } }), false, '6개')
})
