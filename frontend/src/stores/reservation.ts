import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { mockReservations } from '@/mock/reservations'
import { reservationsApi } from '@/shared/api'
import { useMock } from '@/shared/api/config'
import { useAuthStore } from './auth'
import { useMeetingRoomStore } from './meetingRoom'
import { isTimeOverlap } from '@/utils/conflict'
import { toDateStr } from '@/utils/datetime'
import type {
  CurrentUser,
  MeetingRoom,
  Reservation,
  ReservationDraft,
} from '@/types'

/** 冲突检测所需的必填字段 */
export type ConflictQuery = Pick<
  ReservationDraft,
  'roomId' | 'date' | 'startTime' | 'endTime'
>

export const useReservationStore = defineStore('reservation', () => {
  /** Mock 内存数据：刷新页面后还原为初始 Mock，正式版由后端接口替换 */
  const reservations = ref<Reservation[]>(useMock ? mockReservations.map((r) => ({ ...r })) : [])

  const auth = useAuthStore()
  /** 当前用户由角色（auth store）决定：普通用户=李明，管理员=王建国 */
  const currentUser = computed<CurrentUser>(() => auth.currentUser)

  /** 有效预约（未取消） */
  const activeReservations = computed(() =>
    reservations.value.filter((r) => r.status === 'PENDING' || r.status === 'CONFIRMED'),
  )

  /** 我的预约：按日期时间倒序 */
  const myReservations = computed(() =>
    reservations.value
      .filter((r) => r.userId === auth.currentUser.id)
      .slice()
      .sort((a, b) => (b.date + b.startTime).localeCompare(a.date + a.startTime)),
  )

  function getById(id: string): Reservation | undefined {
    return reservations.value.find((r) => r.id === id)
  }

  async function refreshCalendar(date: string, roomId?: string) {
    if (useMock) return reservations.value
    const start = `${date}T00:00:00`
    const next = new Date(`${date}T00:00:00`)
    next.setDate(next.getDate() + 1)
    const end = `${toDateStr(next)}T00:00:00`
    const incoming = await reservationsApi.calendar(start, end, roomId)
    const preserved = reservations.value.filter((r) => r.date !== date || (roomId && r.roomId !== roomId))
    reservations.value = [...preserved, ...incoming]
    return incoming
  }

  async function refreshMine() {
    if (useMock) return myReservations.value
    reservations.value = await reservationsApi.mine()
    return reservations.value
  }

  /** 某会议室某天的有效预约，按开始时间升序（看板使用） */
  function listByRoomAndDate(roomId: string, date: string): Reservation[] {
    return activeReservations.value
      .filter((r) => r.roomId === roomId && r.date === date)
      .slice()
      .sort((a, b) => a.startTime.localeCompare(b.startTime))
  }

  /**
   * 时间冲突检测（核心业务逻辑）。
   * 返回与 [startTime, endTime) 相交的所有同会议室同日期有效预约。
   */
  function findConflicts(input: ConflictQuery, excludeId?: string): Reservation[] {
    return activeReservations.value.filter(
      (r) =>
        r.roomId === input.roomId &&
        r.date === input.date &&
        r.id !== excludeId &&
        isTimeOverlap(input.startTime, input.endTime, r.startTime, r.endTime),
    )
  }

  /** 推荐算法（简化版）：同一时间段没有冲突、且容量足够的其他启用会议室 */
  function findAvailableRooms(input: ReservationDraft): MeetingRoom[] {
    const roomStore = useMeetingRoomStore()
    return roomStore.rooms.filter(
      (room) =>
        room.status === 'AVAILABLE' &&
        room.capacity >= input.participantCount &&
        !activeReservations.value.some(
          (r) =>
            r.roomId === room.id &&
            r.date === input.date &&
            isTimeOverlap(input.startTime, input.endTime, r.startTime, r.endTime),
        ),
    )
  }

  /** 新增预约（调用方需先通过 findConflicts 检查）；user 供并发演示指定预约人 */
  async function addReservation(
    input: ReservationDraft,
    user: CurrentUser = auth.currentUser,
  ): Promise<Reservation> {
    const reservation: Reservation = {
      id: `u${Date.now()}_${Math.floor(Math.random() * 1000)}`,
      userId: user.id,
      userName: user.name,
      status: 'CONFIRMED',
      ...input,
    }
    if (useMock) {
      reservations.value.push(reservation)
      return reservation
    }
    const created = await reservationsApi.create(input)
    reservations.value.push(created)
    return created
  }

  /** 用户取消自己的预约 */
  async function cancelReservation(id: string): Promise<void> {
    if (!useMock) {
      const cancelled = await reservationsApi.cancel(id)
      const index = reservations.value.findIndex((r) => r.id === id)
      if (index >= 0) reservations.value[index] = cancelled
      return
    }
    const target = reservations.value.find((r) => r.id === id)
    if (target && target.status !== 'CANCELLED') {
      target.status = 'CANCELLED'
    }
  }

  /* —— 管理员操作 —— */

  /** 审核通过：待审核 → 已预约 */
  function approveReservation(id: string): boolean {
    const target = reservations.value.find((r) => r.id === id && r.status === 'PENDING')
    if (target) {
      target.status = 'CONFIRMED'
      return true
    }
    return false
  }

  /** 审核驳回：待审核 → 已取消 */
  function rejectReservation(id: string): boolean {
    const target = reservations.value.find((r) => r.id === id && r.status === 'PENDING')
    if (target) {
      target.status = 'REJECTED'
      return true
    }
    return false
  }

  /** 强制取消任意未取消的预约（管理员），需给出原因 */
  function forceCancelReservation(id: string): boolean {
    const target = reservations.value.find((r) => r.id === id && r.status !== 'CANCELLED')
    if (target) {
      target.status = 'CANCELLED'
      return true
    }
    return false
  }

  return {
    reservations,
    currentUser,
    activeReservations,
    refreshCalendar,
    refreshMine,
    myReservations,
    getById,
    listByRoomAndDate,
    findConflicts,
    findAvailableRooms,
    addReservation,
    cancelReservation,
    approveReservation,
    rejectReservation,
    forceCancelReservation,
  }
})
