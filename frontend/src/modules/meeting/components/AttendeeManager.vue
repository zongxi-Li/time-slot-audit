<!--
  文件职责：前端业务模块的 AttendeeManager 入口、页面、组件或类型定义。
  接口：导出本模块的页面、store、API 或类型。
-->
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { meetingsApi } from '../api'
import { formatDateTime } from '@/utils/datetime'
import type { AttendanceView, AttendeeView } from '../api'

const visible = defineModel<boolean>({ default: false })

const props = defineProps<{
  reservationId: number | null
  reservationTitle?: string
  /** 创建人或管理员为 true：允许新增/移除参与人 */
  canManage: boolean
}>()

const emit = defineEmits<{ changed: [] }>()

const loading = ref(false)
const attendance = ref<AttendanceView | null>(null)
const newUsername = ref('')

const roleText: Record<string, string> = { ORGANIZER: '组织者', ATTENDEE: '参与人' }
const statusText: Record<string, string> = {
  EXPECTED: '待签到',
  CHECKED_IN: '已签到',
  CHECKED_OUT: '已签退',
  NO_SHOW: '缺席',
}
const statusTagType: Record<string, 'info' | 'success' | 'primary' | 'danger'> = {
  EXPECTED: 'info',
  CHECKED_IN: 'success',
  CHECKED_OUT: 'primary',
  NO_SHOW: 'danger',
}

const rows = computed<AttendeeView[]>(() => attendance.value?.attendees ?? [])

watch(visible, (open) => {
  if (open && props.reservationId) void load()
})

async function load() {
  if (!props.reservationId) return
  loading.value = true
  try {
    attendance.value = await meetingsApi.attendance(props.reservationId)
  } catch (error) {
    ElMessage.error((error as Error).message || '出勤信息加载失败')
  } finally {
    loading.value = false
  }
}

async function addAttendee() {
  const username = newUsername.value.trim()
  if (!username || !props.reservationId) return
  try {
    await meetingsApi.addAttendee(props.reservationId, { username })
    ElMessage.success(`已添加 ${username}`)
    newUsername.value = ''
    emit('changed')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '添加失败')
  }
}

async function removeAttendee(row: AttendeeView) {
  if (!props.reservationId) return
  try {
    await ElMessageBox.confirm(`确定将「${row.realName}」移出会议吗？`, '移除参与人', {
      confirmButtonText: '确定移除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await meetingsApi.removeAttendee(props.reservationId, row.userId)
    ElMessage.success('已移除')
    emit('changed')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '移除失败')
  }
}

function removable(row: AttendeeView): boolean {
  return props.canManage && row.attendeeRole !== 'ORGANIZER' && row.attendanceStatus === 'EXPECTED'
}
</script>

<template>
  <el-drawer v-model="visible" size="480px" :title="`参与人与出勤 · ${reservationTitle ?? ''}`">
    <div v-if="attendance" class="summary">
      <el-tag effect="light" type="info">共 {{ attendance.totalCount }} 人</el-tag>
      <el-tag effect="light" type="info">待签到 {{ attendance.expectedCount }}</el-tag>
      <el-tag effect="light" type="success">已签到 {{ attendance.checkedInCount }}</el-tag>
      <el-tag effect="light" type="primary">已签退 {{ attendance.checkedOutCount }}</el-tag>
      <el-tag effect="light" type="danger">缺席 {{ attendance.noShowCount }}</el-tag>
    </div>

    <el-table v-loading="loading" :data="rows" style="width: 100%" empty-text="暂无参与人">
      <el-table-column label="成员" min-width="120">
        <template #default="{ row }">
          <span>{{ row.realName }}</span>
          <span class="username">@{{ row.username }}</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="80">
        <template #default="{ row }">{{ roleText[row.attendeeRole] }}</template>
      </el-table-column>
      <el-table-column label="出勤" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType[row.attendanceStatus]" size="small" effect="light">
            {{ statusText[row.attendanceStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签到 / 签退" min-width="150">
        <template #default="{ row }">
          <div class="time-line">签：{{ formatDateTime(row.checkInAt) }}</div>
          <div class="time-line">退：{{ formatDateTime(row.checkOutAt) }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="70" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="removable(row)"
            :icon="Delete"
            link
            type="danger"
            size="small"
            @click="removeAttendee(row)"
          />
        </template>
      </el-table-column>
    </el-table>

    <div v-if="canManage" class="add-bar">
      <el-input
        v-model="newUsername"
        placeholder="输入用户名添加参与人，如 lisi"
        clearable
        @keyup.enter="addAttendee"
      />
      <el-button type="primary" @click="addAttendee">添加</el-button>
    </div>
    <p v-if="canManage" class="add-tip">参与人上限为预约申报人数；已有出勤事实的参与人不可移除。</p>
  </el-drawer>
</template>

<style scoped>
.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.username {
  margin-left: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.time-line {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 18px;
}

.add-bar {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.add-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
