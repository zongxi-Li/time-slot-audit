// 文件职责：封装会议室条件筛选与时段空闲查询 API（独立于 index.ts 的只读查询模块）。
// 接口：GET /api/rooms?location&minCapacity&facility、GET /api/rooms/available?date&startTime&endTime&…。
import { buildQuery, request } from './http'
import type { MeetingRoom } from '@/types'
import type { RoomResponse } from './types'

export interface RoomFilterParams {
  location?: string
  minCapacity?: number
  facility?: string
}

const toRoom = (room: RoomResponse): MeetingRoom => ({
  id: String(room.id),
  name: room.name,
  location: room.location,
  capacity: room.capacity,
  status: room.status,
  category: room.category,
  equipment: room.facilities,
})

const toQuery = (params: RoomFilterParams) => ({
  location: params.location?.trim() || undefined,
  minCapacity: params.minCapacity ?? undefined,
  facility: params.facility?.trim() || undefined,
})

export const roomQueryApi = {
  /** 按位置/最小容量/设施筛选会议室台账；全部缺省即全量。 */
  async list(params: RoomFilterParams = {}) {
    return (await request<RoomResponse[]>(`/rooms${buildQuery(toQuery(params))}`)).map(toRoom)
  },
  /**
   * 查询指定日期与时段内空闲的会议室（HH:mm 起止；结束不晚于开始按次日结束计算，
   * 由后端推导）。可与筛选条件叠加。
   */
  async available(date: string, startTime: string, endTime: string, params: RoomFilterParams = {}) {
    const query = buildQuery({ date, startTime, endTime, ...toQuery(params) })
    return (await request<RoomResponse[]>(`/rooms/available${query}`)).map(toRoom)
  },
}
