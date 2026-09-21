// 文件职责：TimeSlot 项目基础文件。
// 接口：供对应工具链加载。
export type ReservationStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'

/** API 与 Domain 统一角色；Mock 适配器也使用同一组语义。 */
export type Role = 'USER' | 'ADMIN'

/** 资源状态；idle/in-use 只属于展示层的动态状态。 */
export type RoomFlag = 'AVAILABLE' | 'MAINTENANCE' | 'DISABLED'

export interface MeetingRoom {
  id: string
  name: string
  location: string
  capacity: number
  equipment: string[]
  category?: string
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
  requestId?: string
  /** Server-derived display state; never recompute lifecycle state in the browser. */
  displayStatus: DisplayStatus
  /** Optimistic-lock version required by PUT /api/reservations/{id}. */
  version: number
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
  /** 周期性会议（按周重复）周数：缺省/1 为单次会议，2..8 为每周同一时段批量预约 */
  repeatWeeks?: number
}

export type RoomStatus = 'idle' | 'in-use'

/** 展示层状态（由预约时间与当前时间推导） */
export type DisplayStatus = '待进行' | '进行中' | '已结束' | '已取消' | '待审核' | '已驳回'

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

/** 看板网格上拖拽框选出的时间段；endTime 为独占边界，可直接用作表单 endTime */
export interface SlotSelection {
  /** 日视图：框选所在会议室；周视图框选不指定会议室 */
  roomId?: string
  /** 框选所在日期 YYYY-MM-DD */
  date: string
  /** HH:mm */
  startTime: string
  /** HH:mm */
  endTime: string
}
