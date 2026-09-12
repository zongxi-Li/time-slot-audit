<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  Tickets,
  OfficeBuilding,
  Odometer,
  List,
  Management,
  Monitor,
  SwitchButton,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { useMock } from '@/shared/api/config'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const monitor = useMonitorStore()

const userMenus = [
  { path: '/board', label: '预约看板', icon: Calendar },
  { path: '/my', label: '我的预约', icon: Tickets },
  { path: '/rooms', label: '会议室', icon: OfficeBuilding },
]

const adminMenus = [
  { path: '/admin/dashboard', label: '管理控制台', icon: Odometer },
  { path: '/admin/bookings', label: '预约管理', icon: List },
  { path: '/admin/rooms', label: '会议室管理', icon: Management },
  { path: '/admin/monitor', label: '系统监控', icon: Monitor },
]

const showAdminMenus = computed(() => auth.isAdmin)

onMounted(() => void auth.initialize())

function onUserCommand(command: string) {
  if (command === 'switch-role') {
    const next = auth.switchRole()
    monitor.log(
      '切换视角',
      next === 'ADMIN' ? '切换到管理员视角' : '切换到普通用户视角',
      auth.currentUser.name,
      next,
    )
    ElMessage.success(next === 'ADMIN' ? '已切换到管理员视角' : '已切换到普通用户视角')
    if (route.path.startsWith('/admin') && next !== 'ADMIN') {
      router.push('/board')
    }
  } else if (command === 'logout') {
    auth.logout()
    void router.push('/login')
    ElMessage.success(useMock ? '已退出 Demo 视角' : '已退出登录')
  }
}
</script>

<template>
  <el-container class="layout">
    <el-header class="layout-header" height="56px">
      <div class="brand">
        <div class="brand-logo">智</div>
        <span class="brand-name">智会会议室预约系统</span>
      </div>
      <el-dropdown trigger="click" @command="onUserCommand">
        <div class="user-chip">
          <div class="user-avatar" :class="{ admin: auth.isAdmin }">
            {{ auth.currentUser.name.charAt(0) }}
          </div>
          <span class="user-name">{{ auth.currentUser.name }}</span>
          <el-tag
            :type="auth.isAdmin ? 'warning' : 'primary'"
            size="small"
            effect="light"
            class="role-tag"
          >
            {{ auth.isAdmin ? '管理员' : '普通用户' }}
          </el-tag>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="useMock" command="switch-role" :icon="auth.isAdmin ? UserFilled : SwitchButton">
              {{ auth.isAdmin ? '切换到普通用户视角' : '切换到管理员视角' }}
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>

    <el-container class="layout-body">
      <el-aside width="200px" class="layout-aside">
        <el-menu :default-active="route.path" router class="aside-menu">
          <el-menu-item v-for="item in userMenus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>

          <template v-if="showAdminMenus">
            <div class="menu-group-label">系统管理</div>
            <el-menu-item v-for="item in adminMenus" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--card-bg);
  border-bottom: 1px solid var(--border-color);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 8px;
  cursor: pointer;
  outline: none;
}

.user-chip:hover {
  background: var(--page-bg);
}

.user-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #dce6fb;
  color: #2a5acc;
  font-size: 13px;
  font-weight: 600;
}

.user-avatar.admin {
  background: #fdecd2;
  color: #b25e02;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
}

.role-tag {
  border-radius: 5px;
}

.layout-body {
  height: calc(100% - 56px);
}

.layout-aside {
  background: var(--card-bg);
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
}

.aside-menu {
  border-right: none;
  padding: 8px;
}

.aside-menu .el-menu-item {
  height: 42px;
  margin: 2px 0;
  border-radius: 8px;
  color: var(--text-secondary);
}

.aside-menu .el-menu-item:hover {
  background: var(--page-bg);
}

.aside-menu .el-menu-item.is-active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 500;
}

.menu-group-label {
  margin: 14px 0 4px;
  padding: 0 12px;
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.5px;
}

.layout-main {
  padding: 0;
  overflow: hidden;
}
</style>
