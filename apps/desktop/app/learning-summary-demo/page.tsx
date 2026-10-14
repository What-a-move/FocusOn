export default async function Page() {
  if (process.env.NODE_ENV !== 'development') {
    return <main className="p-8">학습 정리 개발 시연은 운영 환경에서 사용할 수 없습니다.</main>
  }
  const { default: LearningSummaryDemo } = await import('./Demo')
  return <LearningSummaryDemo />
}
