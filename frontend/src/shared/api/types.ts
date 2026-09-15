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

/* —— 身份治理（identity 域）：用户/部门/信用 —— */

export interface AdminUserResponse {
  id: number | string
  username: string
  realName: string
  email?: string | null
  phone?: string | null
  role: Role
  status: number
  departmentId?: number | string | null
  departmentName?: string | null
  creditScore: number
  restrictedUntil?: string | null
}

export type ViolationType =
  | 'CREDIT_DEDUCT'
  | 'CREDIT_REWARD'
  | 'BLACKLIST_SET'
  | 'BLACKLIST_RELEASE'
  | 'ACCOUNT_DISABLE'
  | 'ACCOUNT_ENABLE'

export interface ViolationResponse {
  id: number | string
  userId: number | string
  violationType: ViolationType
  creditChange: number
  reason: string
  operatorName?: string | null
  createdAt: string
}

export interface QualificationResponse {
  userId: number | string
  eligible: boolean
  reason?: string | null
  creditScore?: number | null
  restrictedUntil?: string | null
}

export interface DepartmentResponse {
  id: number | string
  deptName: string
  description?: string | null
}

export interface CreateUserPayload {
  username: string
  password: string
  realName: string
  email?: string
  phone?: string
  role: Role
  departmentId?: number | string | null
}

export interface UpdateUserPayload {
  realName?: string
  email?: string
  phone?: string
  departmentId?: number | string | null
  role?: Role
}

export interface CreditAdjustPayload {
  creditChange: number
  reason: string
}

export interface RestrictPayload {
  reason: string
  /** ISO-8601；为空表示解除限制 */
  restrictedUntil?: string
}

export interface DepartmentPayload {
  deptName: string
  description?: string
}
