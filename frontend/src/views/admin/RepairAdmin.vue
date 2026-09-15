<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { repairTicketsApi } from '@/shared/api'
import { useMock } from '@/shared/api/config'
import { ApiError } from '@/shared/api/types'
import type { RepairTicketResponse } from '@/shared/api/types'

const roomStore = useMeetingRoomStore()
const loading = ref(false)
const resolvingId = ref<string | number | null>(null)
const tickets = ref<RepairTicketResponse[]>([])
const roomFilter = ref<string>('all')

onMounted(async () => {
  loading.value = true
  try {
    await roomStore.refreshRooms()
    if (!useMock) await refreshTickets()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
})

async function refreshTickets() {
  try {
    tickets.value = await repairTicketsApi.adminList()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '操作失败，请稍后重试'
}

const filteredTickets = computed(() =>
  roomFilter.value === 'all'
    ? tickets.value
    : tickets.value.filter((t) => String(t.roomId) === roomFilter.value),
)

const openCount = computed(() => tickets.value.filter((t) => t.status === 'OPEN').length)

function formatTime(value?: string | null): string {
  return value ? value.slice(0, 16).replace('T', ' ') : '—'
}

async function resolveTicket(ticket: RepairTicketResponse) {
  try {
    const { value } = await ElMessageBox.prompt(
      `处理「${ticket.roomName} · ${ticket.facilityName}」的报修，请填写处理说明：`,
      '解决工单',
      {
        confirmButtonText: '确认解决',
        cancelButtonText: '再想想',
        inputPlaceholder: '例如：已更换投影仪灯泡，恢复正常',
        inputValidator: (input: string) => (input && input.trim() ? true : '处理说明不能为空'),
      },
    )
    const remark = value.trim()
    resolvingId.value = ticket.id
    try {
      await repairTicketsApi.resolve(ticket.id, remark)
      ElMessage.success('工单已解决')
      await refreshTickets()
    } catch (error) {
      ElMessage.error(errorMessage(error))
    } finally {
      resolvingId.value = null
    }
  } catch {
    /* 用户取消 */
  }
}
</script>

<template>
  <div class="page repair-admin">
    <h2 class="page-title">报修工单</h2>
    <p class="page-subtitle">
      处理用户提交的设施报修；未处理（OPEN）工单置顶展示，处理后保留处理说明与时间
    </p>

    <div class="panel table-panel">
      <div class="filter-bar">
        <el-select v-model="roomFilter" style="width: 150px">
          <el-option value="all" label="全部会议室" />
          <el-option v-for="room in roomStore.rooms" :key="room.id" :value="room.id" :label="room.name" />
        </el-select>
        <span class="count-hint">
          共 {{ filteredTickets.length }} 条
          <template v-if="openCount > 0"> · 待处理 {{ openCount }} 条</template>
        </span>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredTickets"
        style="width: 100%"
        empty-text="暂无报修工单"
      >
        <el-table-column label="会议室" width="100">
          <template #default="{ row }">{{ row.roomName }}</template>
        </el-table-column>
        <el-table-column label="报修对象" width="120">
          <template #default="{ row }">{{ row.facilityName }}</template>
        </el-table-column>
        <el-table-column prop="issue" label="故障描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="报修人" width="100">
          <template #default="{ row }">{{ row.reporterName }}</template>
        </el-table-column>
        <el-table-column label="报修时间" width="140">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'danger' : 'success'" size="small" effect="light">
              {{ row.status === 'OPEN' ? '待处理' : '已解决' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理情况" min-width="180">
          <template #default="{ row }">
            <template v-if="row.status === 'RESOLVED'">
              <div class="resolve-remark">{{ row.resolveRemark || '—' }}</div>
              <div class="resolve-time">{{ formatTime(row.resolvedAt) }}</div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="88" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'OPEN'"
              link
              type="success"
              size="small"
              :loading="resolvingId === row.id"
              @click="resolveTicket(row)"
            >
              解决
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
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

.resolve-remark {
  font-size: 12.5px;
  line-height: 1.5;
}

.resolve-time {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-muted);
}

.muted {
  color: var(--text-muted);
}
</style>
