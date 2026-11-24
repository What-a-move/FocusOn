import type { InterventionLevel, NotificationChannel } from '../api/session-api'

// 집중 준비와 설정이 같이 쓰는 개입 세기·알림 방식 선택지.
export const LEVELS: { key: InterventionLevel; label: string }[] = [{ key: 'GENTLE', label: '약하게' }, { key: 'NORMAL', label: '보통' }, { key: 'ACTIVE', label: '적극적으로' }]
export const CHANNELS: { key: NotificationChannel; label: string }[] = [{ key: 'MOLE_UI', label: '화면 위 포키' }, { key: 'MACOS_NOTIFICATION', label: 'macOS 알림' }, { key: 'SOUND', label: '소리' }]
// 미리보기 기준은 개입 정책 v2(Notion 화면 분석 5.2, 2026-11-23)와 같다. 그림은 강도별 실제 팻말 포키.
export const PREVIEW: Record<InterventionLevel, { scene: string; poki: string; meta: [string, string, string, string] }> = {
  GENTLE: { scene: '구석에서 팻말만 잠깐 올라와요', poki: '/design/poki-sign-gentle.webp', meta: ['다른 화면이 ', '3분 넘게', ' 이어지거나', ' 최근 10분 중 절반 이상일 때'] },
  NORMAL: { scene: '포키가 팻말을 들고 고개를 내밀어요', poki: '/design/poki-sign-normal.webp', meta: ['다른 화면이 ', '1분 넘게', ' 이어지거나', ' 최근 10분 중 40% 이상일 때 · 또 그러면 화면이 흐려져요'] },
  ACTIVE: { scene: '화면을 흐리고 크게 나타나요', poki: '/design/poki-active-sign-1.webp', meta: ['다른 화면이 ', '30초 넘게', ' 이어지거나', ' 최근 10분 중 30% 이상일 때'] },
}
