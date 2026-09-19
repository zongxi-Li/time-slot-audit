<!--
  文件职责：实现 ReservationBoard 页面，负责展示、交互和表单状态。
  接口：通过 Pinia store 或 shared/api 调用后端；管理员页面使用 /api/admin/*。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useBookingWindowStore } from '@/stores/bookingWindow'
import {
  addDays,
  formatDateRange,
  formatShort,
  getRollingDays,
  weekdayName,
} from '@/utils/datetime'
import { useSystemTimeStore } from '@/stores/systemTime'
import type { ReservationDraft, SlotSelection } from '@/types'
import ReservationGrid from '@/components/ReservationGrid.vue'
import ReservationWeekGrid from '@/components/ReservationWeekGrid.vue'
import ReservationDialog from '@/components/ReservationDialog.vue'
import ReservationDetail from '@/components/ReservationDetail.vue'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
const systemTime = useSystemTimeStore()
const bookingWindow = useBookingWindowStore()

/* —— 看板状态 —— */
const selectedDate = ref(systemTime.date)
const roomFilter = ref<string>('all')
const onlyFree = ref(false)
/** 日视图：会议室 × 时间；周视图：日期 × 时间（以当前日期为中心的滚动窗口） */
const viewMode = ref<'day' | 'week'>('day')

/** 日/周视图共用的展示窗口：当前日期前 4 天 ~ 后 4 天（共 9 天） */
const visibleDays = computed(() => getRollingDays(selectedDate.value))
const weekRangeLabel = computed(() =>
  formatDateRange(visibleDays.value[0], visibleDays.value[visibleDays.value.length - 1]),
)

onMounted(async () => {
  void bookingWindow.refresh().catch(() => undefined)
  await roomStore.refreshRooms()
  await store.refreshCalendar(selectedDate.value)
})

watch(selectedDate, (date) => {
  // store 一次拉取整个滚动窗口，日/周视图共用，切日期只需一次刷新
  void store.refreshCalendar(date)
})
watch(() => systemTime.revision, () => {
  selectedDate.value = systemTime.date
})

function isToday(day: string) {
  return day === systemTime.date
}

/** “空闲” = 该会议室当天没有任何有效预约 */
const filteredRooms = computed(() => {
  let rooms = roomStore.rooms
  if (roomFilter.value !== 'all') {
    rooms = rooms.filter((r) => r.id === roomFilter.value)
  }
  if (onlyFree.value) {
    rooms = rooms.filter((r) => store.listByRoomAndDate(r.id, selectedDate.value).length === 0)
  }
  return rooms
})

function shiftDays(days: number) {
  selectedDate.value = addDays(selectedDate.value, days)
}

/** 日/周视图右键拖动：平移日期 */
function onBoardPan(days: number) {
  shiftDays(days)
}

function backToToday() {
  selectedDate.value = systemTime.date
}

/* —— 新建预约 —— */
const dialogVisible = ref(false)
const dialogInitial = ref<Partial<ReservationDraft>>({})

/** 网格上拖拽框选的时间段；点“新建预约”时作为表单预填 */
const gridSelection = ref<SlotSelection | null>(null)

// 切日期/视图/筛选后原框选不再指向可见区域，直接作废
watch([selectedDate, viewMode, roomFilter, onlyFree], () => {
  gridSelection.value = null
})

function onGridSelect(selection: SlotSelection | null) {
  gridSelection.value = selection
}

function openCreate(initial: Partial<ReservationDraft> = {}) {
  dialogInitial.value = initial
  dialogVisible.value = true
}

function onCreateClick() {
  const sel = gridSelection.value
  openCreate(
    sel
      ? { roomId: sel.roomId ?? '', date: sel.date, startTime: sel.startTime, endTime: sel.endTime }
      : {},
  )
}

/* —— 预约详情 —— */
const detailVisible = ref(false)
const detailId = ref<string | null>(null)

function openDetail(id: string) {
  detailId.value = id
  detailVisible.value = true
}
</script>

<template>
  <div class="board-page page">
    <!-- 顶部工具栏 -->
    <header class="toolbar">
      <div class="toolbar-row">
        <h2 class="toolbar-title">会议室预约</h2>
        <div class="toolbar-actions">
          <el-select
            v-model="roomFilter"
            style="width: 150px"
            placeholder="会议室筛选"
          >
            <el-option value="all" label="全部会议室" />
            <el-option
              v-for="room in roomStore.rooms"
              :key="room.id"
              :value="room.id"
              :label="room.name"
            />
          </el-select>
          <el-checkbox v-if="viewMode === 'day'" v-model="onlyFree" class="free-check">
            仅显示空闲会议室
          </el-checkbox>
          <el-button type="primary" @click="onCreateClick">+ 新建预约</el-button>
        </div>
      </div>

      <div class="toolbar-row week-row">
        <div class="week-nav">
          <el-radio-group v-model="viewMode" class="mode-switch">
            <el-radio-button value="day">日视图</el-radio-button>
            <el-radio-button value="week">周视图</el-radio-button>
          </el-radio-group>
          <el-button-group>
            <el-button :icon="ArrowLeft" @click="shiftDays(-1)">前一天</el-button>
            <el-button @click="backToToday">今天</el-button>
            <el-button @click="shiftDays(1)">
              后一天
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </el-button-group>
          <span class="week-range">{{ weekRangeLabel }}</span>
        </div>

        <div v-if="viewMode === 'day'" class="day-tabs">
          <button
            v-for="day in visibleDays"
            :key="day"
            type="button"
            class="day-tab"
            :class="{ selected: day === selectedDate, today: day === systemTime.date }"
            @click="selectedDate = day"
          >
            <span class="day-tab-week">{{ weekdayName(day) }}</span>
            <span class="day-tab-date">{{ formatShort(day) }}</span>
            <span v-if="isToday(day)" class="today-dot" />
          </button>
        </div>
      </div>
    </header>

    <!-- 预约网格：日视图（会议室×时间）/ 周视图（星期×时间） -->
    <div class="board-workspace">
      <section class="grid-panel">
        <el-empty
          v-if="viewMode === 'day' && filteredRooms.length === 0"
          description="没有符合条件的会议室"
          class="grid-empty"
        />
        <ReservationGrid
          v-else-if="viewMode === 'day'"
          :rooms="filteredRooms"
          :date="selectedDate"
          :selection="gridSelection"
          @open="openDetail"
          @select="onGridSelect"
          @pan="onBoardPan"
        />
        <ReservationWeekGrid
          v-else
          :rooms="filteredRooms"
          :days="visibleDays"
          :selection="gridSelection"
          @open="openDetail"
          @select="onGridSelect"
          @pan="onBoardPan"
        />
      </section>

      <ReservationDetail v-model="detailVisible" :reservation-id="detailId" mode="panel" />
    </div>

    <ReservationDialog
      v-model="dialogVisible"
      :initial="dialogInitial"
      @saved="gridSelection = null"
    />
  </div>
</template>

<style scoped>
.board-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.board-workspace {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  gap: 12px;
}

.toolbar {
  padding: 14px 16px 12px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-row + .toolbar-row {
  margin-top: 12px;
}

.toolbar-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.free-check {
  margin-right: 2px;
}

.week-nav {
  display: flex;
  align-items: center;
  gap: 14px;
}

.mode-switch {
  margin-right: 2px;
}

.week-range {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.day-tabs {
  display: flex;
  gap: 6px;
}

.day-tab {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-width: 52px;
  padding: 5px 8px 7px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.day-tab:hover {
  border-color: var(--el-color-primary-light-5);
}

.day-tab.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.day-tab-week {
  font-size: 11px;
  color: var(--text-muted);
  line-height: 14px;
}

.day-tab.selected .day-tab-week {
  color: var(--el-color-primary);
}

.day-tab-date {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 18px;
}

.today-dot {
  position: absolute;
  top: 5px;
  right: 6px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #f04438;
}

.grid-panel {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0 0 4px;
}

.grid-empty {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
</style>

<style scoped>
@media (max-width: 760px) {
  .week-nav {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .week-nav .mode-switch {
    justify-self: start;
  }

  .week-nav :deep(.el-button-group) {
    display: flex;
    width: 100%;
    min-width: 0;
    flex: none;
  }

  .week-nav :deep(.el-button-group .el-button) {
    min-width: 0;
    flex: 1;
    padding: 0 7px;
  }

  .week-range {
    grid-column: 1;
  }
}
</style>

<style scoped>
.toolbar {
  flex: 0 0 auto;
  padding: 14px 18px 12px;
  border-bottom: 1px solid var(--border-light);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: none;
  -webkit-backdrop-filter: blur(18px);
  backdrop-filter: blur(18px);
}

.toolbar-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.045em;
}

.toolbar-actions {
  gap: 12px;
}

.toolbar-actions :deep(.el-select) {
  min-width: 150px;
}

.week-row {
  align-items: flex-end;
  margin-top: 20px !important;
}

.week-nav {
  gap: 12px;
  flex-wrap: wrap;
}

.mode-switch :deep(.el-radio-button__inner) {
  min-width: 68px;
  padding: 9px 14px;
}

.week-nav :deep(.el-button) {
  min-height: 34px;
}

.week-range {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.day-tabs {
  gap: 7px;
}

.day-tab {
  min-width: 57px;
  padding: 8px 9px 9px;
  border-color: transparent;
  border-radius: 13px;
  background: rgba(29, 29, 31, 0.045);
  transition: transform 180ms ease, background-color 180ms ease, box-shadow 180ms ease;
}

.day-tab:hover {
  border-color: transparent;
  background: rgba(0, 113, 227, 0.08);
  transform: translateY(-2px);
}

.day-tab.selected {
  border-color: transparent;
  background: var(--el-color-primary);
  box-shadow: 0 8px 17px rgba(0, 113, 227, 0.2);
}

.day-tab.selected .day-tab-week,
.day-tab.selected .day-tab-date {
  color: #fff;
}

.day-tab.selected .today-dot {
  background: #fff;
}

.grid-panel {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.92);
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.board-page {
  gap: 0;
  padding: 0;
  overflow: hidden;
}

@media (max-width: 980px) {
  .week-row {
    align-items: flex-start;
  }

  .day-tabs {
    width: 100%;
    overflow-x: auto;
    padding-bottom: 3px;
  }
}

@media (max-width: 760px) {
  .board-workspace {
    flex-direction: column;
    gap: 0;
    overflow-y: auto;
  }

  .grid-panel {
    flex: 1 1 auto;
    min-height: 420px;
    overflow: auto;
  }

  .toolbar {
    padding: 14px 14px 12px;
  }

  .toolbar-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .toolbar-actions :deep(.el-select) {
    flex: 1;
    min-width: 140px;
  }

  .toolbar-actions :deep(.el-button) {
    flex: 1;
  }

  .week-nav {
    width: 100%;
  }

  .week-nav :deep(.el-button-group) {
    flex: 1;
  }

  .week-nav :deep(.el-button-group .el-button) {
    padding: 0 9px;
  }

  .week-range {
    width: 100%;
  }
}
</style>
