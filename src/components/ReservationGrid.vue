<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useReservationStore } from '@/stores/reservation'
import {
  BUSINESS_END_HOUR,
  BUSINESS_START_HOUR,
  PX_PER_HOUR,
  isToday,
} from '@/utils/datetime'
import { layoutReservations } from '@/utils/grid'
import type { MeetingRoom } from '@/types'
import ReservationCard from './ReservationCard.vue'

const props = defineProps<{
  /** 已经过筛选的会议室列表 */
  rooms: MeetingRoom[]
  /** 选定日期 YYYY-MM-DD */
  date: string
}>()

const emit = defineEmits<{
  open: [reservationId: string]
  /** 点击空白格子，带着预填信息去新建预约 */
  create: [payload: { roomId: string; startTime: string; endTime: string }]
}>()

const store = useReservationStore()

const hours = computed(() => {
  const list: number[] = []
  for (let h = BUSINESS_START_HOUR; h < BUSINESS_END_HOUR; h++) list.push(h)
  return list
})

const gridBodyHeight = (BUSINESS_END_HOUR - BUSINESS_START_HOUR) * PX_PER_HOUR

function dayBlocks(roomId: string) {
  return layoutReservations(store.listByRoomAndDate(roomId, props.date))
}

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

const showNowLine = computed(() => isToday(props.date) && nowLineTop.value !== null)

/* —— 点击空白处新建 —— */
function onColumnClick(e: MouseEvent, roomId: string) {
  const target = e.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const y = e.clientY - rect.top
  const hour = Math.min(
    BUSINESS_END_HOUR - 1,
    BUSINESS_START_HOUR + Math.floor(y / PX_PER_HOUR),
  )
  const hh = String(hour).padStart(2, '0')
  emit('create', { roomId, startTime: `${hh}:00`, endTime: `${String(hour + 1).padStart(2, '0')}:00` })
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
      <div v-for="room in rooms" :key="room.id" class="grid-room-head">
        <span class="room-name">{{ room.name }}</span>
        <span class="room-cap">{{ room.capacity }} 人</span>
      </div>

      <!-- 时间轴 -->
      <div class="grid-time-col">
        <div
          v-for="h in hours"
          :key="h"
          class="time-label"
          :style="{ height: `${PX_PER_HOUR}px` }"
        >
          {{ String(h).padStart(2, '0') }}:00
        </div>
      </div>

      <!-- 会议室列 -->
      <div
        v-for="room in rooms"
        :key="room.id"
        class="grid-room-col"
        :style="{ height: `${gridBodyHeight}px` }"
        @click="onColumnClick($event, room.id)"
      >
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
  cursor: pointer;
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
