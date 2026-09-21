<!--
  文件职责：前端业务模块的 MyMeetings 入口、页面、组件或类型定义。
  接口：导出本模块的页面、store、API 或类型。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { meetingsApi } from '../api'
import { formatDateTime } from '@/utils/datetime'
import { RESERVATION_STATUS_TEXT } from '@/utils/reservationStatus'
import { useSystemTimeStore } from '@/stores/systemTime'
import type { MeetingExecutionView } from '../api'
import AttendeeManager from '../components/AttendeeManager.vue'
import ExecutionRecordDialog from '../components/ExecutionRecordDialog.vue'

type PhaseFilter = '全部' | '待开始' | '进行中' | '已结束' | '已取消'
const phaseFilter = ref<PhaseFilter>('全部')
const filterOptions: PhaseFilter[] = ['全部', '待开始', '进行中', '已结束', '已取消']

const loading = ref(false)
const meetings = ref<MeetingExecutionView[]>([])
const systemTime = useSystemTimeStore()

/** 按预约时间推导展示阶段（展示状态不回写，遵守核心契约） */
function phaseOf(m: MeetingExecutionView): PhaseFilter {
  if (m.reservationStatus === 'CANCELLED' || m.reservationStatus === 'REJECTED') return '已取消'
  const start = new Date(m.startTime).getTime()
  const end = new Date(m.endTime).getTime()
  const now = systemTime.now.getTime()
  if (now < start) return '待开始'
  if (now < end) return '进行中'
  return '已结束'
}

const filteredList = computed(() =>
  meetings.value.filter((m) => phaseFilter.value === '全部' || phaseOf(m) === phaseFilter.value),
)

const phaseTagType: Record<PhaseFilter, 'primary' | 'success' | 'info' | 'danger' | 'warning'> = {
  待开始: 'primary',
  进行中: 'success',
  已结束: 'info',
  已取消: 'danger',
  全部: 'info',
}

const attendanceText: Record<string, string> = {
  EXPECTED: '待签到',
  CHECKED_IN: '已签到',
  CHECKED_OUT: '已签退',
  NO_SHOW: '缺席',
}
const attendanceTagType: Record<string, 'info' | 'success' | 'primary' | 'danger'> = {
  EXPECTED: 'info',
  CHECKED_IN: 'success',
  CHECKED_OUT: 'primary',
  NO_SHOW: 'danger',
}

async function load() {
  loading.value = true
  try {
    meetings.value = await meetingsApi.my()
  } catch (error) {
    ElMessage.error((error as Error).message || '我的会议加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
watch(() => systemTime.revision, () => void load())

/** 与后端规则一致的客户端按钮预判；最终以后端校验为准 */
function canCheckIn(m: MeetingExecutionView): boolean {
  if (m.reservationStatus !== 'CONFIRMED' || m.myAttendanceStatus !== 'EXPECTED') return false
  const start = new Date(m.startTime).getTime()
  const end = new Date(m.endTime).getTime()
  const now = systemTime.now.getTime()
  return now >= start - 15 * 60_000 && now < end
}

function canCheckOut(m: MeetingExecutionView): boolean {
  if (m.reservationStatus !== 'CONFIRMED' || m.myAttendanceStatus !== 'CHECKED_IN') return false
  const end = new Date(m.endTime).getTime()
  return systemTime.now.getTime() <= end + 60 * 60_000
}

function canManage(m: MeetingExecutionView): boolean {
  return m.myRole === 'ORGANIZER' && (m.reservationStatus === 'CONFIRMED' || m.reservationStatus === 'PENDING')
}

async function doCheckIn(m: MeetingExecutionView) {
  try {
    await meetingsApi.checkIn(m.reservationId)
    ElMessage.success('签到成功')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '签到失败')
  }
}

async function doCheckOut(m: MeetingExecutionView) {
  try {
    await meetingsApi.checkOut(m.reservationId)
    ElMessage.success('签退成功')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '签退失败')
  }
}

const managerVisible = ref(false)
const managerId = ref<number | null>(null)
const managerTitle = ref('')
const managerCanManage = ref(false)
const executionRecordVisible = ref(false)
const executionTarget = ref<MeetingExecutionView | null>(null)

function openManager(m: MeetingExecutionView) {
  managerId.value = m.reservationId
  managerTitle.value = m.title
  managerCanManage.value = canManage(m)
  managerVisible.value = true
}

function canRecordExecution(m: MeetingExecutionView): boolean {
  return canManage(m) && m.reservationStatus === 'CONFIRMED'
    && systemTime.now.getTime() >= new Date(m.endTime).getTime()
}

function openExecutionRecord(m: MeetingExecutionView) {
  executionTarget.value = m
  executionRecordVisible.value = true
}
</script>

<template>
  <div class="page meetings-page">
    <h2 class="page-title">我的会议</h2>
    <p class="page-subtitle">参与人管理、签到签退、出勤与历史记录的个人执行工作台</p>

    <div class="panel table-panel">
      <div class="filter-bar">
        <el-radio-group v-model="phaseFilter">
          <el-radio-button v-for="opt in filterOptions" :key="opt" :value="opt">{{ opt }}</el-radio-button>
        </el-radio-group>
        <el-button text type="primary" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="filteredList" style="width: 100%" empty-text="暂无会议记录">
        <el-table-column prop="title" label="会议主题" min-width="170" show-overflow-tooltip />
        <el-table-column prop="roomName" label="会议室" width="90" />
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }} - {{ row.endTime.slice(11, 16) }}</template>
        </el-table-column>
        <el-table-column label="我的角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.myRole === 'ORGANIZER' ? 'warning' : 'primary'" size="small" effect="light">
              {{ row.myRole === 'ORGANIZER' ? '组织者' : '参与人' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的出勤" width="100">
          <template #default="{ row }">
            <el-tag
              :type="attendanceTagType[row.myAttendanceStatus] ?? 'info'"
              size="small"
              effect="light"
            >
              {{ attendanceText[row.myAttendanceStatus] ?? '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实际使用记录" min-width="190">
          <template #default="{ row }">
            <template v-if="row.actualStartTime && row.actualEndTime">
              <div>{{ formatDateTime(row.actualStartTime) }} - {{ row.actualEndTime.slice(11, 16) }}</div>
              <div class="muted-text">实际参会 {{ row.actualAttendeeCount ?? 0 }} 人</div>
            </template>
            <span v-else class="muted-text">未登记</span>
          </template>
        </el-table-column>
        <el-table-column label="阶段" width="90">
          <template #default="{ row }">
            <el-tag :type="phaseTagType[phaseOf(row)]" size="small" effect="light">
              {{ row.reservationStatus === 'PENDING' ? RESERVATION_STATUS_TEXT.PENDING : phaseOf(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canRecordExecution(row)"
              link
              type="success"
              size="small"
              @click="openExecutionRecord(row)"
            >
              {{ row.actualStartTime ? '修改使用记录' : '登记使用记录' }}
            </el-button>
            <el-button
              v-if="canCheckIn(row)"
              type="success"
              size="small"
              @click="doCheckIn(row)"
            >
              签到
            </el-button>
            <el-button
              v-if="canCheckOut(row)"
              type="warning"
              plain
              size="small"
              @click="doCheckOut(row)"
            >
              签退
            </el-button>
            <el-button
              v-if="row.reservationStatus === 'CONFIRMED' || row.reservationStatus === 'PENDING'"
              link
              type="primary"
              size="small"
              @click="openManager(row)"
            >
              参与人
            </el-button>
            <el-button
              v-if="row.reservationStatus === 'CANCELLED' || row.reservationStatus === 'REJECTED' || phaseOf(row) === '已结束'"
              link
              size="small"
              @click="openManager(row)"
            >
              出勤记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <AttendeeManager
      v-model="managerVisible"
      :reservation-id="managerId"
      :reservation-title="managerTitle"
      :can-manage="managerCanManage"
      @changed="load"
    />
    <ExecutionRecordDialog
      v-if="executionTarget"
      v-model="executionRecordVisible"
      :reservation-id="executionTarget.reservationId"
      :reservation-title="executionTarget.title"
      :actual-start-time="executionTarget.actualStartTime"
      :actual-end-time="executionTarget.actualEndTime"
      :actual-attendee-count="executionTarget.actualAttendeeCount"
      @saved="load"
    />
  </div>
</template>

<style scoped>
.table-panel {
  padding: 16px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.muted-text {
  color: var(--text-muted, #8b96a5);
  font-size: 12px;
}
</style>
