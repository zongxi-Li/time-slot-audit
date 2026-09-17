<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserAdminStore } from '@/stores/userAdmin'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { useMock } from '@/shared/api/config'
import { violationLabel, violationTagType } from '@/utils/violation'
import { formatDateTime } from '@/utils/datetime'
import type { AdminUserResponse } from '@/shared/api'
import type { Role } from '@/types'

const userAdmin = useUserAdminStore()
const auth = useAuthStore()
const monitor = useMonitorStore()

/** 信用规则（与后端 CreditRules 保持一致）：60=预约门槛，40=自动限制阈值 */
const MIN_BOOKING_CREDIT = 60
const AUTO_BLACKLIST_CREDIT = 40

/* —— 列表查询 —— */
const filters = reactive({ keyword: '', status: '' as '' | '0' | '1' })

async function refresh() {
  if (useMock) return
  const params: { keyword?: string; status?: number } = {}
  if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
  if (filters.status !== '') params.status = Number(filters.status)
  userAdmin.lastQuery = params
  await userAdmin.fetchUsers(params).catch((e: Error) => ElMessage.error(e.message))
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  void refresh()
}

onMounted(() => {
  void refresh()
  if (!useMock) void userAdmin.loadDepartments().catch(() => undefined)
})

function onError(e: unknown) {
  ElMessage.error(e instanceof Error ? e.message : '操作失败')
}

/* —— 展示辅助 —— */
function isRestricted(user: AdminUserResponse) {
  return !!user.restrictedUntil && new Date(user.restrictedUntil).getTime() > Date.now()
}

function creditTag(score: number) {
  if (score < AUTO_BLACKLIST_CREDIT) return 'danger'
  if (score < MIN_BOOKING_CREDIT) return 'warning'
  return 'success'
}

function qualificationOf(user: AdminUserResponse) {
  if (user.status === 0) return { text: '已禁用', type: 'danger' as const }
  if (isRestricted(user)) return { text: '限制中', type: 'danger' as const }
  if (user.creditScore < MIN_BOOKING_CREDIT) return { text: '信用不足', type: 'warning' as const }
  return { text: '可预约', type: 'success' as const }
}

function isSelf(user: AdminUserResponse) {
  return String(user.id) === auth.currentUser.id
}

/* —— 新增 / 编辑用户 —— */
const userDialogVisible = ref(false)
const editingUser = ref<AdminUserResponse | null>(null)
const userFormRef = ref()
const userForm = reactive({
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  role: 'USER' as Role,
  departmentId: null as number | string | null,
})

const userRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度需在 2-50 之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度需在 6-100 之间', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function openCreateUser() {
  editingUser.value = null
  Object.assign(userForm, { username: '', password: '', realName: '', email: '', phone: '', role: 'USER', departmentId: null })
  userDialogVisible.value = true
}

function openEditUser(user: AdminUserResponse) {
  editingUser.value = user
  Object.assign(userForm, {
    username: user.username,
    password: '',
    realName: user.realName,
    email: user.email ?? '',
    phone: user.phone ?? '',
    role: user.role,
    departmentId: user.departmentId ?? null,
  })
  userDialogVisible.value = true
}

async function handleUserSave() {
  const valid = await userFormRef.value?.validate().catch(() => false)
  if (!valid) return

  const departmentId = userForm.departmentId === '' ? null : userForm.departmentId
  if (editingUser.value) {
    await userAdmin
      .updateUser(editingUser.value.id, {
        realName: userForm.realName.trim(),
        email: userForm.email.trim() || undefined,
        phone: userForm.phone.trim() || undefined,
        departmentId,
        role: isSelf(editingUser.value) ? undefined : userForm.role,
      })
      .then((updated) => {
        monitor.log('编辑用户', `${updated.username}（${updated.realName}）资料已更新`, auth.currentUser.name, 'ADMIN')
        monitor.pushFeed({ method: 'PUT', path: `/api/admin/users/${updated.id}`, status: 200, user: '管理员', note: `更新用户 ${updated.username}` })
        ElMessage.success('用户资料已更新')
        userDialogVisible.value = false
      })
      .catch(onError)
  } else {
    await userAdmin
      .createUser({
        username: userForm.username.trim(),
        password: userForm.password,
        realName: userForm.realName.trim(),
        email: userForm.email.trim() || undefined,
        phone: userForm.phone.trim() || undefined,
        role: userForm.role,
        departmentId,
      })
      .then((created) => {
        monitor.log('新增用户', `${created.username}（${created.realName}），初始信用分 ${created.creditScore}`, auth.currentUser.name, 'ADMIN')
        monitor.pushFeed({ method: 'POST', path: '/api/admin/users', status: 200, user: '管理员', note: `新增用户 ${created.username}` })
        ElMessage.success(`用户 ${created.username} 已创建`)
        userDialogVisible.value = false
      })
      .catch(onError)
  }
}

/* —— 启用 / 禁用 —— */
async function toggleStatus(user: AdminUserResponse) {
  const disabling = user.status === 1
  const action = disabling ? '禁用' : '启用'
  try {
    const { value } = await ElMessageBox.prompt(
      disabling ? `将禁用 ${user.realName}（${user.username}）的账号，禁用后无法登录与预约。可填写原因：` : `将启用 ${user.realName}（${user.username}）的账号，可填写备注：`,
      `${action}账号`,
      { confirmButtonText: action, cancelButtonText: '取消', inputPlaceholder: disabling ? '例如：多次违约未整改' : '选填' },
    )
    await userAdmin
      .updateStatus(user.id, disabling ? 0 : 1, value?.trim() || undefined)
      .then((updated) => {
        monitor.log(`${action}账号`, `${updated.username}（${updated.realName}）`, auth.currentUser.name, 'ADMIN')
        monitor.pushFeed({ method: 'PUT', path: `/api/admin/users/${user.id}/status`, status: 200, user: '管理员', note: `${action} ${updated.username}` })
        ElMessage.success(`${updated.realName} 已${action}`)
      })
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') onError(e)
  }
}

/* —— 重置密码 —— */
const passwordDialogVisible = ref(false)
const passwordUser = ref<AdminUserResponse | null>(null)
const newPassword = ref('')

function openResetPassword(user: AdminUserResponse) {
  passwordUser.value = user
  newPassword.value = ''
  passwordDialogVisible.value = true
}

async function handleResetPassword() {
  if (newPassword.value.length < 6) {
    ElMessage.warning('新密码长度需在 6-100 之间')
    return
  }
  if (!passwordUser.value) return
  await userAdmin
    .resetPassword(passwordUser.value.id, newPassword.value)
    .then(() => {
      monitor.log('重置密码', `${passwordUser.value?.username}`, auth.currentUser.name, 'ADMIN')
      monitor.pushFeed({ method: 'PUT', path: `/api/admin/users/${passwordUser.value?.id}/password`, status: 200, user: '管理员', note: `重置 ${passwordUser.value?.username} 密码` })
      ElMessage.success('密码已重置')
      passwordDialogVisible.value = false
    })
    .catch(onError)
}

/* —— 信用调整 —— */
const creditDialogVisible = ref(false)
const creditUser = ref<AdminUserResponse | null>(null)
const creditForm = reactive({ creditChange: -10, reason: '' })

const creditPreview = computed(() => {
  if (!creditUser.value) return 0
  return Math.max(0, creditUser.value.creditScore + creditForm.creditChange)
})

function openCredit(user: AdminUserResponse) {
  creditUser.value = user
  creditForm.creditChange = -10
  creditForm.reason = ''
  creditDialogVisible.value = true
}

async function handleCreditSave() {
  if (creditForm.creditChange === 0) {
    ElMessage.warning('信用分变化量不能为 0')
    return
  }
  if (!creditForm.reason.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  if (!creditUser.value) return
  await userAdmin
    .adjustCredit(creditUser.value.id, creditForm.creditChange, creditForm.reason.trim())
    .then((updated) => {
      monitor.log('调整信用分', `${updated.username} ${creditForm.creditChange > 0 ? '+' : ''}${creditForm.creditChange} → ${updated.creditScore} 分`, auth.currentUser.name, 'ADMIN')
      monitor.pushFeed({ method: 'PUT', path: `/api/admin/users/${updated.id}/credit`, status: 200, user: '管理员', note: `${updated.username} 信用分调整为 ${updated.creditScore}` })
      ElMessage.success(`${updated.realName} 信用分已调整为 ${updated.creditScore}`)
      creditDialogVisible.value = false
    })
    .catch(onError)
}

/* —— 黑名单 / 限制 —— */
const restrictDialogVisible = ref(false)
const restrictUser = ref<AdminUserResponse | null>(null)
const restrictMode = ref<'set' | 'release'>('set')
const restrictForm = reactive({ restrictedUntil: '', reason: '' })

function openRestrict(user: AdminUserResponse) {
  restrictUser.value = user
  restrictMode.value = isRestricted(user) ? 'release' : 'set'
  restrictForm.restrictedUntil = ''
  restrictForm.reason = ''
  restrictDialogVisible.value = true
}

async function handleRestrictSave() {
  if (!restrictUser.value) return
  if (!restrictForm.reason.trim()) {
    ElMessage.warning('请填写操作原因')
    return
  }
  const reason = restrictForm.reason.trim()
  if (restrictMode.value === 'set') {
    if (!restrictForm.restrictedUntil) {
      ElMessage.warning('请选择限制截止时间')
      return
    }
    await userAdmin
      .setRestriction(restrictUser.value.id, reason, restrictForm.restrictedUntil)
      .then((updated) => {
        monitor.log('设置限制', `${updated.username} 限制至 ${formatDateTime(updated.restrictedUntil)}`, auth.currentUser.name, 'ADMIN')
        monitor.pushFeed({ method: 'PUT', path: `/api/admin/users/${updated.id}/restriction`, status: 200, user: '管理员', note: `${updated.username} 进入限制期` })
        ElMessage.success(`${updated.realName} 已被限制预约至 ${formatDateTime(updated.restrictedUntil)}`)
        restrictDialogVisible.value = false
      })
      .catch(onError)
  } else {
    await userAdmin
      .setRestriction(restrictUser.value.id, reason)
      .then((updated) => {
        monitor.log('解除限制', `${updated.username}`, auth.currentUser.name, 'ADMIN')
        monitor.pushFeed({ method: 'PUT', path: `/api/admin/users/${updated.id}/restriction`, status: 200, user: '管理员', note: `${updated.username} 解除限制` })
        ElMessage.success(`${updated.realName} 的预约限制已解除`)
        restrictDialogVisible.value = false
      })
      .catch(onError)
  }
}

/* —— 违规 / 信用记录 —— */
const violationsDrawerVisible = ref(false)
const violationsUser = ref<AdminUserResponse | null>(null)
const violationsList = ref<Awaited<ReturnType<typeof userAdmin.fetchViolations>>>([])
const violationsLoading = ref(false)

async function openViolations(user: AdminUserResponse) {
  violationsUser.value = user
  violationsDrawerVisible.value = true
  violationsLoading.value = true
  try {
    violationsList.value = await userAdmin.fetchViolations(user.id)
  } catch (e) {
    onError(e)
  } finally {
    violationsLoading.value = false
  }
}

/* —— 部门管理 —— */
const deptDialogVisible = ref(false)
const deptForm = reactive({ deptName: '', description: '' })

function openDepartments() {
  deptForm.deptName = ''
  deptForm.description = ''
  deptDialogVisible.value = true
}

async function handleDeptCreate() {
  if (!deptForm.deptName.trim()) {
    ElMessage.warning('请输入部门名称')
    return
  }
  await userAdmin
    .createDepartment({ deptName: deptForm.deptName.trim(), description: deptForm.description.trim() || undefined })
    .then(() => {
      ElMessage.success(`部门 ${deptForm.deptName.trim()} 已创建`)
      deptForm.deptName = ''
      deptForm.description = ''
    })
    .catch(onError)
}

async function renameDept(id: number | string, currentName: string) {
  try {
    const { value } = await ElMessageBox.prompt('修改部门名称', '编辑部门', {
      inputValue: currentName,
      inputPattern: /\S+/,
      inputErrorMessage: '部门名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    await userAdmin.updateDepartment(id, { deptName: value.trim() }).then(() => ElMessage.success('部门已更新'))
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') onError(e)
  }
}

/* —— 行内下拉命令 —— */
function onRowCommand(command: string, user: AdminUserResponse) {
  if (command === 'password') openResetPassword(user)
  else if (command === 'status') void toggleStatus(user)
}
</script>

<template>
  <div class="page user-admin">
    <div class="head-row">
      <div>
        <h2 class="page-title">用户与信用管理</h2>
        <p class="page-subtitle">
          信用分低于 {{ MIN_BOOKING_CREDIT }} 分无法预约；低于 {{ AUTO_BLACKLIST_CREDIT }} 分系统自动限制 30 天，
          信用恢复后自动解除；人工限制只能人工解除，所有操作均记录原因
        </p>
      </div>
      <div class="head-actions">
        <el-button @click="openDepartments">部门管理</el-button>
        <el-button type="primary" @click="openCreateUser">+ 新增用户</el-button>
      </div>
    </div>

    <el-alert
      v-if="useMock"
      class="demo-alert"
      title="当前为 Demo 模式（无后端），用户与信用管理需要连接 Spring Boot 后端使用"
      type="info"
      show-icon
      :closable="false"
    />

    <div class="panel toolbar-panel">
      <el-input
        v-model="filters.keyword"
        class="search-input"
        placeholder="搜索用户名 / 姓名"
        clearable
        @keyup.enter="refresh"
        @clear="refresh"
      />
      <el-select v-model="filters.status" class="status-select" @change="refresh">
        <el-option label="全部状态" value="" />
        <el-option label="正常" value="1" />
        <el-option label="禁用" value="0" />
      </el-select>
      <el-button type="primary" plain @click="refresh">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <div class="panel table-panel">
      <el-table v-loading="userAdmin.loading" :data="userAdmin.users" style="width: 100%">
        <el-table-column prop="username" label="用户名" min-width="110">
          <template #default="{ row }">
            <span class="username-cell">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" min-width="90" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'primary'" size="small" effect="light">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门" min-width="110">
          <template #default="{ row }">{{ row.departmentName || '未分配' }}</template>
        </el-table-column>
        <el-table-column label="信用分" width="100">
          <template #default="{ row }">
            <el-tag :type="creditTag(row.creditScore)" size="small" effect="light">{{ row.creditScore }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预约资格" width="100">
          <template #default="{ row }">
            <el-tag :type="qualificationOf(row).type" size="small" effect="light">
              {{ qualificationOf(row).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="限制截止" min-width="130">
          <template #default="{ row }">{{ isRestricted(row) ? formatDateTime(row.restrictedUntil) : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openCredit(row)">信用</el-button>
            <el-button link :type="isRestricted(row) ? 'success' : 'danger'" size="small" @click="openRestrict(row)">
              {{ isRestricted(row) ? '解除限制' : '限制' }}
            </el-button>
            <el-button link type="primary" size="small" @click="openViolations(row)">记录</el-button>
            <el-button link type="primary" size="small" @click="openEditUser(row)">编辑</el-button>
            <el-dropdown class="row-more" trigger="click" @command="(cmd: string) => onRowCommand(cmd, row)">
              <el-button link type="primary" size="small">
                更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="password">重置密码</el-dropdown-item>
                  <el-dropdown-item command="status" :disabled="isSelf(row)">
                    {{ row.status === 1 ? '禁用账号' : '启用账号' }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" :image-size="80" />
        </template>
      </el-table>
    </div>

    <!-- 新增 / 编辑用户 -->
    <el-dialog
      v-model="userDialogVisible"
      :title="editingUser ? `编辑用户：${editingUser.realName}` : '新增用户'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="80px" label-position="left">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="!!editingUser" placeholder="登录用户名" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="初始密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password placeholder="至少 6 位" maxlength="100" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="userForm.realName" maxlength="50" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" maxlength="100" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" :disabled="editingUser ? isSelf(editingUser) : false">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="userForm.departmentId" clearable placeholder="未分配" style="width: 100%">
            <el-option
              v-for="dept in userAdmin.departments"
              :key="dept.id"
              :label="dept.deptName"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUserSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 信用调整 -->
    <el-dialog v-model="creditDialogVisible" width="440px" :close-on-click-modal="false">
      <template #header>
        <span>调整信用分：{{ creditUser?.realName }}（当前 {{ creditUser?.creditScore }} 分）</span>
      </template>
      <el-form label-width="80px" label-position="left">
        <el-form-item label="变化量">
          <div class="credit-input-row">
            <el-input-number v-model="creditForm.creditChange" :step="5" :min="-100" :max="100" />
            <div class="credit-quick">
              <el-button size="small" type="success" plain @click="creditForm.creditChange = 10">+10</el-button>
              <el-button size="small" type="success" plain @click="creditForm.creditChange = 20">+20</el-button>
              <el-button size="small" type="danger" plain @click="creditForm.creditChange = -10">-10</el-button>
              <el-button size="small" type="danger" plain @click="creditForm.creditChange = -20">-20</el-button>
            </div>
          </div>
          <div class="credit-preview">
            调整后：<b>{{ creditPreview }}</b> 分
            <el-tag v-if="creditUser" :type="creditTag(creditPreview)" size="small" effect="light" class="credit-preview-tag">
              {{ creditPreview < AUTO_BLACKLIST_CREDIT ? '将触发自动限制' : creditPreview < MIN_BOOKING_CREDIT ? '低于预约门槛' : '可正常预约' }}
            </el-tag>
          </div>
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input
            v-model="creditForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="必填，将记入违规/信用记录（例如：会议无故缺席）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="creditDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreditSave">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 设置 / 解除限制 -->
    <el-dialog v-model="restrictDialogVisible" width="440px" :close-on-click-modal="false">
      <template #header>
        <span>预约限制：{{ restrictUser?.realName }}</span>
      </template>
      <el-radio-group v-model="restrictMode" class="restrict-mode">
        <el-radio-button value="set">设置限制</el-radio-button>
        <el-radio-button value="release">解除限制</el-radio-button>
      </el-radio-group>
      <el-form v-if="restrictMode === 'set'" label-width="90px" label-position="left" class="restrict-form">
        <el-form-item label="限制截止" required>
          <el-date-picker
            v-model="restrictForm.restrictedUntil"
            type="datetime"
            placeholder="选择解除限制的时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled-date="(d: Date) => d.getTime() < Date.now() - 86400000"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <el-form label-width="90px" label-position="left">
        <el-form-item label="操作原因" required>
          <el-input
            v-model="restrictForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="restrictMode === 'set' ? '必填，例如：多次预约后无故缺席' : '必填，例如：已完成整改，予以解除'"
          />
        </el-form-item>
      </el-form>
      <p class="restrict-note">人工设置的限制不会随信用分恢复自动解除，需在此人工解除。</p>
      <template #footer>
        <el-button @click="restrictDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRestrictSave">
          {{ restrictMode === 'set' ? '确认限制' : '确认解除' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 违规 / 信用记录 -->
    <el-drawer v-model="violationsDrawerVisible" size="560px">
      <template #header>
        <span>违规与信用记录：{{ violationsUser?.realName }}（{{ violationsUser?.username }}）</span>
      </template>
      <el-table v-loading="violationsLoading" :data="violationsList" style="width: 100%">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="violationTagType(row.violationType)" size="small" effect="light">
              {{ violationLabel(row.violationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="信用变化" width="90">
          <template #default="{ row }">
            <span v-if="row.creditChange" :class="row.creditChange > 0 ? 'delta-up' : 'delta-down'">
              {{ row.creditChange > 0 ? `+${row.creditChange}` : row.creditChange }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" />
        <el-table-column label="操作人" width="90">
          <template #default="{ row }">
            {{ row.operatorName || '系统自动' }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="140">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无违规/信用记录" :image-size="80" />
        </template>
      </el-table>
    </el-drawer>

    <!-- 重置密码 -->
    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="400px" :close-on-click-modal="false">
      <el-form label-width="80px" label-position="left">
        <el-form-item label="用户">
          <span>{{ passwordUser?.realName }}（{{ passwordUser?.username }}）</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>

    <!-- 部门管理 -->
    <el-dialog v-model="deptDialogVisible" title="部门管理" width="520px" :close-on-click-modal="false">
      <div class="dept-create">
        <el-input v-model="deptForm.deptName" placeholder="部门名称" maxlength="50" class="dept-name-input" />
        <el-input v-model="deptForm.description" placeholder="部门说明（选填）" maxlength="200" class="dept-desc-input" />
        <el-button type="primary" plain @click="handleDeptCreate">新增</el-button>
      </div>
      <el-table :data="userAdmin.departments" style="width: 100%" max-height="320">
        <el-table-column prop="deptName" label="部门名称" min-width="120" />
        <el-table-column prop="description" label="说明" min-width="180">
          <template #default="{ row }">{{ row.description || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="renameDept(row.id, row.deptName)">改名</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无部门" :image-size="60" />
        </template>
      </el-table>
      <p class="dept-note">部门被用户挂靠后不提供删除，仅支持改名与说明维护。</p>
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

.head-actions {
  display: flex;
  flex: none;
  gap: 10px;
}

.demo-alert {
  margin-top: 12px;
}

.toolbar-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 14px 16px;
}

.search-input {
  width: 240px;
}

.status-select {
  width: 120px;
}

.table-panel {
  margin-top: 14px;
  padding: 16px;
}

.username-cell {
  font-weight: 600;
}

.row-more {
  margin-left: 12px;
  vertical-align: middle;
}

.credit-input-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.credit-quick {
  display: flex;
  gap: 0;
}

.credit-preview {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.credit-preview-tag {
  margin-left: 8px;
}

.restrict-mode {
  margin-bottom: 16px;
}

.restrict-form {
  margin-bottom: 4px;
}

.restrict-note {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.delta-up {
  color: var(--el-color-success);
  font-weight: 600;
}

.delta-down {
  color: var(--el-color-danger);
  font-weight: 600;
}

.muted {
  font-size: 12px;
  color: var(--text-muted);
}

.dept-create {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.dept-name-input {
  width: 150px;
  flex: none;
}

.dept-desc-input {
  flex: 1;
}

.dept-note {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
