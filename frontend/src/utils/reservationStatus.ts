/**
 * 预约状态的用户视角文案与标签色，取值与后端 ReservationStatus 一致。
 * 管理端审批页（AdministrationReservations）刻意使用审批视角文案（待审批/已通过），不走本映射。
 */
export const RESERVATION_STATUS_TEXT: Record<string, string> = {
  PENDING: '待审核',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
}

export const RESERVATION_STATUS_TAG: Record<string, 'primary' | 'warning' | 'info' | 'danger'> = {
  PENDING: 'warning',
  CONFIRMED: 'primary',
  REJECTED: 'danger',
  CANCELLED: 'info',
}
