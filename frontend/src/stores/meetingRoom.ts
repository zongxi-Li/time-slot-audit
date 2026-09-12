import { defineStore } from 'pinia'
import { ref } from 'vue'
import { mockMeetingRooms } from '@/mock/meetingRooms'
import { roomsApi } from '@/shared/api'
import { useMock } from '@/shared/api/config'
import type { MeetingRoom, RoomFlag } from '@/types'

export const useMeetingRoomStore = defineStore('meetingRoom', () => {
  const rooms = ref<MeetingRoom[]>(useMock ? mockMeetingRooms.map((r) => ({ ...r })) : [])
  const loaded = ref(useMock)

  async function refreshRooms() {
    if (useMock) return rooms.value
    rooms.value = await roomsApi.list()
    loaded.value = true
    return rooms.value
  }

  function getRoom(id: string): MeetingRoom | undefined {
    return rooms.value.find((r) => r.id === id)
  }

  const roomName = (id: string): string => getRoom(id)?.name ?? id

  /** 新增会议室（管理员），id 直接取名称，要求唯一 */
  function addRoom(input: Omit<MeetingRoom, 'id' | 'status'>): MeetingRoom | null {
    if (rooms.value.some((r) => r.id === input.name)) return null
    const room: MeetingRoom = { ...input, id: input.name, status: 'AVAILABLE' }
    rooms.value.push(room)
    return room
  }

  /** 编辑会议室基本信息（管理员） */
  function updateRoom(id: string, patch: Partial<Pick<MeetingRoom, 'location' | 'capacity' | 'equipment'>>) {
    const room = getRoom(id)
    if (room) Object.assign(room, patch)
  }

  /** 停用 / 启用（管理员）：停用后不可新建预约，已有预约保留展示 */
  function toggleRoomStatus(id: string): RoomFlag | undefined {
    const room = getRoom(id)
    if (!room) return undefined
    room.status = room.status === 'AVAILABLE' ? 'DISABLED' : 'AVAILABLE'
    return room.status
  }

  return { rooms, loaded, refreshRooms, getRoom, roomName, addRoom, updateRoom, toggleRoomStatus }
})
