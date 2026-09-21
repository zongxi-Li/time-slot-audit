<!--
  文件职责：前端业务模块的 AdministrationReservations 入口、页面、组件或类型定义。
  接口：导出本模块的页面、store、API 或类型。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { administrationApi } from './api'
import type { AdminReservation } from './types'
import { useAutoRefresh } from '@/shared/composables/useAutoRefresh'

const loading = ref(false)
const status = ref('PENDING')
const reservations = ref<AdminReservation[]>([])
const detail = ref<AdminReservation | null>(null)
const detailVisible = ref(false)

const statusLabels: Record<string, string> = {
  PENDING: '待审批',
  CONFIRMED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
}

const statusTypes: Record<string, 'warning' | 'success' | 'danger' | 'info'> = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'info',
}

const pendingCount = computed(() => reservations.value.filter((item) => item.status === 'PENDING').length)

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    reservations.value = await administrationApi.reservations(status.value || undefined)
  } catch (error) {
    // 静默轮询失败不打扰用户，正式加载仍给出错误提示
    if (!silent) ElMessage.error(error instanceof Error ? error.message : '加载预约失败')
  } finally {
    if (!silent) loading.value = false
  }
}

/** 用户端提交的审批会自动出现在列表里：可见时每 1s 静默轮询，切回页面立即刷新 */
useAutoRefresh(() => load(true), 1_000)

async function openDetail(row: AdminReservation) {
  detailVisible.value = true
  detail.value = await administrationApi.reservation(row.id)
}

async function approve(row: AdminReservation) {
  await ElMessageBox.confirm(`确认通过「${row.title}」？`, '审批确认', { type: 'warning' })
  try {
    await administrationApi.approve(row.id)
    ElMessage.success('审批已通过并写入审计日志')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批失败')
  }
}

async function reject(row: AdminReservation) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回预约', {
      inputPattern: /\S+/,
      inputErrorMessage: '驳回原因不能为空',
      confirmButtonText: '确认驳回',
      type: 'warning',
    })
    await administrationApi.reject(row.id, value.trim())
    ElMessage.success('预约已驳回并写入审计日志')
    await load()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  }
}

async function forceCancel(row: AdminReservation) {
  try {
    const { value } = await ElMessageBox.prompt('请输入强制取消原因', '强制取消预约', {
      inputPattern: /\S+/,
      inputErrorMessage: '取消原因不能为空',
      confirmButtonText: '确认取消',
      type: 'warning',
    })
    await administrationApi.forceCancel(row.id, value.trim())
    ElMessage.success('预约已取消并写入审计日志')
    await load()
  } catch (error) {
    if (error instanceof Error) ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <span class="page-eyebrow">Administration</span>
    <h2 class="page-title">预约审批</h2>
    <p class="page-subtitle">处理特殊会议室预约，审批决定与管理员操作均由后端持久化。</p>

    <div class="summary-row">
      <div class="summary-card">
        <span>当前列表</span>
        <strong>{{ reservations.length }}</strong>
      </div>
      <div class="summary-card emphasis">
        <span>待审批</span>
        <strong>{{ pendingCount }}</strong>
      </div>
    </div>

    <section class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="status" style="width: 150px" @change="load">
          <el-option label="全部状态" value="" />
          <el-option label="待审批" value="PENDING" />
          <el-option label="已通过" value="CONFIRMED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="reservations" empty-text="暂无符合条件的预约">
        <el-table-column prop="reservationNo" label="预约单号" min-width="160" />
        <el-table-column prop="title" label="会议主题" min-width="170" show-overflow-tooltip />
        <el-table-column prop="roomName" label="会议室" width="110" />
        <el-table-column prop="userName" label="预约人" width="100" />
        <el-table-column label="开始时间" min-width="160">
          <template #default="{ row }">{{ row.startTime.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="statusTypes[row.status]">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'CONFIRMED'"
              link
              type="danger"
              @click="forceCancel(row)"
            >强制取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" title="审批详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="预约单号">{{ detail.reservationNo }}</el-descriptions-item>
          <el-descriptions-item label="会议主题">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="会议室">{{ detail.roomName }}</el-descriptions-item>
          <el-descriptions-item label="预约人">{{ detail.userName }}</el-descriptions-item>
          <el-descriptions-item label="参与人数">{{ detail.participantCount }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '无' }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="history-title">审批历史</h3>
        <el-timeline v-if="detail.approvalHistory.length">
          <el-timeline-item
            v-for="record in detail.approvalHistory"
            :key="record.id"
            :timestamp="record.createdAt.replace('T', ' ')"
          >
            <strong>{{ record.action === 'APPROVE' ? '审批通过' : '审批驳回' }}</strong>
            <div>{{ record.approverName }}{{ record.remark ? `：${record.remark}` : '' }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审批记录" :image-size="72" />
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.summary-row { display: flex; gap: 14px; margin-bottom: 18px; }
.summary-card { min-width: 150px; padding: 16px 20px; background: #fff; border: 1px solid var(--border-color); border-radius: 16px; }
.summary-card span { display: block; color: var(--text-muted); font-size: 12px; }
.summary-card strong { display: block; margin-top: 4px; font-size: 28px; }
.summary-card.emphasis strong { color: #e6a23c; }
.table-panel { padding: 16px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.history-title { margin: 26px 0 18px; }
</style>
