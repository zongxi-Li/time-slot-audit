import type { ViolationType } from '@/shared/api'

/** 违规/信用记录类型元数据（管理端与用户端共用），取值与后端 ViolationType 一致 */
export const VIOLATION_META: Record<ViolationType, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  CREDIT_DEDUCT: { label: '信用扣分', type: 'danger' },
  CREDIT_REWARD: { label: '信用奖励', type: 'success' },
  BLACKLIST_SET: { label: '进入限制', type: 'danger' },
  BLACKLIST_RELEASE: { label: '解除限制', type: 'success' },
  ACCOUNT_DISABLE: { label: '账号禁用', type: 'warning' },
  ACCOUNT_ENABLE: { label: '账号启用', type: 'info' },
}

export function violationLabel(type: string) {
  return VIOLATION_META[type as ViolationType]?.label ?? type
}

export function violationTagType(type: string) {
  return VIOLATION_META[type as ViolationType]?.type ?? 'info'
}

/** 后端时间为 ISO-8601（yyyy-MM-ddTHH:mm:ss），展示为 yyyy-MM-dd HH:mm */
export function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
