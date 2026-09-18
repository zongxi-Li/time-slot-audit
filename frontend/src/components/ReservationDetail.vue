<!--
  文件职责：实现可复用的 ReservationDetail Vue 组件。
  接口：通过 props、emits 与父页面通信，必要时通过 store 间接访问 API。
-->
<script setup lang="ts">
import { computed, onBeforeUnmount, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useSystemTimeStore } from '@/stores/systemTime'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { administrationApi } from '@/modules/administration/api'
import { parseDateStr } from '@/utils/datetime'
import { RESERVATION_STATUS_TAG, RESERVATION_STATUS_TEXT } from '@/utils/reservationStatus'

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

/** 已结束（结束时间早于当前时间）的预约不允许再取消 */
const isEnded = computed(() => {
  const r = reservation.value
  if (!r) return false
  const end = parseDateStr(r.date)
  const [h, m] = r.endTime.split(':').map(Number)
  end.setHours(h, m, 0, 0)
  return end.getTime() <= systemTime.now.getTime()
})

const canCancel = computed(
  () => isMine.value && !isEnded.value && reservation.value?.status !== 'CANCELLED' && reservation.value?.status !== 'REJECTED',
)

/** 管理员：可对任意待审核预约进行审核 */
const canAudit = computed(
  () => auth.isAdmin && reservation.value?.status === 'PENDING',
)

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
  await store.cancelReservation(r.id)
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
      })
      reason = value?.trim() ?? ''
    } catch {
      return
    }
  }

  // 审批状态迁移由后端唯一状态机裁决；前端只用返回的最新数据替换本地记录。
  const updated = approve
    ? await administrationApi.approve(Number(r.id))
    : await administrationApi.reject(Number(r.id), reason)
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
        <el-tag :type="RESERVATION_STATUS_TAG[reservation.status]" size="small" effect="light">
          {{ RESERVATION_STATUS_TEXT[reservation.status] }}
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
          <el-tag :type="RESERVATION_STATUS_TAG[reservation.status]" size="small" effect="light">
            {{ RESERVATION_STATUS_TEXT[reservation.status] }}
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

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.reservation-inspector {
  position: fixed;
  top: var(--inspector-inset, 12px);
  right: var(--inspector-inset, 12px);
  bottom: var(--inspector-inset, 12px);
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: var(--inspector-width, 340px);
  min-width: 0;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--border-color);
  border-radius: 18px;
  box-shadow: var(--shadow-float);
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
    top: 10px;
    right: 10px;
    bottom: 10px;
    width: min(340px, 88vw);
  }
}
</style>

<style>
/* 全局样式：详情侧栏为浮动卡片（圆角 + 投影，四周留缝悬于页面之上）。
   打开时顶栏与工作台（标签栏/内容/状态栏）整体左移让位；
   窄屏下不再挤压布局，改为浮层。 */
:root {
  --inspector-width: 340px;
  --inspector-inset: 12px;
}

.layout-header,
.workbench-shell {
  transition: padding-right 220ms ease;
}

body.reservation-inspector-open .layout-header {
  padding-right: calc(var(--inspector-width) + var(--inspector-inset) * 2 + 26px);
}

body.reservation-inspector-open .workbench-shell {
  padding-right: calc(var(--inspector-width) + var(--inspector-inset) * 2);
}

@media (max-width: 760px) {
  body.reservation-inspector-open .layout-header,
  body.reservation-inspector-open .workbench-shell {
    padding-right: 0;
  }
}
</style>
