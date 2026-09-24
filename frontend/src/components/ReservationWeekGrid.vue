<!--
  文件职责：实现可复用的 ReservationWeekGrid Vue 组件。
  接口：通过 props、emits 与父页面通信，必要时通过 store 间接访问 API。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useReservationStore } from '@/stores/reservation'
import { useSystemTimeStore } from '@/stores/systemTime'
import { useBookingWindowStore } from '@/stores/bookingWindow'
import {
  PX_PER_HOUR,
  formatShort,
  hourLabel,
  parseDateStr,
  timeLabel,
  weekdayName,
} from '@/utils/datetime'
import { layoutReservations } from '@/utils/grid'
import { useWeekColumnsStore } from '@/stores/weekColumns'
import type { MeetingRoom, SlotSelection } from '@/types'
import ReservationCard from './ReservationCard.vue'

const props = defineProps<{
  /** 已经过筛选的会议室（决定周视图里显示哪些会议室的预约） */
  rooms: MeetingRoom[]
  /** 滚动窗口要展示的日期：当前日期前 4 天 ~ 后 4 天（共 9 天，今天居中） */
  days: string[]
  /** 当前框选的时间段（父组件持有） */
  selection?: SlotSelection | null
}>()

const emit = defineEmits<{
  open: [reservationId: string]
  /** 拖拽框选变化；null 表示取消选择 */
  select: [selection: SlotSelection | null]
  /** 右键拖动平移日期窗口；正数往后看，负数往前看 */
  pan: [days: number]
}>()

const store = useReservationStore()
const systemTime = useSystemTimeStore()
const bookingWindow = useBookingWindowStore()

const roomIds = computed(() => new Set(props.rooms.map((r) => r.id)))

/** 卡片徽标显示会议室名而不是数据库 ID；映射不到时退回原始 ID */
const roomNameById = computed(() => new Map(props.rooms.map((r) => [r.id, r.name])))

function roomNameOf(roomId: string): string {
  return roomNameById.value.get(roomId) ?? roomId
}

function dayBlocks(date: string) {
  return layoutReservations(
    store.activeReservations.filter(
      (r) => r.date === date && roomIds.value.has(r.roomId),
    ),
    bookingWindow.startHour,
  )
}

const hours = computed(() => {
  const list: number[] = []
  for (let h = bookingWindow.startHour; h < bookingWindow.endHour; h++) list.push(h)
  return list
})

const gridBodyHeight = computed(() => bookingWindow.hourCount * PX_PER_HOUR)

/* —— 当前时段高亮：今天列中业务时刻所在的小时格（业务时刻落在可预约窗口内才显示） —— */
function nowSlotTop(day: string): number | null {
  const offsetMinute = (systemTime.now.getTime() - parseDateStr(day).getTime()) / 60000
  if (offsetMinute < bookingWindow.startMinute || offsetMinute > bookingWindow.endMinute) return null
  const slot = Math.min(bookingWindow.hourCount - 1, Math.floor((offsetMinute - bookingWindow.startMinute) / 60))
  return slot * PX_PER_HOUR
}

function isSystemToday(day: string) {
  return day === systemTime.date
}

function isToday(day: string) {
  return isSystemToday(day)
}

/* —— 悬停高亮 + 左键拖拽框选连续时间段 —— */
const hover = ref<{ date: string; slot: number } | null>(null)
const dragDate = ref<string | null>(null)
const dragAnchor = ref<number | null>(null)
const dragging = ref(false)

/** 框选边界的内部时间值（小时可 ≥24，如 "26:00"）；展示时再转 "次日" 标签 */
function slotLabel(slot: number, edge: 'start' | 'end'): string {
  return `${String(bookingWindow.startHour + slot + (edge === 'end' ? 1 : 0)).padStart(2, '0')}:00`
}

/** 指针相对列顶部的 y 坐标 -> 小时格下标（夹在可预约窗口内） */
function slotFromEvent(e: PointerEvent, el: HTMLElement): number {
  const y = e.clientY - el.getBoundingClientRect().top
  return Math.min(bookingWindow.hourCount - 1, Math.max(0, Math.floor(y / PX_PER_HOUR)))
}

function emitSelection(date: string, from: number, to: number) {
  const start = Math.min(from, to)
  const end = Math.max(from, to)
  emit('select', {
    date,
    startTime: slotLabel(start, 'start'),
    endTime: slotLabel(end, 'end'),
  })
}

/** 当前框选在周视图上的呈现（框选不指定会议室）；无则 null */
const selectionBox = computed(() => {
  const sel = props.selection
  if (!sel || sel.roomId) return null
  const start = Number(sel.startTime.slice(0, 2)) - bookingWindow.startHour
  const end = Number(sel.endTime.slice(0, 2)) - bookingWindow.startHour
  return {
    date: sel.date,
    start,
    end,
    top: start * PX_PER_HOUR + 2,
    height: Math.max(0, (end - start) * PX_PER_HOUR - 4),
    label: `${timeLabel(sel.startTime)} - ${timeLabel(sel.endTime)}`,
  }
})

function inSelection(date: string, slot: number): boolean {
  const box = selectionBox.value
  return !!box && box.date === date && slot >= box.start && slot < box.end
}

function onPointerDown(e: PointerEvent, date: string) {
  if (e.button !== 0 || e.pointerType === 'touch') return
  // 预约卡片自己处理点击打开详情，不作为框选起点
  if ((e.target as HTMLElement).closest('.res-card')) return
  e.preventDefault()
  const slot = slotFromEvent(e, e.currentTarget as HTMLElement)
  // 再点一次已选中的单格 -> 取消选择
  const sel = props.selection
  if (
    sel && sel.date === date &&
    sel.startTime === slotLabel(slot, 'start') && sel.endTime === slotLabel(slot, 'end')
  ) {
    emit('select', null)
    return
  }
  dragDate.value = date
  dragAnchor.value = slot
  dragging.value = true
  emitSelection(date, slot, slot)
  try {
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  } catch {
    // 指针已失效（如合成事件）时无需捕获，不影响框选
  }
}

function onPointerMove(e: PointerEvent, date: string) {
  const slot = slotFromEvent(e, e.currentTarget as HTMLElement)
  if (dragging.value) {
    // 框选锁定在按下时的那一天，跨列移动只改变时间范围
    if (dragDate.value === date && dragAnchor.value !== null) {
      emitSelection(date, dragAnchor.value, slot)
    }
    hover.value = { date, slot }
    return
  }
  // 悬停在预约卡片上时不显示格子高亮
  if ((e.target as HTMLElement).closest('.res-card')) {
    if (hover.value?.date === date) hover.value = null
    return
  }
  hover.value = { date, slot }
}

function onPointerLeave(date: string) {
  if (dragging.value) return
  if (hover.value?.date === date) hover.value = null
}

function onPointerUp(e: PointerEvent, date: string) {
  if (!dragging.value) return
  dragging.value = false
  dragAnchor.value = null
  dragDate.value = null
  // 松开时指针已在列外（拖出后松手）则不再保留悬停高亮
  const el = e.currentTarget as HTMLElement
  const rect = el.getBoundingClientRect()
  if (e.clientY < rect.top || e.clientY > rect.bottom) {
    if (hover.value?.date === date) hover.value = null
  }
}

/* —— 右键拖动：无极横向平移日期窗口（像素级跟手，拖过一列换一天） —— */
const panning = ref(false)
const panOffset = ref(0)
let panLastX = 0
let panStepWidth = 160

function onPanPointerDown(e: PointerEvent) {
  if (e.button !== 2 || e.pointerType === 'touch') return
  const grid = e.currentTarget as HTMLElement
  panning.value = true
  panLastX = e.clientX
  // 一列的宽度 = 换一天的位移阈值
  const head = grid.querySelector<HTMLDivElement>('.week-day-head')
  panStepWidth = head ? head.getBoundingClientRect().width : 160
  try {
    // 捕获在网格根节点上：窗口平移导致列重渲染后拖动不中断
    grid.setPointerCapture(e.pointerId)
  } catch {
    // 合成事件无真实 pointerId 时无需捕获
  }
}

function onPanPointerMove(e: PointerEvent) {
  if (!panning.value) return
  panOffset.value += e.clientX - panLastX
  panLastX = e.clientX
  // 网格实时跟手；累计拖过一列就平移一天并回收等量位移，衔接无缝
  while (Math.abs(panOffset.value) >= panStepWidth) {
    const sign = Math.sign(panOffset.value)
    panOffset.value -= sign * panStepWidth
    emit('pan', -sign)
  }
}

function onPanPointerUp() {
  panning.value = false
  panOffset.value = 0 // 松手后由过渡动画滑回列对齐位
}

/* —— 列宽拖拽：表头右缘手柄，Excel 式逐列调宽 —— */
const weekColumns = useWeekColumnsStore()

/** 已拖过的列固定 px，其余列保持等分弹性宽度，网格始终铺满面板 */
const columnTemplate = computed(() => {
  const cols = props.days.map((_, index) => {
    const width = weekColumns.widths[index] ?? null
    return width !== null ? `${width}px` : 'minmax(122px, 1fr)'
  })
  return `64px ${cols.join(' ')}`
})

/** 全部列固定后网格收拢为实际总宽，避免右侧留一截无列的空白 */
const gridMinWidth = computed(() => {
  const widths = weekColumns.widths
  if (!props.days.every((_, index) => (widths[index] ?? null) !== null)) return 1162
  return 64 + widths.reduce<number>((sum, w) => sum + (w ?? 0), 0)
})

const resizingIndex = ref(-1)
let resizeStartX = 0
let resizeStartWidth = 0

function onGripPointerDown(e: PointerEvent, index: number) {
  if (e.button !== 0 || e.pointerType === 'touch') return
  e.preventDefault()
  e.stopPropagation()
  const head = (e.currentTarget as HTMLElement).closest('.week-day-head') as HTMLElement
  resizeStartX = e.clientX
  resizeStartWidth = head.getBoundingClientRect().width
  resizingIndex.value = index
  try {
    // 捕获在手柄上：拖出表头后仍持续跟手，也不会误触下方列的框选
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  } catch {
    // 合成事件无真实 pointerId 时无需捕获
  }
}

function onGripPointerMove(e: PointerEvent) {
  if (resizingIndex.value < 0) return
  if (e.buttons === 0) {
    // 指针已抬起但 up 事件丢失（如捕获失败）时退出拖动，防止悬停误调列宽
    resizingIndex.value = -1
    return
  }
  // 只在真正拖动时写入自定义宽度：单纯点按手柄不改变布局
  weekColumns.setWidth(resizingIndex.value, resizeStartWidth + (e.clientX - resizeStartX))
}

function onGripPointerUp() {
  resizingIndex.value = -1
}

/** 双击手柄：只复位被双击的那一列 */
function onGripDblClick(index: number) {
  weekColumns.resetColumn(index)
}
</script>

<template>
  <div class="week-wrap thin-scroll">
    <div
      class="week-grid"
      :class="{ 'is-panning': panning, 'is-resizing': resizingIndex >= 0 }"
      :style="{
        gridTemplateColumns: columnTemplate,
        minWidth: `${gridMinWidth}px`,
        transform: `translateX(${panOffset}px)`,
      }"
      @pointerdown="onPanPointerDown"
      @pointermove="onPanPointerMove"
      @pointerup="onPanPointerUp"
      @pointercancel="onPanPointerUp"
      @contextmenu.prevent
    >
      <!-- 表头 -->
      <div class="week-corner">时间</div>
      <div
        v-for="(day, index) in days"
        :key="day"
        class="week-day-head"
        :class="{ 'is-today': isSystemToday(day) }"
      >
        <span class="wh-name">{{ weekdayName(day) }}</span>
        <span class="wh-date">{{ formatShort(day) }}</span>
        <span v-if="isToday(day)" class="wh-today">今天</span>
        <!-- 列宽拖拽手柄：骑在本列表头右缘，拖动调宽、双击复位 -->
        <span
          class="col-grip"
          :class="{ 'is-active': resizingIndex === index }"
          title="拖动调整列宽，双击复位"
          @pointerdown="onGripPointerDown($event, index)"
          @pointermove="onGripPointerMove"
          @pointerup="onGripPointerUp"
          @pointercancel="onGripPointerUp"
          @dblclick="onGripDblClick(index)"
        />
      </div>

      <!-- 时间轴 -->
      <div class="week-time-col">
        <div
          v-for="h in hours"
          :key="h"
          class="time-label"
          :style="{ height: `${PX_PER_HOUR}px` }"
        >
          {{ hourLabel(h) }}
        </div>
        <div class="time-end-label">{{ hourLabel(bookingWindow.endHour) }}</div>
      </div>

      <!-- 滚动窗口的每一天一列 -->
      <div
        v-for="day in days"
        :key="day"
        class="week-day-col"
        :class="{ 'is-today-col': isSystemToday(day) }"
        :style="{ height: `${gridBodyHeight}px` }"
        @pointerdown="onPointerDown($event, day)"
        @pointermove="onPointerMove($event, day)"
        @pointerleave="onPointerLeave(day)"
        @pointerup="onPointerUp($event, day)"
      >
        <div
          v-if="hover && hover.date === day && !inSelection(day, hover.slot)"
          class="slot-hover"
          :style="{ top: `${hover.slot * PX_PER_HOUR}px`, height: `${PX_PER_HOUR}px` }"
        />
        <div
          v-if="isSystemToday(day) && nowSlotTop(day) !== null"
          class="slot-now"
          :style="{ top: `${nowSlotTop(day)}px`, height: `${PX_PER_HOUR}px` }"
        />
        <div
          v-if="selectionBox && selectionBox.date === day"
          class="slot-selection"
          :style="{ top: `${selectionBox.top}px`, height: `${selectionBox.height}px` }"
        >
          <span class="slot-selection-time">{{ selectionBox.label }}</span>
        </div>
        <ReservationCard
          v-for="block in dayBlocks(day)"
          :key="block.reservation.id"
          :item="block"
          :room-name="roomNameOf(block.reservation.roomId)"
          @open="emit('open', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.week-wrap {
  flex: 1; /* 父级 .grid-panel 是 flex 容器，没有它网格会缩成内容宽度，右侧留白 */
  min-width: 0;
  height: 100%;
  overflow: auto;
}

.week-grid {
  display: grid;
  min-width: 1162px; /* 64px 时间轴 + 9 天 × 122px 最小列宽 */
  transition: transform 200ms ease; /* 松手回弹到列对齐位 */
}

.week-grid.is-panning {
  transition: none; /* 拖动中 1:1 跟手，不加过渡 */
  cursor: grabbing;
  user-select: none;
}

.week-grid.is-resizing {
  cursor: col-resize;
  user-select: none;
}

/* 列宽拖拽手柄：本列表头右缘 12px 的隐形热区 */
.col-grip {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 5;
  width: 12px;
  height: 100%;
  cursor: col-resize;
}

/* 悬停/拖动时亮起的竖条，贴在列边界内侧 */
.col-grip::after {
  content: '';
  position: absolute;
  top: 14px;
  bottom: 14px;
  right: 0;
  width: 2px;
  border-radius: 2px;
  background: transparent;
  transition: background 150ms ease;
}

.col-grip:hover::after,
.col-grip.is-active::after {
  background: var(--el-color-primary);
  box-shadow: 0 0 6px rgba(0, 113, 227, 0.55);
}

.week-grid.is-panning .week-day-col {
  cursor: grabbing;
}

.week-corner,
.week-day-head {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 50px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-color);
}

.week-corner {
  left: 0;
  z-index: 4;
  font-size: 12px;
  color: var(--text-muted);
}

.week-day-head {
  border-left: 1px solid var(--border-light);
  flex-direction: row;
  gap: 6px;
}

.wh-name {
  font-size: 13px;
  font-weight: 600;
}

.wh-date {
  font-size: 12px;
  color: var(--text-muted);
}

.wh-today {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
}

.week-day-head.is-today .wh-name {
  color: var(--el-color-primary);
}

.week-time-col {
  position: relative; /* 配合 z-index：向左拖时日期列从时间轴下方滑过 */
  z-index: 2;
  border-right: 1px solid var(--border-color);
}

.time-label {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  padding: 2px 8px 0 0;
  font-size: 11px;
  color: var(--text-muted);
  box-sizing: border-box;
}

.time-end-label {
  position: absolute;
  right: 8px;
  bottom: 2px;
  font-size: 11px;
  color: var(--text-muted);
}

.week-day-col {
  --hour: 64px; /* 与 PX_PER_HOUR 保持一致 */
  position: relative;
  border-left: 1px solid var(--border-light);
  cursor: cell;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(var(--hour) - 1px),
    var(--border-light) calc(var(--hour) - 1px),
    var(--border-light) var(--hour)
  );
  background-size: 100% var(--hour);
}

/* 鼠标划过的小时格 */
.slot-hover {
  position: absolute;
  left: 0;
  right: 0;
  background: rgba(0, 113, 227, 0.07);
  pointer-events: none;
  z-index: 0;
}

/* 拖拽框选出的连续时间段 */
.slot-selection {
  position: absolute;
  left: 3px;
  right: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid var(--el-color-primary);
  border-radius: 8px;
  background: rgba(0, 113, 227, 0.12);
  pointer-events: none;
  z-index: 1;
}

.slot-selection-time {
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  white-space: nowrap;
  box-shadow: 0 1px 4px rgba(0, 113, 227, 0.25);
}

/* 今日列：淡蓝底色突出 */
.week-day-col.is-today-col {
  background-color: #f7faff;
}

/* 当前时段：业务时刻所在小时格的整格底色，压在卡片之下 */
.slot-now {
  position: absolute;
  left: 0;
  right: 0;
  background: rgba(0, 113, 227, 0.08);
  pointer-events: none;
  z-index: 0;
}

</style>

<style scoped>
.week-corner,
.week-day-head {
  height: 58px;
  background: rgba(255, 255, 255, 0.86);
  border-bottom-color: var(--border-light);
  -webkit-backdrop-filter: blur(16px);
  backdrop-filter: blur(16px);
}

.week-corner {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.week-day-head {
  gap: 8px;
}

.wh-name {
  font-size: 14px;
  letter-spacing: -0.02em;
}

.wh-date {
  font-variant-numeric: tabular-nums;
}

.wh-today {
  padding: 2px 7px;
  color: #fff;
  background: var(--el-color-primary);
  box-shadow: 0 4px 10px rgba(0, 113, 227, 0.18);
  font-weight: 600;
}

.week-time-col {
  border-right-color: var(--border-light);
  background: rgba(255, 255, 255, 0.35);
}

.week-day-col {
  border-left-color: var(--border-light);
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(var(--hour) - 1px),
    rgba(29, 29, 31, 0.08) calc(var(--hour) - 1px),
    rgba(29, 29, 31, 0.08) var(--hour)
  );
}

.week-day-col.is-today-col {
  background-color: rgba(0, 113, 227, 0.028);
}
</style>
