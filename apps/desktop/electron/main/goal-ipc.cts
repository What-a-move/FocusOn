import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { GOAL_CHANNELS, isGoalId, isValidClarifyInput, isValidCreateGoalInput, isGoalListCursor, parseClarifyResult, parseCreatedGoal, parseOpenGoalList } from '../shared/goal-contract.cjs'
import type { ClarifyInput, ClarifyResult, CreateGoalInput, CreatedGoal, OpenGoalList } from '../shared/goal-contract.cjs'
import type { ApiResult } from './auth-api.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { AuthService } from './auth-service.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const LOCAL_APP_ERROR = { success: false, error: { code: 'LOCAL_APP_ERROR', retryable: true } } as const
// 서버는 AI 호출 한 번에 60초까지 기다리고, 질문 하나에 보통 10~15초 걸린다. 기본 10초면 정상 응답도 끊긴다.
export const AI_TIMEOUT_MS = 70_000

/** 목표 Backend 계약 (Notion FocusOn API 명세서). POST라 자동 재전송하지 않는다. AI 재시도는 Backend가 맡는다. */
export class GoalService {
  constructor(private readonly auth: Pick<AuthService, 'authorizedRequest'>) {}

  clarify(input: ClarifyInput): Promise<ApiResult<ClarifyResult>> {
    const body: ClarifyInput = { goalText: input.goalText.trim(), conversationTurn: input.conversationTurn, previousAnswers: input.previousAnswers }
    if (input.clarificationSessionId) body.clarificationSessionId = input.clarificationSessionId
    return this.auth.authorizedRequest({ path: '/api/v1/goals/clarify', method: 'POST', expectedStatus: 200, parse: parseClarifyResult, body, timeoutMs: AI_TIMEOUT_MS })
  }

  create(input: CreateGoalInput): Promise<ApiResult<CreatedGoal>> {
    const body: Omit<CreateGoalInput, 'idempotencyKey'> = { goalText: input.goalText.trim(), confirmationSource: input.confirmationSource }
    if (input.clarificationSessionId) body.clarificationSessionId = input.clarificationSessionId
    if (input.goalProfile) body.goalProfile = input.goalProfile
    return this.auth.authorizedRequest({
      // 확정 문구에 범위 초안이 없으면 서버가 저장 전에 AI로 범위를 만든다.
      path: '/api/v1/goals', method: 'POST', expectedStatus: 201, parse: parseCreatedGoal, body, timeoutMs: AI_TIMEOUT_MS,
      ...(input.idempotencyKey ? { headers: { 'Idempotency-Key': input.idempotencyKey } } : {}),
    })
  }

  listOpen(cursor: string | null): Promise<ApiResult<OpenGoalList>> {
    const query = cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''
    return this.auth.authorizedRequest({ path: `/api/v1/goals?todoStatus=OPEN${query}`, method: 'GET', expectedStatus: 200, parse: parseOpenGoalList })
  }

  /** 할 일 논리 삭제. 진행 중인 세션이 쓰면 서버가 409 GOAL_UPDATE_BLOCKED로 막는다. */
  /** 할 일을 끝냄으로 바꾼다. 진행 중인 세션이 써도 todoStatus만은 바꿀 수 있다. */
  complete(goalId: string): Promise<ApiResult<Record<string, never>>> {
    return this.auth.authorizedRequest({ path: `/api/v1/goals/${encodeURIComponent(goalId)}`, method: 'PATCH', body: { todoStatus: 'DONE' }, expectedStatus: 200, parse: () => ({}) })
  }

  remove(goalId: string): Promise<ApiResult<Record<string, never>>> {
    return this.auth.authorizedRequest({ path: `/api/v1/goals/${encodeURIComponent(goalId)}`, method: 'DELETE', expectedStatus: 204, parse: () => ({}) })
  }
}

export function registerGoalIpc(getMainWindow: () => BrowserWindow | null, rendererUrl: string, service: GoalService | null): void {
  function handle<I, T>(channel: string, accept: (input: unknown) => input is I, run: (service: GoalService, input: I) => Promise<ApiResult<T>>): void {
    ipcMain.handle(channel, async (event: IpcMainInvokeEvent, ...args: unknown[]): Promise<IpcResult<T>> => {
      if (!isTrustedAuthSender(event, getMainWindow(), rendererUrl) || args.length !== 1 || !accept(args[0])) return INVALID_REQUEST
      if (!service) return LOCAL_APP_ERROR
      try {
        return await run(service, args[0])
      } catch {
        return LOCAL_APP_ERROR
      }
    })
  }

  handle(GOAL_CHANNELS.clarify, isValidClarifyInput, (s, input) => s.clarify(input))
  handle(GOAL_CHANNELS.create, isValidCreateGoalInput, (s, input) => s.create(input))
  handle(GOAL_CHANNELS.listOpen, isGoalListCursor, (s, cursor) => s.listOpen(cursor))
  handle(GOAL_CHANNELS.remove, isGoalId, (s, goalId) => s.remove(goalId))
  handle(GOAL_CHANNELS.complete, isGoalId, (s, goalId) => s.complete(goalId))
}
