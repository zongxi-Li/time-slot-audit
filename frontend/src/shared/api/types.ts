// 文件职责：定义后端 API 请求、响应、错误和资源类型。
// 接口：对应 Spring Boot Controller 的 JSON 契约。
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

export type SystemTimeMode = 'REALTIME' | 'FIXED'

/** Unified business clock returned by the server. */
export interface SystemTimeResponse {
  currentTime: string
  mode: SystemTimeMode
}

/** 全局可预约时段；分钟数相对预约日期 00:00，endMinute > 1440 表示延伸到次日 */
export interface BookingWindowResponse {
  startMinute: number
  endMinute: number
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
  version: number
}

export interface CreateReservationRequest {
  requestId: string
  roomId: number | string
  title: string
  startTime: string
  endTime: string
  participantCount: number
  remark?: string
  /** 周期性会议（按周重复）周数：缺省/1 为单次，2..8 由后端逐周校验后批量创建 */
  repeatWeeks?: number
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
