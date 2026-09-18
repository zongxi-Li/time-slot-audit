<!--
  文件职责：实现可复用的 ReservationDialog Vue 组件。
  接口：通过 props、emits 与父页面通信，必要时通过 store 间接访问 API。
-->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useMonitorStore } from '@/stores/monitor'
import { todayStr } from '@/utils/datetime'
import { ApiError } from '@/shared/api'
import type { MeetingRoom, Reservation, ReservationDraft } from '@/types'

const visible = defineModel<boolean>({ default: false })

const props = withDefaults(defineProps<{
  /** 从看板点击空白格进入时的预填信息 */
  initial?: Partial<Pick<ReservationDraft, 'roomId' | 'date' | 'startTime' | 'endTime'>>
  /** 传入已有预约时为编辑（改期）模式，提交走 PUT /api/reservations/{id} */
  editing?: Reservation | null
}>(), {
  editing: null,
})

const emit = defineEmits<{
  saved: []
}>()

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
const monitor = useMonitorStore()

/** 仅可预约“启用”状态的会议室（管理员停用的房间不出现在选项里） */
const bookableRooms = computed(() => roomStore.rooms.filter((r) => r.status === 'AVAILABLE'))

const formRef = ref<FormInstance>()
const submitting = ref(false)
// A request ID belongs to one logical create action, including a retry after a lost response.
const createRequestId = ref<string | null>(null)

interface ConflictResult {
  conflicts: Reservation[]
  roomName: string
  alternatives: MeetingRoom[]
}

const conflictResult = ref<ConflictResult | null>(null)
const serverConflictMessage = ref('')

const form = reactive<ReservationDraft>({
  title: '',
  roomId: '',
  date: todayStr(),
  startTime: '10:00',
  endTime: '11:00',
  participantCount: 4,
  remark: '',
})

watch(visible, (open) => {
  if (!open) return
  conflictResult.value = null
  serverConflictMessage.value = ''
  createRequestId.value = props.editing ? null : crypto.randomUUID()
  // 编辑模式预填现有预约；新建模式回退到看板预填信息或默认值。
  form.title = props.editing?.title ?? ''
  form.roomId = props.editing?.roomId ?? props.initial?.roomId ?? ''
  form.date = props.editing?.date ?? props.initial?.date ?? todayStr()
  form.startTime = props.editing?.startTime ?? props.initial?.startTime ?? '10:00'
  form.endTime = props.editing?.endTime ?? props.initial?.endTime ?? '11:00'
  form.participantCount = props.editing?.participantCount ?? 4
  form.remark = props.editing?.remark ?? ''
})

const selectedRoom = computed(() => roomStore.getRoom(form.roomId))

const rules: FormRules = {
  title: [{ required: true, message: '请输入会议主题', trigger: 'blur' }],
  roomId: [{ required: true, message: '请选择会议室', trigger: 'change' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && form.startTime && value <= form.startTime) {
          callback(new Error('结束时间必须晚于开始时间'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  participantCount: [{ required: true, message: '请输入参会人数', trigger: 'change' }],
}

/** 表单变化后清除旧的冲突提示 */
function clearConflict() {
  conflictResult.value = null
}

/** 点击推荐会议室：直接切换到该会议室 */
function applyAlternative(room: MeetingRoom) {
  form.roomId = room.id
  clearConflict()
  ElMessage.success(`已切换到 ${room.name}`)
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 冲突与状态重算的最终裁决在 Spring Boot + MySQL 事务内；前端不做业务预判。
    if (props.editing) {
      const updated = await store.updateReservation(props.editing.id, { ...form })
      ElMessage.success(updated.status === 'PENDING'
        ? '预约已修改，新会议室需管理员审批'
        : '预约已修改')
      monitor.pushFeed({
        method: 'PUT',
        path: `/api/reservations/${props.editing.id}`,
        status: 200,
        user: store.currentUser.name,
        note: `修改预约 ${form.title} · ${roomStore.roomName(form.roomId)} ${form.startTime}-${form.endTime}`,
      })
      monitor.log(
        '修改预约',
        `${form.title} · ${roomStore.roomName(form.roomId)} ${form.date} ${form.startTime}-${form.endTime}`,
        store.currentUser.name,
        store.currentUser.role,
      )
      visible.value = false
      emit('saved')
      return
    }
    const requestId = createRequestId.value ?? crypto.randomUUID()
    createRequestId.value = requestId
    const created = await store.addReservation({ ...form }, requestId)
    ElMessage.success(created.status === 'PENDING'
      ? '预约已提交，等待管理员审批'
      : '预约成功')
    // 上报并发监视：201 成功
    monitor.noteSuccess()
    monitor.pushFeed({
      method: 'POST',
      path: '/api/reservations',
      status: 201,
      user: store.currentUser.name,
      note: `${form.title} · ${roomStore.roomName(form.roomId)} ${form.startTime}-${form.endTime}`,
    })
    monitor.log(
      '新建预约',
      `${form.title} · ${roomStore.roomName(form.roomId)} ${form.date} ${form.startTime}-${form.endTime}`,
      store.currentUser.name,
      store.currentUser.role,
    )
    visible.value = false
    emit('saved')
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      // 后端 409：展示占用事实，并基于最新日历数据推荐可用会议室。
      serverConflictMessage.value = error.message
      await store.refreshCalendar(form.date)
      conflictResult.value = {
        conflicts: store.findConflicts({ ...form }),
        roomName: roomStore.roomName(form.roomId),
        alternatives: store.findAvailableRooms({ ...form }),
      }
      monitor.noteConflict()
      monitor.pushFeed({
        method: props.editing ? 'PUT' : 'POST',
        path: props.editing ? `/api/reservations/${props.editing.id}` : '/api/reservations',
        status: 409,
        user: store.currentUser.name,
        note: `冲突：${conflictResult.value.roomName} ${form.startTime} - ${form.endTime} 已被占用`,
      })
      ElMessage.error(error.message)
      return
    }
    if (error instanceof ApiError) {
      ElMessage.error(error.message)
      return
    }
    throw error
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="props.editing ? '修改预约' : '新建预约'"
    width="520px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      label-position="left"
    >
      <el-form-item label="会议主题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="例如：软件工程课程设计讨论"
          maxlength="50"
          show-word-limit
          @input="clearConflict"
        />
      </el-form-item>

      <el-form-item label="会议室" prop="roomId">
        <el-select
          v-model="form.roomId"
          placeholder="请选择会议室"
          style="width: 100%"
          @change="clearConflict"
        >
          <el-option
            v-for="room in bookableRooms"
            :key="room.id"
            :value="room.id"
            :label="`${room.name}（${room.location} · ${room.capacity}人）`"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="日期" prop="date">
        <el-date-picker
          v-model="form.date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 100%"
          @change="clearConflict"
        />
      </el-form-item>

      <el-form-item label="时间" required>
        <div class="time-row">
          <el-form-item prop="startTime" class="time-item">
            <el-time-select
              v-model="form.startTime"
              start="08:00"
              end="19:00"
              step="00:30"
              placeholder="开始时间"
              style="width: 100%"
              @change="clearConflict"
            />
          </el-form-item>
          <span class="time-sep">-</span>
          <el-form-item prop="endTime" class="time-item">
            <el-time-select
              v-model="form.endTime"
              :start="form.startTime || '08:00'"
              end="19:00"
              step="00:30"
              placeholder="结束时间"
              style="width: 100%"
              @change="clearConflict"
            />
          </el-form-item>
        </div>
      </el-form-item>

      <el-form-item label="参会人数" prop="participantCount">
        <el-input-number
          v-model="form.participantCount"
          :min="1"
          :max="selectedRoom?.capacity ?? 200"
          style="width: 140px"
        />
        <span v-if="selectedRoom" class="capacity-hint">
          {{ selectedRoom.name }} 容量 {{ selectedRoom.capacity }} 人
        </span>
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="选填，如需要提前准备的设备或材料"
        />
      </el-form-item>
    </el-form>

    <!-- 时间冲突提示：不关闭弹窗，给出占用详情与可用会议室推荐 -->
    <div v-if="conflictResult" class="conflict-panel">
      <div class="conflict-title">预约失败：时间冲突</div>
      <p v-if="serverConflictMessage" class="conflict-msg">{{ serverConflictMessage }}</p>
      <p v-else class="conflict-msg">
        {{ conflictResult.roomName }} 会议室在
        {{ conflictResult.conflicts[0]?.startTime }} -
        {{ conflictResult.conflicts[0]?.endTime }} 已被占用，当前预约时间与「{{
          conflictResult.conflicts[0]?.title
        }}」发生冲突。请选择其他时间或会议室。
      </p>
      <ul v-if="conflictResult.conflicts.length > 1" class="conflict-extra">
        <li v-for="c in conflictResult.conflicts.slice(1)" :key="c.id">
          {{ c.startTime }} - {{ c.endTime }}：{{ c.title }}（{{ c.userName }}）
        </li>
      </ul>
      <div v-if="conflictResult.alternatives.length" class="conflict-suggest">
        <div class="suggest-label">以下会议室在该时间段可用，点击可直接切换：</div>
        <div class="suggest-list">
          <button
            v-for="room in conflictResult.alternatives"
            :key="room.id"
            type="button"
            class="suggest-chip"
            @click="applyAlternative(room)"
          >
            {{ room.name }} 当前时间段可用
          </button>
        </div>
      </div>
      <div v-else class="conflict-suggest">
        <div class="suggest-label">该时间段暂无其他可用会议室，请尝试调整时间。</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        提交预约
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.time-row {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
}

.time-item {
  flex: 1;
  margin-right: 0 !important;
}

.time-sep {
  color: var(--text-muted);
}

.capacity-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.conflict-panel {
  margin: 0 0 4px;
  padding: 12px 14px;
  border: 1px solid #f5c6cb;
  border-radius: 8px;
  background: #fdf2f3;
}

.conflict-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #c0392b;
}

.conflict-title::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #e74c3c;
}

.conflict-msg {
  margin: 8px 0 0;
  font-size: 12.5px;
  line-height: 20px;
  color: #8a3a34;
}

.conflict-extra {
  margin: 6px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 20px;
  color: #8a3a34;
}

.conflict-suggest {
  margin-top: 10px;
}

.suggest-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.suggest-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.suggest-chip {
  padding: 4px 10px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.suggest-chip:hover {
  background: var(--el-color-primary-light-8);
}
</style>
