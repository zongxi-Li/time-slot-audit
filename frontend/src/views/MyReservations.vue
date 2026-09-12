<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { parseDateStr, toMinutes } from '@/utils/datetime'
import type { DisplayStatus } from '@/types'
import ReservationDetail from '@/components/ReservationDetail.vue'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
const auth = useAuthStore()
const monitor = useMonitorStore()

type StatusFilter = '全部' | DisplayStatus
const statusFilter = ref<StatusFilter>('全部')
const filterOptions: StatusFilter[] = ['全部', '待进行', '已结束', '已取消']

/** 展示状态：已取消 > 按结束时间与当前时间比较 */
function displayStatus(reservationId: string): DisplayStatus {
  const r = store.getById(reservationId)
  if (!r) return '待进行'
  if (r.status === 'CANCELLED') return '已取消'
  if (r.status === 'REJECTED') return '已驳回'
  if (r.status === 'PENDING') return '待审核'
  const end = parseDateStr(r.date)
  const [h, m] = r.endTime.split(':').map(Number)
  end.setHours(h, m, 0, 0)
  if (end.getTime() < Date.now()) return '已结束'
  return Date.now() >= end.getTime() - (toMinutes(r.endTime) - toMinutes(r.startTime)) * 60 * 1000 ? '进行中' : '待进行'
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

onMounted(() => void store.refreshMine())

const detailVisible = ref(false)
const detailId = ref<string | null>(null)

function openDetail(id: string) {
  detailId.value = id
  detailVisible.value = true
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
    <h2 class="page-title">我的预约</h2>
    <p class="page-subtitle">查看和管理你创建的所有会议预约</p>

    <div class="panel table-panel">
      <div class="filter-bar">
        <el-radio-group v-model="statusFilter">
          <el-radio-button v-for="opt in filterOptions" :key="opt" :value="opt">
            {{ opt }}
          </el-radio-button>
        </el-radio-group>
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
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.id)">
              查看
            </el-button>
            <el-button
              v-if="displayStatus(row.id) === '待进行'"
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
