<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useMeetingRoomStore } from '@/stores/meetingRoom'
import { useMonitorStore } from '@/stores/monitor'
import type { MeetingRoom } from '@/types'

const roomStore = useMeetingRoomStore()
const monitor = useMonitorStore()

const EQUIPMENT_OPTIONS = ['投影仪', '白板', '电视屏', '视频会议', '麦克风']

/* —— 新增 / 编辑弹窗 —— */
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const formRef = ref()
const form = reactive({
  name: '',
  location: '',
  capacity: 8,
  equipment: [] as string[],
})

const rules = {
  name: [
    { required: true, message: '请输入会议室名称', trigger: 'blur' },
    {
      validator: (_r: unknown, value: string, cb: (err?: Error) => void) => {
        const exists = roomStore.rooms.some((r) => r.name === value.trim())
        if (editingId.value === null && exists) cb(new Error('已存在同名会议室'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容量', trigger: 'change' }],
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.location = ''
  form.capacity = 8
  form.equipment = []
  dialogVisible.value = true
}

function openEdit(room: MeetingRoom) {
  editingId.value = room.id
  form.name = room.name
  form.location = room.location
  form.capacity = room.capacity
  form.equipment = [...room.equipment]
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const payload = {
    name: form.name.trim(),
    location: form.location.trim(),
    capacity: form.capacity,
    equipment: [...form.equipment],
  }

  if (editingId.value === null) {
    const room = roomStore.addRoom(payload)
    if (!room) {
      ElMessage.error('已存在同名会议室')
      return
    }
    monitor.log('新增会议室', `${room.name} · ${room.location} · 容量 ${room.capacity} 人`, '王建国', 'ADMIN')
    monitor.pushFeed({ method: 'POST', path: '/api/rooms', status: 201, user: '管理员', note: `新增会议室 ${room.name}` })
    ElMessage.success(`会议室 ${room.name} 已创建`)
  } else {
    roomStore.updateRoom(editingId.value, payload)
    monitor.log('编辑会议室', `${payload.name} · ${payload.location} · 容量 ${payload.capacity} 人`, '王建国', 'ADMIN')
    monitor.pushFeed({ method: 'PUT', path: `/api/rooms/${editingId.value}`, status: 200, user: '管理员', note: `更新会议室 ${payload.name}` })
    ElMessage.success('会议室信息已更新')
  }
  dialogVisible.value = false
}

function toggleStatus(room: MeetingRoom) {
  const next = roomStore.toggleRoomStatus(room.id)
  if (!next) return
  monitor.log(
    next === 'AVAILABLE' ? '启用会议室' : '停用会议室',
    `${room.name} · ${room.location}${next !== 'AVAILABLE' ? '（停用后不可新建预约，已有预约保留）' : ''}`,
    '王建国',
    'ADMIN',
  )
  monitor.pushFeed({
    method: 'PUT',
    path: `/api/rooms/${room.id}/status`,
    status: 200,
    user: '管理员',
    note: `${next === 'AVAILABLE' ? '启用' : '停用'} ${room.name}`,
  })
  ElMessage.success(next === 'AVAILABLE' ? `${room.name} 已启用` : `${room.name} 已停用`)
}
</script>

<template>
  <div class="page room-admin">
    <div class="head-row">
      <div>
        <h2 class="page-title">会议室管理</h2>
        <p class="page-subtitle">新增或编辑会议室信息；停用后该会议室不可被新预约选中，已有预约保留展示</p>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增会议室</el-button>
    </div>

    <div class="panel table-panel">
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
        <el-table-column label="设备" min-width="220">
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
            <el-tag :type="row.status === 'AVAILABLE' ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 'AVAILABLE' ? '启用中' : '不可预约' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'AVAILABLE' ? 'danger' : 'success'"
              size="small"
              @click="toggleStatus(row)"
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
          <el-input v-model="form.name" :disabled="editingId !== null" placeholder="例如：C305" maxlength="10" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="例如：第三教学楼 3F" maxlength="20" />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="2" :max="200" />
          <span class="capacity-unit">人</span>
        </el-form-item>
        <el-form-item label="设备">
          <el-checkbox-group v-model="form.equipment">
            <el-checkbox v-for="eq in EQUIPMENT_OPTIONS" :key="eq" :value="eq" :label="eq">
              {{ eq }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
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
</style>
