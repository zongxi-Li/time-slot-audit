<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useMonitorStore } from '@/stores/monitor'
import type { Reservation, ReservationStatus } from '@/types'
import ReservationDetail from '@/components/ReservationDetail.vue'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
const monitor = useMonitorStore()

/* —— 筛选 —— */
const statusFilter = ref<'all' | ReservationStatus>('all')
const roomFilter = ref<string>('all')
const keyword = ref('')

const statusOptions: { value: 'all' | ReservationStatus; label: string }[] = [
  { value: 'all', label: '全部状态' },
  { value: 'CONFIRMED', label: '已预约' },
  { value: 'PENDING', label: '待审核' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已取消' },
]

const statusText: Record<ReservationStatus, string> = {
  CONFIRMED: '已预约',
  PENDING: '待审核',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
}

const statusTagType: Record<ReservationStatus, 'primary' | 'warning' | 'info' | 'danger'> = {
  CONFIRMED: 'primary',
  PENDING: 'warning',
  REJECTED: 'danger',
  CANCELLED: 'info',
}

const filteredList = computed(() =>
  store.reservations
    .filter((r) => statusFilter.value === 'all' || r.status === statusFilter.value)
    .filter((r) => roomFilter.value === 'all' || r.roomId === roomFilter.value)
    .filter(
      (r) =>
        !keyword.value.trim() ||
        r.title.includes(keyword.value.trim()) ||
        r.userName.includes(keyword.value.trim()),
    )
    .slice()
    .sort((a, b) => (b.date + b.startTime).localeCompare(a.date + a.startTime)),
)

/* —— 详情抽屉 —— */
const detailVisible = ref(false)
const detailId = ref<string | null>(null)

function openDetail(id: string) {
  detailId.value = id
  detailVisible.value = true
}

/* —— 管理员操作 —— */
function audit(id: string, approve: boolean) {
  const r = store.getById(id)
  if (!r) return

  if (approve) {
    store.approveReservation(id)
    afterAudit(r, true)
    ElMessage.success('已通过，预约生效')
  } else {
    ElMessageBox.prompt('请输入驳回原因（写入审计日志）', '驳回预约', {
      confirmButtonText: '确定驳回',
      cancelButtonText: '再想想',
      inputPlaceholder: '例如：该时段需优先保障教学活动',
    })
      .then(({ value }) => {
        store.rejectReservation(id)
        afterAudit(r, false, value?.trim() ?? '')
        ElMessage.success('已驳回该预约')
      })
      .catch(() => {})
  }
}

function afterAudit(r: Reservation, approve: boolean, reason = '') {
  monitor.pushFeed({
    method: 'PUT',
    path: `/api/reservations/${r.id}/audit`,
    status: 200,
    user: '管理员',
    note: approve ? `通过「${r.title}」` : `驳回「${r.title}」${reason ? '：' + reason : ''}`,
  })
  monitor.log(
    approve ? '审核通过' : '审核驳回',
    `${r.title} · ${r.roomId} ${r.date} ${r.startTime}-${r.endTime}${reason ? '（原因：' + reason + '）' : ''}`,
    '王建国',
    'ADMIN',
  )
}

function forceCancel(row: Reservation) {
  ElMessageBox.prompt(
    `将强制取消「${row.title}」（预约人：${row.userName}），并释放该时间段。请输入操作原因：`,
    '强制取消',
    {
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
      inputPlaceholder: '例如：教室临时维修',
      type: 'warning',
    },
  )
    .then(({ value }) => {
      const reason = value?.trim() || '管理员操作'
      store.forceCancelReservation(row.id)
      monitor.pushFeed({
        method: 'DELETE',
        path: `/api/reservations/${row.id}`,
        status: 200,
        user: '管理员',
        note: `强制取消「${row.title}」`,
      })
      monitor.log(
        '强制取消',
        `${row.title} · ${row.roomId} ${row.date} ${row.startTime}-${row.endTime}（预约人：${row.userName}；原因：${reason}）`,
        '王建国',
        'ADMIN',
      )
      ElNotification.success({ title: '已强制取消', message: `「${row.title}」已被取消，原因已记录`, duration: 3000 })
    })
    .catch(() => {})
}
</script>

<template>
  <div class="page admin-bookings">
    <h2 class="page-title">预约管理</h2>
    <p class="page-subtitle">
      查看全部用户预约，对待审核预约进行审核，必要时强制取消（所有操作均写入审计日志）
    </p>

    <div class="panel table-panel">
      <div class="filter-bar">
        <el-select v-model="statusFilter" style="width: 130px">
          <el-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
        <el-select v-model="roomFilter" style="width: 130px">
          <el-option value="all" label="全部会议室" />
          <el-option v-for="room in roomStore.rooms" :key="room.id" :value="room.id" :label="room.name" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索主题 / 预约人"
          style="width: 220px"
          clearable
        />
        <span class="count-hint">共 {{ filteredList.length }} 条</span>
      </div>

      <el-table :data="filteredList" style="width: 100%" empty-text="没有符合条件的预约">
        <el-table-column prop="title" label="会议主题" min-width="170" show-overflow-tooltip />
        <el-table-column label="会议室" width="92">
          <template #default="{ row }">{{ row.roomId }}</template>
        </el-table-column>
        <el-table-column prop="date" label="日期" width="112" sortable />
        <el-table-column label="时间" width="126">
          <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="userName" label="预约人" width="92" />
        <el-table-column prop="participantCount" label="人数" width="70" />
        <el-table-column label="状态" width="94">
          <template #default="{ row }">
            <el-tag :type="statusTagType[row.status as ReservationStatus]" size="small" effect="light">
              {{ statusText[row.status as ReservationStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="216" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.id)">查看</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" size="small" @click="audit(row.id, true)">通过</el-button>
              <el-button link type="warning" size="small" @click="audit(row.id, false)">驳回</el-button>
            </template>
            <el-button
              v-if="row.status !== 'CANCELLED' && row.status !== 'REJECTED'"
              link
              type="danger"
              size="small"
              @click="forceCancel(row)"
            >
              强制取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <ReservationDetail v-model="detailVisible" :reservation-id="detailId" />
  </div>
</template>

<style scoped>
.table-panel {
  padding: 16px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.count-hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
