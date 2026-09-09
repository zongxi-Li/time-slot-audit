<script setup lang="ts">
import { computed } from 'vue'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { nowMinutes, toMinutes, todayStr } from '@/utils/datetime'
import type { RoomStatus } from '@/types'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()

interface RoomCard {
  id: string
  name: string
  location: string
  capacity: number
  equipment: string[]
  status: RoomStatus
  todayCount: number
  nextSlot: string | null
}

const cards = computed<RoomCard[]>(() => {
  const now = nowMinutes()
  return roomStore.rooms.map((room) => {
    const today = store.listByRoomAndDate(room.id, todayStr())
    const inUse = today.some(
      (r) => now >= toMinutes(r.startTime) && now < toMinutes(r.endTime),
    )
    const next = today.find((r) => toMinutes(r.startTime) >= now)
    return {
      ...room,
      status: inUse ? 'in-use' : 'idle',
      todayCount: today.length,
      nextSlot: next ? `${next.startTime} - ${next.endTime} ${next.title}` : null,
    }
  })
})
</script>

<template>
  <div class="page rooms-page">
    <h2 class="page-title">会议室</h2>
    <p class="page-subtitle">查看会议室容量、设备与当前使用状态（状态根据当前时间实时计算）</p>

    <el-row :gutter="14">
      <el-col v-for="card in cards" :key="card.id" :xs="24" :sm="12" :md="8" :lg="8" class="room-col">
        <div class="panel room-card">
          <div class="room-head">
            <div class="room-name">{{ card.name }}</div>
            <span class="room-status" :class="card.status">
              <span class="status-dot" />
              {{ card.status === 'idle' ? '空闲' : '使用中' }}
            </span>
          </div>

          <div class="room-loc">{{ card.location }}</div>

          <div class="room-meta">
            <span class="meta-item">容量 {{ card.capacity }} 人</span>
            <span class="meta-item">今日 {{ card.todayCount }} 场预约</span>
          </div>

          <div class="room-equipment">
            <el-tag
              v-for="eq in card.equipment"
              :key="eq"
              size="small"
              type="info"
              effect="plain"
              class="eq-tag"
            >
              {{ eq }}
            </el-tag>
          </div>

          <div class="room-next">
            <template v-if="card.nextSlot">下一场：{{ card.nextSlot }}</template>
            <template v-else>今日无后续预约</template>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.room-col {
  margin-bottom: 14px;
}

.room-card {
  padding: 16px;
  height: 100%;
  box-sizing: border-box;
}

.room-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.room-name {
  font-size: 16px;
  font-weight: 600;
}

.room-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.room-status.idle {
  color: #12855f;
}
.room-status.idle .status-dot {
  background: #17b26a;
}

.room-status.in-use {
  color: #b54708;
}
.room-status.in-use .status-dot {
  background: #f79009;
}

.room-loc {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-muted);
}

.room-meta {
  display: flex;
  gap: 14px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--text-secondary);
}

.room-equipment {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
  min-height: 24px;
}

.eq-tag {
  border-radius: 5px;
}

.room-next {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-color);
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
