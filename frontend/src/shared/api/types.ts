import type { ReservationStatus, Role, RoomFlag } from '@/types'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
}

export interface ApiErrorBody {
  code: string
  message: string
  data: null
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly data: unknown = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface LoginRequest {
  username: string
  password: string
}

export interface UserResponse {
  id: number | string
  username: string
  realName: string
  role: Role
}

export interface LoginResponse {
  token: string
  user: UserResponse
}

export interface RoomResponse {
  id: number | string
  name: string
  location: string
  capacity: number
  status: RoomFlag
  category: string
  facilities: string[]
}

export interface ReservationResponse {
  id: number | string
  requestId: string
  reservationNo: string
  roomId: number | string
  roomName: string
  userId: number | string
  userName: string
  title: string
  startTime: string
  endTime: string
  participantCount: number
  status: ReservationStatus
  displayStatus: string
  remark?: string | null
}

export interface CreateReservationRequest {
  requestId: string
  roomId: number | string
  title: string
  startTime: string
  endTime: string
  participantCount: number
  remark?: string
}
