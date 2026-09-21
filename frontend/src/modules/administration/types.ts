// 文件职责：前端业务模块的 types 入口、页面、组件或类型定义。
// 接口：导出本模块的页面、store、API 或类型。
export interface ApprovalRecord {
  id: number
  reservationId: number
  approverId: number
  approverName: string
  action: 'APPROVE' | 'REJECT'
  remark?: string | null
  createdAt: string
}

export interface AdminReservation {
  id: number
  reservationNo: string
  roomId: number
  roomName: string
  userId: number
  userName: string
  title: string
  startTime: string
  endTime: string
  participantCount: number
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED'
  remark?: string | null
  createdAt: string
  approvalHistory: ApprovalRecord[]
}

export interface AuditLog {
  id: number
  userId: number
  operatorName: string
  operationType: string
  businessType: string
  businessId: number
  content: string
  ipAddress?: string | null
  createdAt: string
}

export interface RoomUsage {
  roomId: number
  roomName: string
  bookingCount: number
  usedHours: number
}

export interface PeakHour {
  hour: number
  bookingCount: number
}

/** 会议室使用率明细：使用率 = 已确认使用时长 ÷（统计天数 × 每日开放时长） */
export interface RoomUtilization {
  roomId: number
  roomName: string
  confirmedCount: number
  usedHours: number
  avgDailyHours: number
  utilizationRate: number
}

export interface OperationsDashboard {
  start: string
  end: string
  totalReservations: number
  cancelledReservations: number
  cancellationRate: number
  popularRooms: RoomUsage[]
  peakHours: PeakHour[]
  /** 统计周期整日数 */
  statDays: number
  /** 已确认会议总时长 ÷ 统计天数 */
  avgDailyMeetingHours: number
  /** 爽约率：NO_SHOW 人次 ÷ 应到人次 × 100 */
  noShowRate: number
  roomUtilizations: RoomUtilization[]
}
