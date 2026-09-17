// 文件职责：封装会议执行、参会人、签到签退和通知 API。
// 接口：/api/meetings/*、/api/notifications/*。
import { request } from '@/shared/api/http'

/** 参与人/出勤视图（与后端 AttendeeView 对齐） */
export interface AttendeeView {
  id: number
  reservationId: number
  userId: number
  username: string
  realName: string
  attendeeRole: 'ORGANIZER' | 'ATTENDEE'
  attendanceStatus: 'EXPECTED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'NO_SHOW'
  checkInAt: string | null
  checkOutAt: string | null
  joinedAt: string
}

/** 单场预约出勤总览（与后端 AttendanceView 对齐） */
export interface AttendanceView {
  reservationId: number
  totalCount: number
  expectedCount: number
  checkedInCount: number
  checkedOutCount: number
  noShowCount: number
  attendees: AttendeeView[]
  mine: AttendeeView | null
}

/** 我的会议行（与后端 MeetingExecutionView 对齐） */
export interface MeetingExecutionView {
  reservationId: number
  reservationNo: string
  title: string
  roomId: number
  roomName: string
  reservationStatus: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'
  startTime: string
  endTime: string
  myRole: 'ORGANIZER' | 'ATTENDEE'
  myAttendanceStatus: 'EXPECTED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'NO_SHOW'
  checkInAt: string | null
  checkOutAt: string | null
}

/** 个人通知（与后端 NotificationView 对齐） */
export interface NotificationView {
  id: number
  type: string
  title: string
  content: string
  reservationId: number | null
  read: boolean
  createdAt: string
  readAt: string | null
}

export const meetingsApi = {
  async my() {
    return request<MeetingExecutionView[]>('/meetings/my')
  },
  async attendees(reservationId: number | string) {
    return request<AttendeeView[]>(`/meetings/${reservationId}/attendees`)
  },
  async addAttendee(
    reservationId: number | string,
    payload: { userId?: number | string; username?: string },
  ) {
    return request<AttendeeView>(`/meetings/${reservationId}/attendees`, { method: 'POST', body: payload })
  },
  async removeAttendee(reservationId: number | string, userId: number | string) {
    return request<void>(`/meetings/${reservationId}/attendees/${userId}`, { method: 'DELETE' })
  },
  async checkIn(reservationId: number | string) {
    return request<AttendeeView>(`/meetings/${reservationId}/check-in`, { method: 'POST' })
  },
  async checkOut(reservationId: number | string) {
    return request<AttendeeView>(`/meetings/${reservationId}/check-out`, { method: 'POST' })
  },
  async attendance(reservationId: number | string) {
    return request<AttendanceView>(`/meetings/${reservationId}/attendance`)
  },
}

export const notificationsApi = {
  async list(unreadOnly = false) {
    return request<NotificationView[]>(`/notifications${unreadOnly ? '?unreadOnly=true' : ''}`)
  },
  async unreadCount() {
    return request<number>('/notifications/unread-count')
  },
  async markRead(id: number) {
    return request<void>(`/notifications/${id}/read`, { method: 'POST' })
  },
  async markAllRead() {
    return request<void>('/notifications/read-all', { method: 'POST' })
  },
}
