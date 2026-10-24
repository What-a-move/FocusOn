import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 목표 Channel. 목표 설정 화면(D06·D07)이 쓴다.
export const GOAL_CHANNELS = {
  clarify: 'goal:clarify',
  create: 'goal:create',
} as const

export type GoalDecision = 'CLEAR' | 'AMBIGUOUS' | 'MULTI_GOAL' | 'UNKNOWN_TERM'
export type ConfirmationSource = 'AI_CONFIRMED' | 'MANUAL_CONFIRMED'
export type PreviousAnswer = { question: string; answer: string }

export type ClarifyInput = { goalText: string; conversationTurn: number; clarificationSessionId?: string; previousAnswers: PreviousAnswer[] }
export type ClarifyResult = {
  decision: GoalDecision
  normalizedGoalText: string | null
  question: string | null
  suggestions: string[]
  goalCandidates: string[]
  conversationTurn: number
  clarificationSessionId: string | null
}
export type CreateGoalInput = { goalText: string; confirmationSource: ConfirmationSource; clarificationSessionId?: string }
export type CreatedGoal = { goalId: string; goalText: string; confirmationSource: ConfirmationSource }

export type GoalsApi = {
  clarify(input: ClarifyInput): Promise<IpcResult<ClarifyResult>>
  create(input: CreateGoalInput): Promise<IpcResult<CreatedGoal>>
}

export const MAX_QUESTIONS = 2
const DECISIONS: readonly GoalDecision[] = ['CLEAR', 'AMBIGUOUS', 'MULTI_GOAL', 'UNKNOWN_TERM']
const SOURCES: readonly ConfirmationSource[] = ['AI_CONFIRMED', 'MANUAL_CONFIRMED']

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)
const isText = (value: unknown, min: number, max: number): value is string => {
  if (typeof value !== 'string') return false
  const length = [...value.trim()].length
  return length >= min && length <= max
}
const isStringList = (value: unknown): value is string[] => Array.isArray(value) && value.every((item) => typeof item === 'string' && item.trim().length > 0)
const isId = (value: unknown): value is string => typeof value === 'string' && /^[A-Za-z0-9_-]{1,64}$/u.test(value)

/** 목표는 공백을 뺀 2~40자다 (로그인 설계서 6.1). */
export const isValidGoalText = (value: unknown): value is string => isText(value, 2, 40)

export function isValidClarifyInput(value: unknown): value is ClarifyInput {
  if (!isRecord(value) || !isValidGoalText(value.goalText)) return false
  const keys = Object.keys(value)
  if (!keys.every((key) => ['goalText', 'conversationTurn', 'clarificationSessionId', 'previousAnswers'].includes(key))) return false
  if (!Number.isInteger(value.conversationTurn) || (value.conversationTurn as number) < 0 || (value.conversationTurn as number) > MAX_QUESTIONS) return false
  if (value.clarificationSessionId !== undefined && !isId(value.clarificationSessionId)) return false
  return Array.isArray(value.previousAnswers) && value.previousAnswers.length <= MAX_QUESTIONS
    && value.previousAnswers.every((item) => isRecord(item) && Object.keys(item).length === 2 && isText(item.question, 1, 200) && isText(item.answer, 1, 40))
}

export function isValidCreateGoalInput(value: unknown): value is CreateGoalInput {
  if (!isRecord(value) || !isValidGoalText(value.goalText) || !SOURCES.includes(value.confirmationSource as ConfirmationSource)) return false
  if (!Object.keys(value).every((key) => ['goalText', 'confirmationSource', 'clarificationSessionId'].includes(key))) return false
  return value.clarificationSessionId === undefined || isId(value.clarificationSessionId)
}

export function parseClarifyResult(value: unknown): ClarifyResult | null {
  if (!isRecord(value) || !DECISIONS.includes(value.decision as GoalDecision)) return null
  const { normalizedGoalText, question, suggestions, goalCandidates, conversationTurn, clarificationSessionId } = value
  if (!(normalizedGoalText === null || isValidGoalText(normalizedGoalText)) || !(question === null || typeof question === 'string')) return null
  if (!isStringList(suggestions) || !isStringList(goalCandidates) || !Number.isInteger(conversationTurn)) return null
  // clarificationSessionId는 Frontend 제안 필드다(Backend 확인 전). 없으면 null로 읽는다.
  if (!(clarificationSessionId === undefined || clarificationSessionId === null || isId(clarificationSessionId))) return null
  const decision = value.decision as GoalDecision
  // [주의] 결정에 필요한 값이 없으면 화면에서 할 수 있는 일이 없으므로 계약 위반으로 본다.
  if (decision === 'MULTI_GOAL' && goalCandidates.length < 2) return null
  if ((decision === 'UNKNOWN_TERM' || decision === 'MULTI_GOAL') && !question) return null
  if (decision === 'AMBIGUOUS' && suggestions.length < 2 && !question) return null
  return {
    decision,
    normalizedGoalText: normalizedGoalText as string | null,
    question: question as string | null,
    suggestions: suggestions.filter(isValidGoalText).slice(0, 3),
    goalCandidates: goalCandidates.filter(isValidGoalText),
    conversationTurn: conversationTurn as number,
    clarificationSessionId: (clarificationSessionId as string | null | undefined) ?? null,
  }
}

export function parseCreatedGoal(value: unknown): CreatedGoal | null {
  if (!isRecord(value) || !isId(value.goalId) || !isValidGoalText(value.goalText) || !SOURCES.includes(value.confirmationSource as ConfirmationSource)) return null
  return { goalId: value.goalId, goalText: value.goalText, confirmationSource: value.confirmationSource as ConfirmationSource }
}
