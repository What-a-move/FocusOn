import type { InterventionLevel, NotificationChannel } from '../api/session-api'

// 집중 준비와 설정이 같이 쓰는 개입 세기·알림 방식 선택지.
export const LEVELS: { key: InterventionLevel; label: string }[] = [{ key: 'GENTLE', label: '약하게' }, { key: 'NORMAL', label: '보통' }, { key: 'ACTIVE', label: '적극적으로' }]
export const CHANNELS: { key: NotificationChannel; label: string }[] = [{ key: 'MOLE_UI', label: '화면 위 포키' }, { key: 'MACOS_NOTIFICATION', label: 'macOS 알림' }, { key: 'SOUND', label: '소리' }]
// 미리보기 기준은 개입 정책 v1(화면 분석 5.2)과 같다.
export const PREVIEW: Record<InterventionLevel, { say: string; poki: string; alt: string; meta: [string, string, string] }> = {
  GENTLE: { say: '목표에서 조금 멀어진 것 같아요.', poki: '/design/poki-floating-master-v3.png', alt: '포키', meta: ['다른 화면이 ', '3분 넘게 이어지거나, 10분 안에 1분 넘게 3번', ' 이어지면 조용히 알려요'] },
  NORMAL: { say: '지금 화면이 목표와 달라 보여요.\n다시 돌아가 볼까요?', poki: '/design/poki-floating-curious-v8.png', alt: '궁금해하는 포키', meta: ['다른 화면이 ', '1분 넘게 이어지거나, 10분 안에 30초 넘게 2번', ' 이어지면 알려요'] },
  ACTIVE: { say: '어? 또 딴 데 가 있네!\n다시 목표로 가요!', poki: '/design/poki-floating-angry-v2.png', alt: '눈을 부릅뜬 포키', meta: ['다른 화면이 ', '30초 넘게 이어지거나, 10분 안에 20초 넘게 2번', ' 이어지면 바로 알려요'] },
}
