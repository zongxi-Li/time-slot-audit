export type ReservationStatus = 'confirmed' | 'pending' | 'cancelled'

/** 演示用角色：普通用户 / 管理员（无真实鉴权，仅前端视角切换） */
export type Role = 'user' | 'admin'

/** 会议室状态：启用（可预约）/ 停用 */
export type RoomFlag = 'active' | 'disabled'

export interface MeetingRoom {
  id: string
  name: string
  location: string
  capacity: number
  equipment: string[]
  status: RoomFlag
}

export interface Reservation {
  id: string
  title: string
  roomId: string
  userId: string
  userName: string
  /** YYYY-MM-DD */
  date: string
  /** HH:mm */
  startTime: string
  /** HH:mm */
  endTime: string
  participantCount: number
  remark?: string
  status: ReservationStatus
}

export interface CurrentUser {
  id: string
  name: string
  department: string
  role?: Role
}

/** 新建预约时的表单数据 */
export interface ReservationDraft {
  title: string
  roomId: string
  date: string
  startTime: string
  endTime: string
  participantCount: number
  remark?: string
}

export type RoomStatus = 'idle' | 'in-use'

/** 展示层状态（由预约时间与当前时间推导） */
export type DisplayStatus = '待进行' | '已结束' | '已取消'

/** 审计日志（系统监视） */
export interface AuditLogEntry {
  id: string
  /** HH:mm:ss */
  time: string
  actor: string
  role: Role
  action: string
  detail: string
}

/** 并发请求流水（并发监视） */
export interface RequestFeedItem {
  id: string
  /** HH:mm:ss */
  time: string
  method: string
  path: string
  status: number
  user?: string
  note?: string
}

/** 服务健康状态（系统监视） */
export interface ServiceStatus {
  name: string
  state: '正常' | '繁忙' | '异常'
  latency: string
  note: string
}
