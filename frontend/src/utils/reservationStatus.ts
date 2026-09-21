/**
 * 预约状态的用户视角文案与标签色，取值与后端 ReservationStatus 一致。
 * 管理端审批页（AdministrationReservations）刻意使用审批视角文案（待审批/已通过），不走本映射。
 */
import { parseDateStr } from './datetime'

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

/* —— 时间推导态 ——
   状态机没有 COMPLETED 落库态，「进行中/已结束」由业务时钟判定，供看板卡片与详情面板共用。 */

/** 预约当天的某个 HH:mm 时刻；结束小时可 ≥24（次日约定），setHours 自动进位到次日 */
export function reservationMoment(date: string, time: string): Date {
  const d = parseDateStr(date)
  const [h, m] = time.split(':').map(Number)
  d.setHours(h, m, 0, 0)
  return d
}

export type ReservationTimePhase = 'ongoing' | 'ended' | null

/**
 * 时间推导态：ongoing = 已确认且当前落在时段内；ended = 有效预约（已确认/待审核）已过结束时刻。
 * 已取消/已驳回不参与推导（自身已表达终态）。
 */
export function reservationTimePhase(
  r: { status: string; date: string; startTime: string; endTime: string },
  now: Date,
): ReservationTimePhase {
  const nowTs = now.getTime()
  const endTs = reservationMoment(r.date, r.endTime).getTime()
  if ((r.status === 'CONFIRMED' || r.status === 'PENDING') && endTs <= nowTs) return 'ended'
  if (r.status === 'CONFIRMED') {
    const startTs = reservationMoment(r.date, r.startTime).getTime()
    if (startTs <= nowTs && nowTs < endTs) return 'ongoing'
  }
  return null
}

export const RESERVATION_PHASE_TEXT: Record<Exclude<ReservationTimePhase, null>, string> = {
  ongoing: '进行中',
  ended: '已结束',
}

export const RESERVATION_PHASE_TAG: Record<Exclude<ReservationTimePhase, null>, 'success' | 'info'> = {
  ongoing: 'success',
  ended: 'info',
}
