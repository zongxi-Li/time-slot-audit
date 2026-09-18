<!--
  文件职责：实现 MyReservations 页面，负责展示、交互和表单状态。
  接口：通过 Pinia store 或 shared/api 调用后端；管理员页面使用 /api/admin/*。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useSystemTimeStore } from '@/stores/systemTime'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { formatDateTime, parseDateStr } from '@/utils/datetime'
import { violationLabel, violationTagType } from '@/utils/violation'
import { myViolationsApi } from '@/shared/api'
import type { ViolationResponse } from '@/shared/api'
import type { DisplayStatus, Reservation } from '@/types'
import ReservationDetail from '@/components/ReservationDetail.vue'
import ReservationDialog from '@/components/ReservationDialog.vue'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
const systemTime = useSystemTimeStore()
const auth = useAuthStore()
const monitor = useMonitorStore()

type StatusFilter = '全部' | DisplayStatus
const statusFilter = ref<StatusFilter>('全部')
const filterOptions: StatusFilter[] = ['全部', '待进行', '已结束', '已取消']

/** 展示状态由 ReservationResponse 在服务端按统一时钟推导。 */
function displayStatus(reservationId: string): DisplayStatus {
  const r = store.getById(reservationId)
  if (!r) return '待进行'
  return r.displayStatus
}

const filteredList = computed(() =>
  store.myReservations.filter((r) => {
    if (statusFilter.value === '全部') return true
    return displayStatus(r.id) === statusFilter.value
  }),
)

const statusTagType: Record<DisplayStatus, 'primary' | 'info' | 'danger' | 'warning' | 'success'> = {
  待进行: 'primary',
  进行中: 'success',
  已结束: 'info',
  已取消: 'danger',
  待审核: 'warning',
  已驳回: 'danger',
}

onMounted(async () => {
  await roomStore.refreshRooms()
  await store.refreshMine()
})

watch(() => systemTime.revision, () => void store.refreshMine())

const detailVisible = ref(false)
const detailId = ref<string | null>(null)

function openDetail(id: string) {
  detailId.value = id
  detailVisible.value = true
}

/* —— 修改/改期 —— */
const editDialogVisible = ref(false)
const editingReservation = ref<Reservation | null>(null)

/** 与后端 Guard 一致的展示层入口条件：PENDING/CONFIRMED 且尚未开始（最终裁决在后端） */
function canEdit(r: Reservation): boolean {
  if (r.status !== 'PENDING' && r.status !== 'CONFIRMED') return false
  const start = parseDateStr(r.date)
  const [h, m] = r.startTime.split(':').map(Number)
  start.setHours(h, m, 0, 0)
  return start.getTime() > systemTime.now.getTime()
}

function openEdit(id: string) {
  editingReservation.value = store.getById(id) ?? null
  editDialogVisible.value = true
}

// 关闭后清空编辑对象，避免下次打开时闪现上一条的预填数据
watch(editDialogVisible, (open) => {
  if (!open) editingReservation.value = null
})

/* —— 我的违规/信用记录 —— */
const creditDrawerVisible = ref(false)
const myViolations = ref<ViolationResponse[]>([])
const violationsLoading = ref(false)

async function openCreditRecords() {
  creditDrawerVisible.value = true
  violationsLoading.value = true
  try {
    myViolations.value = await myViolationsApi.list()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '信用记录加载失败')
  } finally {
    violationsLoading.value = false
  }
}

async function handleCancel(row: { id: string; title: string }) {
  try {
    await ElMessageBox.confirm(
      `确定取消「${row.title}」吗？取消后将释放该时间段。`,
      '取消预约',
      { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' },
    )
  } catch {
    return
  }
  const r = store.getById(row.id)
  await store.cancelReservation(row.id)
  monitor.log(
    '取消预约',
    r ? `${r.title} · ${r.roomId} ${r.date} ${r.startTime}-${r.endTime}` : row.title,
    auth.currentUser.name,
    auth.currentUser.role,
  )
  ElMessage.success('预约已取消')
}
</script>

<template>
  <div class="page my-page">
    <span class="page-eyebrow">My space / Reservations</span>
    <h2 class="page-title">我的预约</h2>
    <p class="page-subtitle">查看和管理你创建的所有会议预约</p>

    <div class="panel table-panel">
      <div class="filter-bar">
        <el-radio-group v-model="statusFilter">
          <el-radio-button v-for="opt in filterOptions" :key="opt" :value="opt">
            {{ opt }}
          </el-radio-button>
        </el-radio-group>
        <el-button class="credit-entry" link type="primary" @click="openCreditRecords">
          信用记录
        </el-button>
      </div>

      <el-table :data="filteredList" style="width: 100%" empty-text="暂无预约记录">
        <el-table-column prop="title" label="会议主题" min-width="180" show-overflow-tooltip />
        <el-table-column label="会议室" width="100">
          <template #default="{ row }">{{ roomStore.roomName(row.roomId) }}</template>
        </el-table-column>
        <el-table-column prop="date" label="日期" width="120" sortable />
        <el-table-column label="时间" width="130">
          <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType[displayStatus(row.id)]" size="small" effect="light">
              {{ displayStatus(row.id) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.id)">
              查看
            </el-button>
            <el-button
              v-if="canEdit(row)"
              link
              type="warning"
              size="small"
              @click="openEdit(row.id)"
            >
              修改
            </el-button>
            <el-button
              v-if="displayStatus(row.id) === '待进行' || displayStatus(row.id) === '待审核'"
              link
              type="danger"
              size="small"
              @click="handleCancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <ReservationDetail v-model="detailVisible" :reservation-id="detailId" />

    <ReservationDialog
      v-model="editDialogVisible"
      :editing="editingReservation"
      @saved="store.refreshMine().catch(() => undefined)"
    />

    <el-drawer v-model="creditDrawerVisible" size="560px" title="我的违规与信用记录">
      <el-table v-loading="violationsLoading" :data="myViolations" style="width: 100%">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="violationTagType(row.violationType)" size="small" effect="light">
              {{ violationLabel(row.violationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="信用变化" width="90">
          <template #default="{ row }">
            <span v-if="row.creditChange" :class="row.creditChange > 0 ? 'delta-up' : 'delta-down'">
              {{ row.creditChange > 0 ? `+${row.creditChange}` : row.creditChange }}
            </span>
            <span v-else class="credit-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" />
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无违规/信用记录" :image-size="80" />
        </template>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.table-panel {
  padding: 16px;
}

.filter-bar {
  margin-bottom: 14px;
}
</style>

<style scoped>
.table-panel {
  padding: 18px 20px 12px;
  border-color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.82);
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.credit-entry {
  flex: none;
}

.delta-up {
  color: var(--el-color-success);
  font-weight: 600;
}

.delta-down {
  color: var(--el-color-danger);
  font-weight: 600;
}

.credit-muted {
  font-size: 12px;
  color: var(--text-muted);
}

.filter-bar :deep(.el-radio-button__inner) {
  min-width: 72px;
  padding: 9px 13px;
}

.my-page :deep(.el-table td:first-child .cell) {
  font-weight: 600;
}

.my-page :deep(.el-table .el-button) {
  min-height: 28px;
}

@media (max-width: 760px) {
  .table-panel {
    padding: 14px 12px 8px;
  }

  .filter-bar {
    overflow-x: auto;
    padding-bottom: 3px;
  }
}
</style>
