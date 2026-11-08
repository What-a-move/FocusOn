import type { AuthSessionSnapshot } from '../../electron/shared/auth-contract.cjs'

export type AuthView = 'loading' | 'login' | 'offline' | 'onboarding' | 'home'

// [유지보수] 화면 선택은 서버가 확정한 인증·온보딩 상태만 사용한다. 로컬 클릭만으로 완료 상태를 만들지 않는다.
export function selectAuthView(snapshot: AuthSessionSnapshot | null): AuthView {
  if (!snapshot) return 'loading'
  if (snapshot.status === 'OFFLINE') return 'offline'
  if (snapshot.status === 'SIGNED_IN' && snapshot.user) {
    if (snapshot.onboardingStatus === 'COMPLETED') return 'home'
    if (snapshot.onboardingStatus === 'INCOMPLETE') return 'onboarding'
  }
  if (snapshot.status === 'LINK_REQUIRED' && snapshot.user) return 'onboarding'
  return 'login'
}
