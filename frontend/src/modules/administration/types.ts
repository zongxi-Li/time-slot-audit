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

export interface OperationsDashboard {
  start: string
  end: string
  totalReservations: number
  cancelledReservations: number
  cancellationRate: number
  popularRooms: RoomUsage[]
  peakHours: PeakHour[]
}
