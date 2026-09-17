<!--
  文件职责：实现 MeetingRooms 页面，负责展示、交互和表单状态。
  接口：通过 Pinia store 或 shared/api 调用后端；管理员页面使用 /api/admin/*。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { repairTicketsApi, roomsApi } from '@/shared/api'
import { ApiError } from '@/shared/api/types'
import type { FacilityResponse } from '@/shared/api/types'
import { nowMinutes, toMinutes, todayStr } from '@/utils/datetime'
import type { RoomStatus } from '@/types'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()

onMounted(async () => {
  await roomStore.refreshRooms()
  await store.refreshCalendar(todayStr())
})

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

/* —— 设施报修 —— */
const repairVisible = ref(false)
const repairSaving = ref(false)
const repairRoom = ref<RoomCard | null>(null)
const facilityOptions = ref<FacilityResponse[]>([])
const repairForm = reactive({
  facilityId: null as number | string | null,
  facilityName: '',
  issue: '',
})

async function openRepair(room: RoomCard) {
  repairRoom.value = room
  repairForm.facilityId = null
  repairForm.facilityName = ''
  repairForm.issue = ''
  repairVisible.value = true
  try {
    const detail = await roomsApi.detail(room.id)
    facilityOptions.value = detail.facilities
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '获取设施列表失败')
  }
}

async function submitRepair() {
  if (!repairRoom.value) return
  const issue = repairForm.issue.trim()
  if (!issue) {
    ElMessage.error('请填写故障描述')
    return
  }
  repairSaving.value = true
  try {
    const selected = facilityOptions.value.find((f) => f.id === repairForm.facilityId)
    await repairTicketsApi.create(repairRoom.value.id, {
      facilityId: selected ? selected.id : null,
      facilityName: selected ? null : repairForm.facilityName.trim() || null,
      issue,
    })
    ElMessage.success('报修已提交，管理员会尽快处理')
    repairVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '报修提交失败，请稍后重试')
  } finally {
    repairSaving.value = false
  }
}
</script>

<template>
  <div class="page rooms-page">
    <span class="page-eyebrow">Rooms / Live status</span>
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

          <div class="room-actions">
            <el-button size="small" text type="primary" @click="openRepair(card)">设施报修</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-dialog
      v-model="repairVisible"
      :title="`设施报修：${repairRoom?.name ?? ''}`"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-width="72px" label-position="left">
        <el-form-item label="报修对象">
          <el-select
            v-model="repairForm.facilityId"
            placeholder="整室报修（不选具体设施）"
            clearable
            style="width: 100%"
          >
            <el-option v-for="f in facilityOptions" :key="f.id" :value="f.id" :label="f.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="repairForm.facilityId === null" label="对象名称">
          <el-input v-model="repairForm.facilityName" placeholder="选填，默认按整室处理" maxlength="50" />
        </el-form-item>
        <el-form-item label="故障描述">
          <el-input
            v-model="repairForm.issue"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：投影仪无法开机 / 空调制冷异常"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="primary" :loading="repairSaving" @click="submitRepair">提交报修</el-button>
      </template>
    </el-dialog>
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

<style scoped>
.rooms-page :deep(.el-row) {
  row-gap: 16px;
}

.room-col {
  margin-bottom: 0;
}

.room-card {
  position: relative;
  min-height: 212px;
  padding: 22px;
  border-color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 12px 30px rgba(29, 29, 31, 0.045);
  transition: transform 220ms ease, box-shadow 220ms ease, border-color 220ms ease;
}

.room-card::after {
  position: absolute;
  right: 20px;
  bottom: 20px;
  width: 46px;
  height: 46px;
  border: 1px solid rgba(0, 113, 227, 0.08);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.room-card:hover {
  border-color: rgba(0, 113, 227, 0.22);
  box-shadow: 0 18px 42px rgba(29, 29, 31, 0.09);
  transform: translateY(-4px);
}

.room-name {
  font-size: 18px;
  letter-spacing: -0.035em;
}

.room-status {
  position: relative;
  z-index: 1;
  padding: 5px 9px;
  border-radius: 999px;
  background: rgba(29, 29, 31, 0.045);
  font-size: 11px;
  font-weight: 600;
}

.room-loc {
  margin-top: 7px;
  font-size: 13px;
}

.room-meta {
  gap: 20px;
  margin-top: 20px;
  color: var(--text-secondary);
  font-size: 12px;
}

.room-equipment {
  gap: 7px;
  margin-top: 15px;
}

.eq-tag {
  position: relative;
  z-index: 1;
  padding: 0 9px;
  border-color: rgba(29, 29, 31, 0.08);
  color: var(--text-secondary);
  background: rgba(29, 29, 31, 0.045);
}

.room-next {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
}

.room-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 180ms ease;
}

.room-card:hover .room-actions {
  opacity: 1;
}
</style>
