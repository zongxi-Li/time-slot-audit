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

/** 后端状态是大写枚举，CSS 类名统一转小写，避免大小写不匹配导致配色失效 */
const statusClass = computed(() => `status-${(r.value.status ?? '').toLowerCase()}`)

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
    :class="[statusClass, { mine: isMine }]"
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
  padding: 6px 9px;
  overflow: hidden;
  background: #fff;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: 0 3px 8px rgba(29, 29, 31, 0.055);
  transition: transform 180ms ease, box-shadow 180ms ease, filter 180ms ease;
}

.res-card:hover {
  box-shadow: 0 6px 14px rgba(29, 29, 31, 0.1);
  transform: translateY(-1px);
  z-index: 2;
}

/* 已确认（占用中）：实心蓝底 + 蓝描边，与空白格明显区分 */
.res-card.status-confirmed {
  background: #d2e6ff;
  border-color: rgba(0, 113, 227, 0.38);
}

.res-card.status-confirmed .res-title-text {
  color: #004da0;
}

.res-card.status-confirmed .res-meta {
  color: #3f6ea6;
}

/* 待审核：琥珀底 + 琥珀描边 */
.res-card.status-pending {
  background: #ffe8c4;
  border-color: rgba(202, 138, 25, 0.45);
}

.res-card.status-pending .res-title-text {
  color: #8a5300;
}

.res-card.status-pending .res-meta {
  color: #a37e3c;
}

/* 已取消 / 已驳回：置灰、虚线框、删除线，表示时段实际空闲 */
.res-card.status-cancelled,
.res-card.status-rejected {
  background: rgba(29, 29, 31, 0.055);
  border: 1px dashed rgba(29, 29, 31, 0.28);
  opacity: 0.78;
}

.res-card.status-cancelled .res-title-text,
.res-card.status-rejected .res-title-text {
  color: #86868b;
  text-decoration: line-through;
}

.res-card.status-cancelled .res-meta,
.res-card.status-rejected .res-meta {
  color: #a0a0a5;
}

/* 当前用户预约：外圈高亮 */
.res-card.mine {
  box-shadow: inset 0 0 0 1.5px rgba(0, 113, 227, 0.5), 0 3px 8px rgba(29, 29, 31, 0.055);
}

.res-card.mine.status-pending {
  box-shadow: inset 0 0 0 1.5px rgba(214, 143, 29, 0.55), 0 3px 8px rgba(29, 29, 31, 0.055);
}

.res-title {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.res-title-text {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  line-height: 17px;
  letter-spacing: -0.01em;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mine-tag,
.room-badge {
  flex-shrink: 0;
  padding: 0 4px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  line-height: 15px;
}

.mine-tag {
  background: rgba(255, 255, 255, 0.72);
  color: #2a5acc;
}

.room-badge {
  background: rgba(255, 255, 255, 0.65);
  color: inherit;
}

.res-meta {
  overflow: hidden;
  font-size: 11px;
  line-height: 15px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
