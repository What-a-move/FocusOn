'use client'

import { useState } from 'react'

export default function Page() {
  const [isStudying, setIsStudying] = useState(false)

  return (
    <main className="min-h-screen bg-[#f4f8f6] px-6 py-14 text-[#152b2a] sm:px-10 lg:px-24">
      <header className="mb-12 flex items-center gap-3.5">
        <div className="grid h-[38px] w-[38px] place-items-center rounded-xl bg-teal-700 text-[28px] leading-none text-white" aria-hidden="true">·</div>
        <div>
          <p className="mb-1 text-[13px] font-bold uppercase tracking-[0.12em] text-teal-700">FocusOn</p>
          <h1 className="text-[clamp(28px,4vw,42px)] font-bold tracking-[-0.05em]">집중을 켜는 시간</h1>
        </div>
      </header>

      <section className="max-w-[760px] rounded-3xl border border-[#d5e3de] bg-white p-7 sm:p-14" aria-labelledby="study-title">
        <div className="flex items-center gap-2 text-sm font-bold text-[#5a6e6b]">
          <span className={`h-2.5 w-2.5 rounded-full ${isStudying ? 'bg-teal-700' : 'bg-[#b7c7c2]'}`} />
          <span>{isStudying ? '학습 세션 진행 중' : '학습 세션 준비됨'}</span>
        </div>
        <h2 id="study-title" className="mb-3 mt-8 text-[clamp(22px,3vw,32px)] font-bold tracking-[-0.04em]">오늘의 학습 목표를 설정해보자</h2>
        <p className="mb-7 max-w-[460px] leading-7 text-[#6c7c78]">화면과 학습 흐름을 기록해서 집중 습관을 확인할 수 있어.</p>
        <button type="button" className="cursor-pointer rounded-xl border-0 bg-teal-700 px-[18px] py-[13px] font-bold text-white transition-colors hover:bg-teal-800" onClick={() => setIsStudying((current) => !current)}>
          {isStudying ? '학습 세션 일시정지' : '학습 세션 시작'}
        </button>
      </section>

      <section className="mt-[18px] grid max-w-[760px] grid-cols-1 gap-3 sm:grid-cols-3" aria-label="FocusOn 준비 상태">
        <article className="flex min-h-24 flex-col justify-between gap-4 rounded-2xl border border-[#d5e3de] bg-white p-[18px]"><span className="text-xs font-bold text-[#7c8d89]">목표</span><strong className="text-sm">아직 설정되지 않음</strong></article>
        <article className="flex min-h-24 flex-col justify-between gap-4 rounded-2xl border border-[#d5e3de] bg-white p-[18px]"><span className="text-xs font-bold text-[#7c8d89]">타이머</span><strong className="text-sm">00:00:00</strong></article>
        <article className="flex min-h-24 flex-col justify-between gap-4 rounded-2xl border border-[#d5e3de] bg-white p-[18px]"><span className="text-xs font-bold text-[#7c8d89]">분석</span><strong className="text-sm">대기 중</strong></article>
      </section>
    </main>
  )
}
