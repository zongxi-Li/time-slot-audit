// 文件职责：Pinia store，维护周视图各日期列的手动列宽（Excel 式拖拽调宽）。
// 接口：ReservationWeekGrid 拖拽时写入，看板工具栏「列宽归位」按钮消费。
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/** 拖拽宽度的上下限（px）：下限保证卡片时间文字不折行，上限防止单列吞掉整行 */
export const WEEK_COL_MIN_PX = 80
export const WEEK_COL_MAX_PX = 480

export const useWeekColumnsStore = defineStore('weekColumns', () => {
  /**
   * 按列位置（而非日期）记录自定义宽度，null 表示该列保持默认的等分弹性宽度。
   * 按位置记录：右键平移日期窗口时列宽稳定不跳变，与 Excel 列宽语义一致。
   */
  const widths = ref<(number | null)[]>([])

  /** 是否存在手动调过的列：决定「列宽归位」按钮是否可点 */
  const hasCustom = computed(() => widths.value.some((w) => w !== null))

  function clamp(value: number): number {
    return Math.min(WEEK_COL_MAX_PX, Math.max(WEEK_COL_MIN_PX, value))
  }

  /** 拷贝扩容后写入：ref 数组直接扩 length 会留空洞（读出 undefined 而非 null） */
  function setWidth(index: number, px: number) {
    const next = widths.value.slice()
    while (next.length <= index) next.push(null)
    next[index] = clamp(Math.round(px))
    widths.value = next
  }

  /** 单列复位（表头拖拽手柄双击） */
  function resetColumn(index: number) {
    const next = widths.value.slice()
    if (index < next.length) next[index] = null
    widths.value = next
  }

  /** 全部复位，回到等分弹性布局 */
  function resetAll() {
    widths.value = []
  }

  return { widths, hasCustom, setWidth, resetColumn, resetAll }
})
