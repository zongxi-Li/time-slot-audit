<!--
  文件职责：实现可复用的 ReservationDetail Vue 组件。
  接口：通过 props、emits 与父页面通信，必要时通过 store 间接访问 API。
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useSystemTimeStore } from '@/stores/systemTime'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { administrationApi } from '@/modules/administration/api'
import { meetingsApi, type AttendanceView } from '@/modules/meeting/api'
import {
  RESERVATION_PHASE_TAG,
  RESERVATION_PHASE_TEXT,
  RESERVATION_STATUS_TAG,
  RESERVATION_STATUS_TEXT,
  reservationMoment,
  reservationTimePhase,
} from '@/utils/reservationStatus'

const visible = defineModel<boolean>({ default: false })

const props = withDefaults(defineProps<{
  reservationId: string | null
  mode?: 'drawer' | 'panel'
}>(), {
  mode: 'drawer',
})

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
const systemTime = useSystemTimeStore()
const auth = useAuthStore()
const monitor = useMonitorStore()

const reservation = computed(() =>
  props.reservationId ? store.getById(props.reservationId) : undefined,
)

const room = computed(() =>
  reservation.value ? roomStore.getRoom(reservation.value.roomId) : undefined,
)

const isMine = computed(
  () => reservation.value?.userId === auth.currentUser.id,
)

/** 已结束（结束时间不晚于当前时间）的预约不允许再取消 */
const isEnded = computed(() => {
  if (!reservation.value) return false
  return reservationMoment(reservation.value.date, reservation.value.endTime).getTime()
    <= systemTime.now.getTime()
})

/** 已开始（开始时间不晚于当前时间）的预约不允许再审批，与后端 requireBeforeStart 一致 */
const isStarted = computed(() => {
  if (!reservation.value) return false
  return reservationMoment(reservation.value.date, reservation.value.startTime).getTime()
    <= systemTime.now.getTime()
})

const canCancel = computed(
  () => isMine.value && !isEnded.value && reservation.value?.status !== 'CANCELLED' && reservation.value?.status !== 'REJECTED',
)

/** 管理员：可对未开始的待审核预约进行审核 */
const canAudit = computed(
  () => auth.isAdmin && reservation.value?.status === 'PENDING' && !isStarted.value,
)

/** 待审核但已开始：审批按钮消失时给出解释，避免像“点击没反应” */
const pendingStarted = computed(
  () => auth.isAdmin && reservation.value?.status === 'PENDING' && isStarted.value,
)

/** 详情标签的时间推导态：进行中/已结束优先于数据库状态展示 */
const timePhase = computed(() =>
  reservation.value ? reservationTimePhase(reservation.value, systemTime.now) : null,
)

/* —— 签到 / 出勤：与「我的会议」同一套窗口规则（开始前 15 分钟 ~ 结束；签退宽限 60 分钟）—— */
const CHECK_IN_EARLY_MINUTES = 15
const CHECK_OUT_GRACE_MINUTES = 60

const attendanceView = ref<AttendanceView | null>(null)
const attendanceLoading = ref(false)
const checkInBusy = ref(false)

const mine = computed(() => attendanceView.value?.mine ?? null)

/** 窗口内允许签到；最终以后端校验为准 */
const canCheckIn = computed(() => {
  const r = reservation.value
  if (!r || r.status !== 'CONFIRMED' || mine.value?.attendanceStatus !== 'EXPECTED') return false
  const now = systemTime.now.getTime()
  const start = reservationMoment(r.date, r.startTime).getTime()
  const end = reservationMoment(r.date, r.endTime).getTime()
  return now >= start - CHECK_IN_EARLY_MINUTES * 60_000 && now < end
})

const canCheckOut = computed(() => {
  const r = reservation.value
  if (!r || mine.value?.attendanceStatus !== 'CHECKED_IN') return false
  const end = reservationMoment(r.date, r.endTime).getTime()
  return systemTime.now.getTime() <= end + CHECK_OUT_GRACE_MINUTES * 60_000
})

const attendanceTextMap: Record<string, string> = {
  EXPECTED: '待签到',
  CHECKED_IN: '已签到',
  CHECKED_OUT: '已签退',
  NO_SHOW: '缺席',
}
const attendanceTagMap: Record<string, 'info' | 'success' | 'primary' | 'danger'> = {
  EXPECTED: 'info',
  CHECKED_IN: 'success',
  CHECKED_OUT: 'primary',
  NO_SHOW: 'danger',
}

/** 按钮不出现时给出解释，避免“找不到签到入口” */
const attendanceHint = computed(() => {
  const r = reservation.value
  const status = mine.value?.attendanceStatus
  if (!r || !mine.value) return ''
  if (r.status === 'PENDING') return '预约审批通过后开放签到'
  if (status === 'CHECKED_IN') return `已签到 ${mine.value.checkInAt?.slice(11, 16) ?? ''}`
  if (status === 'CHECKED_OUT') return `已签退 ${mine.value.checkOutAt?.slice(11, 16) ?? ''}`
  if (status === 'NO_SHOW') return '会议已结束且未签到，已记录缺席'
  // EXPECTED
  const now = systemTime.now.getTime()
  const start = reservationMoment(r.date, r.startTime).getTime()
  const end = reservationMoment(r.date, r.endTime).getTime()
  if (now >= end) return '会议已结束，签到窗口已关闭'
  if (now < start - CHECK_IN_EARLY_MINUTES * 60_000) {
    return `会议开始前 ${CHECK_IN_EARLY_MINUTES} 分钟开放签到（${r.startTime} 开始）`
  }
  return ''
})

/** 出勤接口仅参与人/创建人/管理员可见；他人预约静默隐藏本区块 */
async function loadAttendance() {
  const r = reservation.value
  if (!r) return
  attendanceLoading.value = true
  try {
    attendanceView.value = await meetingsApi.attendance(r.id)
  } catch {
    attendanceView.value = null
  } finally {
    attendanceLoading.value = false
  }
}

watch(
  [visible, () => props.reservationId],
  ([open, id]) => {
    if (open && id) void loadAttendance()
    if (!open) attendanceView.value = null
  },
  { immediate: true },
)

async function handleCheckIn() {
  const r = reservation.value
  if (!r) return
  checkInBusy.value = true
  try {
    await meetingsApi.checkIn(r.id)
    ElMessage.success('签到成功')
    await loadAttendance()
  } catch (error) {
    ElMessage.error((error as Error).message || '签到失败')
  } finally {
    checkInBusy.value = false
  }
}

async function handleCheckOut() {
  const r = reservation.value
  if (!r) return
  checkInBusy.value = true
  try {
    await meetingsApi.checkOut(r.id)
    ElMessage.success('签退成功')
    await loadAttendance()
  } catch (error) {
    ElMessage.error((error as Error).message || '签退失败')
  } finally {
    checkInBusy.value = false
  }
}

/* panel 模式：侧栏为固定定位的整条右栏，打开时让页面布局为其让位（见文件底部全局样式） */
const INSPECTOR_OPEN_CLASS = 'reservation-inspector-open'

watch(
  [visible, () => props.mode],
  ([open, mode]) => {
    document.body.classList.toggle(INSPECTOR_OPEN_CLASS, open && mode === 'panel')
  },
  { immediate: true },
)

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (props.mode !== 'panel' || !visible.value) return
  // 焦点在弹窗（如驳回原因输入框）内时，Esc 交给弹窗自己处理
  if ((event.target as HTMLElement | null)?.closest?.('.el-overlay')) return
  visible.value = false
}

window.addEventListener('keydown', handleGlobalKeydown)

onBeforeUnmount(() => {
  document.body.classList.remove(INSPECTOR_OPEN_CLASS)
  window.removeEventListener('keydown', handleGlobalKeydown)
})

/**
 * 统一兜底：接口失败必须给出可见提示，否则表现为“点击没反应”。
 * 失败时返回 undefined，调用方据此跳过后续本地更新与埋点。
 */
async function runGuarded<T>(action: () => Promise<T>, fallback: string): Promise<T | undefined> {
  try {
    return await action()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : fallback)
    return undefined
  }
}

async function handleCancel() {
  const r = reservation.value
  if (!r) return
  try {
    await ElMessageBox.confirm(
      `确定取消「${r.title}」吗？取消后将释放该时间段。`,
      '取消预约',
      { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' },
    )
  } catch {
    return
  }
  await runGuarded(() => store.cancelReservation(r.id), '取消预约失败')
  monitor.pushFeed({
    method: 'DELETE',
    path: `/api/reservations/${r.id}`,
    status: 200,
    user: auth.currentUser.name,
    note: `用户取消「${r.title}」`,
  })
  monitor.log(
    '取消预约',
    `${r.title} · ${r.roomId} ${r.date} ${r.startTime}-${r.endTime}`,
    auth.currentUser.name,
    auth.currentUser.role,
  )
  ElMessage.success('预约已取消')
  visible.value = false
}

async function handleAudit(approve: boolean) {
  const r = reservation.value
  if (!r) return

  let reason = ''
  if (!approve) {
    try {
      const { value } = await ElMessageBox.prompt('请输入驳回原因（写入审批记录与审计日志）', '驳回预约', {
        confirmButtonText: '确定驳回',
        cancelButtonText: '再想想',
        inputPlaceholder: '例如：该时段需优先保障教学活动',
        inputPattern: /\S+/,
        inputErrorMessage: '驳回原因不能为空',
      })
      reason = value?.trim() ?? ''
    } catch {
      return
    }
  }

  // 审批状态迁移由后端唯一状态机裁决；前端只用返回的最新数据替换本地记录。
  const updated = await runGuarded(
    () => (approve ? administrationApi.approve(Number(r.id)) : administrationApi.reject(Number(r.id), reason)),
    approve ? '审批失败' : '驳回失败',
  )
  if (!updated) return
  store.replaceLocalReservation({
    ...r,
    status: updated.status,
  })

  monitor.pushFeed({
    method: 'PUT',
    path: `/api/admin/reservations/${r.id}/${approve ? 'approve' : 'reject'}`,
    status: 200,
    user: auth.currentUser.name,
    note: approve ? '审核通过' : `驳回${reason ? '：' + reason : ''}`,
  })
  monitor.log(
    approve ? '审核通过' : '审核驳回',
    `${r.title} · ${r.roomId} ${r.date} ${r.startTime}-${r.endTime}${reason ? '（原因：' + reason + '）' : ''}`,
    auth.currentUser.name,
    'ADMIN',
  )
  ElMessage.success(approve ? '已通过，预约生效' : '已驳回该预约')
}
</script>

<template>
  <el-drawer
    v-if="props.mode === 'drawer'"
    v-model="visible"
    title="预约详情"
    size="400px"
    :destroy-on-close="false"
  >
    <template v-if="reservation">
      <div class="detail-head">
        <div class="detail-title">{{ reservation.title }}</div>
        <el-tag
          :type="timePhase ? RESERVATION_PHASE_TAG[timePhase] : RESERVATION_STATUS_TAG[reservation.status]"
          size="small"
          effect="light"
        >
          {{ timePhase ? RESERVATION_PHASE_TEXT[timePhase] : RESERVATION_STATUS_TEXT[reservation.status] }}
        </el-tag>
      </div>

      <el-descriptions :column="1" border size="large" class="detail-desc">
        <el-descriptions-item label="会议室">
          {{ room?.name ?? reservation.roomId }}
          <span v-if="room" class="room-loc">{{ room.location }} · 容量 {{ room.capacity }} 人</span>
        </el-descriptions-item>
        <el-descriptions-item label="预约日期">{{ reservation.date }}</el-descriptions-item>
        <el-descriptions-item label="时间">
          {{ reservation.startTime }} - {{ reservation.endTime }}
        </el-descriptions-item>
        <el-descriptions-item label="预约人">{{ reservation.userName }}</el-descriptions-item>
        <el-descriptions-item label="参会人数">{{ reservation.participantCount }} 人</el-descriptions-item>
        <el-descriptions-item label="备注">
          {{ reservation.remark || '—' }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="isMine" class="mine-note">这是我创建的预约</div>
      <div v-if="pendingStarted" class="audit-hint">该预约已开始，不能再审批；如需撤销可在「预约审批」页强制取消。</div>

      <div v-if="mine" v-loading="attendanceLoading" class="attendance-card">
        <div class="attendance-head">
          <span class="attendance-title">签到 / 出勤</span>
          <el-tag :type="attendanceTagMap[mine.attendanceStatus]" size="small" effect="light">
            {{ attendanceTextMap[mine.attendanceStatus] }}
          </el-tag>
        </div>
        <p v-if="attendanceHint" class="attendance-hint">{{ attendanceHint }}</p>
        <div v-if="canCheckIn || canCheckOut" class="attendance-actions">
          <el-button v-if="canCheckIn" type="success" size="small" :loading="checkInBusy" @click="handleCheckIn">
            签到
          </el-button>
          <el-button v-if="canCheckOut" type="warning" plain size="small" :loading="checkInBusy" @click="handleCheckOut">
            签退
          </el-button>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="visible = false">关闭</el-button>
        <template v-if="canAudit">
          <el-button type="warning" plain @click="handleAudit(false)">驳回</el-button>
          <el-button type="primary" @click="handleAudit(true)">通过</el-button>
        </template>
        <el-button v-else-if="canCancel" type="danger" plain @click="handleCancel">
          取消预约
        </el-button>
      </div>
    </template>
  </el-drawer>

  <Transition name="inspector">
    <aside
      v-if="props.mode === 'panel' && visible"
      class="reservation-inspector"
      aria-label="预约详情"
    >
    <header class="inspector-head">
      <div>
        <span class="inspector-kicker">RESERVATION</span>
        <h3>{{ reservation?.title ?? '预约详情' }}</h3>
      </div>
      <button
        type="button"
        class="inspector-close"
        aria-label="关闭预约详情"
        title="关闭预约详情"
        @click="visible = false"
      >
        <el-icon><Close /></el-icon>
      </button>
    </header>

    <div class="inspector-body">
      <template v-if="reservation">
        <div class="detail-head">
          <div class="detail-title">预约信息</div>
          <el-tag
            :type="timePhase ? RESERVATION_PHASE_TAG[timePhase] : RESERVATION_STATUS_TAG[reservation.status]"
            size="small"
            effect="light"
          >
            {{ timePhase ? RESERVATION_PHASE_TEXT[timePhase] : RESERVATION_STATUS_TEXT[reservation.status] }}
          </el-tag>
        </div>

        <el-descriptions :column="1" border size="large" class="detail-desc">
          <el-descriptions-item label="会议室">
            {{ room?.name ?? reservation.roomId }}
            <span v-if="room" class="room-loc">{{ room.location }} · 容量 {{ room.capacity }} 人</span>
          </el-descriptions-item>
          <el-descriptions-item label="预约日期">{{ reservation.date }}</el-descriptions-item>
          <el-descriptions-item label="时间">
            {{ reservation.startTime }} - {{ reservation.endTime }}
          </el-descriptions-item>
          <el-descriptions-item label="预约人">{{ reservation.userName }}</el-descriptions-item>
          <el-descriptions-item label="参会人数">{{ reservation.participantCount }} 人</el-descriptions-item>
          <el-descriptions-item label="备注">
            {{ reservation.remark || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="isMine" class="mine-note">这是我创建的预约</div>
        <div v-if="pendingStarted" class="audit-hint">该预约已开始，不能再审批；如需撤销可在「预约审批」页强制取消。</div>

        <div v-if="mine" v-loading="attendanceLoading" class="attendance-card">
          <div class="attendance-head">
            <span class="attendance-title">签到 / 出勤</span>
            <el-tag :type="attendanceTagMap[mine.attendanceStatus]" size="small" effect="light">
              {{ attendanceTextMap[mine.attendanceStatus] }}
            </el-tag>
          </div>
          <p v-if="attendanceHint" class="attendance-hint">{{ attendanceHint }}</p>
          <div v-if="canCheckIn || canCheckOut" class="attendance-actions">
            <el-button v-if="canCheckIn" type="success" size="small" :loading="checkInBusy" @click="handleCheckIn">
              签到
            </el-button>
            <el-button v-if="canCheckOut" type="warning" plain size="small" :loading="checkInBusy" @click="handleCheckOut">
              签退
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-else description="暂无预约详情" :image-size="64" />
    </div>

    <footer class="inspector-footer">
      <el-button @click="visible = false">关闭</el-button>
      <template v-if="canAudit">
        <el-button type="warning" plain @click="handleAudit(false)">驳回</el-button>
        <el-button type="primary" @click="handleAudit(true)">通过</el-button>
      </template>
      <el-button v-else-if="canCancel" type="danger" plain @click="handleCancel">
        取消预约
      </el-button>
    </footer>
    </aside>
  </Transition>
</template>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

.detail-desc :deep(.el-descriptions__label) {
  width: 88px;
  color: var(--text-muted);
}

.room-loc {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.mine-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-muted);
}

.audit-hint {
  margin-top: 12px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-color-warning);
}

.attendance-card {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  background: var(--surface-subtle, #f7f9fc);
}

.attendance-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.attendance-title {
  font-size: 13px;
  font-weight: 600;
}

.attendance-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-muted);
}

.attendance-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.reservation-inspector {
  position: fixed;
  /* 顶部避开布局顶栏（68px）+ 工作区标签条（42px），底部避开状态栏（23px），
     四周留 inset 间隙，让浮动卡片与周围结构有明确分割 */
  top: calc(var(--inspector-top, 110px) + var(--inspector-inset, 12px));
  right: var(--inspector-inset, 12px);
  bottom: calc(var(--inspector-bottom, 23px) + var(--inspector-inset, 12px));
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: var(--inspector-width, 340px);
  min-width: 0;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--border-color);
  border-radius: 18px;
  box-shadow: var(--shadow-panel);
  -webkit-backdrop-filter: blur(24px);
  backdrop-filter: blur(24px);
}

.inspector-enter-active,
.inspector-leave-active {
  transition: transform 220ms ease, opacity 220ms ease;
}

.inspector-enter-from,
.inspector-leave-to {
  transform: translateX(28px);
  opacity: 0;
}

.inspector-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 13px;
  border-bottom: 1px solid var(--border-light);
}

.inspector-kicker {
  display: block;
  color: var(--el-color-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.inspector-head h3 {
  max-width: 250px;
  margin: 5px 0 0;
  overflow: hidden;
  font-size: 16px;
  font-weight: 700;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inspector-close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.inspector-close:hover {
  color: var(--text-primary);
  background: var(--surface-hover);
}

.inspector-body {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
}

.inspector-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
}

@media (max-width: 760px) {
  .reservation-inspector {
    --inspector-inset: 10px;
    width: min(340px, 88vw);
  }
}
</style>

<style>
/* 全局样式：详情侧栏为浮动卡片（圆角 + 投影，悬于工作台区域之上、顶栏之下）。
   打开时 body 挂 reservation-inspector-open 类，主工作区右侧让出
   「面板宽 + 双侧间隙」，内容压缩排布而不是被浮层盖住。 */
:root {
  --inspector-width: 340px;
  --inspector-inset: 12px;
  /* 与 MainLayout 布局常量保持一致：顶栏 68px + 标签条 42px；底部状态栏 23px */
  --inspector-top: 110px;
  --inspector-bottom: 23px;
}

.workbench-content {
  transition: padding-right 220ms ease;
}

body.reservation-inspector-open .workbench-content {
  padding-right: calc(var(--inspector-width, 340px) + var(--inspector-inset, 12px) * 2);
}

/* ≤760px 小屏面板仍为浮层覆盖，不让主界面压缩（断点与 .reservation-inspector 一致） */
@media (max-width: 760px) {
  body.reservation-inspector-open .workbench-content {
    padding-right: 0;
  }
}
</style>
