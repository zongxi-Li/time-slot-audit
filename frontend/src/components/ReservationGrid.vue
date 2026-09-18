<!--
  文件职责：实现可复用的 ReservationGrid Vue 组件。
  接口：通过 props、emits 与父页面通信，必要时通过 store 间接访问 API。
-->
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useReservationStore } from '@/stores/reservation'
import { useSystemTimeStore } from '@/stores/systemTime'
import { useBookingWindowStore } from '@/stores/bookingWindow'
import {
  PX_PER_HOUR,
  hourLabel,
  parseDateStr,
  timeLabel,
} from '@/utils/datetime'
import { layoutReservations } from '@/utils/grid'
import { roomStatusMeta } from '@/utils/roomStatus'
import type { MeetingRoom, SlotSelection } from '@/types'
import ReservationCard from './ReservationCard.vue'

const props = defineProps<{
  /** 已经过筛选的会议室列表 */
  rooms: MeetingRoom[]
  /** 选定日期 YYYY-MM-DD */
  date: string
  /** 当前框选的时间段（父组件持有） */
  selection?: SlotSelection | null
}>()

const emit = defineEmits<{
  open: [reservationId: string]
  /** 拖拽框选变化；null 表示取消选择 */
  select: [selection: SlotSelection | null]
}>()

const store = useReservationStore()
const systemTime = useSystemTimeStore()
const bookingWindow = useBookingWindowStore()

const hours = computed(() => {
  const list: number[] = []
  for (let h = bookingWindow.startHour; h < bookingWindow.endHour; h++) list.push(h)
  return list
})

const gridBodyHeight = computed(() => bookingWindow.hourCount * PX_PER_HOUR)

function dayBlocks(roomId: string) {
  return layoutReservations(store.listByRoomAndDate(roomId, props.date), bookingWindow.startHour)
}

/* —— “当前时间”红线：业务时刻落在本日可预约窗口内即显示（窗口延伸到次日晨时也成立） —— */
const nowLineTop = computed(() => {
  const offsetMinute = (systemTime.now.getTime() - parseDateStr(props.date).getTime()) / 60000
  if (offsetMinute < bookingWindow.startMinute || offsetMinute > bookingWindow.endMinute) return null
  return ((offsetMinute - bookingWindow.startMinute) / 60) * PX_PER_HOUR
})

const showNowLine = computed(() => nowLineTop.value !== null)

/* —— 悬停高亮 + 左键拖拽框选连续时间段 —— */
const hover = ref<{ roomId: string; slot: number } | null>(null)
const dragRoom = ref<string | null>(null)
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

function emitSelection(roomId: string, from: number, to: number) {
  const start = Math.min(from, to)
  const end = Math.max(from, to)
  emit('select', {
    roomId,
    date: props.date,
    startTime: slotLabel(start, 'start'),
    endTime: slotLabel(end, 'end'),
  })
}

/** 当前框选在网格上的呈现（同一时刻只会有一个列被框选）；无则 null */
const selectionBox = computed(() => {
  const sel = props.selection
  if (!sel || !sel.roomId) return null
  const start = Number(sel.startTime.slice(0, 2)) - bookingWindow.startHour
  const end = Number(sel.endTime.slice(0, 2)) - bookingWindow.startHour
  return {
    roomId: sel.roomId,
    start,
    end,
    top: start * PX_PER_HOUR + 2,
    height: Math.max(0, (end - start) * PX_PER_HOUR - 4),
    label: `${timeLabel(sel.startTime)} - ${timeLabel(sel.endTime)}`,
  }
})

function inSelection(roomId: string, slot: number): boolean {
  const box = selectionBox.value
  return !!box && box.roomId === roomId && slot >= box.start && slot < box.end
}

function onPointerDown(e: PointerEvent, roomId: string) {
  if (e.button !== 0 || e.pointerType === 'touch') return
  // 预约卡片自己处理点击打开详情，不作为框选起点
  if ((e.target as HTMLElement).closest('.res-card')) return
  e.preventDefault()
  const slot = slotFromEvent(e, e.currentTarget as HTMLElement)
  // 再点一次已选中的单格 -> 取消选择
  const sel = props.selection
  if (
    sel && sel.roomId === roomId &&
    sel.startTime === slotLabel(slot, 'start') && sel.endTime === slotLabel(slot, 'end')
  ) {
    emit('select', null)
    return
  }
  dragRoom.value = roomId
  dragAnchor.value = slot
  dragging.value = true
  emitSelection(roomId, slot, slot)
  try {
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  } catch {
    // 指针已失效（如合成事件）时无需捕获，不影响框选
  }
}

function onPointerMove(e: PointerEvent, roomId: string) {
  const slot = slotFromEvent(e, e.currentTarget as HTMLElement)
  if (dragging.value) {
    // 框选锁定在按下时的那一列，跨列移动只改变时间范围
    if (dragRoom.value === roomId && dragAnchor.value !== null) {
      emitSelection(roomId, dragAnchor.value, slot)
    }
    hover.value = { roomId, slot }
    return
  }
  // 悬停在预约卡片上时不显示格子高亮
  if ((e.target as HTMLElement).closest('.res-card')) {
    if (hover.value?.roomId === roomId) hover.value = null
    return
  }
  hover.value = { roomId, slot }
}

function onPointerLeave(roomId: string) {
  if (dragging.value) return
  if (hover.value?.roomId === roomId) hover.value = null
}

function onPointerUp(e: PointerEvent, roomId: string) {
  if (!dragging.value) return
  dragging.value = false
  dragAnchor.value = null
  dragRoom.value = null
  // 松开时指针已在列外（拖出后松手）则不再保留悬停高亮
  const el = e.currentTarget as HTMLElement
  const rect = el.getBoundingClientRect()
  if (e.clientY < rect.top || e.clientY > rect.bottom) {
    if (hover.value?.roomId === roomId) hover.value = null
  }
}
</script>

<template>
  <div class="grid-wrap thin-scroll">
    <div
      class="board-grid"
      :style="{ gridTemplateColumns: `72px repeat(${rooms.length}, minmax(150px, 1fr))` }"
    >
      <!-- 表头：左上角 + 会议室 -->
      <div class="grid-corner">时间</div>
      <div
        v-for="room in rooms"
        :key="room.id"
        class="grid-room-head"
        :class="`room-flag--${roomStatusMeta[room.status].tone}`"
      >
        <span class="room-name">{{ room.name }}</span>
        <span class="room-cap">{{ room.capacity }} 人</span>
        <span class="room-flag">{{ roomStatusMeta[room.status].label }}</span>
      </div>

      <!-- 时间轴 -->
      <div class="grid-time-col">
        <div
          v-for="h in hours"
          :key="h"
          class="time-label"
          :style="{ height: `${PX_PER_HOUR}px` }"
        >
          {{ hourLabel(h) }}
        </div>
      </div>

      <!-- 会议室列 -->
      <div
        v-for="room in rooms"
        :key="room.id"
        class="grid-room-col"
        :class="`room-column--${roomStatusMeta[room.status].tone}`"
        :style="{ height: `${gridBodyHeight}px` }"
        @pointerdown="onPointerDown($event, room.id)"
        @pointermove="onPointerMove($event, room.id)"
        @pointerleave="onPointerLeave(room.id)"
        @pointerup="onPointerUp($event, room.id)"
      >
        <div
          v-if="hover && hover.roomId === room.id && !inSelection(room.id, hover.slot)"
          class="slot-hover"
          :style="{ top: `${hover.slot * PX_PER_HOUR}px`, height: `${PX_PER_HOUR}px` }"
        />
        <div
          v-if="selectionBox && selectionBox.roomId === room.id"
          class="slot-selection"
          :style="{ top: `${selectionBox.top}px`, height: `${selectionBox.height}px` }"
        >
          <span class="slot-selection-time">{{ selectionBox.label }}</span>
        </div>
        <ReservationCard
          v-for="block in dayBlocks(room.id)"
          :key="block.reservation.id"
          :item="block"
          @open="emit('open', $event)"
        />
        <div
          v-if="showNowLine"
          class="now-line"
          :style="{ top: `${nowLineTop}px` }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid-wrap {
  height: 100%;
  overflow: auto;
}

.board-grid {
  display: grid;
  min-width: 820px;
}

.grid-corner,
.grid-room-head {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 46px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-color);
}

.grid-corner {
  left: 0;
  z-index: 4;
  font-size: 12px;
  color: var(--text-muted);
}

.grid-room-head {
  border-left: 1px solid var(--border-light);
}

.room-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.room-cap {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 15px;
}

.room-flag {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}

.room-flag--available .room-flag {
  color: #087443;
  background: #dcfae6;
}

.room-flag--maintenance .room-flag {
  color: #9a6700;
  background: #fff1c2;
}

.room-flag--disabled .room-flag {
  color: #5f6368;
  background: #e5e7eb;
}

.grid-time-col {
  border-right: 1px solid var(--border-color);
}

.time-label {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  padding: 2px 8px 0 0;
  font-size: 11px;
  color: var(--text-muted);
  /* 数字右对齐到轴线上方更好看，这里左上对齐即可 */
  justify-content: flex-end;
  box-sizing: border-box;
}

.grid-room-col {
  --hour: 64px; /* 与 PX_PER_HOUR 保持一致 */
  position: relative;
  border-left: 1px solid var(--border-light);
  cursor: cell;
  /* 每小时一条浅色分隔线 */
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

.grid-room-col.room-column--maintenance {
  background-color: rgba(245, 158, 11, 0.035);
}

.grid-room-col.room-column--disabled {
  background-color: rgba(107, 114, 128, 0.045);
}

.now-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 0;
  border-top: 2px solid #f04438;
  z-index: 1;
  pointer-events: none;
}

.now-line::before {
  content: '';
  position: absolute;
  left: -1px;
  top: -4px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f04438;
}
</style>

<style scoped>
.grid-wrap {
  border-radius: inherit;
  background: rgba(255, 255, 255, 0.55);
}

.grid-corner,
.grid-room-head {
  height: 58px;
  background: rgba(255, 255, 255, 0.86);
  border-bottom-color: var(--border-light);
  -webkit-backdrop-filter: blur(16px);
  backdrop-filter: blur(16px);
}

.grid-corner {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.grid-room-head {
  gap: 2px;
}

.room-name {
  font-size: 14px;
  letter-spacing: -0.02em;
}

.room-cap {
  font-size: 11px;
}

.room-flag {
  margin-top: 1px;
}

.grid-time-col {
  border-right-color: var(--border-light);
  background: rgba(255, 255, 255, 0.35);
}

.grid-room-col {
  border-left-color: var(--border-light);
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(var(--hour) - 1px),
    rgba(29, 29, 31, 0.08) calc(var(--hour) - 1px),
    rgba(29, 29, 31, 0.08) var(--hour)
  );
}

.time-label {
  padding-right: 10px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
</style>
