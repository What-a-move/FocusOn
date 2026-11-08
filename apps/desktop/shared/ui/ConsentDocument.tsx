'use client'

import { useState } from 'react'
import type { Detail } from '../lib/consent-docs'
import './consent-doc.css'

/** 동의 문서 하나를 읽기 쉬운 문서 형태로 보여 준다. 여러 개면 위에 문서 고르기 탭을 둔다. */
export function ConsentDocument({ docs, titleId }: { docs: Detail[]; titleId: string }) {
  const [index, setIndex] = useState(0)
  const doc = docs[index] ?? docs[0]
  if (!doc) return null
  return <div className="fo-doc">
    {docs.length > 1 && <div className="fo-doc-tabs" role="tablist" aria-label="동의 문서">
      {docs.map((item, i) => <button key={item.title} type="button" role="tab" aria-selected={i === index} onClick={() => setIndex(i)}>{item.title}</button>)}
    </div>}
    <header className="fo-doc-head">
      <p className="fo-doc-meta"><span className={doc.required ? 'is-required' : ''}>{doc.required ? '필수' : '선택'}</span></p>
      <h2 id={titleId}>{doc.title}</h2>
      <p className="fo-doc-summary">{doc.summary}</p>
    </header>
    <div className="fo-doc-body" key={doc.title}>
      {doc.sections.map((section) => <section key={section.heading}>
        <h3>{section.heading}</h3>
        <ul>{section.items.map((item) => <li key={item}>{item}</li>)}</ul>
      </section>)}
    </div>
  </div>
}
