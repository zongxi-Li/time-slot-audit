/** 营业时段：08:00 - 19:00 */
export const BUSINESS_START_HOUR = 8
export const BUSINESS_END_HOUR = 19
export const PX_PER_HOUR = 64

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

/** 包含 dateStr 所在周的周一到周日（共 7 天） */
export function getWeekDays(dateStr: string): string[] {
  const d = parseDateStr(dateStr)
  const weekday = (d.getDay() + 6) % 7 // 周一=0
  const monday = addDays(dateStr, -weekday)
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i))
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

/** 周范围标题，如 "2026年9月7日 - 9月13日"，自动处理跨月/跨年 */
export function formatWeekRange(dateStr: string): string {
  const days = getWeekDays(dateStr)
  const first = parseDateStr(days[0])
  const last = parseDateStr(days[6])
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
