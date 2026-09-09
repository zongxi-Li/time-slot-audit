<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import {
  addDays,
  formatShort,
  formatWeekRange,
  getWeekDays,
  isToday,
  todayStr,
  weekdayName,
} from '@/utils/datetime'
import type { ReservationDraft } from '@/types'
import ReservationGrid from '@/components/ReservationGrid.vue'
import ReservationWeekGrid from '@/components/ReservationWeekGrid.vue'
import ReservationDialog from '@/components/ReservationDialog.vue'
import ReservationDetail from '@/components/ReservationDetail.vue'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()

/* —— 看板状态 —— */
const selectedDate = ref(todayStr())
const roomFilter = ref<string>('all')
const onlyFree = ref(false)
/** 日视图：会议室 × 时间；周视图：星期 × 时间（课程表样式） */
const viewMode = ref<'day' | 'week'>('day')

const weekDays = computed(() => getWeekDays(selectedDate.value))
const weekRangeLabel = computed(() => formatWeekRange(selectedDate.value))

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

function shiftWeek(days: number) {
  selectedDate.value = addDays(selectedDate.value, days)
}

function backToThisWeek() {
  selectedDate.value = todayStr()
}

/* —— 新建预约 —— */
const dialogVisible = ref(false)
const dialogInitial = ref<Partial<ReservationDraft>>({})

function openCreate(initial: Partial<ReservationDraft> = {}) {
  dialogInitial.value = initial
  dialogVisible.value = true
}

function onCreateFromGrid(payload: { roomId: string; startTime: string; endTime: string }) {
  openCreate({ ...payload, date: selectedDate.value })
}

function onCreateFromWeek(payload: { date: string; startTime: string; endTime: string }) {
  openCreate({ ...payload })
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
    <div class="panel toolbar">
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
          <el-button type="primary" @click="openCreate()">+ 新建预约</el-button>
        </div>
      </div>

      <div class="toolbar-row week-row">
        <div class="week-nav">
          <el-radio-group v-model="viewMode" class="mode-switch">
            <el-radio-button value="day">日视图</el-radio-button>
            <el-radio-button value="week">周视图</el-radio-button>
          </el-radio-group>
          <el-button-group>
            <el-button :icon="ArrowLeft" @click="shiftWeek(-7)">上一周</el-button>
            <el-button @click="backToThisWeek">本周</el-button>
            <el-button @click="shiftWeek(7)">
              下一周
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </el-button-group>
          <span class="week-range">{{ weekRangeLabel }}</span>
        </div>

        <div v-if="viewMode === 'day'" class="day-tabs">
          <button
            v-for="day in weekDays"
            :key="day"
            type="button"
            class="day-tab"
            :class="{ selected: day === selectedDate, today: isToday(day) }"
            @click="selectedDate = day"
          >
            <span class="day-tab-week">{{ weekdayName(day) }}</span>
            <span class="day-tab-date">{{ formatShort(day) }}</span>
            <span v-if="isToday(day)" class="today-dot" />
          </button>
        </div>
      </div>
    </div>

    <!-- 预约网格：日视图（会议室×时间）/ 周视图（星期×时间） -->
    <div class="panel grid-panel">
      <el-empty
        v-if="viewMode === 'day' && filteredRooms.length === 0"
        description="没有符合条件的会议室"
        class="grid-empty"
      />
      <ReservationGrid
        v-else-if="viewMode === 'day'"
        :rooms="filteredRooms"
        :date="selectedDate"
        @open="openDetail"
        @create="onCreateFromGrid"
      />
      <ReservationWeekGrid
        v-else
        :rooms="filteredRooms"
        :week-days="weekDays"
        @open="openDetail"
        @create="onCreateFromWeek"
      />
    </div>

    <ReservationDialog v-model="dialogVisible" :initial="dialogInitial" />
    <ReservationDetail v-model="detailVisible" :reservation-id="detailId" />
  </div>
</template>

<style scoped>
.board-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
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
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
</style>
