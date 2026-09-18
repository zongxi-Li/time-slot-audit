<!--
  文件职责：实现可复用的 ReservationCard Vue 组件。
  接口：通过 props、emits 与父页面通信，必要时通过 store 间接访问 API。
-->
<script setup lang="ts">
import { computed } from 'vue'
import type { LaidOutReservation } from '@/utils/grid'
import { timeLabel } from '@/utils/datetime'
import { useReservationStore } from '@/stores/reservation'

const props = defineProps<{
  item: LaidOutReservation
  /** 周视图等跨会议室场景下显示会议室徽标 */
  roomName?: string
}>()

const emit = defineEmits<{
  open: [reservationId: string]
}>()

const store = useReservationStore()
const r = computed(() => props.item.reservation)
const isMine = computed(() => r.value.userId === store.currentUser.id)
const timeMeta = computed(() => `${timeLabel(r.value.startTime)} - ${timeLabel(r.value.endTime)}`)

const cardStyle = computed(() => {
  const { top, height, lane, laneCount } = props.item
  const width = `calc(${(100 / laneCount).toFixed(2)}% - 6px)`
  return {
    top: `${top + 1}px`,
    height: `${height}px`,
    left: `calc(${((lane * 100) / laneCount).toFixed(2)}% + 3px)`,
    width,
  }
})

const showMeta = computed(() => props.item.height >= 40)
const showOwner = computed(() => props.item.height >= 58)
</script>

<template>
  <div
    class="res-card"
    :class="[`status-${r.status}`, { mine: isMine }]"
    :style="cardStyle"
    :title="`${r.title} ${timeMeta} · ${r.userName}`"
    @click.stop="emit('open', r.id)"
  >
    <div class="res-title">
      <span v-if="roomName" class="room-badge">{{ roomName }}</span>
      <span class="res-title-text">{{ r.title }}</span>
      <span v-if="isMine" class="mine-tag">我</span>
    </div>
    <div v-if="showMeta" class="res-meta">{{ timeMeta }}</div>
    <div v-if="showOwner" class="res-meta">{{ r.userName }}</div>
  </div>
</template>

<style scoped>
.res-card {
  position: absolute;
  padding: 4px 8px;
  border-radius: 6px;
  border-left: 3px solid transparent;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.15s ease, filter 0.15s ease;
}

.res-card:hover {
  filter: brightness(0.97);
  box-shadow: 0 2px 8px rgba(31, 35, 41, 0.12);
  z-index: 2;
}

/* 已预约：蓝色系 */
.res-card.status-confirmed {
  background: #e9f0fe;
  border-left-color: #3d7fff;
}
.res-card.status-confirmed .res-title-text {
  color: #2456b3;
}
.res-card.status-confirmed .res-meta {
  color: #6a83b8;
}

/* 待审核：橙色系 */
.res-card.status-pending {
  background: #fdf3e4;
  border-left-color: #ee9a2e;
}
.res-card.status-pending .res-title-text {
  color: #9c5f10;
}
.res-card.status-pending .res-meta {
  color: #b78a4c;
}

/* 当前用户预约：外圈高亮 */
.res-card.mine {
  box-shadow: inset 0 0 0 1.5px rgba(61, 127, 255, 0.55);
}
.res-card.mine.status-pending {
  box-shadow: inset 0 0 0 1.5px rgba(238, 154, 46, 0.6);
}

.res-title {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.res-title-text {
  font-size: 12px;
  font-weight: 600;
  line-height: 17px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mine-tag {
  flex-shrink: 0;
  padding: 0 4px;
  border-radius: 4px;
  font-size: 10px;
  line-height: 15px;
  background: rgba(61, 127, 255, 0.16);
  color: #2a5acc;
  font-weight: 600;
}

.room-badge {
  flex-shrink: 0;
  padding: 0 4px;
  border-radius: 4px;
  font-size: 10px;
  line-height: 15px;
  background: rgba(255, 255, 255, 0.65);
  color: inherit;
  font-weight: 600;
}

.res-meta {
  font-size: 11px;
  line-height: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>

<style scoped>
.res-card {
  padding: 6px 9px;
  border-left-width: 0;
  border-radius: 10px;
  box-shadow: 0 3px 8px rgba(29, 29, 31, 0.055);
  transition: transform 180ms ease, box-shadow 180ms ease, filter 180ms ease;
}

.res-card:hover {
  filter: none;
  box-shadow: 0 9px 18px rgba(29, 29, 31, 0.12);
  transform: translateY(-1px);
}

.res-card.status-confirmed {
  background: #e9f4ff;
  border-left-color: transparent;
}

.res-card.status-confirmed .res-title-text {
  color: #0062c4;
}

.res-card.status-confirmed .res-meta {
  color: #5c8db9;
}

.res-card.status-pending {
  background: #fff4df;
  border-left-color: transparent;
}

.res-card.status-pending .res-title-text {
  color: #a65f00;
}

.res-card.status-pending .res-meta {
  color: #b58a4b;
}

.res-card.mine {
  box-shadow: inset 0 0 0 2px rgba(0, 113, 227, 0.42), 0 3px 8px rgba(29, 29, 31, 0.055);
}

.res-card.mine.status-pending {
  box-shadow: inset 0 0 0 2px rgba(238, 154, 46, 0.48), 0 3px 8px rgba(29, 29, 31, 0.055);
}

.res-title-text {
  font-size: 12px;
  letter-spacing: -0.01em;
}

.mine-tag,
.room-badge {
  border-radius: 999px;
}
</style>
