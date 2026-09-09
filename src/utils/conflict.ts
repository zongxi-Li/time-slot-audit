/**
 * 时间冲突检测：两个 [start, end) 区间是否相交。
 * 时间为 "HH:mm" 零填充字符串，字典序比较即为时间序。
 * 判定逻辑：newStart < existingEnd && newEnd > existingStart
 */
export function isTimeOverlap(
  aStart: string,
  aEnd: string,
  bStart: string,
  bEnd: string,
): boolean {
  return aStart < bEnd && aEnd > bStart
}
