import type { InterventionLevel, NotificationChannel } from '../api/session-api'

// 집중 준비와 설정이 같이 쓰는 개입 세기·알림 방식 선택지.
export const LEVELS: { key: InterventionLevel; label: string }[] = [{ key: 'GENTLE', label: '약하게' }, { key: 'NORMAL', label: '보통' }, { key: 'ACTIVE', label: '적극적으로' }]
export const CHANNELS: { key: NotificationChannel; label: string }[] = [{ key: 'MOLE_UI', label: '화면 위 포키' }, { key: 'MACOS_NOTIFICATION', label: 'macOS 알림' }, { key: 'SOUND', label: '소리' }]
// 미리보기 기준은 개입 정책 v2(Notion 화면 분석 5.2, 2026-11-23)와 같다. 포키 말은 반말.
export const PREVIEW: Record<InterventionLevel, { say: string; poki: string; alt: string; meta: [string, string, string] }> = {
  GENTLE: { say: '할 일에서 조금 멀어진 것 같아.', poki: '/design/poki-floating-master-v3.png', alt: '포키', meta: ['다른 화면이 ', '3분 넘게', ' 이어지거나 최근 10분 중 절반 이상일 때'] },
  NORMAL: { say: '지금 화면이 할 일이랑 달라 보여.\n다시 돌아가 볼까?', poki: '/design/poki-floating-curious-v8.png', alt: '궁금해하는 포키', meta: ['다른 화면이 ', '1분 넘게', ' 이어지거나 최근 10분 중 40% 이상일 때'] },
  ACTIVE: { say: '어? 또 딴 데 가 있네!\n얼른 돌아가자!', poki: '/design/poki-floating-angry-v2.png', alt: '눈을 부릅뜬 포키', meta: ['다른 화면이 ', '30초 넘게', ' 이어지거나 최근 10분 중 30% 이상일 때'] },
}
