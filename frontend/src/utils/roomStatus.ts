import type { RoomFlag } from '@/types'

/**
 * 会议室资源状态的统一展示语义。
 * 预约页面和会议室卡片均以此为准，避免同一资源在不同页面出现不同颜色。
 */
export const roomStatusMeta: Record<RoomFlag, { label: string; tone: 'available' | 'maintenance' | 'disabled' }> = {
  AVAILABLE: { label: '可预约', tone: 'available' },
  MAINTENANCE: { label: '维护中', tone: 'maintenance' },
  DISABLED: { label: '已停用', tone: 'disabled' },
}
