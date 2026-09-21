<!--
  文件职责：实现 MeetingRooms 页面，负责展示、交互和表单状态。
  接口：通过 Pinia store 或 shared/api 调用后端；管理员页面使用 /api/admin/*。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useReservationStore } from '@/stores/reservation'
import { useSystemTimeStore } from '@/stores/systemTime'
import { repairTicketsApi, roomsApi } from '@/shared/api'
import { roomQueryApi, type RoomFilterParams } from '@/shared/api/roomQuery'
import { ApiError } from '@/shared/api/types'
import type { FacilityResponse } from '@/shared/api/types'
import { Calendar, InfoFilled, Location, Monitor, RefreshLeft, Search } from '@element-plus/icons-vue'
import { toMinutes } from '@/utils/datetime'
import { roomStatusMeta } from '@/utils/roomStatus'
import type { MeetingRoom } from '@/types'

const store = useReservationStore()
const systemTime = useSystemTimeStore()

/* —— 条件筛选与时段空闲查询：走 /rooms 与 /rooms/available 真实接口 —— */
const rooms = ref<MeetingRoom[]>([])
const roomsLoading = ref(false)
const filterForm = reactive({
  location: '',
  minCapacity: null as number | null,
  facility: '',
})
const slotQuery = reactive({
  date: '',
  startTime: '',
  endTime: '',
  active: false,
})

onMounted(async () => {
  slotQuery.date = systemTime.date
  await fetchRooms()
  await store.refreshCalendar(systemTime.date)
})

watch(() => systemTime.revision, () => void store.refreshCalendar(systemTime.date))

function filterParams(): RoomFilterParams {
  return {
    location: filterForm.location.trim() || undefined,
    minCapacity: filterForm.minCapacity ?? undefined,
    facility: filterForm.facility.trim() || undefined,
  }
}

/** 按当前查询模式取数：空闲查询激活时叠加日期时段，否则仅按筛选条件。 */
async function fetchRooms() {
  roomsLoading.value = true
  try {
    rooms.value = slotQuery.active
      ? await roomQueryApi.available(slotQuery.date, slotQuery.startTime, slotQuery.endTime, filterParams())
      : await roomQueryApi.list(filterParams())
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '查询会议室失败')
  } finally {
    roomsLoading.value = false
  }
}

async function queryAvailable() {
  if (!slotQuery.date || !slotQuery.startTime || !slotQuery.endTime) {
    ElMessage.warning('请先选择查询日期与起止时间')
    return
  }
  slotQuery.active = true
  await fetchRooms()
}

async function clearSlotQuery() {
  slotQuery.active = false
  await fetchRooms()
}

async function resetFilters() {
  filterForm.location = ''
  filterForm.minCapacity = null
  filterForm.facility = ''
  slotQuery.active = false
  await fetchRooms()
}

const availabilitySummary = computed(() => {
  if (!slotQuery.active) return ''
  const crossesDay = slotQuery.endTime <= slotQuery.startTime
  return `${slotQuery.date} ${slotQuery.startTime} ~ ${slotQuery.endTime}${crossesDay ? '（次日结束）' : ''}，共 ${rooms.value.length} 间空闲`
})

interface RoomCard extends MeetingRoom {
  displayStatus: 'available' | 'in-use' | 'maintenance' | 'disabled'
  statusLabel: string
  todayCount: number
  nextSlot: string | null
}

const cards = computed<RoomCard[]>(() => {
  const current = systemTime.now
  const now = current.getHours() * 60 + current.getMinutes()
  return rooms.value.map((room) => {
    const today = store.listByRoomAndDate(room.id, systemTime.date)
    const inUse = today.some(
      (r) => now >= toMinutes(r.startTime) && now < toMinutes(r.endTime),
    )
    const next = today.find((r) => toMinutes(r.startTime) >= now)
    const displayStatus = room.status === 'AVAILABLE'
      ? (inUse ? 'in-use' : 'available')
      : roomStatusMeta[room.status].tone
    const statusLabel = room.status === 'AVAILABLE' && inUse
      ? '使用中'
      : roomStatusMeta[room.status].label

    return {
      ...room,
      displayStatus,
      statusLabel,
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
    <p class="page-subtitle">按位置、容量与设备筛选会议室，并可查询指定日期时段内的空闲会议室</p>

    <div class="panel filter-panel">
      <div class="filter-grid">
        <div class="field">
          <span class="field-label">位置</span>
          <el-input
            v-model="filterForm.location"
            placeholder="关键字，如 3F"
            clearable
            :prefix-icon="Location"
            @keyup.enter="fetchRooms"
            @clear="fetchRooms"
          />
        </div>
        <div class="field field--capacity">
          <span class="field-label">最小容量</span>
          <el-input-number
            v-model="filterForm.minCapacity"
            :min="1"
            placeholder="人数"
            controls-position="right"
          />
        </div>
        <div class="field">
          <span class="field-label">设备</span>
          <el-input
            v-model="filterForm.facility"
            placeholder="设施关键字，如 投影"
            clearable
            :prefix-icon="Monitor"
            @keyup.enter="fetchRooms"
            @clear="fetchRooms"
          />
        </div>
        <div class="field field--slot" :class="{ 'is-active': slotQuery.active }">
          <span class="field-label">
            <el-icon class="field-label-icon"><Calendar /></el-icon>
            空闲时段
          </span>
          <div class="slot-controls">
            <el-date-picker
              v-model="slotQuery.date"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="日期"
            />
            <span class="slot-sep">~</span>
            <el-time-select
              v-model="slotQuery.startTime"
              start="00:00"
              end="23:30"
              step="00:30"
              placeholder="开始"
            />
            <span class="slot-sep">~</span>
            <el-time-select
              v-model="slotQuery.endTime"
              start="00:00"
              end="23:30"
              step="00:30"
              placeholder="结束"
            />
          </div>
        </div>
        <div class="filter-actions">
          <el-button type="primary" :icon="Search" :loading="roomsLoading" @click="fetchRooms">查询</el-button>
          <el-button plain type="primary" :icon="Calendar" :loading="roomsLoading" @click="queryAvailable">查空闲</el-button>
          <el-button text :icon="RefreshLeft" @click="resetFilters">重置</el-button>
        </div>
      </div>

      <transition name="slot-chip">
        <div v-if="slotQuery.active" class="slot-chip">
          <el-icon class="slot-chip-icon"><Calendar /></el-icon>
          <span class="slot-chip-text">空闲查询：{{ availabilitySummary }}</span>
          <el-button link type="primary" @click="clearSlotQuery">清除时段过滤</el-button>
        </div>
      </transition>

      <div class="filter-foot">
        <el-icon class="foot-icon"><InfoFilled /></el-icon>
        结束时间不晚于开始时间按次日结束计算（跨天）；“查空闲”仅返回该时段无预约且处于可预约状态的会议室。
      </div>
    </div>

    <div v-if="!cards.length && !roomsLoading" class="panel empty-panel">
      <div class="empty-icon">
        <el-icon :size="26"><Calendar /></el-icon>
      </div>
      <div class="empty-title">{{ slotQuery.active ? '该时段没有空闲会议室' : '没有符合条件的会议室' }}</div>
      <div class="empty-desc">
        {{ slotQuery.active ? '换个时间段试试，或清除时段过滤后查看全部会议室' : '调整筛选条件，或重置后查看全部会议室' }}
      </div>
      <el-button plain type="primary" :icon="RefreshLeft" @click="resetFilters">重置筛选</el-button>
    </div>

    <el-row v-show="cards.length" v-loading="roomsLoading" :gutter="14">
      <el-col v-for="card in cards" :key="card.id" :xs="24" :sm="12" :md="8" :lg="8" class="room-col">
        <div class="panel room-card" :class="`room-card--${card.displayStatus}`">
          <div class="room-head">
            <div class="room-name">{{ card.name }}</div>
            <span class="room-status" :class="card.displayStatus">
              <span class="status-dot" />
              {{ card.statusLabel }}
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
/* —— 筛选工具栏：字段块 + 迷你标签，时段查询为蓝色强调分组 —— */
.filter-panel {
  margin-bottom: 18px;
  padding: 18px 20px 14px;
}

.filter-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 18px;
  align-items: flex-end;
}

.field {
  display: flex;
  flex: 1 1 160px;
  min-width: 150px;
  max-width: 230px;
  flex-direction: column;
}

.field--capacity {
  flex: 0 1 140px;
  min-width: 128px;
  max-width: 150px;
}

.field-label {
  margin-bottom: 6px;
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--text-muted);
}

.field :deep(.el-input-number) {
  width: 100%;
}

.field--slot {
  flex: 1.9 1 400px;
  max-width: 560px;
  padding: 9px 12px 11px;
  margin: -9px -6px -11px;
  border: 1px solid rgba(0, 113, 227, 0.16);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, rgba(237, 246, 253, 0.62), rgba(237, 246, 253, 0.28));
  transition: border-color 200ms ease, background 200ms ease, box-shadow 200ms ease;
}

.field--slot .field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #0062c4;
}

.field-label-icon {
  font-size: 13px;
}

.field--slot.is-active {
  border-color: rgba(0, 113, 227, 0.34);
  background: rgba(237, 246, 253, 0.92);
  box-shadow: 0 6px 18px rgba(0, 113, 227, 0.09);
}

.slot-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.slot-controls :deep(.el-date-editor) {
  width: 126px;
  flex: none;
}

.slot-sep {
  font-size: 12px;
  color: var(--text-muted);
}

.filter-actions {
  display: flex;
  flex: none;
  margin-left: auto;
  align-self: flex-end;
}

.slot-chip {
  display: flex;
  width: fit-content;
  gap: 8px;
  align-items: center;
  margin-top: 12px;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.07);
  color: #0062c4;
  font-size: 12.5px;
}

.slot-chip-icon {
  font-size: 14px;
}

.slot-chip-enter-active,
.slot-chip-leave-active {
  transition: opacity 200ms ease, transform 200ms ease;
}

.slot-chip-enter-from,
.slot-chip-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.filter-foot {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 13px;
  padding-top: 10px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-muted);
}

.foot-icon {
  font-size: 13px;
  color: var(--text-muted);
}

/* —— 空态面板：图标 + 结论 + 动作 —— */
.empty-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 54px 24px 48px;
  text-align: center;
}

.empty-icon {
  display: flex;
  width: 64px;
  height: 64px;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
  border-radius: 50%;
  background: rgba(0, 113, 227, 0.07);
  color: var(--el-color-primary);
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.empty-desc {
  margin-bottom: 14px;
  font-size: 12.5px;
  color: var(--text-muted);
}

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

.room-status.available {
  color: #12855f;
}
.room-status.available .status-dot {
  background: #17b26a;
}

.room-status.in-use {
  color: #b54708;
}
.room-status.in-use .status-dot {
  background: #f79009;
}

.room-status.maintenance {
  color: #9a6700;
}
.room-status.maintenance .status-dot {
  background: #d97706;
}

.room-status.disabled {
  color: #6e6e73;
}
.room-status.disabled .status-dot {
  background: #86868b;
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

.room-card--maintenance {
  border-color: rgba(217, 119, 6, 0.2);
  background: rgba(255, 251, 235, 0.86);
}

.room-card--disabled {
  border-color: rgba(110, 110, 115, 0.18);
  background: rgba(245, 245, 247, 0.9);
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
