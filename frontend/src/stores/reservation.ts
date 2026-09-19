// 文件职责：Pinia store，维护预约日历、我的预约、创建和取消预约。
// 接口：调用 reservationsApi；预约状态完全以后端 Response 为准，前端不自造状态机。
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { reservationsApi } from '@/shared/api'
import { useAuthStore } from './auth'
import { useMeetingRoomStore } from './meetingRoom'
import { isTimeOverlap } from '@/utils/conflict'
import { addDays, BOARD_WINDOW_BEFORE, BOARD_WINDOW_AFTER } from '@/utils/datetime'
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
  /** 缓存的后端预约数据；最终裁决（冲突/状态）始终在 Spring Boot + MySQL */
  const reservations = ref<Reservation[]>([])

  const auth = useAuthStore()
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

  /** 用后端返回的最新数据替换本地同 id 记录 */
  function replaceLocalReservation(updated: Reservation) {
    const index = reservations.value.findIndex((r) => r.id === updated.id)
    if (index >= 0) reservations.value[index] = updated
    else reservations.value.push(updated)
  }

  /** 拉取以 date 为中心的滚动窗口（前 4 天 ~ 后 4 天）的日历数据，日/周视图共用 */
  async function refreshCalendar(date: string, roomId?: string) {
    const first = addDays(date, -BOARD_WINDOW_BEFORE)
    const last = addDays(date, BOARD_WINDOW_AFTER)
    const afterLast = addDays(last, 1)
    const incoming = await reservationsApi.calendar(
      `${first}T00:00:00`,
      `${afterLast}T00:00:00`,
      roomId,
    )
    const preserved = reservations.value.filter(
      (r) => r.date < first || r.date > last || (roomId && r.roomId !== roomId),
    )
    reservations.value = [...preserved, ...incoming]
    return incoming
  }

  async function refreshMine() {
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
   * 时间冲突检测（基于已加载日历数据的本地预览，仅用于 UI 提示）。
   * 返回与 [startTime, endTime) 相交的所有同会议室同日期有效预约；
   * 最终裁决由后端事务内的冲突查询给出。
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
        !findConflicts({ ...input }).some((r) => r.roomId === room.id),
    )
  }

  /** 新增预约：状态由后端根据会议室审批规则决定（CONFIRMED 或 PENDING） */
  async function addReservation(input: ReservationDraft, requestId: string): Promise<Reservation> {
    const created = await reservationsApi.create(input, requestId)
    replaceLocalReservation(created)
    return created
  }

  /** 用户取消自己的预约：以取消后的后端 Response 为准 */
  async function cancelReservation(id: string, reason?: string): Promise<void> {
    const cancelled = await reservationsApi.cancel(id, reason)
    replaceLocalReservation(cancelled)
  }

  /** 修改/改期：改到需审批的会议室时后端会把状态重算为 PENDING */
  async function updateReservation(id: string, input: ReservationDraft): Promise<Reservation> {
    const current = getById(id)
    if (!current) throw new Error('预约不存在或已过期，请刷新后重试')
    const updated = await reservationsApi.update(id, input, current.version)
    replaceLocalReservation(updated)
    return updated
  }

  return {
    reservations,
    currentUser,
    activeReservations,
    refreshCalendar,
    refreshMine,
    myReservations,
    getById,
    replaceLocalReservation,
    listByRoomAndDate,
    findConflicts,
    findAvailableRooms,
    addReservation,
    cancelReservation,
    updateReservation,
  }
})
