<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { administrationApi } from './api'
import type { OperationsDashboard } from './types'

const loading = ref(false)
const dashboard = ref<OperationsDashboard | null>(null)
const range = ref<[Date, Date] | null>(null)

const maxRoomHours = computed(() => Math.max(...(dashboard.value?.popularRooms.map((item) => Number(item.usedHours)) ?? [1]), 1))
const maxPeakCount = computed(() => Math.max(...(dashboard.value?.peakHours.map((item) => item.bookingCount) ?? [1]), 1))

async function load() {
  loading.value = true
  try {
    dashboard.value = await administrationApi.statistics({
      start: range.value?.[0].toISOString().slice(0, 19),
      end: range.value?.[1].toISOString().slice(0, 19),
      top: 8,
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载运营统计失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <span class="page-eyebrow">Operations</span>
    <h2 class="page-title">运营分析</h2>
    <p class="page-subtitle">指标均由预约数据实时聚合，不使用前端写死统计值。</p>

    <div class="range-toolbar">
      <el-date-picker v-model="range" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" />
      <el-button type="primary" @click="load">更新统计</el-button>
    </div>

    <template v-if="dashboard">
      <div class="metric-grid">
        <article class="panel metric"><span>预约总数</span><strong>{{ dashboard.totalReservations }}</strong></article>
        <article class="panel metric"><span>取消预约</span><strong>{{ dashboard.cancelledReservations }}</strong></article>
        <article class="panel metric"><span>取消率</span><strong>{{ dashboard.cancellationRate }}%</strong></article>
        <article class="panel metric"><span>统计会议室</span><strong>{{ dashboard.popularRooms.length }}</strong></article>
      </div>

      <div class="chart-grid">
        <section class="panel chart-card">
          <h3>热门会议室 · 使用时长</h3>
          <p>已确认预约与查询区间的实际重叠小时数</p>
          <div v-if="dashboard.popularRooms.length" class="bars">
            <div v-for="room in dashboard.popularRooms" :key="room.roomId" class="bar-row">
              <span class="bar-label">{{ room.roomName }}</span>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: `${Number(room.usedHours) / maxRoomHours * 100}%` }" />
              </div>
              <strong>{{ room.usedHours }}h</strong>
            </div>
          </div>
          <el-empty v-else description="区间内暂无已确认预约" :image-size="72" />
        </section>

        <section class="panel chart-card">
          <h3>高峰时段 · 预约次数</h3>
          <p>按已确认预约的开始小时聚合</p>
          <div v-if="dashboard.peakHours.length" class="peak-chart">
            <div v-for="item in dashboard.peakHours" :key="item.hour" class="peak-column">
              <span>{{ item.bookingCount }}</span>
              <div class="peak-bar" :style="{ height: `${item.bookingCount / maxPeakCount * 150}px` }" />
              <small>{{ String(item.hour).padStart(2, '0') }}:00</small>
            </div>
          </div>
          <el-empty v-else description="区间内暂无已确认预约" :image-size="72" />
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.range-toolbar { display: flex; gap: 10px; margin-bottom: 18px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(140px, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric { padding: 20px; }
.metric span { display: block; color: var(--text-muted); font-size: 12px; }
.metric strong { display: block; margin-top: 6px; font-size: 30px; }
.chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.chart-card { min-height: 310px; padding: 22px; }
.chart-card h3 { margin: 0 0 5px; }
.chart-card > p { margin: 0 0 24px; color: var(--text-muted); font-size: 12px; }
.bars { display: grid; gap: 16px; }
.bar-row { display: grid; grid-template-columns: 80px 1fr 55px; align-items: center; gap: 10px; }
.bar-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bar-track { height: 10px; overflow: hidden; background: #edf1f5; border-radius: 99px; }
.bar-fill { height: 100%; min-width: 3px; background: linear-gradient(90deg, #0071e3, #5ac8fa); border-radius: inherit; }
.peak-chart { display: flex; align-items: end; gap: 12px; height: 205px; overflow-x: auto; }
.peak-column { display: flex; min-width: 42px; align-items: center; flex-direction: column; justify-content: end; gap: 5px; }
.peak-column span, .peak-column small { color: var(--text-muted); font-size: 11px; }
.peak-bar { width: 26px; min-height: 3px; background: linear-gradient(#34c759, #30b65c); border-radius: 7px 7px 2px 2px; }
@media (max-width: 900px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
}
</style>
