<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useMonitorStore } from '@/stores/monitor'
import { adminCategoriesApi, adminRoomsApi, roomsApi } from '@/shared/api'
import { useMock } from '@/shared/api/config'
import { ApiError } from '@/shared/api/types'
import { formatDateTime } from '@/utils/datetime'
import type { CategoryResponse, MaintenanceResponse, SaveFacilityRequest } from '@/shared/api/types'
import type { MeetingRoom, RoomFlag } from '@/types'

const roomStore = useMeetingRoomStore()
const monitor = useMonitorStore()

const WEEKDAY_LABELS = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
const EQUIPMENT_OPTIONS = ['投影仪', '白板', '电视屏', '视频会议', '麦克风']
const STATUS_TEXT: Record<RoomFlag, string> = { AVAILABLE: '启用中', MAINTENANCE: '维护中', DISABLED: '停用' }
const STATUS_TAG: Record<RoomFlag, 'success' | 'warning' | 'info'> = {
  AVAILABLE: 'success',
  MAINTENANCE: 'warning',
  DISABLED: 'info',
}

const loading = ref(false)
const categories = ref<CategoryResponse[]>([])

onMounted(async () => {
  loading.value = true
  try {
    await roomStore.refreshRooms()
    if (!useMock) categories.value = await adminCategoriesApi.list()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
})

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '操作失败，请稍后重试'
}

function mockFeed(method: string, path: string, note: string) {
  if (useMock) monitor.pushFeed({ method, path, status: 200, user: '管理员', note })
}

/* —— 新增 / 编辑弹窗 —— */
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  name: '',
  location: '',
  capacity: 8,
  categoryId: null as number | string | null,
  description: '',
  equipment: [] as string[],
})

const selectedCategory = computed(() => categories.value.find((c) => c.id === form.categoryId))

const rules = computed(() => ({
  name: [
    { required: true, message: '请输入会议室名称', trigger: 'blur' },
    ...(useMock
      ? [
          {
            validator: (_r: unknown, value: string, cb: (err?: Error) => void) => {
              const exists = roomStore.rooms.some((r) => r.name === value.trim())
              if (editingId.value === null && exists) cb(new Error('已存在同名会议室'))
              else cb()
            },
            trigger: 'blur',
          },
        ]
      : []),
  ],
  categoryId: useMock ? [] : [{ required: true, message: '请选择会议室分类', trigger: 'change' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'change' }],
}))

function openCreate() {
  editingId.value = null
  form.name = ''
  form.location = ''
  form.capacity = 8
  form.categoryId = null
  form.description = ''
  form.equipment = []
  dialogVisible.value = true
}

function openEdit(room: MeetingRoom) {
  editingId.value = room.id
  form.name = room.name
  form.location = room.location
  form.capacity = room.capacity
  form.description = ''
  form.equipment = [...room.equipment]
  form.categoryId = null
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!useMock && selectedCategory.value) {
    const { minCapacity, maxCapacity } = selectedCategory.value
    if (form.capacity < minCapacity || form.capacity > maxCapacity) {
      ElMessage.error(`容量必须在分类范围 [${minCapacity}, ${maxCapacity}] 内`)
      return
    }
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      const ok = await roomStore.createRoom({
        name: form.name.trim(),
        categoryId: form.categoryId ?? 0,
        location: form.location.trim(),
        capacity: form.capacity,
        description: form.description.trim() || null,
      })
      if (!ok) {
        ElMessage.error('已存在同名会议室')
        return
      }
      mockFeed('POST', '/api/rooms', `新增会议室 ${form.name.trim()}`)
      monitor.log('新增会议室', `${form.name.trim()} · ${form.location} · 容量 ${form.capacity} 人`, '管理员', 'ADMIN')
      ElMessage.success(`会议室 ${form.name.trim()} 已创建`)
    } else {
      await roomStore.saveRoom(editingId.value, {
        name: form.name.trim(),
        categoryId: form.categoryId ?? 0,
        location: form.location.trim(),
        capacity: form.capacity,
        description: form.description.trim() || null,
      })
      mockFeed('PUT', `/api/rooms/${editingId.value}`, `更新会议室 ${form.name.trim()}`)
      monitor.log('编辑会议室', `${form.name.trim()} · ${form.location} · 容量 ${form.capacity} 人`, '管理员', 'ADMIN')
      ElMessage.success('会议室信息已更新')
    }
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    saving.value = false
  }
}

/* —— 编辑时补拉详情（分类、描述） —— */
async function openEditReal(room: MeetingRoom) {
  openEdit(room)
  if (useMock) return
  try {
    const detail = await roomsApi.detail(room.id)
    form.categoryId = detail.categoryId
    form.description = detail.description ?? ''
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

/* —— 状态管理 —— */
const statusOptions: { value: RoomFlag; label: string }[] = [
  { value: 'AVAILABLE', label: '启用' },
  { value: 'MAINTENANCE', label: '置为维护中' },
  { value: 'DISABLED', label: '停用' },
]

async function handleStatus(room: MeetingRoom, status: RoomFlag) {
  if (status === room.status) return
  if (status !== 'AVAILABLE' && !(await confirmStatusChange(room, status))) return
  try {
    await roomStore.changeRoomStatus(room.id, status)
    mockFeed('POST', `/api/admin/rooms/${room.id}/status`, `${STATUS_TEXT[status]} ${room.name}`)
    ElMessage.success(`${room.name} 已${status === 'AVAILABLE' ? '启用' : status === 'MAINTENANCE' ? '置为维护中' : '停用'}`)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function confirmStatusChange(room: MeetingRoom, status: RoomFlag) {
  return ElMessageBox.confirm(
    `${status === 'MAINTENANCE' ? '维护中' : '停用'}后「${room.name}」不可接受新预约，已有预约保留展示。`,
    status === 'MAINTENANCE' ? '置为维护中' : '停用会议室',
    { confirmButtonText: '确定', cancelButtonText: '再想想', type: 'warning' },
  ).then(
    () => true,
    () => false,
  )
}

function toggleStatusMock(room: MeetingRoom) {
  const next = roomStore.toggleRoomStatus(room.id)
  if (!next) return
  monitor.log(
    next === 'AVAILABLE' ? '启用会议室' : '停用会议室',
    `${room.name} · ${room.location}${next !== 'AVAILABLE' ? '（停用后不可新建预约，已有预约保留）' : ''}`,
    '王建国',
    'ADMIN',
  )
  mockFeed('PUT', `/api/rooms/${room.id}/status`, `${next === 'AVAILABLE' ? '启用' : '停用'} ${room.name}`)
  ElMessage.success(next === 'AVAILABLE' ? `${room.name} 已启用` : `${room.name} 已停用`)
}

/* —— 设施管理 —— */
const facilityDialogVisible = ref(false)
const facilityRoom = ref<MeetingRoom | null>(null)
const facilitySaving = ref(false)
const facilityRows = ref<SaveFacilityRequest[]>([])

async function openFacilities(room: MeetingRoom) {
  facilityRoom.value = room
  facilityDialogVisible.value = true
  try {
    const detail = await roomsApi.detail(room.id)
    facilityRows.value = detail.facilities.map((f) => ({ name: f.name, quantity: f.quantity, description: f.description ?? '' }))
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

function addFacilityRow() {
  facilityRows.value.push({ name: '', quantity: 1, description: '' })
}

function removeFacilityRow(index: number) {
  facilityRows.value.splice(index, 1)
}

async function saveFacilities() {
  if (!facilityRoom.value) return
  const rows = facilityRows.value
  const names = new Set<string>()
  for (const row of rows) {
    if (!row.name.trim()) {
      ElMessage.error('设施名称不能为空')
      return
    }
    if (!names.add(row.name.trim())) {
      ElMessage.error('同一会议室的设施名称不能重复')
      return
    }
  }
  facilitySaving.value = true
  try {
    await adminRoomsApi.replaceFacilities(facilityRoom.value.id, rows)
    await roomStore.refreshRooms()
    mockFeed('PUT', `/api/admin/rooms/${facilityRoom.value.id}/facilities`, `更新设施 ${facilityRoom.value.name}`)
    ElMessage.success('设施已更新')
    facilityDialogVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    facilitySaving.value = false
  }
}

/* —— 开放时间管理 —— */
interface OpenRuleDraft {
  weekday: number
  enabled: boolean
  openTime: string
  closeTime: string
}

const ruleDialogVisible = ref(false)
const ruleRoom = ref<MeetingRoom | null>(null)
const ruleSaving = ref(false)
const ruleRows = ref<OpenRuleDraft[]>([])

async function openRules(room: MeetingRoom) {
  ruleRoom.value = room
  ruleDialogVisible.value = true
  const defaults: OpenRuleDraft[] = WEEKDAY_LABELS.slice(1).map((_, index) => ({
    weekday: index + 1,
    enabled: false,
    openTime: '08:00:00',
    closeTime: '22:00:00',
  }))
  try {
    const detail = await roomsApi.detail(room.id)
    for (const rule of detail.openRules) {
      const row = defaults[rule.weekday - 1]
      if (row) {
        row.enabled = rule.enabled
        row.openTime = rule.openTime
        row.closeTime = rule.closeTime
      }
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    ruleRows.value = defaults
  }
}

async function saveRules() {
  if (!ruleRoom.value) return
  const enabledRows = ruleRows.value.filter((row) => row.enabled)
  for (const row of enabledRows) {
    if (row.closeTime <= row.openTime) {
      ElMessage.error(`${WEEKDAY_LABELS[row.weekday]} 的关闭时间必须晚于开放时间`)
      return
    }
  }
  ruleSaving.value = true
  try {
    await adminRoomsApi.replaceOpenRules(
      ruleRoom.value.id,
      enabledRows.map((row) => ({ weekday: row.weekday, openTime: row.openTime, closeTime: row.closeTime, enabled: true })),
    )
    mockFeed('PUT', `/api/admin/rooms/${ruleRoom.value.id}/open-rules`, `更新开放时间 ${ruleRoom.value.name}`)
    ElMessage.success('开放时间已更新')
    ruleDialogVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    ruleSaving.value = false
  }
}

/* —— 维护计划管理 —— */
const maintenanceDialogVisible = ref(false)
const maintenanceRoom = ref<MeetingRoom | null>(null)
const maintenanceLoading = ref(false)
const maintenanceSaving = ref(false)
const maintenancePlans = ref<MaintenanceResponse[]>([])
const maintenanceForm = reactive({ reason: '', range: null as [string, string] | null })

const openedRuleSummary = (room: MeetingRoom): string => room.equipment.length ? `${room.equipment.length} 项设施` : '无设施'

async function openMaintenance(room: MeetingRoom) {
  maintenanceRoom.value = room
  maintenanceDialogVisible.value = true
  maintenanceForm.reason = ''
  maintenanceForm.range = null
  await refreshMaintenance(room.id)
}

async function refreshMaintenance(roomId: string) {
  maintenanceLoading.value = true
  try {
    maintenancePlans.value = await adminRoomsApi.listMaintenance(roomId)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    maintenanceLoading.value = false
  }
}

async function createMaintenance() {
  if (!maintenanceRoom.value) return
  if (!maintenanceForm.reason.trim() || !maintenanceForm.range) {
    ElMessage.error('请填写维护原因与时间段')
    return
  }
  const [startTime, endTime] = maintenanceForm.range
  maintenanceSaving.value = true
  try {
    await adminRoomsApi.createMaintenance(maintenanceRoom.value.id, {
      reason: maintenanceForm.reason.trim(),
      startTime,
      endTime,
    })
    mockFeed('POST', `/api/admin/rooms/${maintenanceRoom.value.id}/maintenance`, `登记维护 ${maintenanceRoom.value.name}`)
    ElMessage.success('维护计划已登记')
    maintenanceForm.reason = ''
    maintenanceForm.range = null
    await refreshMaintenance(maintenanceRoom.value.id)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    maintenanceSaving.value = false
  }
}

async function finishMaintenance(plan: MaintenanceResponse) {
  if (!maintenanceRoom.value) return
  try {
    await adminRoomsApi.finishMaintenance(maintenanceRoom.value.id, plan.id)
    ElMessage.success('维护计划已结束')
    await refreshMaintenance(maintenanceRoom.value.id)
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}
</script>

<template>
  <div class="page room-admin">
    <div class="head-row">
      <div>
        <h2 class="page-title">会议室管理</h2>
        <p class="page-subtitle">
          新增或编辑会议室信息；维护中 / 停用的会议室不可被新预约选中，已有预约保留展示
        </p>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增会议室</el-button>
    </div>

    <div class="panel table-panel" v-loading="loading">
      <el-table :data="roomStore.rooms" style="width: 100%">
        <el-table-column prop="name" label="名称" width="100">
          <template #default="{ row }">
            <span class="room-name-cell">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" min-width="140" />
        <el-table-column prop="capacity" label="容量" width="80">
          <template #default="{ row }">{{ row.capacity }} 人</template>
        </el-table-column>
        <el-table-column v-if="!useMock" prop="category" label="分类" width="110">
          <template #default="{ row }">{{ row.category || '—' }}</template>
        </el-table-column>
        <el-table-column label="设施" min-width="220">
          <template #default="{ row }">
            <el-tag
              v-for="eq in row.equipment"
              :key="eq"
              size="small"
              type="info"
              effect="plain"
              class="eq-tag"
            >
              {{ eq }}
            </el-tag>
            <span v-if="row.equipment.length === 0" class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG[row.status as RoomFlag]" size="small" effect="light">
              {{ STATUS_TEXT[row.status as RoomFlag] ?? '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="useMock ? 150 : 300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="useMock ? openEdit(row) : openEditReal(row)">
              编辑
            </el-button>
            <template v-if="!useMock">
              <el-button link type="primary" size="small" @click="openFacilities(row)">设施</el-button>
              <el-button link type="primary" size="small" @click="openRules(row)">开放时间</el-button>
              <el-button link type="primary" size="small" @click="openMaintenance(row)">维护</el-button>
            </template>
            <el-dropdown v-if="!useMock" class="status-dropdown" @command="(cmd: RoomFlag) => handleStatus(row, cmd)">
              <el-button link size="small" :type="row.status === 'AVAILABLE' ? 'danger' : 'success'">
                状态
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="opt in statusOptions"
                    :key="opt.value"
                    :command="opt.value"
                    :disabled="opt.value === row.status"
                  >
                    {{ opt.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button
              v-else
              link
              :type="row.status === 'AVAILABLE' ? 'danger' : 'success'"
              size="small"
              @click="toggleStatusMock(row)"
            >
              {{ row.status === 'AVAILABLE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增会议室' : `编辑会议室：${form.name}`"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" label-position="left">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：C305" maxlength="10" />
        </el-form-item>
        <el-form-item v-if="!useMock" label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
          <span v-if="selectedCategory" class="field-hint">
            容量范围 {{ selectedCategory.minCapacity }}~{{ selectedCategory.maxCapacity }} ·
            最长 {{ selectedCategory.maxDurationMinutes }} 分钟
            <template v-if="selectedCategory.approvalRequired"> · 需审批</template>
          </span>
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="例如：第三教学楼 3F" maxlength="20" />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="2" :max="200" />
          <span class="capacity-unit">人</span>
        </el-form-item>
        <el-form-item v-if="!useMock" label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
        <el-form-item v-else label="设备">
          <el-checkbox-group v-model="form.equipment">
            <el-checkbox v-for="eq in EQUIPMENT_OPTIONS" :key="eq" :value="eq" :label="eq">
              {{ eq }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="facilityDialogVisible"
      :title="`设施管理：${facilityRoom?.name ?? ''}`"
      width="560px"
      :close-on-click-modal="false"
    >
      <div v-for="(row, index) in facilityRows" :key="index" class="facility-row">
        <el-input v-model="row.name" placeholder="设施名称" maxlength="50" style="width: 150px" />
        <el-input-number v-model="row.quantity" :min="1" :max="999" controls-position="right" style="width: 100px" />
        <el-input v-model="row.description" placeholder="说明（选填）" maxlength="100" style="flex: 1" />
        <el-button link type="danger" size="small" @click="removeFacilityRow(index)">删除</el-button>
      </div>
      <el-button link type="primary" @click="addFacilityRow">+ 添加设施</el-button>
      <p v-if="facilityRows.length === 0" class="muted">暂无设施，点击下方按钮添加</p>
      <template #footer>
        <el-button @click="facilityDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="facilitySaving" @click="saveFacilities">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ruleDialogVisible"
      :title="`每周开放时间：${ruleRoom?.name ?? ''}`"
      width="480px"
      :close-on-click-modal="false"
    >
      <p class="muted rule-hint">勾选后该日按设置的时间段开放预约；跨日开放暂不支持</p>
      <div v-for="row in ruleRows" :key="row.weekday" class="rule-row">
        <el-checkbox v-model="row.enabled">{{ WEEKDAY_LABELS[row.weekday] }}</el-checkbox>
        <el-time-picker
          v-model="row.openTime"
          :disabled="!row.enabled"
          value-format="HH:mm:ss"
          format="HH:mm"
          placeholder="开放"
          style="width: 110px"
        />
        <span class="rule-sep">至</span>
        <el-time-picker
          v-model="row.closeTime"
          :disabled="!row.enabled"
          value-format="HH:mm:ss"
          format="HH:mm"
          placeholder="关闭"
          style="width: 110px"
        />
      </div>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="saveRules">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="maintenanceDialogVisible"
      :title="`维护计划：${maintenanceRoom?.name ?? ''}`"
      width="620px"
      :close-on-click-modal="false"
    >
      <div class="maintenance-form">
        <el-input
          v-model="maintenanceForm.reason"
          placeholder="维护原因，例如：投影仪检修"
          maxlength="200"
          style="width: 220px"
        />
        <el-date-picker
          v-model="maintenanceForm.range"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 320px"
        />
        <el-button type="primary" :loading="maintenanceSaving" @click="createMaintenance">登记</el-button>
      </div>
      <el-table :data="maintenancePlans" v-loading="maintenanceLoading" size="small" style="width: 100%">
        <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
        <el-table-column label="时间段" min-width="220">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }} ~ {{ formatDateTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PLANNED' ? 'warning' : 'success'" size="small" effect="light">
              {{ row.status === 'PLANNED' ? '计划中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PLANNED'" link type="success" size="small" @click="finishMaintenance(row)">
              结束
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="muted maintenance-hint">维护计划仅作登记，需要阻断预约请同时将会议室置为维护中（当前设施：{{ maintenanceRoom ? openedRuleSummary(maintenanceRoom) : '' }}）</p>
      <template #footer>
        <el-button @click="maintenanceDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.table-panel {
  padding: 16px;
}

.room-name-cell {
  font-weight: 600;
}

.eq-tag {
  margin: 0 6px 4px 0;
  border-radius: 5px;
}

.muted {
  font-size: 12px;
  color: var(--text-muted);
}

.capacity-unit {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.field-hint {
  display: block;
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-muted);
}

.status-dropdown {
  margin-left: 12px;
}

.facility-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.rule-hint {
  margin: 0 0 10px;
}

.rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.rule-row .el-checkbox {
  width: 64px;
}

.rule-sep {
  font-size: 12px;
  color: var(--text-muted);
}

.maintenance-form {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.maintenance-hint {
  margin: 10px 0 0;
}
</style>
