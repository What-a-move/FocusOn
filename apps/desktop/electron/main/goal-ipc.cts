import type { BrowserWindow, IpcMainInvokeEvent } from 'electron'
import { ipcMain } from 'electron'
import type { IpcResult } from '../shared/auth-contract.cjs'
import { GOAL_CHANNELS, isValidClarifyInput, isValidCreateGoalInput, parseClarifyResult, parseCreatedGoal } from '../shared/goal-contract.cjs'
import type { ClarifyInput, ClarifyResult, CreateGoalInput, CreatedGoal } from '../shared/goal-contract.cjs'
import type { ApiResult } from './auth-api.cjs'
import { isTrustedAuthSender } from './auth-ipc.cjs'
import type { AuthService } from './auth-service.cjs'

const INVALID_REQUEST = { success: false, error: { code: 'VALIDATION_ERROR', retryable: false } } as const
const SERVICE_UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

/** 목표 Backend 계약 (Notion FocusOn API 명세서). POST라 자동 재전송하지 않는다. AI 재시도는 Backend가 맡는다. */
export class GoalService {
  constructor(private readonly auth: Pick<AuthService, 'authorizedRequest'>) {}

  clarify(input: ClarifyInput): Promise<ApiResult<ClarifyResult>> {
    const body: ClarifyInput = { goalText: input.goalText.trim(), conversationTurn: input.conversationTurn, previousAnswers: input.previousAnswers }
    if (input.clarificationSessionId) body.clarificationSessionId = input.clarificationSessionId
    return this.auth.authorizedRequest({ path: '/api/v1/goals/clarify', method: 'POST', expectedStatus: 200, parse: parseClarifyResult, body })
  }

  create(input: CreateGoalInput): Promise<ApiResult<CreatedGoal>> {
    const body: CreateGoalInput = { goalText: input.goalText.trim(), confirmationSource: input.confirmationSource }
    if (input.clarificationSessionId) body.clarificationSessionId = input.clarificationSessionId
    return this.auth.authorizedRequest({ path: '/api/v1/goals', method: 'POST', expectedStatus: 201, parse: parseCreatedGoal, body })
  }
}

export function registerGoalIpc(getMainWindow: () => BrowserWindow | null, rendererUrl: string, service: GoalService | null): void {
  function handle<I, T>(channel: string, accept: (input: unknown) => input is I, run: (service: GoalService, input: I) => Promise<ApiResult<T>>): void {
    ipcMain.handle(channel, async (event: IpcMainInvokeEvent, ...args: unknown[]): Promise<IpcResult<T>> => {
      if (!isTrustedAuthSender(event, getMainWindow(), rendererUrl) || args.length !== 1 || !accept(args[0])) return INVALID_REQUEST
      if (!service) return SERVICE_UNAVAILABLE
      try {
        return await run(service, args[0])
      } catch {
        return SERVICE_UNAVAILABLE
      }
    })
  }

  handle(GOAL_CHANNELS.clarify, isValidClarifyInput, (s, input) => s.clarify(input))
  handle(GOAL_CHANNELS.create, isValidCreateGoalInput, (s, input) => s.create(input))
}
