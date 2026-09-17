import type { Reservation, MeetingRoom, CurrentUser, ReservationDraft } from '@/types'
import { buildQuery, request } from './http'
import { TOKEN_STORAGE_KEY } from './config'
import type {
  AdminUserResponse,
  CategoryResponse,
  CreateRepairTicketRequest,
  CreateUserPayload,
  CreditAdjustPayload,
  DepartmentPayload,
  DepartmentResponse,
  QualificationResponse,
  RestrictPayload,
  UpdateUserPayload,
  CreateReservationRequest,
  FacilityResponse,
  LoginRequest,
  LoginResponse,
  MaintenanceResponse,
  OpenRuleResponse,
  ReservationResponse,
  RepairTicketResponse,
  RoomDetailResponse,
  RoomResponse,
  SaveFacilityRequest,
  SaveMaintenanceRequest,
  SaveOpenRuleRequest,
  SaveRoomRequest,
  SaveCategoryRequest,
  UserResponse,
  ViolationResponse,
} from './types'

const toRoom = (room: RoomResponse): MeetingRoom => ({
  id: String(room.id),
  name: room.name,
  location: room.location,
  capacity: room.capacity,
  status: room.status,
  category: room.category,
  equipment: room.facilities,
})

const toReservation = (reservation: ReservationResponse): Reservation => {
  const start = reservation.startTime.slice(0, 16)
  const end = reservation.endTime.slice(0, 16)
  return {
    id: String(reservation.id),
    requestId: reservation.requestId,
    title: reservation.title,
    roomId: String(reservation.roomId),
    userId: String(reservation.userId),
    userName: reservation.userName,
    date: start.slice(0, 10),
    startTime: start.slice(11),
    endTime: end.slice(11),
    participantCount: reservation.participantCount,
    remark: reservation.remark ?? undefined,
    status: reservation.status,
    displayStatus: reservation.displayStatus as Reservation['displayStatus'],
  }
}

const toUser = (user: UserResponse): CurrentUser => ({
  id: String(user.id),
  name: user.realName,
  department: '',
  role: user.role,
})

export const authApi = {
  async login(credentials: LoginRequest) {
    const result = await request<LoginResponse>('/auth/login', { method: 'POST', body: credentials })
    localStorage.setItem(TOKEN_STORAGE_KEY, result.token)
    return { ...result, currentUser: toUser(result.user) }
  },
  async me() {
    return toUser(await request<UserResponse>('/users/me'))
  },
}

/* —— 身份治理（identity 域）—— */

export const departmentsApi = {
  async list() {
    return request<DepartmentResponse[]>('/admin/departments')
  },
  async create(body: DepartmentPayload) {
    return request<DepartmentResponse>('/admin/departments', { method: 'POST', body })
  },
  async update(id: number | string, body: DepartmentPayload) {
    return request<DepartmentResponse>(`/admin/departments/${id}`, { method: 'PUT', body })
  },
}

export const adminUsersApi = {
  async list(params?: { keyword?: string; status?: number }) {
    return request<AdminUserResponse[]>(`/admin/users${buildQuery(params ?? {})}`)
  },
  async qualification(id: number | string) {
    return request<QualificationResponse>(`/admin/users/${id}/qualification`)
  },
  async violations(id: number | string) {
    return request<ViolationResponse[]>(`/admin/users/${id}/violations`)
  },
  async adjustCredit(id: number | string, body: CreditAdjustPayload) {
    return request<AdminUserResponse>(`/admin/users/${id}/credit`, { method: 'PUT', body })
  },
  async setRestriction(id: number | string, body: RestrictPayload) {
    return request<AdminUserResponse>(`/admin/users/${id}/restriction`, { method: 'PUT', body })
  },
  async create(body: CreateUserPayload) {
    return request<AdminUserResponse>('/admin/users', { method: 'POST', body })
  },
  async update(id: number | string, body: UpdateUserPayload) {
    return request<AdminUserResponse>(`/admin/users/${id}`, { method: 'PUT', body })
  },
  async updateStatus(id: number | string, body: { status: number; reason?: string }) {
    return request<AdminUserResponse>(`/admin/users/${id}/status`, { method: 'PUT', body })
  },
  async resetPassword(id: number | string, password: string) {
    await request<void>(`/admin/users/${id}/password`, { method: 'PUT', body: { password } })
  },
}

/** 当前登录用户查看自己的违规/信用记录 */
export const myViolationsApi = {
  async list() {
    return request<ViolationResponse[]>('/users/me/violations')
  },
}

export const roomsApi = {
  async list() {
    return (await request<RoomResponse[]>('/rooms')).map(toRoom)
  },
  async detail(roomId: string | number) {
    return request<RoomDetailResponse>(`/rooms/${roomId}`)
  },
}

/** 管理端资源治理接口；Demo（useMock）模式下视图层不应调用。 */
export const adminRoomsApi = {
  async create(payload: SaveRoomRequest) {
    return request<RoomResponse>('/admin/rooms', { method: 'POST', body: payload })
  },
  async update(roomId: string | number, payload: SaveRoomRequest) {
    return request<RoomResponse>(`/admin/rooms/${roomId}`, { method: 'PUT', body: payload })
  },
  async changeStatus(roomId: string | number, status: string) {
    return request<RoomResponse>(`/admin/rooms/${roomId}/status`, { method: 'POST', body: { status } })
  },
  async replaceFacilities(roomId: string | number, facilities: SaveFacilityRequest[]) {
    return request<FacilityResponse[]>(`/admin/rooms/${roomId}/facilities`, {
      method: 'PUT',
      body: { facilities },
    })
  },
  async replaceOpenRules(roomId: string | number, rules: SaveOpenRuleRequest[]) {
    return request<OpenRuleResponse[]>(`/admin/rooms/${roomId}/open-rules`, {
      method: 'PUT',
      body: { rules },
    })
  },
  async listMaintenance(roomId: string | number) {
    return request<MaintenanceResponse[]>(`/admin/rooms/${roomId}/maintenance`)
  },
  async createMaintenance(roomId: string | number, payload: SaveMaintenanceRequest) {
    return request<MaintenanceResponse>(`/admin/rooms/${roomId}/maintenance`, { method: 'POST', body: payload })
  },
  async finishMaintenance(roomId: string | number, planId: string | number) {
    return request<MaintenanceResponse>(`/admin/rooms/${roomId}/maintenance/${planId}/finish`, { method: 'POST' })
  },
}

export const adminCategoriesApi = {
  async list() {
    return request<CategoryResponse[]>('/admin/room-categories')
  },
  async create(payload: SaveCategoryRequest) {
    return request<CategoryResponse>('/admin/room-categories', { method: 'POST', body: payload })
  },
  async update(categoryId: string | number, payload: SaveCategoryRequest) {
    return request<CategoryResponse>(`/admin/room-categories/${categoryId}`, { method: 'PUT', body: payload })
  },
}

export const repairTicketsApi = {
  /** 用户报修 */
  async create(roomId: string | number, payload: CreateRepairTicketRequest) {
    return request<RepairTicketResponse>(`/rooms/${roomId}/repair-tickets`, { method: 'POST', body: payload })
  },
  /** 管理员查看工单 */
  async adminList(roomId?: string | number) {
    const query = roomId ? `?roomId=${encodeURIComponent(roomId)}` : ''
    return request<RepairTicketResponse[]>(`/admin/repair-tickets${query}`)
  },
  /** 管理员解决工单 */
  async resolve(ticketId: string | number, remark?: string) {
    return request<RepairTicketResponse>(`/admin/repair-tickets/${ticketId}/resolve`, {
      method: 'POST',
      body: { remark },
    })
  },
}

export const reservationsApi = {
  async calendar(start: string, end: string, roomId?: string) {
    return (await request<ReservationResponse[]>(`/reservations/calendar${buildQuery({ start, end, roomId })}`)).map(toReservation)
  },
  async mine() {
    return (await request<ReservationResponse[]>('/reservations/my')).map(toReservation)
  },
  async create(draft: ReservationDraft): Promise<Reservation> {
    const body: CreateReservationRequest = {
      requestId: crypto.randomUUID(),
      roomId: draft.roomId,
      title: draft.title,
      startTime: `${draft.date}T${draft.startTime}:00`,
      endTime: `${draft.date}T${draft.endTime}:00`,
      participantCount: draft.participantCount,
      remark: draft.remark,
    }
    return toReservation(await request<ReservationResponse>('/reservations', { method: 'POST', body }))
  },
  async cancel(id: string, reason?: string) {
    return toReservation(await request<ReservationResponse>(`/reservations/${id}/cancel`, {
      method: 'POST',
      body: reason ? { reason } : undefined,
    }))
  },
}

export { ApiError } from './types'
export type {
  AdminUserResponse,
  DepartmentResponse,
  DepartmentPayload,
  QualificationResponse,
  ViolationResponse,
  ViolationType,
} from './types'
