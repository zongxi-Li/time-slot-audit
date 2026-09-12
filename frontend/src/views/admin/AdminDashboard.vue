<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useMonitorStore } from '@/stores/monitor'
import { getWeekDays, todayStr } from '@/utils/datetime'

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
const monitor = useMonitorStore()

const today = todayStr()
const weekDays = getWeekDays(today)

/* —— 概览指标 —— */
const todayCount = computed(
  () => store.activeReservations.filter((r) => r.date === today).length,
)
const pendingList = computed(() =>
  store.activeReservations.filter((r) => r.status === 'PENDING'),
)
const weekCount = computed(
  () => store.activeReservations.filter((r) => weekDays.includes(r.date)).length,
)
const activeRoomCount = computed(
  () => roomStore.rooms.filter((r) => r.status === 'AVAILABLE').length,
)

const statCards = computed(() => [
  { label: '今日预约', value: `${todayCount.value}`, unit: '场', tone: 'blue' },
  { label: '待审核', value: `${pendingList.value.length}`, unit: '条', tone: 'orange' },
  { label: '本周预约', value: `${weekCount.value}`, unit: '场', tone: 'blue' },
  { label: '当前并发请求', value: `${monitor.activeRequests}`, unit: '个', tone: 'cyan', live: true },
  { label: '今日冲突拦截', value: `${monitor.conflictCount}`, unit: '次', tone: 'red' },
  { label: '可用会议室', value: `${activeRoomCount.value}/${roomStore.rooms.length}`, unit: '间', tone: 'green' },
])

/* —— 今日各会议室占用率（ booked 分钟 / 全天 660 分钟） —— */
const FULL_MINUTES = 11 * 60

function bookedMinutes(roomId: string): number {
  return store
    .listByRoomAndDate(roomId, today)
    .reduce((sum, r) => {
      const [sh, sm] = r.startTime.split(':').map(Number)
      const [eh, em] = r.endTime.split(':').map(Number)
      return sum + (eh * 60 + em - (sh * 60 + sm))
    }, 0)
}

const occupancy = computed(() =>
  roomStore.rooms.map((room) => {
    const minutes = bookedMinutes(room.id)
    return {
      id: room.id,
      name: room.name,
      status: room.status,
      percent: Math.min(100, Math.round((minutes / FULL_MINUTES) * 100)),
      text: `${(minutes / 60).toFixed(1)} 小时`,
    }
  }),
)

/* —— 待审核快捷处理 —— */
async function quickAudit(id: string, title: string, approve: boolean) {
  const ok = approve ? store.approveReservation(id) : store.rejectReservation(id)
  if (!ok) return
  monitor.pushFeed({
    method: 'PUT',
    path: `/api/reservations/${id}/audit`,
    status: 200,
    user: '管理员',
    note: approve ? '审核通过（控制台快捷操作）' : '驳回（控制台快捷操作）',
  })
  monitor.log(
    approve ? '审核通过' : '审核驳回',
    `${title}（控制台快捷操作）`,
    '王建国',
    'ADMIN',
  )
  ElMessage.success(approve ? '已通过' : '已驳回')
}

onMounted(() => monitor.startTicker())
onBeforeUnmount(() => monitor.stopTicker())
</script>

<template>
  <div class="page dash-page">
    <h2 class="page-title">管理控制台</h2>
    <p class="page-subtitle">今日运行概览 · 数据为前端 Mock，指标实时模拟波动</p>

    <!-- 指标卡片 -->
    <div class="stat-grid">
      <div
        v-for="card in statCards"
        :key="card.label"
        class="panel stat-card"
        :class="`tone-${card.tone}`"
      >
        <div class="stat-label">
          {{ card.label }}
          <span v-if="card.live" class="live-dot" title="实时模拟数据" />
        </div>
        <div class="stat-value">
          {{ card.value }}<span class="stat-unit">{{ card.unit }}</span>
        </div>
      </div>
    </div>

    <div class="dash-columns">
      <!-- 今日占用率 -->
      <div class="panel dash-block">
        <div class="block-title">今日会议室占用率</div>
        <div v-for="row in occupancy" :key="row.id" class="occ-row">
          <span class="occ-name">
            {{ row.name }}
            <el-tag v-if="row.status !== 'AVAILABLE'" size="small" type="info" effect="plain">不可预约</el-tag>
          </span>
          <div class="occ-bar">
            <div class="occ-bar-inner" :style="{ width: `${row.percent}%` }" />
          </div>
          <span class="occ-text">{{ row.text }} · {{ row.percent }}%</span>
        </div>
      </div>

      <!-- 待审核队列 -->
      <div class="panel dash-block">
        <div class="block-title">
          待审核队列
          <el-tag v-if="pendingList.length" size="small" type="warning" effect="light" round>
            {{ pendingList.length }}
          </el-tag>
        </div>
        <div v-if="pendingList.length === 0" class="empty-hint">暂无待审核预约</div>
        <div v-for="p in pendingList.slice(0, 6)" :key="p.id" class="pending-row">
          <div class="pending-info">
            <div class="pending-title">{{ p.title }}</div>
            <div class="pending-meta">
              {{ p.roomId }} · {{ p.date }} {{ p.startTime }}-{{ p.endTime }} · {{ p.userName }}
            </div>
          </div>
          <div class="pending-ops">
            <el-button size="small" type="primary" plain @click="quickAudit(p.id, p.title, true)">
              通过
            </el-button>
            <el-button size="small" type="danger" plain @click="quickAudit(p.id, p.title, false)">
              驳回
            </el-button>
          </div>
        </div>
      </div>

      <!-- 最近动态 -->
      <div class="panel dash-block">
        <div class="block-title">最近动态（审计日志）</div>
        <div v-for="item in monitor.auditLogs.slice(0, 6)" :key="item.id" class="feed-row">
          <span class="feed-time">{{ item.time }}</span>
          <span class="feed-action">{{ item.action }}</span>
          <span class="feed-detail" :title="item.detail">{{ item.detail }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

@media (max-width: 1280px) {
  .stat-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.stat-card {
  padding: 14px 16px;
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #17b26a;
  animation: blink 1.6s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

.stat-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 650;
  line-height: 32px;
}

.stat-unit {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}

.tone-blue .stat-value { color: #2456b3; }
.tone-orange .stat-value { color: #b25e02; }
.tone-red .stat-value { color: #c0392b; }
.tone-green .stat-value { color: #12855f; }
.tone-cyan .stat-value { color: #0e7490; }

.dash-columns {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 12px;
  align-items: start;
}

@media (max-width: 1100px) {
  .dash-columns {
    grid-template-columns: 1fr;
  }
}

.dash-block {
  padding: 16px;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 13px;
  font-weight: 600;
}

.occ-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.occ-name {
  flex: 0 0 96px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
}

.occ-bar {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--border-light);
  overflow: hidden;
}

.occ-bar-inner {
  height: 100%;
  border-radius: 4px;
  background: var(--el-color-primary);
  transition: width 0.3s ease;
}

.occ-text {
  flex: 0 0 96px;
  font-size: 11.5px;
  color: var(--text-muted);
  text-align: right;
}

.empty-hint {
  padding: 18px 0;
  font-size: 12.5px;
  color: var(--text-muted);
  text-align: center;
}

.pending-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-light);
}

.pending-row:last-child {
  border-bottom: none;
}

.pending-title {
  font-size: 13px;
  font-weight: 500;
}

.pending-meta {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.pending-ops {
  flex-shrink: 0;
}

.pending-ops .el-button + .el-button {
  margin-left: 6px;
}

.feed-row {
  display: flex;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid var(--border-light);
  font-size: 12px;
}

.feed-row:last-child {
  border-bottom: none;
}

.feed-time {
  flex-shrink: 0;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.feed-action {
  flex-shrink: 0;
  color: var(--el-color-primary);
  font-weight: 500;
}

.feed-detail {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
}
</style>
