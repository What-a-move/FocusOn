export type DemoScenario = 'ready' | 'insufficient' | 'failed'

export type DemoSummary = {
  overview: string
  keyConcepts: Array<{
    name: string
    briefExplanation: string
    sessionEvidence: string
  }>
  nextStartPrompt: string
}

export const demoSummary: DemoSummary = {
  overview: 'React Query의 캐시와 쿼리 키에 관한 자료 두 가지를 살펴봤습니다. 이해하거나 구현을 완료했는지는 확인되지 않았습니다.',
  keyConcepts: [
    {
      name: '캐시',
      briefExplanation: '캐시와 갱신 개념을 다룬 자료를 확인했습니다.',
      sessionEvidence: 'React Query: 캐시와 갱신 개념을 다룬 자료 확인',
    },
    {
      name: '쿼리 키',
      briefExplanation: '쿼리 키 구성 예시를 확인했습니다.',
      sessionEvidence: '쿼리 키: 구성 예시 확인',
    },
  ],
  nextStartPrompt: '다음에는 캐시 갱신 조건을 다시 살펴보세요.',
}

export const scenarioLabels: Record<DemoScenario, string> = {
  ready: '정상 생성 · RELATED 근거 2개',
  insufficient: '근거 부족 · 1개만 허용',
  failed: '생성 실패 · 세션 기록 유지',
}

export function summaryCopyText(summary: DemoSummary): string {
  return [
    summary.overview,
    ...summary.keyConcepts.map((concept) =>
      `${concept.name}: ${concept.briefExplanation} (근거: ${concept.sessionEvidence})`,
    ),
    summary.nextStartPrompt,
  ].join('\n')
}
