<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useReservationStore } from '@/stores/reservation'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { parseDateStr } from '@/utils/datetime'
import type { ReservationStatus } from '@/types'

const visible = defineModel<boolean>({ default: false })

const props = defineProps<{
  reservationId: string | null
}>()

const roomStore = useMeetingRoomStore()
const store = useReservationStore()
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
  return end.getTime() < Date.now()
})

const canCancel = computed(
  () => isMine.value && !isEnded.value && reservation.value?.status !== 'cancelled',
)

/** 管理员：可对任意待审核预约进行审核 */
const canAudit = computed(
  () => auth.isAdmin && reservation.value?.status === 'pending',
)

const statusText: Record<ReservationStatus, string> = {
  confirmed: '已预约',
  pending: '待审核',
  cancelled: '已取消',
}

const statusTagType: Record<ReservationStatus, 'primary' | 'warning' | 'info'> = {
  confirmed: 'primary',
  pending: 'warning',
  cancelled: 'info',
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
  store.cancelReservation(r.id)
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
      const { value } = await ElMessageBox.prompt('请输入驳回原因（写入审计日志）', '驳回预约', {
        confirmButtonText: '确定驳回',
        cancelButtonText: '再想想',
        inputPlaceholder: '例如：该时段需优先保障教学活动',
      })
      reason = value?.trim() ?? ''
    } catch {
      return
    }
  }

  const ok = approve ? store.approveReservation(r.id) : store.rejectReservation(r.id)
  if (!ok) return

  monitor.pushFeed({
    method: 'PUT',
    path: `/api/reservations/${r.id}/audit`,
    status: 200,
    user: auth.currentUser.name,
    note: approve ? '审核通过' : `驳回${reason ? '：' + reason : ''}`,
  })
  monitor.log(
    approve ? '审核通过' : '审核驳回',
    `${r.title} · ${r.roomId} ${r.date} ${r.startTime}-${r.endTime}${reason ? '（原因：' + reason + '）' : ''}`,
    auth.currentUser.name,
    'admin',
  )
  ElMessage.success(approve ? '已通过，预约生效' : '已驳回该预约')
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="预约详情"
    size="400px"
    :destroy-on-close="false"
  >
    <template v-if="reservation">
      <div class="detail-head">
        <div class="detail-title">{{ reservation.title }}</div>
        <el-tag :type="statusTagType[reservation.status]" size="small" effect="light">
          {{ statusText[reservation.status] }}
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
</style>
