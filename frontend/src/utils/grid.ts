// 文件职责：提供 grid 前端工具函数。
// 接口：被 stores、views 和 components 调用，不直接访问后端。
import { PX_PER_HOUR, toMinutes } from './datetime'
import type { Reservation } from '@/types'

/** 看板上一个预约块完成定位后的全部信息 */
export interface LaidOutReservation {
  reservation: Reservation
  /** 冲突时横向分栏的列号与总列数 */
  lane: number
  laneCount: number
  /** 距列顶部的像素偏移 */
  top: number
  /** 块高度（像素） */
  height: number
}

/**
 * 将某会议室同一天的预约布局到时间轴上：
 * 纵向按时间换算像素，横向对互相重叠的预约做简单的分栏（lane packing）。
 * startHour 为看板可预约窗口的起始小时（管理员可调）。
 */
export function layoutReservations(list: Reservation[], startHour: number): LaidOutReservation[] {
  const sorted = [...list].sort(
    (a, b) => a.startTime.localeCompare(b.startTime) || a.endTime.localeCompare(b.endTime),
  )

  const items = sorted.map((r) => {
    const start = toMinutes(r.startTime)
    // 结束时间用 "HH:mm" 小时 ≥24 的内部约定表示次日（见 shared/api toReservation）
    const end = Math.max(toMinutes(r.endTime), start + 30)
    return { reservation: r, start, end, lane: 0 }
  })

  // 贪心分配 lane：放进第一个“上一场已结束”的栏位
  const laneEnds: number[] = []
  for (const item of items) {
    let lane = laneEnds.findIndex((end) => end <= item.start)
    if (lane === -1) {
      lane = laneEnds.length
      laneEnds.push(item.end)
    } else {
      laneEnds[lane] = item.end
    }
    item.lane = lane
  }

  // 仅在互相重叠的“簇”内按最大 lane 数分栏，避免不相邻的预约被压窄
  const result: LaidOutReservation[] = []
  let clusterEnd = -1
  let clusterLaneCount = 1
  let clusterItems: { item: (typeof items)[number]; top: number; height: number }[] = []

  const flush = () => {
    for (const entry of clusterItems) {
      result.push({
        reservation: entry.item.reservation,
        lane: entry.item.lane,
        laneCount: clusterLaneCount,
        top: entry.top,
        height: entry.height,
      })
    }
    clusterItems = []
  }

  for (const item of items) {
    if (item.start >= clusterEnd) {
      flush()
      clusterEnd = item.end
      clusterLaneCount = item.lane + 1
    } else {
      clusterEnd = Math.max(clusterEnd, item.end)
      clusterLaneCount = Math.max(clusterLaneCount, item.lane + 1)
    }
    clusterItems.push({
      item,
      top: ((item.start - startHour * 60) / 60) * PX_PER_HOUR,
      height: Math.max(((item.end - item.start) / 60) * PX_PER_HOUR - 3, 26),
    })
  }
  flush()

  return result
}
