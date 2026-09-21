<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { meetingsApi } from '../api'

const props = defineProps<{
  modelValue: boolean
  reservationId: number | null
  reservationTitle: string
  actualStartTime: string | null
  actualEndTime: string | null
  actualAttendeeCount: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const saving = ref(false)
const form = reactive({
  actualStartTime: '',
  actualEndTime: '',
  actualAttendeeCount: 0,
})

function resetForm() {
  form.actualStartTime = props.actualStartTime ?? ''
  form.actualEndTime = props.actualEndTime ?? ''
  form.actualAttendeeCount = props.actualAttendeeCount ?? 0
}

watch(() => props.modelValue, (visible) => {
  if (visible) resetForm()
})

async function submit() {
  if (!props.reservationId) return
  if (!form.actualStartTime || !form.actualEndTime) {
    ElMessage.warning('请填写实际开始时间和实际结束时间')
    return
  }
  saving.value = true
  try {
    await meetingsApi.saveExecution(props.reservationId, { ...form })
    ElMessage.success('会议实际使用记录已保存')
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    ElMessage.error((error as Error).message || '会议实际使用记录保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`登记实际使用记录：${reservationTitle}`"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="120px" @submit.prevent="submit">
      <el-form-item label="实际开始时间" required>
        <el-date-picker
          v-model="form.actualStartTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="选择实际开始时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="实际结束时间" required>
        <el-date-picker
          v-model="form.actualEndTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="选择实际结束时间"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="实际参会人数" required>
        <el-input-number v-model="form.actualAttendeeCount" :min="0" :max="9999" controls-position="right" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存记录</el-button>
    </template>
  </el-dialog>
</template>
