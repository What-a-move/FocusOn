'use client'

import { useEffect, useState } from 'react'
import { demoSummary, scenarioLabels, summaryCopyText, type DemoScenario } from './fixtures'

type DemoStatus = 'IDLE' | 'QUEUED' | 'GENERATING' | 'READY' | 'INSUFFICIENT_DATA' | 'FAILED'

export default function LearningSummaryDemo() {
  const [scenario, setScenario] = useState<DemoScenario>('ready')
  const [status, setStatus] = useState<DemoStatus>('IDLE')
  const [copyMessage, setCopyMessage] = useState('')

  useEffect(() => {
    if (status !== 'QUEUED') return
    const timer = window.setTimeout(() => setStatus('GENERATING'), 450)
    return () => window.clearTimeout(timer)
  }, [status])

  useEffect(() => {
    if (status !== 'GENERATING') return
    const timer = window.setTimeout(() => {
      setStatus(scenario === 'ready' ? 'READY' : scenario === 'insufficient' ? 'INSUFFICIENT_DATA' : 'FAILED')
    }, 900)
    return () => window.clearTimeout(timer)
  }, [scenario, status])

  async function copySummary() {
    try {
      await navigator.clipboard.writeText(summaryCopyText(demoSummary))
      setCopyMessage('정리 내용을 복사했습니다.')
    } catch {
      setCopyMessage('복사할 수 없습니다. 브라우저 클립보드 권한을 확인해 주세요.')
    }
  }

  const pending = status === 'QUEUED' || status === 'GENERATING'

  return (
    <main className="min-h-screen bg-[#f4f8f6] px-5 py-12 text-[#152b2a] sm:px-10">
      <div className="mx-auto max-w-[760px]">
        <div className="mb-5 inline-flex rounded-full bg-amber-100 px-3 py-1 text-sm font-bold text-amber-900">개발 시연 · 가짜 데이터</div>
        <h1 className="text-3xl font-bold tracking-tight">세션 종료 후 학습 정리</h1>
        <p className="mt-3 leading-7 text-[#546763]">실제 화면 분석, AI, 서버 저장은 연결되지 않았습니다. 종료부터 결과까지의 화면 흐름만 확인합니다.</p>

        <section className="mt-8 rounded-3xl border border-[#d5e3de] bg-white p-6 sm:p-9" aria-labelledby="session-title">
          <h2 id="session-title" className="text-xl font-bold">가상 학습 세션</h2>
          <p className="mt-2 text-sm text-[#546763]">목표: React Query의 캐시와 쿼리 키 공부</p>
          <label htmlFor="scenario" className="mt-7 block text-sm font-bold">테스트할 상황</label>
          <select
            id="scenario"
            value={scenario}
            disabled={pending}
            onChange={(event) => {
              setScenario(event.target.value as DemoScenario)
              setStatus('IDLE')
              setCopyMessage('')
            }}
            className="mt-2 w-full rounded-xl border border-[#9eb7ae] bg-white px-4 py-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-700"
          >
            {Object.entries(scenarioLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
          <button
            type="button"
            disabled={pending}
            onClick={() => {
              setCopyMessage('')
              setStatus('QUEUED')
            }}
            className="mt-5 rounded-xl bg-teal-700 px-5 py-3 font-bold text-white hover:bg-teal-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {status === 'IDLE' ? '가상 세션 종료' : '다시 시연하기'}
          </button>
        </section>

        <section className="mt-5 rounded-3xl border border-[#d5e3de] bg-white p-6 sm:p-9" aria-live="polite" aria-atomic="true" aria-labelledby="summary-title">
          <h2 id="summary-title" className="text-xl font-bold">학습 정리</h2>
          {status === 'IDLE' && <p className="mt-4 text-[#546763]">세션을 종료하면 정리 상태를 볼 수 있습니다.</p>}
          {status === 'QUEUED' && <p className="mt-4 text-[#546763]">QUEUED · 세션 종료와 시간 기록이 확정됐다고 가정합니다.</p>}
          {status === 'GENERATING' && <p className="mt-4 text-[#546763]">GENERATING · 로컬 모의 정리 중입니다. 실제 AI 호출은 없습니다.</p>}
          {status === 'READY' && (
            <div className="mt-5 space-y-6">
              <p className="leading-7">{demoSummary.overview}</p>
              <div>
                <h3 className="font-bold">핵심 개념</h3>
                <ul className="mt-3 space-y-3">
                  {demoSummary.keyConcepts.map((concept) => (
                    <li key={concept.name} className="rounded-xl bg-[#f4f8f6] p-4">
                      <strong>{concept.name}</strong><p className="mt-1">{concept.briefExplanation}</p>
                      <p className="mt-2 text-sm text-[#546763]">관찰 근거: {concept.sessionEvidence}</p>
                    </li>
                  ))}
                </ul>
              </div>
              <p className="rounded-xl border border-teal-200 bg-teal-50 p-4">다음 시작 지점: {demoSummary.nextStartPrompt}</p>
              <button type="button" onClick={copySummary} className="rounded-xl border border-teal-700 px-5 py-3 font-bold text-teal-800 hover:bg-teal-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-700">정리 복사</button>
              {copyMessage && <p role="status" className="text-sm">{copyMessage}</p>}
            </div>
          )}
          {status === 'INSUFFICIENT_DATA' && <p className="mt-4 rounded-xl bg-amber-50 p-4 text-amber-900">INSUFFICIENT_DATA · 서로 다른 관련 근거가 2개 미만이어서 정리를 만들지 않았습니다. 세션 종료와 시간 기록은 유지됩니다.</p>}
          {status === 'FAILED' && <p className="mt-4 rounded-xl bg-rose-50 p-4 text-rose-900">FAILED · 가상 생성에 실패했습니다. 세션 종료와 시간 기록은 유지됩니다.</p>}
        </section>
      </div>
    </main>
  )
}
