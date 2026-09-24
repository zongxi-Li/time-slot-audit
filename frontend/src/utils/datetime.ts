/** 营业时段兜底值：06:00 - 次日 06:00；实际以 bookingWindow store（后端配置）为准 */
export const BUSINESS_START_HOUR = 6
export const BUSINESS_END_HOUR = 30
export const PX_PER_HOUR = 64

/** 小时数（可 ≥24，表示次日）-> 展示标签，如 26 -> "次日 02:00" */
export function hourLabel(hour: number, minute = 0): string {
  const mm = String(minute).padStart(2, '0')
  const base = `${String(hour % 24).padStart(2, '0')}:${mm}`
  return hour >= 24 ? `次日 ${base}` : base
}

/** "HH:mm"（小时可 ≥24）-> 展示标签，如 "26:30" -> "次日 02:30" */
export function timeLabel(time: string): string {
  const [h, m] = time.split(':').map(Number)
  if (Number.isNaN(h)) return time
  return hourLabel(h, m || 0)
}

/** 组合日期与 "HH:mm"（小时可 ≥24 表示次日）为后端 ISO 时间串 */
export function toIsoDateTime(date: string, time: string): string {
  const hour = Number(time.slice(0, 2))
  const dayShift = Math.floor(hour / 24)
  const hh = String(hour % 24).padStart(2, '0')
  const mm = time.slice(3, 5)
  return `${dayShift ? addDays(date, dayShift) : date}T${hh}:${mm}:00`
}

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/** Date -> 本地时区 YYYY-MM-DD */
export function toDateStr(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

export function todayStr(): string {
  return toDateStr(new Date())
}

/** YYYY-MM-DD -> 本地时区当天 00:00 的 Date */
export function parseDateStr(dateStr: string): Date {
  const [y, m, d] = dateStr.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDays(dateStr: string, days: number): string {
  const d = parseDateStr(dateStr)
  d.setDate(d.getDate() + days)
  return toDateStr(d)
}

/** 看板滚动窗口：以当前日期为中心，前 4 天 ~ 后 4 天（共 9 天） */
export const BOARD_WINDOW_BEFORE = 4
export const BOARD_WINDOW_AFTER = 4

/** 以 dateStr 为中心，取前 before 天 ~ 后 after 天（当前日期居中） */
export function getRollingDays(
  dateStr: string,
  before = BOARD_WINDOW_BEFORE,
  after = BOARD_WINDOW_AFTER,
): string[] {
  return Array.from({ length: before + after + 1 }, (_, i) => addDays(dateStr, i - before))
}

const WEEKDAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'] as const

export function weekdayName(dateStr: string): string {
  return WEEKDAY_NAMES[parseDateStr(dateStr).getDay()]
}

export function isToday(dateStr: string): boolean {
  return dateStr === todayStr()
}

/** "2026-09-07" -> "9/7" */
export function formatShort(dateStr: string): string {
  const d = parseDateStr(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** 日期范围标题，如 "2026年9月15日 - 9月23日"，自动处理跨月/跨年 */
export function formatDateRange(startStr: string, endStr: string): string {
  const first = parseDateStr(startStr)
  const last = parseDateStr(endStr)
  const startYear = first.getFullYear()
  const startMonth = first.getMonth() + 1
  const endYear = last.getFullYear()
  const endMonth = last.getMonth() + 1
  const start = `${startYear}年${startMonth}月${first.getDate()}日`
  const end =
    startYear !== endYear
      ? `${endYear}年${endMonth}月${last.getDate()}日`
      : `${endMonth}月${last.getDate()}日`
  return `${start} - ${end}`
}

/** "HH:mm" -> 分钟数（零填充字符串可直接按字典序比较，此函数用于像素定位） */
export function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

export function fromMinutes(min: number): string {
  return `${pad2(Math.floor(min / 60))}:${pad2(min % 60)}`
}

/** 当前时刻距离今天 0 点的分钟数 */
export function nowMinutes(): number {
  const now = new Date()
  return now.getHours() * 60 + now.getMinutes()
}

/** 后端时间为 ISO-8601（yyyy-MM-ddTHH:mm:ss），展示为 yyyy-MM-dd HH:mm */
export function formatDateTime(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
