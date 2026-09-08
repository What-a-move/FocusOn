'use client'

import { useState } from 'react'

export default function Page() {
  const [isStudying, setIsStudying] = useState(false)

  return (
    <main className="min-h-[520px] bg-[#f7f7f7] px-[22px] py-7 text-[#171717]">
      <header className="mb-7 flex items-center gap-3">
        <div className="grid h-8 w-8 place-items-center rounded-[10px] bg-[#171717] text-2xl leading-none text-white" aria-hidden="true">·</div>
        <div>
          <p className="mb-0.5 text-[11px] font-bold uppercase tracking-[0.12em] text-[#555]">FocusOn</p>
          <h1 className="text-[25px] font-bold tracking-[-0.06em]">브라우저 집중 상태</h1>
        </div>
      </header>

      <section className="rounded-2xl border border-[#ddd] bg-white px-5 py-6" aria-labelledby="study-title">
        <div className="flex items-center gap-2 text-xs font-bold text-[#666]">
          <span className={`h-2 w-2 rounded-full ${isStudying ? 'bg-[#171717]' : 'bg-[#bbb]'}`} />
          <span>{isStudying ? '브라우저 학습 분석 중' : '브라우저 학습 준비됨'}</span>
        </div>
        <h2 id="study-title" className="mb-2.5 mt-6 text-[21px] font-bold leading-[1.35] tracking-[-0.05em]">현재 페이지를 학습 기록에 연결해보자</h2>
        <p className="mb-[22px] text-[13px] leading-[1.6] text-[#777]">페이지 이동과 체류 흐름을 기록해서 집중 습관을 확인할 수 있어.</p>
        <button type="button" className="w-full cursor-pointer rounded-[10px] border-0 bg-[#171717] px-3.5 py-3 text-[13px] font-bold text-white transition-colors hover:bg-[#444]" onClick={() => setIsStudying((current) => !current)}>
          {isStudying ? '분석 일시정지' : '분석 시작'}
        </button>
      </section>

      <section className="mt-3 grid gap-2" aria-label="FocusOn 브라우저 상태">
        <article className="flex items-center justify-between gap-3 rounded-xl border border-[#ddd] bg-white px-4 py-3.5"><span className="text-[11px] font-bold text-[#888]">현재 페이지</span><strong className="text-xs">확인 대기 중</strong></article>
        <article className="flex items-center justify-between gap-3 rounded-xl border border-[#ddd] bg-white px-4 py-3.5"><span className="text-[11px] font-bold text-[#888]">학습 목표</span><strong className="text-xs">설정되지 않음</strong></article>
        <article className="flex items-center justify-between gap-3 rounded-xl border border-[#ddd] bg-white px-4 py-3.5"><span className="text-[11px] font-bold text-[#888]">분석 상태</span><strong className="text-xs">{isStudying ? '진행 중' : '대기 중'}</strong></article>
      </section>
    </main>
  )
}
