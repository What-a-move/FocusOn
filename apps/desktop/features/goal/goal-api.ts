import type { ClarifyInput, ClarifyResult, CreateGoalInput, CreatedGoal } from '../../electron/shared/goal-contract.cjs'

export type ApiError = { code: string; retryable: boolean }
export type ApiResult<T> = { success: true; data: T } | { success: false; error: ApiError }
export type { ClarifyResult, CreatedGoal }
// [주의] Renderer는 electron/shared/*.cts에서 타입만 가져온다. 값은 Next 빌드가 .cjs를 찾지 못한다. Main의 MAX_QUESTIONS와 같은 값이다.
export const MAX_QUESTIONS = 2

const UNAVAILABLE = { success: false, error: { code: 'SERVICE_UNAVAILABLE', retryable: true } } as const

// [유지보수] 목표 요청도 Main IPC(window.focusOn.goals)로만 보낸다. 브라우저에서 화면만 열면 연결이 없어 실패로 보인다.
async function viaGoals<T>(call: (api: NonNullable<Window['focusOn']>['goals']) => Promise<ApiResult<T>>): Promise<ApiResult<T>> {
  const api = typeof window === 'undefined' ? undefined : window.focusOn?.goals
  if (!api) return UNAVAILABLE
  try {
    return await call(api)
  } catch {
    return UNAVAILABLE
  }
}

export const goalApi = {
  clarify: (input: ClarifyInput) => viaGoals<ClarifyResult>((api) => api.clarify(input)),
  create: (input: CreateGoalInput) => viaGoals<CreatedGoal>((api) => api.create(input)),
}

/** 저장한 목표를 세션 설정 화면으로 넘긴다. 목표 문구와 id만 두며 민감 정보가 아니다. */
export const CONFIRMED_GOAL_KEY = 'focuson.confirmedGoal'

export const goalLength = (text: string) => [...text.trim()].length
