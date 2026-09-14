<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useReservationStore } from '@/stores/reservation'
import {
  BUSINESS_END_HOUR,
  BUSINESS_START_HOUR,
  PX_PER_HOUR,
  formatShort,
  isToday,
  weekdayName,
} from '@/utils/datetime'
import { layoutReservations } from '@/utils/grid'
import type { MeetingRoom } from '@/types'
import ReservationCard from './ReservationCard.vue'

const props = defineProps<{
  /** 已经过筛选的会议室（决定周视图里显示哪些会议室的预约） */
  rooms: MeetingRoom[]
  /** 周视图要展示的 7 天（周一到周日） */
  weekDays: string[]
}>()

const emit = defineEmits<{
  open: [reservationId: string]
  /** 点击空白格子，带预填信息新建预约 */
  create: [payload: { date: string; startTime: string; endTime: string }]
}>()

const store = useReservationStore()

const roomIds = computed(() => new Set(props.rooms.map((r) => r.id)))

function dayBlocks(date: string) {
  return layoutReservations(
    store.activeReservations.filter(
      (r) => r.date === date && roomIds.value.has(r.roomId),
    ),
  )
}

const hours = computed(() => {
  const list: number[] = []
  for (let h = BUSINESS_START_HOUR; h < BUSINESS_END_HOUR; h++) list.push(h)
  return list
})

const gridBodyHeight = (BUSINESS_END_HOUR - BUSINESS_START_HOUR) * PX_PER_HOUR

/* —— “当前时间”红线：30 秒刷新一次 —— */
const now = ref(new Date())
let timer: number | undefined
onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date()
  }, 30_000)
})
onBeforeUnmount(() => window.clearInterval(timer))

const nowLineTop = computed(() => {
  const minutes = now.value.getHours() * 60 + now.value.getMinutes()
  const start = BUSINESS_START_HOUR * 60
  const end = BUSINESS_END_HOUR * 60
  if (minutes < start || minutes > end) return null
  return ((minutes - start) / 60) * PX_PER_HOUR
})

/* —— 点击空白处新建（日期取所点列） —— */
function onColumnClick(e: MouseEvent, date: string) {
  const target = e.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const y = e.clientY - rect.top
  const hour = Math.min(
    BUSINESS_END_HOUR - 1,
    BUSINESS_START_HOUR + Math.floor(y / PX_PER_HOUR),
  )
  emit('create', {
    date,
    startTime: `${String(hour).padStart(2, '0')}:00`,
    endTime: `${String(hour + 1).padStart(2, '0')}:00`,
  })
}
</script>

<template>
  <div class="week-wrap thin-scroll">
    <div
      class="week-grid"
      :style="{ gridTemplateColumns: `64px repeat(${weekDays.length}, minmax(122px, 1fr))` }"
    >
      <!-- 表头 -->
      <div class="week-corner">时间</div>
      <div
        v-for="day in weekDays"
        :key="day"
        class="week-day-head"
        :class="{ 'is-today': isToday(day) }"
      >
        <span class="wh-name">{{ weekdayName(day) }}</span>
        <span class="wh-date">{{ formatShort(day) }}</span>
        <span v-if="isToday(day)" class="wh-today">今天</span>
      </div>

      <!-- 时间轴 -->
      <div class="week-time-col">
        <div
          v-for="h in hours"
          :key="h"
          class="time-label"
          :style="{ height: `${PX_PER_HOUR}px` }"
        >
          {{ String(h).padStart(2, '0') }}:00
        </div>
      </div>

      <!-- 七天列 -->
      <div
        v-for="day in weekDays"
        :key="day"
        class="week-day-col"
        :class="{ 'is-today-col': isToday(day) }"
        :style="{ height: `${gridBodyHeight}px` }"
        @click="onColumnClick($event, day)"
      >
        <ReservationCard
          v-for="block in dayBlocks(day)"
          :key="block.reservation.id"
          :item="block"
          :room-name="block.reservation.roomId"
          @open="emit('open', $event)"
        />
        <div
          v-if="isToday(day) && nowLineTop !== null"
          class="now-line"
          :style="{ top: `${nowLineTop}px` }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.week-wrap {
  height: 100%;
  overflow: auto;
}

.week-grid {
  display: grid;
  min-width: 980px;
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

.week-day-col {
  --hour: 64px; /* 与 PX_PER_HOUR 保持一致 */
  position: relative;
  border-left: 1px solid var(--border-light);
  cursor: pointer;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(var(--hour) - 1px),
    var(--border-light) calc(var(--hour) - 1px),
    var(--border-light) var(--hour)
  );
  background-size: 100% var(--hour);
}

/* 今日列：淡蓝底色突出 */
.week-day-col.is-today-col {
  background-color: #f7faff;
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
