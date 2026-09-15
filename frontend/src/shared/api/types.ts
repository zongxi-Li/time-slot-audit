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
  description?: string | null
}

export interface CategoryResponse {
  id: number | string
  name: string
  minCapacity: number
  maxCapacity: number
  approvalRequired: boolean
  maxDurationMinutes: number
  advanceDays: number
  description?: string | null
}

export interface SaveCategoryRequest {
  name: string
  minCapacity: number
  maxCapacity: number
  approvalRequired: boolean
  maxDurationMinutes: number
  advanceDays: number
  description?: string | null
}

export interface SaveRoomRequest {
  name: string
  categoryId: number | string
  location?: string | null
  capacity: number
  description?: string | null
}

export interface FacilityResponse {
  id: number | string
  roomId: number | string
  name: string
  quantity: number
  description?: string | null
}

export interface SaveFacilityRequest {
  name: string
  quantity: number
  description?: string | null
}

export interface OpenRuleResponse {
  id: number | string
  roomId: number | string
  weekday: number
  openTime: string
  closeTime: string
  enabled: boolean
}

export interface SaveOpenRuleRequest {
  weekday: number
  openTime: string
  closeTime: string
  enabled?: boolean
}

export interface RoomDetailResponse {
  id: number | string
  name: string
  location: string
  capacity: number
  status: RoomFlag
  categoryId: number | string
  category: string
  description?: string | null
  facilities: FacilityResponse[]
  openRules: OpenRuleResponse[]
}

export interface SaveMaintenanceRequest {
  reason: string
  startTime: string
  endTime: string
}

export interface MaintenanceResponse {
  id: number | string
  roomId: number | string
  reason: string
  startTime: string
  endTime: string
  status: 'PLANNED' | 'FINISHED'
  createdBy: number | string
  createdAt: string
}

export interface RepairTicketResponse {
  id: number | string
  roomId: number | string
  roomName: string
  facilityId?: number | string | null
  facilityName: string
  issue: string
  status: 'OPEN' | 'RESOLVED'
  reporterId: number | string
  reporterName: string
  createdAt: string
  resolvedAt?: string | null
  resolveRemark?: string | null
}

export interface CreateRepairTicketRequest {
  facilityId?: number | string | null
  facilityName?: string | null
  issue: string
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
