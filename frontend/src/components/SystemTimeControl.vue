<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useSystemTimeStore } from '@/stores/systemTime'
import { formatDateTime } from '@/utils/datetime'

const systemTime = useSystemTimeStore()
const saving = ref(false)
const draft = ref('')

const displayTime = computed(() => formatDateTime(systemTime.snapshot?.currentTime))

watch(
  () => systemTime.snapshot,
  (value) => {
    if (value) draft.value = value.currentTime.slice(0, 19)
  },
  { immediate: true },
)

async function applyFixedTime() {
  if (!draft.value) return
  saving.value = true
  try {
    await systemTime.setFixed(draft.value)
    ElMessage.success('测试业务时间已更新，页面状态已刷新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '设置测试时间失败')
  } finally {
    saving.value = false
  }
}

async function resetTime() {
  saving.value = true
  try {
    await systemTime.reset()
    ElMessage.success('已恢复真实业务时间，页面状态已刷新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '恢复真实时间失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-popover placement="bottom-end" :width="330" trigger="click">
    <template #reference>
      <el-button class="time-trigger" text>
        <span class="time-trigger-label">业务时间</span>
        <span class="time-trigger-value">{{ displayTime }}</span>
        <el-tag size="small" :type="systemTime.isFixed ? 'warning' : 'info'" effect="plain">
          {{ systemTime.isFixed ? '测试中' : '实时' }}
        </el-tag>
      </el-button>
    </template>

    <div class="time-control">
      <strong>测试业务时间</strong>
      <p>预约状态、签到资格与状态机展示均以此时间为准。</p>
      <el-date-picker
        v-model="draft"
        type="datetime"
        value-format="YYYY-MM-DDTHH:mm:ss"
        placeholder="选择业务时间"
        :clearable="false"
        style="width: 100%"
      />
      <div class="time-actions">
        <el-button v-if="systemTime.isFixed" :loading="saving" @click="resetTime">恢复实时</el-button>
        <el-button type="primary" :loading="saving" @click="applyFixedTime">应用并刷新</el-button>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.time-trigger { gap: 6px; padding: 4px 8px; color: var(--text-secondary); }
.time-trigger-label { color: var(--text-muted); font-size: 11px; }
.time-trigger-value { font-variant-numeric: tabular-nums; font-size: 12px; }
.time-control p { margin: 6px 0 14px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.time-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px; }
</style>
