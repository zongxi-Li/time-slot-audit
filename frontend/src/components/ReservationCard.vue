<script setup lang="ts">
import { computed } from 'vue'
import type { LaidOutReservation } from '@/utils/grid'
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
    :title="`${r.title} ${r.startTime}-${r.endTime} · ${r.userName}`"
    @click.stop="emit('open', r.id)"
  >
    <div class="res-title">
      <span v-if="roomName" class="room-badge">{{ roomName }}</span>
      <span class="res-title-text">{{ r.title }}</span>
      <span v-if="isMine" class="mine-tag">我</span>
    </div>
    <div v-if="showMeta" class="res-meta">{{ r.startTime }} - {{ r.endTime }}</div>
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
