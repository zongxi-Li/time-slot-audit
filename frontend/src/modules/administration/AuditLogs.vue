<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { administrationApi } from './api'
import type { AuditLog } from './types'

const loading = ref(false)
const exporting = ref(false)
const logs = ref<AuditLog[]>([])
const operatorId = ref<number | undefined>()
const businessType = ref('')
const range = ref<[Date, Date] | null>(null)

function parameters() {
  return {
    operatorId: operatorId.value,
    businessType: businessType.value || undefined,
    start: range.value?.[0].toISOString().slice(0, 19),
    end: range.value?.[1].toISOString().slice(0, 19),
  }
}

async function load() {
  loading.value = true
  try {
    logs.value = await administrationApi.auditLogs({ ...parameters(), limit: 500 })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载审计日志失败')
  } finally {
    loading.value = false
  }
}

async function exportCsv() {
  exporting.value = true
  try {
    await administrationApi.exportAuditLogs(parameters())
    ElMessage.success('审计日志已导出')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <span class="page-eyebrow">Traceability</span>
    <h2 class="page-title">操作审计</h2>
    <p class="page-subtitle">按操作人、业务类型和时间范围追踪关键管理员操作。</p>

    <section class="panel audit-panel">
      <div class="filters">
        <el-input-number v-model="operatorId" :min="1" placeholder="操作人 ID" controls-position="right" />
        <el-select v-model="businessType" clearable placeholder="业务类型" style="width: 170px">
          <el-option label="预约" value="RESERVATION" />
          <el-option label="会议室" value="MEETING_ROOM" />
          <el-option label="会议室分类" value="ROOM_CATEGORY" />
        </el-select>
        <el-date-picker
          v-model="range"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 360px"
        />
        <el-button type="primary" :icon="Refresh" @click="load">查询</el-button>
        <el-button :icon="Download" :loading="exporting" @click="exportCsv">导出 CSV</el-button>
      </div>

      <el-table v-loading="loading" :data="logs" empty-text="暂无审计记录">
        <el-table-column prop="createdAt" label="操作时间" min-width="170">
          <template #default="{ row }">{{ row.createdAt.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="operationType" label="操作类型" min-width="190" />
        <el-table-column prop="businessType" label="业务类型" width="140" />
        <el-table-column prop="businessId" label="业务 ID" width="100" />
        <el-table-column prop="content" label="操作内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP 地址" width="130" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.audit-panel { padding: 16px; }
.filters { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
</style>
