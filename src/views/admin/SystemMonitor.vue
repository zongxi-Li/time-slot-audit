<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useMonitorStore } from '@/stores/monitor'

const monitor = useMonitorStore()

onMounted(() => monitor.startTicker())
onBeforeUnmount(() => monitor.stopTicker())

function statusTagType(state: string) {
  return state === '正常' ? 'success' : state === '繁忙' ? 'warning' : 'danger'
}

/** 模拟 3 个用户并发抢订同一时段（真实触发冲突检测，结果写入看板与流水） */
function runSimulation() {
  const { free } = monitor.simulateConcurrentBooking()
  ElMessage.info(
    free
      ? '已发起 3 个并发抢订请求，仅 1 个会成功，其余被冲突检测拦截（结果见下方流水与看板）'
      : '该时段已被占用，3 个并发请求都将被冲突检测拦截',
  )
}

function barHeight(v: number, max: number): string {
  return `${Math.min(100, Math.round((v / max) * 100))}%`
}
</script>

<template>
  <div class="page monitor-page">
    <h2 class="page-title">系统监控</h2>
    <p class="page-subtitle">
      并发监视与系统指标为前端模拟（2 秒刷新）；本会话内的真实预约、审核、取消操作会实时写入请求流水与审计日志
    </p>

    <!-- ═══════════ 并发监视 ═══════════ -->
    <div class="panel section">
      <div class="section-head">
        <span class="section-title"><span class="live-dot" /> 并发监视</span>
        <el-button type="primary" plain size="small" @click="runSimulation">
          模拟 3 用户并发抢订同一时段
        </el-button>
      </div>

      <div class="metric-row">
        <div class="metric-chip">
          <div class="metric-label">实时并发请求</div>
          <div class="metric-value cyan">{{ monitor.activeRequests }}<span class="unit">个</span></div>
        </div>
        <div class="metric-chip">
          <div class="metric-label">提交 QPS</div>
          <div class="metric-value">{{ monitor.qps }}</div>
        </div>
        <div class="metric-chip">
          <div class="metric-label">平均响应</div>
          <div class="metric-value">{{ monitor.latency }}<span class="unit">ms</span></div>
        </div>
        <div class="metric-chip">
          <div class="metric-label">冲突拦截（今日）</div>
          <div class="metric-value red">{{ monitor.conflictCount }}<span class="unit">次</span></div>
        </div>
        <div class="metric-chip">
          <div class="metric-label">预约成功（今日）</div>
          <div class="metric-value green">{{ monitor.successCount }}<span class="unit">次</span></div>
        </div>
        <div class="metric-chip">
          <div class="metric-label">冲突率</div>
          <div class="metric-value orange">{{ monitor.conflictRate }}</div>
        </div>
      </div>

      <div class="sub-title">请求流水（最新在前，最多保留 30 条）</div>
      <el-table :data="monitor.feed" size="small" style="width: 100%" max-height="320">
        <el-table-column prop="time" label="时间" width="96" />
        <el-table-column label="请求" min-width="240">
          <template #default="{ row }">
            <span class="req-method" :class="row.method.toLowerCase()">{{ row.method }}</span>
            <span class="req-path">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <el-tag
              :type="row.status < 300 ? 'success' : row.status < 500 ? 'warning' : 'danger'"
              size="small"
              effect="plain"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="user" label="用户" width="96" />
        <el-table-column prop="note" label="说明" min-width="220" show-overflow-tooltip />
      </el-table>
    </div>

    <!-- ═══════════ 系统监视 ═══════════ -->
    <div class="monitor-columns">
      <div class="panel section">
        <div class="section-head">
          <span class="section-title">系统指标</span>
          <span class="muted-text">运行时长 {{ monitor.uptime }}</span>
        </div>

        <div class="chart-grid">
          <div class="chart-block">
            <div class="chart-head">
              <span>CPU 使用率</span><b>{{ monitor.cpu }}%</b>
            </div>
            <div class="mini-bars">
              <div
                v-for="(v, i) in monitor.cpuHistory"
                :key="i"
                class="bar"
                :style="{ height: barHeight(v, 100) }"
              />
            </div>
          </div>
          <div class="chart-block">
            <div class="chart-head">
              <span>内存使用率</span><b>{{ monitor.mem }}%</b>
            </div>
            <div class="mini-bars">
              <div
                v-for="(v, i) in monitor.memHistory"
                :key="i"
                class="bar green"
                :style="{ height: barHeight(v, 100) }"
              />
            </div>
          </div>
          <div class="chart-block">
            <div class="chart-head">
              <span>提交 QPS</span><b>{{ monitor.qps }}</b>
            </div>
            <div class="mini-bars">
              <div
                v-for="(v, i) in monitor.qpsHistory"
                :key="i"
                class="bar cyan"
                :style="{ height: barHeight(v, 50) }"
              />
            </div>
          </div>
        </div>

        <div class="service-list">
          <div v-for="s in monitor.services" :key="s.name" class="service-row">
            <span class="service-state" :class="{ ok: s.state === '正常' }" />
            <span class="service-name">{{ s.name }}</span>
            <span class="service-latency">{{ s.latency }}</span>
            <el-tag :type="statusTagType(s.state)" size="small" effect="light">{{ s.state }}</el-tag>
            <span class="service-note">{{ s.note }}</span>
          </div>
        </div>
      </div>

      <div class="panel section">
        <div class="section-head">
          <span class="section-title">审计日志</span>
          <span class="muted-text">最多保留 50 条</span>
        </div>
        <div class="audit-list thin-scroll">
          <div v-for="item in monitor.auditLogs" :key="item.id" class="audit-row">
            <div class="audit-top">
              <span class="audit-time">{{ item.time }}</span>
              <span class="audit-actor">{{ item.actor }}</span>
              <el-tag
                :type="item.role === 'admin' ? 'warning' : 'info'"
                size="small"
                effect="plain"
              >
                {{ item.role === 'admin' ? '管理员' : '用户' }}
              </el-tag>
              <span class="audit-action">{{ item.action }}</span>
            </div>
            <div class="audit-detail">{{ item.detail }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.section {
  padding: 16px;
  margin-bottom: 14px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #17b26a;
  animation: blink 1.6s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

.muted-text {
  font-size: 12px;
  color: var(--text-muted);
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

@media (max-width: 1200px) {
  .metric-row {
    grid-template-columns: repeat(3, 1fr);
  }
}

.metric-chip {
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: #fafbfc;
}

.metric-label {
  font-size: 11.5px;
  color: var(--text-muted);
}

.metric-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 650;
  line-height: 28px;
  font-variant-numeric: tabular-nums;
}

.metric-value .unit {
  margin-left: 3px;
  font-size: 11px;
  font-weight: 400;
  color: var(--text-muted);
}

.metric-value.cyan { color: #0e7490; }
.metric-value.red { color: #c0392b; }
.metric-value.green { color: #12855f; }
.metric-value.orange { color: #b25e02; }

.sub-title {
  margin-bottom: 8px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-secondary);
}

.req-method {
  display: inline-block;
  min-width: 44px;
  margin-right: 8px;
  font-size: 11px;
  font-weight: 700;
  font-family: ui-monospace, Consolas, monospace;
}

.req-method.get { color: #12855f; }
.req-method.post { color: #2456b3; }
.req-method.put { color: #b25e02; }
.req-method.delete { color: #c0392b; }

.req-path {
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  color: var(--text-secondary);
}

.monitor-columns {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 14px;
  align-items: start;
}

@media (max-width: 1100px) {
  .monitor-columns {
    grid-template-columns: 1fr;
  }
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}

.chart-block {
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.chart-head {
  display: flex;
  justify-content: space-between;
  font-size: 11.5px;
  color: var(--text-muted);
}

.chart-head b {
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.mini-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 44px;
  margin-top: 8px;
}

.bar {
  flex: 1;
  min-height: 3px;
  border-radius: 2px 2px 0 0;
  background: var(--el-color-primary);
  opacity: 0.75;
  transition: height 0.4s ease;
}

.bar.green { background: #17b26a; }
.bar.cyan { background: #06b6d4; }

.service-list {
  border-top: 1px solid var(--border-light);
  padding-top: 6px;
}

.service-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  font-size: 12.5px;
}

.service-state {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #17b26a;
}

.service-name {
  flex: 1;
  font-weight: 500;
}

.service-latency {
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

.service-note {
  color: var(--text-muted);
  font-size: 11.5px;
}

.audit-list {
  max-height: 420px;
  overflow-y: auto;
}

.audit-row {
  padding: 9px 0;
  border-bottom: 1px solid var(--border-light);
}

.audit-row:last-child {
  border-bottom: none;
}

.audit-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-time {
  font-size: 11.5px;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.audit-actor {
  font-size: 12.5px;
  font-weight: 600;
}

.audit-action {
  font-size: 12.5px;
  color: var(--el-color-primary);
  font-weight: 500;
}

.audit-detail {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 18px;
}
</style>
