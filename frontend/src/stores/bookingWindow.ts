// 文件职责：Pinia store，维护管理员可调的「全局可预约时段」（后端 booking_window_config）。
// 接口：调用 bookingWindowApi；看板时间轴、预约弹窗时间选项、预约校验提示都消费此 store。
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { bookingWindowApi } from '@/shared/api'
import { BUSINESS_END_HOUR, BUSINESS_START_HOUR } from '@/utils/datetime'

export const useBookingWindowStore = defineStore('bookingWindow', () => {
  /** 相对预约日期 00:00 的分钟数；endMinute 可超过 1440 表示次日 */
  const startMinute = ref(BUSINESS_START_HOUR * 60)
  const endMinute = ref(BUSINESS_END_HOUR * 60)
  const loaded = ref(false)

  /** 起止小时（整点，窗口设置只允许整点） */
  const startHour = computed(() => Math.round(startMinute.value / 60))
  const endHour = computed(() => Math.round(endMinute.value / 60))
  /** 时间轴上的小时格数量 */
  const hourCount = computed(() => endHour.value - startHour.value)
  /** 是否覆盖到次日 */
  const spansNextDay = computed(() => endHour.value > 24)

  async function refresh() {
    const next = await bookingWindowApi.current()
    startMinute.value = next.startMinute
    endMinute.value = next.endMinute
    loaded.value = true
  }

  /** 管理员设置（整点）；后端裁决合法性与权限，成功后以返回值为准 */
  async function set(nextStartHour: number, nextEndHour: number) {
    const next = await bookingWindowApi.set(nextStartHour * 60, nextEndHour * 60)
    startMinute.value = next.startMinute
    endMinute.value = next.endMinute
    return next
  }

  return { startMinute, endMinute, startHour, endHour, hourCount, spansNextDay, loaded, refresh, set }
})
