'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { OnboardingShell } from './OnboardingShell'
import { onboardingApi, type OnboardingSummary } from './onboarding-api'

export function DoneStep() {
  const router = useRouter()
  const [summary, setSummary] = useState<OnboardingSummary | null>(null)
  const [extensionLinked, setExtensionLinked] = useState(false)

  useEffect(() => {
    // [유지보수] 요약은 서버가 확정한 값만 보여 준다. 불러오지 못하면 요약 없이 완료 안내만 둔다.
    void onboardingApi.getSummary().then((result) => { if (result.success) setSummary(result.data) })
    void onboardingApi.getReadiness().then((result) => { if (result.success) setExtensionLinked(result.data.steps.extensionLinked) })
  }, [])

  const chips = summary ? [
    ...(extensionLinked ? ['Chrome 연결됨'] : []),
    summary.screenAnalysisReady ? '화면 기록 허용' : '화면 기록 확인 필요',
    summary.cameraConsent ? '카메라 사용' : '카메라 사용 안 함',
    summary.excludedDomainCount ? `제외 사이트 ${summary.excludedDomainCount}개` : '제외 사이트 없음',
  ] : []

  return (
    <OnboardingShell currentStep={4} next={{ label: '홈으로 가기', blocked: false, onClick: () => { void onboardingApi.getReadiness().finally(() => router.push('/')) } }}>
      <img className="poki" src="/design/poki-floating-cheer-v7.png" alt="응원하는 포키" />
      <h1 id="done-title">{summary?.nickname ? `${summary.nickname}님, ` : ''}준비가 끝났어요!</h1>
      <p>홈에서 오늘 목표를 정하면 바로 집중을 시작할 수 있어요.</p>
      {chips.length ? <ul className="summary" aria-label="준비한 항목">{chips.map((chip) => <li key={chip}>{chip}</li>)}</ul> : null}
    </OnboardingShell>
  )
}
