import type { Reservation, MeetingRoom, CurrentUser, ReservationDraft } from '@/types'
import { request } from './http'
import { TOKEN_STORAGE_KEY } from './config'
import type {
  CreateReservationRequest,
  LoginRequest,
  LoginResponse,
  ReservationResponse,
  RoomResponse,
  UserResponse,
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

export const roomsApi = {
  async list() {
    return (await request<RoomResponse[]>('/rooms')).map(toRoom)
  },
}

export const reservationsApi = {
  async calendar(start: string, end: string, roomId?: string) {
    const params = new URLSearchParams({ start, end })
    if (roomId) params.set('roomId', roomId)
    return (await request<ReservationResponse[]>(`/reservations/calendar?${params}`)).map(toReservation)
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
