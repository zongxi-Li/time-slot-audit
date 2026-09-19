<!--
  文件职责：实现 RoomAdmin 页面，负责展示、交互和表单状态。
  接口：通过 Pinia store 或 shared/api 调用后端；管理员页面使用 /api/admin/*。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useMonitorStore } from '@/stores/monitor'
import { useBookingWindowStore } from '@/stores/bookingWindow'
import { adminCategoriesApi, adminRoomsApi, roomsApi } from '@/shared/api'
import { ApiError } from '@/shared/api/types'
import { formatDateTime, hourLabel } from '@/utils/datetime'
import type { CategoryResponse, MaintenanceResponse, SaveFacilityRequest } from '@/shared/api/types'
import type { MeetingRoom, RoomFlag } from '@/types'

const roomStore = useMeetingRoomStore()
const monitor = useMonitorStore()
const bookingWindow = useBookingWindowStore()

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
    categories.value = await adminCategoriesApi.list()
    await bookingWindow.refresh()
    windowForm.startHour = bookingWindow.startHour
    windowForm.endHour = bookingWindow.endHour
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
})

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : '操作失败，请稍后重试'
}

/** 把真实落库的管理操作写入监控流水（请求本身已由后端执行） */
function logApi(method: string, path: string, note: string) {
  monitor.pushFeed({ method, path, status: 200, user: '管理员', note })
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
  name: [{ required: true, message: '请输入会议室名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择会议室分类', trigger: 'change' }],
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

async function openEdit(room: MeetingRoom) {
  editingId.value = room.id
  form.name = room.name
  form.location = room.location
  form.capacity = room.capacity
  form.description = ''
  form.equipment = [...room.equipment]
  form.categoryId = null
  dialogVisible.value = true
  // 编辑时补拉详情（分类、描述）
  try {
    const detail = await roomsApi.detail(room.id)
    form.categoryId = detail.categoryId
    form.description = detail.description ?? ''
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (selectedCategory.value) {
    const { minCapacity, maxCapacity } = selectedCategory.value
    if (form.capacity < minCapacity || form.capacity > maxCapacity) {
      ElMessage.error(`容量必须在分类范围 [${minCapacity}, ${maxCapacity}] 内`)
      return
    }
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await roomStore.createRoom({
        name: form.name.trim(),
        categoryId: form.categoryId ?? 0,
        location: form.location.trim(),
        capacity: form.capacity,
        description: form.description.trim() || null,
      })
      logApi('POST', '/api/rooms', `新增会议室 ${form.name.trim()}`)
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
      logApi('PUT', `/api/rooms/${editingId.value}`, `更新会议室 ${form.name.trim()}`)
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
    logApi('POST', `/api/admin/rooms/${room.id}/status`, `${STATUS_TEXT[status]} ${room.name}`)
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
    logApi('PUT', `/api/admin/rooms/${facilityRoom.value.id}/facilities`, `更新设施 ${facilityRoom.value.name}`)
    ElMessage.success('设施已更新')
    facilityDialogVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    facilitySaving.value = false
  }
}

/* —— 全局可预约时段（对所有会议室生效；结束可到次日） —— */
const windowForm = reactive({ startHour: 8, endHour: 32 })
const windowSaving = ref(false)

const windowStartOptions = computed(() => {
  const list: { value: number; label: string }[] = []
  for (let h = 0; h < 24; h++) list.push({ value: h, label: hourLabel(h) })
  return list
})

const windowEndOptions = computed(() => {
  const list: { value: number; label: string }[] = []
  const maxEnd = Math.min(windowForm.startHour + 24, 48)
  for (let h = windowForm.startHour + 1; h <= maxEnd; h++) list.push({ value: h, label: hourLabel(h) })
  return list
})

// 起点变化后保证终点仍在 [起点+1, 起点+24] 内
watch(() => windowForm.startHour, (start) => {
  if (windowForm.endHour <= start) windowForm.endHour = start + 1
  if (windowForm.endHour > start + 24) windowForm.endHour = start + 24
})

async function saveWindow() {
  windowSaving.value = true
  try {
    await bookingWindow.set(windowForm.startHour, windowForm.endHour)
    logApi(
      'PUT',
      '/api/admin/booking-window',
      `设置可预约时段 ${hourLabel(windowForm.startHour)} - ${hourLabel(windowForm.endHour)}`,
    )
    monitor.log(
      '设置可预约时段',
      `${hourLabel(windowForm.startHour)} 至 ${hourLabel(windowForm.endHour)}`,
      '管理员',
      'ADMIN',
    )
    ElMessage.success('可预约时段已更新，看板与预约校验即时生效')
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    windowSaving.value = false
  }
}

/* —— 分类规则管理：容量范围 / 审批要求 / 单次最长时长 / 可提前预约天数 —— */
const categoryDialogVisible = ref(false)
const categoryEditingId = ref<number | string | null>(null)
const categorySaving = ref(false)
const categoryFormRef = ref()
const categoryForm = reactive({
  name: '',
  minCapacity: 1,
  maxCapacity: 8,
  approvalRequired: false,
  maxDurationMinutes: 1440,
  advanceDays: 7,
  description: '',
})

/** 分钟数转可读文案：整小时用“小时”，否则保留“分钟” */
function formatDuration(minutes: number): string {
  if (minutes >= 60 && minutes % 60 === 0) return `${minutes / 60} 小时`
  return `${minutes} 分钟`
}

const categoryRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  maxDurationMinutes: [{ required: true, message: '请设置单次最长时长', trigger: 'change' }],
  advanceDays: [{ required: true, message: '请设置可提前预约天数', trigger: 'change' }],
}

function openCategoryCreate() {
  categoryEditingId.value = null
  categoryForm.name = ''
  categoryForm.minCapacity = 1
  categoryForm.maxCapacity = 8
  categoryForm.approvalRequired = false
  categoryForm.maxDurationMinutes = 1440
  categoryForm.advanceDays = 7
  categoryForm.description = ''
  categoryDialogVisible.value = true
}

function openCategoryEdit(category: CategoryResponse) {
  categoryEditingId.value = category.id
  categoryForm.name = category.name
  categoryForm.minCapacity = category.minCapacity
  categoryForm.maxCapacity = category.maxCapacity
  categoryForm.approvalRequired = category.approvalRequired
  categoryForm.maxDurationMinutes = category.maxDurationMinutes
  categoryForm.advanceDays = category.advanceDays
  categoryForm.description = category.description ?? ''
  categoryDialogVisible.value = true
}

async function handleCategorySave() {
  const valid = await categoryFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (categoryForm.minCapacity > categoryForm.maxCapacity) {
    ElMessage.error('容量下限不能大于容量上限')
    return
  }
  categorySaving.value = true
  try {
    const payload = {
      name: categoryForm.name.trim(),
      minCapacity: categoryForm.minCapacity,
      maxCapacity: categoryForm.maxCapacity,
      approvalRequired: categoryForm.approvalRequired,
      maxDurationMinutes: categoryForm.maxDurationMinutes,
      advanceDays: categoryForm.advanceDays,
      description: categoryForm.description.trim() || null,
    }
    if (categoryEditingId.value === null) {
      await adminCategoriesApi.create(payload)
      logApi('POST', '/api/admin/room-categories', `新增分类 ${payload.name}`)
      monitor.log(
        '新增会议分类',
        `${payload.name} · 单次最长 ${formatDuration(payload.maxDurationMinutes)}`,
        '管理员',
        'ADMIN',
      )
      ElMessage.success(`分类 ${payload.name} 已创建`)
    } else {
      await adminCategoriesApi.update(categoryEditingId.value, payload)
      logApi('PUT', `/api/admin/room-categories/${categoryEditingId.value}`, `更新分类 ${payload.name}`)
      monitor.log(
        '编辑会议分类',
        `${payload.name} · 单次最长 ${formatDuration(payload.maxDurationMinutes)}`,
        '管理员',
        'ADMIN',
      )
      ElMessage.success('分类规则已更新，看板与预约校验即时生效')
    }
    categories.value = await adminCategoriesApi.list()
    categoryDialogVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    categorySaving.value = false
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
    logApi('POST', `/api/admin/rooms/${maintenanceRoom.value.id}/maintenance`, `登记维护 ${maintenanceRoom.value.name}`)
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

    <div class="panel window-panel">
      <div class="window-info">
        <h3 class="window-title">全局可预约时段</h3>
        <p class="muted">对所有会议室生效；结束时间可选到次日，如 08:00 至 次日 08:00 即全天 24 小时可预约</p>
      </div>
      <div class="window-controls">
        <el-select v-model="windowForm.startHour" style="width: 140px">
          <el-option
            v-for="opt in windowStartOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
        <span class="rule-sep">至</span>
        <el-select v-model="windowForm.endHour" style="width: 140px">
          <el-option
            v-for="opt in windowEndOptions"
            :key="opt.value"
            :value="opt.value"
            :label="opt.label"
          />
        </el-select>
        <el-button type="primary" :loading="windowSaving" @click="saveWindow">保存</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="category-head">
        <div>
          <h3 class="window-title">会议室分类规则</h3>
          <p class="muted">
            决定各分类的容量范围、审批要求、单次最长时长与可提前预约天数；单次最长可设 1440 分钟（24 小时），一天之内任意时长均可预约
          </p>
        </div>
        <el-button type="primary" plain @click="openCategoryCreate">+ 新增分类</el-button>
      </div>
      <el-table :data="categories" size="small" style="width: 100%">
        <el-table-column prop="name" label="分类" width="120" />
        <el-table-column label="容量范围" width="110">
          <template #default="{ row }">{{ row.minCapacity }}~{{ row.maxCapacity }} 人</template>
        </el-table-column>
        <el-table-column label="审批" width="90">
          <template #default="{ row }">
            <el-tag :type="row.approvalRequired ? 'warning' : 'success'" size="small" effect="light">
              {{ row.approvalRequired ? '需审批' : '免审批' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单次最长时长" width="120">
          <template #default="{ row }">{{ formatDuration(row.maxDurationMinutes) }}</template>
        </el-table-column>
        <el-table-column label="可提前预约" width="110">
          <template #default="{ row }">{{ row.advanceDays }} 天</template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openCategoryEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-table-column prop="category" label="分类" width="110">
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
        <el-table-column label="操作" :width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button link type="primary" size="small" @click="openFacilities(row)">设施</el-button>
            <el-button link type="primary" size="small" @click="openMaintenance(row)">维护</el-button>
            <el-dropdown class="status-dropdown" @command="(cmd: RoomFlag) => handleStatus(row, cmd)">
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
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
          <span v-if="selectedCategory" class="field-hint">
            容量范围 {{ selectedCategory.minCapacity }}~{{ selectedCategory.maxCapacity }} ·
            单次最长 {{ formatDuration(selectedCategory.maxDurationMinutes) }}
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
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="categoryDialogVisible"
      :title="categoryEditingId === null ? '新增会议分类' : `编辑会议分类：${categoryForm.name}`"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="categoryFormRef"
        :model="categoryForm"
        :rules="categoryRules"
        label-width="110px"
        label-position="left"
      >
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="categoryForm.name" placeholder="例如：中型会议室" maxlength="20" />
        </el-form-item>
        <el-form-item label="容量范围" required>
          <div class="capacity-range-row">
            <el-input-number v-model="categoryForm.minCapacity" :min="1" :max="999" controls-position="right" />
            <span class="rule-sep">至</span>
            <el-input-number v-model="categoryForm.maxCapacity" :min="1" :max="999" controls-position="right" />
            <span class="capacity-unit">人</span>
          </div>
        </el-form-item>
        <el-form-item label="审批要求">
          <el-switch
            v-model="categoryForm.approvalRequired"
            active-text="需审批"
            inactive-text="免审批"
          />
        </el-form-item>
        <el-form-item label="单次最长时长" prop="maxDurationMinutes">
          <div class="duration-row">
            <el-input-number
              v-model="categoryForm.maxDurationMinutes"
              :min="30"
              :max="1440"
              :step="30"
              controls-position="right"
              style="width: 140px"
            />
            <span class="capacity-unit">分钟</span>
          </div>
          <span class="field-hint">一天之内任意时长均可预约；上限 1440 分钟即 24 小时</span>
        </el-form-item>
        <el-form-item label="可提前预约" prop="advanceDays">
          <div class="duration-row">
            <el-input-number
              v-model="categoryForm.advanceDays"
              :min="0"
              :max="365"
              controls-position="right"
              style="width: 140px"
            />
            <span class="capacity-unit">天</span>
          </div>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="categoryForm.description" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="handleCategorySave">保存</el-button>
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
/* 页面内多张卡片纵向堆叠，统一留出呼吸间距 */
.room-admin {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

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

.category-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.capacity-range-row,
.duration-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.window-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  flex-wrap: wrap;
}

.window-title {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 600;
}

.window-info .muted {
  margin: 0;
}

.window-controls {
  display: flex;
  align-items: center;
  gap: 8px;
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
