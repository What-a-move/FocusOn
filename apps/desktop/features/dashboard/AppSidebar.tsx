'use client'

import Image from 'next/image'
import Link from 'next/link'
import './dashboard.css'

// 아직 화면이 없는 메뉴는 링크 없이 보여 준다. 화면이 생기면 href를 채운다.
const NAVIGATION = [
  { key: 'home', label: '홈', asset: 'nav-home.png', href: '/' },
  { key: 'start', label: '집중 시작', asset: 'nav-start.svg', href: null },
  { key: 'records', label: '집중 기록', asset: 'nav-records.png', href: null },
  { key: 'notes', label: '학습 노트', asset: 'nav-notes.svg', href: null },
  { key: 'settings', label: '설정', asset: 'nav-settings.png', href: '/settings/' },
] as const

export type SidebarPage = (typeof NAVIGATION)[number]['key']

/** 홈과 하위 화면이 함께 쓰는 사이드바 (fo-base.css `.sidebar`). 하위 화면에서도 그대로 남고 내용만 바뀐다. */
export function AppSidebar({ active, name, onLogout, busy }: { active: SidebarPage; name: string; onLogout: () => void; busy: boolean }) {
  return <aside className="fo-sidebar" aria-label="주 메뉴">
    <span className="fo-logo fo-sidebar-logo">
      <Image className="fo-logo-light" src="/design/focuson-logo.png" alt="FocusOn" width={2172} height={724} priority />
      <Image className="fo-logo-dark" src="/design/focuson-logo-dark.png" alt="FocusOn" width={2172} height={724} priority />
    </span>
    <nav className="fo-nav">{NAVIGATION.map(({ key, label, asset, href }) => {
      const current = key === active
      const content = <><Image src={`/design/${asset}`} alt="" width={28} height={28} /><span>{label}</span></>
      const className = `fo-nav-item${current ? ' active' : ''}`
      return href && !current
        ? <Link className={className} href={href} key={key}>{content}</Link>
        : <span className={className} aria-current={current ? 'page' : undefined} key={key}>{content}</span>
    })}</nav>
    <div className="fo-sidebar-spacer" />
    <div className="fo-profile"><span className="fo-profile-avatar" aria-hidden="true" /><div><strong>{name}</strong><small>Google 계정</small></div></div>
    <button className="fo-sidebar-logout" type="button" onClick={onLogout} disabled={busy}>{busy ? '로그아웃 중…' : '로그아웃'}</button>
  </aside>
}
