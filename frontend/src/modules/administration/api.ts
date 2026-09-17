import { API_BASE_URL, TOKEN_STORAGE_KEY } from '@/shared/api/config'
import { buildQuery, request } from '@/shared/api/http'
import type { AdminReservation, AuditLog, OperationsDashboard } from './types'

export const administrationApi = {
  reservations(status?: string) {
    return request<AdminReservation[]>(`/admin/reservations${buildQuery({ status })}`)
  },
  reservation(id: number) {
    return request<AdminReservation>(`/admin/reservations/${id}`)
  },
  approve(id: number) {
    return request<AdminReservation>(`/admin/reservations/${id}/approve`, { method: 'POST' })
  },
  reject(id: number, reason: string) {
    return request<AdminReservation>(`/admin/reservations/${id}/reject`, {
      method: 'POST',
      body: { reason },
    })
  },
  forceCancel(id: number, reason: string) {
    return request<AdminReservation>(`/admin/reservations/${id}/force-cancel`, {
      method: 'POST',
      body: { reason },
    })
  },
  auditLogs(params: {
    operatorId?: number
    businessType?: string
    start?: string
    end?: string
    limit?: number
  }) {
    return request<AuditLog[]>(`/admin/audit-logs${buildQuery(params)}`)
  },
  statistics(params: { start?: string; end?: string; top?: number }) {
    return request<OperationsDashboard>(`/admin/statistics${buildQuery(params)}`)
  },
  async exportAuditLogs(params: { operatorId?: number; businessType?: string; start?: string; end?: string }) {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY)
    const response = await fetch(`${API_BASE_URL}/admin/audit-logs/export${buildQuery(params)}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    })
    if (!response.ok) throw new Error('导出审计日志失败')
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'audit-logs.csv'
    anchor.click()
    URL.revokeObjectURL(url)
  },
}
