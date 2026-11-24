import type { IpcResult } from './auth-contract.cjs'

// IPC_CONTRACT.md 목표 Channel. 목표 설정 화면(D06·D07)이 쓴다.
export const GOAL_CHANNELS = {
  clarify: 'goal:clarify',
  create: 'goal:create',
  listOpen: 'goal:listOpen',
} as const

export type GoalDecision = 'CLEAR' | 'AMBIGUOUS' | 'MULTI_GOAL' | 'UNKNOWN_TERM' | 'INVALID'
export type ConfirmationSource = 'AI_CONFIRMED' | 'MANUAL_CONFIRMED'
export type GoalProfile = {
  mainTopic: string
  purpose: string
  coreTopics: string[]
  supportingTopics: string[]
  expectedActivities: string[]
}
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
  goalProfileDraft?: GoalProfile
}
export type CreateGoalInput = { goalText: string; confirmationSource: ConfirmationSource; clarificationSessionId?: string; goalProfile?: GoalProfile; idempotencyKey?: string }
export type CreatedGoal = { goalId: string; goalText: string; confirmationSource: ConfirmationSource }

export type OpenGoalList = { items: CreatedGoal[]; nextCursor: string | null }

export type GoalsApi = {
  clarify(input: ClarifyInput): Promise<IpcResult<ClarifyResult>>
  create(input: CreateGoalInput): Promise<IpcResult<CreatedGoal>>
  listOpen(cursor?: string): Promise<IpcResult<OpenGoalList>>
}

export const MAX_QUESTIONS = 2
const DECISIONS: readonly GoalDecision[] = ['CLEAR', 'AMBIGUOUS', 'MULTI_GOAL', 'UNKNOWN_TERM', 'INVALID']
const CLARITY_TO_DECISION = new Map<string, GoalDecision>([
  ['CLEAR', 'CLEAR'], ['NEEDS_SUGGESTION', 'AMBIGUOUS'], ['NEEDS_QUESTION', 'AMBIGUOUS'],
  ['NEEDS_SELECTION', 'MULTI_GOAL'], ['UNRECOGNIZED_TERM', 'UNKNOWN_TERM'], ['INVALID', 'INVALID'],
])
const PURPOSES = ['CONCEPT_LEARNING', 'IMPLEMENTATION', 'PROBLEM_SOLVING', 'DEBUGGING', 'PRACTICE', 'PRESENTATION_OR_ASSIGNMENT', 'OTHER']
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

function isProfileList(value: unknown): value is string[] {
  return Array.isArray(value) && value.length <= 5 && value.every((item) => isText(item, 1, 100))
    && new Set(value.map((item) => item.trim().toLowerCase())).size === value.length
}

export function isGoalProfile(value: unknown): value is GoalProfile {
  return isRecord(value) && Object.keys(value).length === 5 && isText(value.mainTopic, 1, 100)
    && typeof value.purpose === 'string' && PURPOSES.includes(value.purpose) && isProfileList(value.coreTopics)
    && isProfileList(value.supportingTopics) && isProfileList(value.expectedActivities)
}

export function isGoalListCursor(value: unknown): value is string | null {
  return value === null || isText(value, 1, 512)
}

export function isValidCreateGoalInput(value: unknown): value is CreateGoalInput {
  if (!isRecord(value) || !isValidGoalText(value.goalText) || !SOURCES.includes(value.confirmationSource as ConfirmationSource)) return false
  if (!Object.keys(value).every((key) => ['goalText', 'confirmationSource', 'clarificationSessionId', 'goalProfile', 'idempotencyKey'].includes(key))) return false
  return (value.clarificationSessionId === undefined || isId(value.clarificationSessionId))
    && (value.goalProfile === undefined || isGoalProfile(value.goalProfile))
    && (value.idempotencyKey === undefined || (typeof value.idempotencyKey === 'string'
      && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu.test(value.idempotencyKey)))
}

export function parseClarifyResult(value: unknown): ClarifyResult | null {
  if (!isRecord(value)) return null
  const decision = DECISIONS.includes(value.decision as GoalDecision) ? value.decision as GoalDecision
    : typeof value.clarityStatus === 'string' ? CLARITY_TO_DECISION.get(value.clarityStatus) : undefined
  if (!decision) return null
  const { normalizedGoalText, question, suggestions, goalCandidates, conversationTurn, clarificationSessionId, goalProfileDraft } = value
  if (!(normalizedGoalText === null || isText(normalizedGoalText, 1, 200)) || !(question === null || typeof question === 'string')) return null
  if (!isStringList(suggestions) || !isStringList(goalCandidates) || !Number.isInteger(conversationTurn)) return null
  // clarificationSessionId는 2026-10-24 확정 필드다(결정 020). 첫 응답 전에는 없으므로 없으면 null로 읽는다.
  if (!(clarificationSessionId === undefined || clarificationSessionId === null || isId(clarificationSessionId))) return null
  // 화면에 보여 줄 수 있는(2~40자) 선택지만 남긴 뒤 개수를 센다.
  const choices = suggestions.filter(isValidGoalText).slice(0, 3)
  const candidates = [...new Set(goalCandidates.filter(isValidGoalText))].slice(0, 3)
  // [주의] 결정에 필요한 값이 없으면 화면에서 할 수 있는 일이 없으므로 계약 위반으로 본다.
  if (decision === 'MULTI_GOAL' && candidates.length < 2) return null
  if ((decision === 'UNKNOWN_TERM' || decision === 'MULTI_GOAL') && !question) return null
  if (decision === 'AMBIGUOUS' && choices.length < 2 && !question) return null
  return {
    decision,
    normalizedGoalText: normalizedGoalText as string | null,
    question: question as string | null,
    suggestions: choices,
    goalCandidates: candidates,
    conversationTurn: conversationTurn as number,
    clarificationSessionId: (clarificationSessionId as string | null | undefined) ?? null,
    ...(isGoalProfile(goalProfileDraft) ? { goalProfileDraft } : {}),
  }
}

export function parseCreatedGoal(value: unknown): CreatedGoal | null {
  if (!isRecord(value) || !isId(value.goalId) || !isValidGoalText(value.goalText) || !SOURCES.includes(value.confirmationSource as ConfirmationSource)) return null
  return { goalId: value.goalId, goalText: value.goalText, confirmationSource: value.confirmationSource as ConfirmationSource }
}

export function parseOpenGoalList(value: unknown): OpenGoalList | null {
  if (!isRecord(value) || !Array.isArray(value.items) || !isGoalListCursor(value.nextCursor)) return null
  const items = value.items.map(parseCreatedGoal)
  if (items.some((item) => item === null)) return null
  return { items: items.filter((item) => item !== null), nextCursor: value.nextCursor }
}
