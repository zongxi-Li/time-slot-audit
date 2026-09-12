import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useReservationStore } from './reservation'
import { todayStr } from '@/utils/datetime'
import type { AuditLogEntry, RequestFeedItem, ServiceStatus } from '@/types'

let seq = 0
const uid = (prefix: string) => `${prefix}${++seq}`

function clock(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function seedHistory(base: number, spread: number): number[] {
  return Array.from(
    { length: 24 },
    () => Math.max(2, Math.round(base + (Math.random() - 0.5) * spread)),
  )
}

/**
 * 系统监控（Mock）：并发监视、系统指标、审计日志。
 * 指标由 2s 定时器随机游走模拟；本会话内的真实操作（预约/审核/取消等）
 * 会即时写入请求流水与审计日志，与模拟数据混合展示。
 */
export const useMonitorStore = defineStore('monitor', () => {
  /* —— 并发监视 —— */
  const activeRequests = ref(2)
  const qps = ref(18)
  const latency = ref(23)
  const dbPoolUsed = ref(8)
  const dbPoolTotal = 25
  const activeUsers = ref(36)
  const conflictCount = ref(7) // 今日冲突拦截次数
  const successCount = ref(52)

  const conflictRate = computed(() => {
    const total = conflictCount.value + successCount.value
    return total === 0 ? '0%' : `${Math.round((conflictCount.value / total) * 100)}%`
  })

  const feed = ref<RequestFeedItem[]>([
    { id: uid('f'), time: clock(), method: 'GET', path: '/api/rooms', status: 200, note: '会议室列表' },
    { id: uid('f'), time: clock(), method: 'POST', path: '/api/reservations', status: 409, user: '刘同学', note: '冲突：A102 09:00 - 10:30 已被占用' },
    { id: uid('f'), time: clock(), method: 'GET', path: '/api/reservations?week=current', status: 200, note: '看板查询' },
  ])

  /* —— 系统监视 —— */
  const cpu = ref(23)
  const mem = ref(41)
  const cpuHistory = ref(seedHistory(23, 14))
  const memHistory = ref(seedHistory(41, 8))
  const qpsHistory = ref(seedHistory(18, 12))
  const startedAt = Date.now()

  const services = ref<ServiceStatus[]>([
    { name: 'API 服务（2 节点）', state: '正常', latency: '12 ms', note: '负载均衡正常' },
    { name: 'MySQL 数据库', state: '正常', latency: '3 ms', note: '连接池 8/25' },
    { name: '缓存服务（演示）', state: '正常', latency: '1 ms', note: '命中率 96%' },
  ])

  /* —— 审计日志 —— */
  const auditLogs = ref<AuditLogEntry[]>([
    { id: uid('a'), time: clock(), actor: '王建国', role: 'ADMIN', action: '登录系统', detail: '管理员视角登录（Mock）' },
    { id: uid('a'), time: clock(), actor: '张三', role: 'USER', action: '新建预约', detail: '软件工程项目讨论 · A101' },
    { id: uid('a'), time: clock(), actor: '系统', role: 'USER', action: '冲突拦截', detail: '李四 尝试预约 A102 09:00-10:30，与“项目晨会”冲突' },
    { id: uid('a'), time: clock(), actor: '王建国', role: 'ADMIN', action: '审核通过', detail: '课程设计讨论 · A103' },
  ])

  /* —— 指标定时器（监控页挂载时启动） —— */
  let ticker: number | undefined

  function walk(value: number, min: number, max: number, step: number): number {
    const next = value + (Math.random() - 0.5) * step
    return Math.min(max, Math.max(min, Math.round(next)))
  }

  function tick() {
    activeRequests.value = walk(activeRequests.value, 0, 9, 6)
    qps.value = walk(qps.value, 6, 46, 10)
    latency.value = walk(latency.value, 9, 80, 12)
    dbPoolUsed.value = walk(dbPoolUsed.value, 4, dbPoolTotal - 2, 4)
    activeUsers.value = walk(activeUsers.value, 20, 80, 8)
    cpu.value = walk(cpu.value, 8, 78, 14)
    mem.value = walk(mem.value, 35, 70, 6)

    cpuHistory.value = [...cpuHistory.value.slice(1), cpu.value]
    memHistory.value = [...memHistory.value.slice(1), mem.value]
    qpsHistory.value = [...qpsHistory.value.slice(1), qps.value]

    services.value[1].note = `连接池 ${dbPoolUsed.value}/${dbPoolTotal}`

    // 偶尔产生一条查询心跳流水
    if (Math.random() < 0.35) {
      pushFeed({
        method: 'GET',
        path: Math.random() < 0.5 ? '/api/rooms' : '/api/reservations?week=current',
        status: 200,
        note: '例行查询',
      })
    }
  }

  function startTicker() {
    if (ticker !== undefined) return
    ticker = window.setInterval(tick, 2000)
  }

  function stopTicker() {
    if (ticker !== undefined) {
      window.clearInterval(ticker)
      ticker = undefined
    }
  }

  function pushFeed(item: Omit<RequestFeedItem, 'id' | 'time'>) {
    feed.value.unshift({ id: uid('f'), time: clock(), ...item })
    if (feed.value.length > 30) feed.value.length = 30
  }

  function log(action: string, detail: string, actor?: string, role: AuditLogEntry['role'] = 'USER') {
    auditLogs.value.unshift({
      id: uid('a'),
      time: clock(),
      actor: actor ?? '当前用户',
      role,
      action,
      detail,
    })
    if (auditLogs.value.length > 50) auditLogs.value.length = 50
  }

  function noteConflict() {
    conflictCount.value += 1
  }

  function noteSuccess() {
    successCount.value += 1
  }

  /**
   * 并发演示：模拟 3 个用户同时抢订 A101 今天 16:00-17:00。
   * 时段空闲时仅 1 个请求成功（真实写入看板），其余被冲突检测以 409 拒绝；
   * 时段已被占用时全部 409。
   */
  function simulateConcurrentBooking(): { free: boolean } {
    const reservationStore = useReservationStore()
    const roomId = 'A101'
    const date = todayStr()
    const startTime = '16:00'
    const endTime = '17:00'
    const racers = [
      { id: 'u2001', name: '张三', department: '机械学院' },
      { id: 'u2002', name: '李四', department: '计算机学院' },
      { id: 'u2003', name: '王五', department: '经济管理学院' },
    ]

    const free =
      reservationStore.findConflicts({ roomId, date, startTime, endTime }).length === 0
    const winner = free ? racers[Math.floor(Math.random() * racers.length)] : null

    racers.forEach((u, i) => {
      window.setTimeout(() => {
        if (winner && u.id === winner.id) {
          reservationStore.addReservation(
            {
              title: '高并发抢订演示',
              roomId,
              date,
              startTime,
              endTime,
              participantCount: 3,
              remark: '由“模拟并发提交”生成',
            },
            u,
          )
          pushFeed({ method: 'POST', path: '/api/reservations', status: 201, user: u.name, note: `抢订 ${roomId} ${startTime}-${endTime} 成功` })
          successCount.value += 1
          log('并发演示', `${u.name} 抢订 A101 ${startTime}-${endTime} 成功，其余请求被冲突拦截`, '系统', 'ADMIN')
        } else {
          pushFeed({ method: 'POST', path: '/api/reservations', status: 409, user: u.name, note: `冲突：${roomId} ${startTime}-${endTime} 已被占用` })
          conflictCount.value += 1
        }
      }, 250 * (i + 1))
    })

    if (!winner) {
      log('并发演示', `A101 ${startTime}-${endTime} 已被占用，3 个并发请求全部被冲突检测拦截`, '系统', 'ADMIN')
    }
    return { free }
  }

  const uptime = computed(() => {
    const ms = Date.now() - startedAt + 3 * 24 * 3600 * 1000 + 14 * 3600 * 1000
    const days = Math.floor(ms / 86400000)
    const hours = Math.floor((ms % 86400000) / 3600000)
    return `${days} 天 ${hours} 小时`
  })

  return {
    activeRequests,
    qps,
    latency,
    dbPoolUsed,
    dbPoolTotal,
    activeUsers,
    conflictCount,
    successCount,
    conflictRate,
    feed,
    cpu,
    mem,
    cpuHistory,
    memHistory,
    qpsHistory,
    services,
    auditLogs,
    uptime,
    startTicker,
    stopTicker,
    pushFeed,
    log,
    noteConflict,
    noteSuccess,
    simulateConcurrentBooking,
  }
})
