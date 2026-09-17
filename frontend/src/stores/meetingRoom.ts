// 文件职责：Pinia store，维护会议室列表和管理员资源操作。
// 接口：调用 roomsApi、adminRoomsApi。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminRoomsApi, roomsApi } from '@/shared/api'
import type { MeetingRoom, RoomFlag } from '@/types'
import type { SaveRoomRequest } from '@/shared/api/types'

export const useMeetingRoomStore = defineStore('meetingRoom', () => {
  const rooms = ref<MeetingRoom[]>([])
  const loaded = ref(false)

  async function refreshRooms() {
    rooms.value = await roomsApi.list()
    loaded.value = true
    return rooms.value
  }

  function getRoom(id: string): MeetingRoom | undefined {
    return rooms.value.find((r) => r.id === id)
  }

  const roomName = (id: string): string => getRoom(id)?.name ?? id

  /* —— 管理端动作：校验与落库均由后端完成，错误向上抛出 —— */

  async function createRoom(payload: SaveRoomRequest): Promise<boolean> {
    await adminRoomsApi.create(payload)
    await refreshRooms()
    return true
  }

  async function saveRoom(id: string, payload: SaveRoomRequest): Promise<void> {
    await adminRoomsApi.update(id, payload)
    await refreshRooms()
  }

  /** 变更资源状态（AVAILABLE / MAINTENANCE / DISABLED） */
  async function changeRoomStatus(id: string, status: RoomFlag): Promise<void> {
    await adminRoomsApi.changeStatus(id, status)
    const room = getRoom(id)
    if (room) room.status = status
  }

  return {
    rooms,
    loaded,
    refreshRooms,
    getRoom,
    roomName,
    createRoom,
    saveRoom,
    changeRoomStatus,
  }
})
