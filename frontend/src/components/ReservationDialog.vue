<!--
  文件职责：实现可复用的 ReservationDialog Vue 组件。
  接口：通过 props、emits 与父页面通信，必要时通过 store 间接访问 API。
-->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useSystemTimeStore } from '@/stores/systemTime'
import { useBookingWindowStore } from '@/stores/bookingWindow'
import { useMonitorStore } from '@/stores/monitor'
import { ApiError } from '@/shared/api'
import { userDirectoryApi } from '@/shared/api'
import type { UserDirectoryResponse } from '@/shared/api'
import { timeLabel } from '@/utils/datetime'
import type { MeetingRoom, Reservation, ReservationDraft } from '@/types'

const visible = defineModel<boolean>({ default: false })

const props = withDefaults(defineProps<{
  /** 从看板框选/点击进入时的预填信息 */
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
const systemTime = useSystemTimeStore()
const bookingWindow = useBookingWindowStore()
const monitor = useMonitorStore()

/** 仅可预约“启用”状态的会议室（管理员停用的房间不出现在选项里） */
const bookableRooms = computed(() => roomStore.rooms.filter((r) => r.status === 'AVAILABLE'))

interface TimeOption {
  value: string
  label: string
}

/** 预约弹窗按 15 分钟一档（quarter）展示，后端仍以 LocalDateTime 负责最终校验。 */
const TIME_STEP_MINUTES = 15

function timeValue(totalMinutes: number): string {
  const hour = Math.floor(totalMinutes / 60)
  const minute = totalMinutes % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}

/** 开始时间选项：窗口起点到当天 24 点前（次日晨间时段只能作为前一天预约的结束）。 */
const startOptions = computed<TimeOption[]>(() => {
  const list: TimeOption[] = []
  const lastStartMinute = Math.min(bookingWindow.endMinute, 24 * 60) - TIME_STEP_MINUTES
  for (let minute = bookingWindow.startMinute; minute <= lastStartMinute; minute += TIME_STEP_MINUTES) {
    const value = timeValue(minute)
    list.push({ value, label: timeLabel(value) })
  }
  return list
})

/** 结束时间选项：晚于已选开始，可到窗口终点（含次日） */
const endOptions = computed<TimeOption[]>(() => {
  const list: TimeOption[] = []
  for (let minute = bookingWindow.startMinute + TIME_STEP_MINUTES;
    minute <= bookingWindow.endMinute;
    minute += TIME_STEP_MINUTES) {
    const value = timeValue(minute)
    if (form.startTime && value <= form.startTime) continue
    list.push({ value, label: timeLabel(value) })
  }
  return list
})

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
  date: systemTime.date,
  startTime: '10:00',
  endTime: '11:00',
  participantCount: 4,
  remark: '',
  repeatWeeks: 1,
  attendeeIds: [],
})

/* —— 参会人选择：用户目录按部门分组，创建时随预约一并登记 —— */
const directory = ref<UserDirectoryResponse[]>([])
const directoryLoading = ref(false)

async function loadDirectory() {
  directoryLoading.value = true
  try {
    directory.value = await userDirectoryApi.list()
  } catch {
    // 目录加载失败不阻断预约：选择器显示为空，用户仍可提交后到「参与人」里补充
    directory.value = []
  } finally {
    directoryLoading.value = false
  }
}

interface DirectoryGroup {
  label: string
  options: UserDirectoryResponse[]
}

/** 后端已按部门名排序；这里仅做相邻分组，保持稳定顺序 */
const groupedDirectory = computed<DirectoryGroup[]>(() => {
  const groups: DirectoryGroup[] = []
  for (const user of directory.value) {
    const label = user.departmentName || '未分配部门'
    const last = groups[groups.length - 1]
    if (last && last.label === label) last.options.push(user)
    else groups.push({ label, options: [user] })
  }
  return groups
})

/** 申报人数（含组织者）必须 ≥ 已选参与人 + 1 */
function syncParticipantCount() {
  const needed = (form.attendeeIds?.length ?? 0) + 1
  if (form.participantCount < needed) {
    form.participantCount = Math.min(needed, selectedRoom.value?.capacity ?? 200)
  }
}

watch(visible, (open) => {
  if (!open) return
  conflictResult.value = null
  serverConflictMessage.value = ''
  createRequestId.value = props.editing ? null : crypto.randomUUID()
  void loadDirectory()
  // 编辑模式预填现有预约；新建模式回退到看板预填信息，再退到窗口起点+1小时。
  // 看板可能框选到维护中/停用的会议室，这类房间不在可预约选项里，roomId 置空让用户自选。
  const initialRoomId = props.initial?.roomId
  const fallbackStart = timeValue(bookingWindow.startMinute)
  const fallbackEnd = timeValue(Math.min(bookingWindow.startMinute + 60, bookingWindow.endMinute))
  form.title = props.editing?.title ?? ''
  form.roomId =
    props.editing?.roomId ??
    (initialRoomId && bookableRooms.value.some((r) => r.id === initialRoomId) ? initialRoomId : '')
  form.date = props.editing?.date ?? props.initial?.date ?? systemTime.date
  form.startTime = props.editing?.startTime ?? props.initial?.startTime ?? fallbackStart
  form.endTime = props.editing?.endTime ?? props.initial?.endTime ?? fallbackEnd
  form.participantCount = props.editing?.participantCount ?? 4
  form.remark = props.editing?.remark ?? ''
  form.repeatWeeks = 1
  // 编辑模式不提供选人（参与人经「参与人」抽屉管理），始终清空避免旧选择残留
  form.attendeeIds = []
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
  participantCount: [
    { required: true, message: '请输入参会人数', trigger: 'change' },
    {
      validator: (_rule, value: number, callback) => {
        const needed = (form.attendeeIds?.length ?? 0) + 1
        if (value < needed) {
          callback(new Error(`需容纳组织者与已选参与人，至少 ${needed} 人`))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
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
    const weeklyNote = (form.repeatWeeks ?? 1) > 1 ? `，已按周创建 ${form.repeatWeeks} 场预约` : ''
    const created = await store.addReservation({ ...form }, requestId)
    ElMessage.success((created.status === 'PENDING'
      ? '预约已提交，等待管理员审批'
      : '预约成功') + weeklyNote)
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
            <el-select
              v-model="form.startTime"
              placeholder="开始时间"
              style="width: 100%"
              @change="clearConflict"
            >
              <el-option
                v-for="opt in startOptions"
                :key="opt.value"
                :value="opt.value"
                :label="opt.label"
              />
            </el-select>
          </el-form-item>
          <span class="time-sep">-</span>
          <el-form-item prop="endTime" class="time-item">
            <el-select
              v-model="form.endTime"
              placeholder="结束时间"
              style="width: 100%"
              @change="clearConflict"
            >
              <el-option
                v-for="opt in endOptions"
                :key="opt.value"
                :value="opt.value"
                :label="opt.label"
              />
            </el-select>
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

      <el-form-item v-if="!props.editing" label="参会人员" prop="attendeeIds">
        <el-select
          v-model="form.attendeeIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          :loading="directoryLoading"
          :multiple-limit="(selectedRoom?.capacity ?? 200) - 1"
          placeholder="按部门选择参会人员（可留空，稍后在「参与人」中添加）"
          style="width: 100%"
          @change="syncParticipantCount(); clearConflict()"
        >
          <el-option-group v-for="group in groupedDirectory" :key="group.label" :label="group.label">
            <el-option
              v-for="user in group.options"
              :key="user.id"
              :value="user.id"
              :label="`${user.realName}（@${user.username}）`"
            />
          </el-option-group>
        </el-select>
        <span class="capacity-hint">组织者自动参会；人数上限为申报人数，超出时自动上调参会人数</span>
      </el-form-item>

      <el-form-item v-if="!props.editing" label="重复">
        <el-select v-model="form.repeatWeeks" style="width: 220px" @change="clearConflict">
          <el-option :value="1" label="不重复（单次会议）" />
          <el-option :value="2" label="每周重复 · 共 2 周" />
          <el-option :value="3" label="每周重复 · 共 3 周" />
          <el-option :value="4" label="每周重复 · 共 4 周" />
        </el-select>
        <span class="capacity-hint">按周批量创建，冲突逐周校验，任一周冲突整批失败</span>
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
        {{ timeLabel(conflictResult.conflicts[0]?.startTime ?? '') }} -
        {{ timeLabel(conflictResult.conflicts[0]?.endTime ?? '') }} 已被占用，当前预约时间与「{{
          conflictResult.conflicts[0]?.title
        }}」发生冲突。请选择其他时间或会议室。
      </p>
      <ul v-if="conflictResult.conflicts.length > 1" class="conflict-extra">
        <li v-for="c in conflictResult.conflicts.slice(1)" :key="c.id">
          {{ timeLabel(c.startTime) }} - {{ timeLabel(c.endTime) }}：{{ c.title }}（{{ c.userName }}）
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
