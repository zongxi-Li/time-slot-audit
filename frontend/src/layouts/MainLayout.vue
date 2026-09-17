<!--
  文件职责：TimeSlot 项目基础文件。
  接口：供对应工具链加载。
-->
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  DArrowLeft,
  DArrowRight,
  Tickets,
  OfficeBuilding,
  Odometer,
  List,
  Management,
  Monitor,
  SwitchButton,
  Tools,
  User,
  UserFilled,
  AlarmClock,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { useMock } from '@/shared/api/config'
import NotificationBell from '@/modules/meeting/components/NotificationBell.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const monitor = useMonitorStore()

const userMenus = [
  { path: '/board', label: '预约看板', icon: Calendar },
  { path: '/my', label: '我的预约', icon: Tickets },
  { path: '/meetings', label: '我的会议', icon: AlarmClock },
  { path: '/rooms', label: '会议室', icon: OfficeBuilding },
]

const adminMenus = [
  { path: '/admin/dashboard', label: '运营分析', icon: Odometer },
  { path: '/admin/bookings', label: '预约审批', icon: List },
  { path: '/admin/rooms', label: '会议室管理', icon: Management },
  { path: '/admin/users', label: '用户与信用管理', icon: User },
  { path: '/admin/repairs', label: '报修工单', icon: Tools },
  { path: '/admin/monitor', label: '操作审计', icon: Monitor },
]

const showAdminMenus = computed(() => auth.isAdmin)
const activityMenus = computed(() => (auth.isAdmin ? [...userMenus, ...adminMenus] : userMenus))
const currentMenu = computed(() => activityMenus.value.find((item) => item.path === route.path))
const isSidebarCollapsed = ref(false)

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
  <el-container class="layout" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
    <el-header class="layout-header" height="68px">
      <div class="brand">
        <div class="brand-logo">智</div>
        <div class="brand-copy">
          <span class="brand-name">智会会议室预约系统</span>
          <span class="brand-kicker">TIME / SLOT WORKSPACE</span>
        </div>
      </div>

      <div class="header-right">
        <NotificationBell />
        <el-dropdown trigger="click" @command="onUserCommand">
        <div class="user-chip">
          <div class="user-avatar" :class="{ admin: auth.isAdmin }">
            {{ auth.currentUser.name.charAt(0) }}
          </div>
          <div class="user-copy">
            <span class="user-name">{{ auth.currentUser.name }}</span>
            <span class="user-department">{{ auth.currentUser.department }}</span>
          </div>
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
            <el-dropdown-item
              v-if="useMock"
              command="switch-role"
              :icon="auth.isAdmin ? UserFilled : SwitchButton"
            >
              {{ auth.isAdmin ? '切换到普通用户视角' : '切换到管理员视角' }}
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      </div>
    </el-header>

    <el-container class="layout-body">
      <nav class="activity-bar" aria-label="主导航">
        <div class="activity-group">
          <button
            v-for="item in activityMenus"
            :key="item.path"
            type="button"
            class="activity-button"
            :class="{ active: route.path === item.path }"
            :aria-label="item.label"
            :title="item.label"
            @click="router.push(item.path)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
          </button>
        </div>
        <div class="activity-group activity-bottom">
          <button
            type="button"
            class="activity-button"
            :aria-label="isSidebarCollapsed ? '展开导航栏' : '收起导航栏'"
            :title="isSidebarCollapsed ? '展开导航栏' : '收起导航栏'"
            @click="isSidebarCollapsed = !isSidebarCollapsed"
          >
            <el-icon><component :is="isSidebarCollapsed ? DArrowRight : DArrowLeft" /></el-icon>
          </button>
        </div>
      </nav>

      <el-aside width="206px" class="layout-aside">
        <el-menu :default-active="route.path" router class="aside-menu">
          <div class="menu-group-label menu-group-heading">
            <span>工作台</span>
            <button
              type="button"
              class="aside-collapse-button"
              aria-label="收起导航面板"
              title="收起导航面板"
              @click="isSidebarCollapsed = true"
            >
              <el-icon><DArrowLeft /></el-icon>
            </button>
          </div>
          <el-menu-item v-for="item in userMenus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>

          <template v-if="showAdminMenus">
            <div class="menu-group-label admin-label">系统管理</div>
            <el-menu-item v-for="item in adminMenus" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <div class="workbench-shell">
          <div class="workbench-tabs" role="tablist" aria-label="工作区标签">
            <div class="workbench-tab is-active" role="tab" aria-selected="true">
              <el-icon><component :is="currentMenu ? currentMenu.icon : Calendar" /></el-icon>
              <span>{{ currentMenu?.label ?? '工作台' }}</span>
            </div>
            <div class="workbench-tab-spacer" />
            <span class="workbench-context">TIME / SLOT WORKSPACE</span>
          </div>

          <div class="workbench-content">
            <router-view />
          </div>

          <div class="workbench-statusbar">
            <span>TimeSlot</span>
            <span>{{ currentMenu?.label ?? '工作台' }}</span>
            <span class="statusbar-spacer" />
            <span>LOCAL WORKSPACE</span>
          </div>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
  background: transparent;
}

.layout-header {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px 0 28px;
  background: rgba(255, 255, 255, 0.78);
  border-bottom: 1px solid var(--border-light);
  -webkit-backdrop-filter: blur(24px);
  backdrop-filter: blur(24px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.brand-logo {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: 11px;
  color: #fff;
  background: linear-gradient(145deg, #1687f5, #0066cc);
  box-shadow: 0 6px 14px rgba(0, 113, 227, 0.23);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.08em;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.brand-name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: -0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-kicker {
  color: var(--text-muted);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.16em;
  line-height: 1;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 9px 5px 6px;
  border-radius: 14px;
  cursor: pointer;
  outline: none;
  transition: background-color 180ms ease;
}

.user-chip:hover {
  background: rgba(29, 29, 31, 0.055);
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #0062c4;
  background: #e4f1fd;
  font-size: 13px;
  font-weight: 700;
}

.user-avatar.admin {
  color: #a65f00;
  background: #fff0d9;
}

.user-copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 44px;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 16px;
}

.user-department {
  color: var(--text-muted);
  font-size: 10px;
  line-height: 13px;
}

.role-tag {
  border-radius: 999px;
  line-height: 20px;
}

.layout-body {
  height: calc(100% - 68px);
  min-height: 0;
}

.activity-bar {
  z-index: 6;
  display: flex;
  flex: 0 0 54px;
  flex-direction: column;
  align-items: center;
  padding: 12px 8px;
  background: rgba(239, 240, 242, 0.86);
  border-right: 1px solid var(--border-light);
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
}

.activity-group {
  display: flex;
  width: 100%;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.activity-bottom {
  margin-top: auto;
}

.activity-button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border: 0;
  border-radius: 10px;
  cursor: pointer;
  transition: color 180ms ease, background-color 180ms ease, box-shadow 180ms ease,
    transform 180ms ease;
}

.activity-button:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.76);
  transform: translateY(-1px);
}

.activity-button.active {
  color: var(--el-color-primary);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 4px 12px rgba(29, 29, 31, 0.08), inset 0 0 0 1px rgba(0, 113, 227, 0.1);
}

.activity-button .el-icon {
  font-size: 19px;
}

.layout-aside {
  z-index: 5;
  width: 206px !important;
  flex: 0 0 206px;
  background: rgba(255, 255, 255, 0.72);
  border-right: 1px solid var(--border-light);
  overflow-y: auto;
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
}

.menu-group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 3px;
}

.aside-collapse-button {
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
  transition: color 180ms ease, background-color 180ms ease;
}

.aside-collapse-button:hover {
  color: var(--el-color-primary);
  background: rgba(0, 113, 227, 0.08);
}

.aside-menu {
  border-right: none;
  padding: 18px 12px;
  background: transparent;
}

.aside-menu .el-menu-item {
  height: 44px;
  margin: 3px 0;
  border-radius: 12px;
  color: var(--text-secondary);
  font-size: 13px;
  transition: color 180ms ease, background-color 180ms ease, transform 180ms ease;
}

.aside-menu .el-menu-item .el-icon {
  margin-right: 11px;
  color: var(--text-muted);
  font-size: 17px;
}

.aside-menu .el-menu-item:hover {
  color: var(--text-primary);
  background: rgba(29, 29, 31, 0.045);
  transform: translateX(2px);
}

.aside-menu .el-menu-item.is-active {
  color: var(--el-color-primary);
  background: rgba(0, 113, 227, 0.095);
  font-weight: 600;
}

.aside-menu .el-menu-item.is-active .el-icon {
  color: var(--el-color-primary);
}

.menu-group-label {
  margin: 2px 0 8px;
  padding: 0 13px;
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  line-height: 20px;
  text-transform: uppercase;
}

.menu-group-label.admin-label {
  margin-top: 24px;
}

.layout-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 0;
  overflow: hidden;
  background: transparent;
}

.workbench-shell {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.workbench-tabs {
  display: flex;
  flex: 0 0 42px;
  align-items: stretch;
  min-width: 0;
  background: rgba(245, 245, 247, 0.88);
  border-bottom: 1px solid var(--border-light);
  -webkit-backdrop-filter: blur(18px);
  backdrop-filter: blur(18px);
}

.workbench-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 156px;
  padding: 0 17px;
  color: var(--text-muted);
  border-right: 1px solid var(--border-light);
  font-size: 12px;
  font-weight: 600;
}

.workbench-tab.is-active {
  position: relative;
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.9);
}

.workbench-tab.is-active::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  background: var(--el-color-primary);
  content: '';
}

.workbench-tab .el-icon {
  color: var(--el-color-primary);
  font-size: 15px;
}

.workbench-tab-spacer {
  flex: 1;
  min-width: 0;
}

.workbench-context {
  align-self: center;
  padding-right: 20px;
  color: var(--text-muted);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.workbench-content {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.workbench-statusbar {
  display: flex;
  flex: 0 0 23px;
  align-items: center;
  gap: 16px;
  padding: 0 13px;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.94);
  background: #0071e3;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.statusbar-spacer {
  flex: 1;
}

.sidebar-collapsed .layout-aside {
  width: 0 !important;
  flex-basis: 0;
  overflow: hidden;
  border-right: 0;
}

.sidebar-collapsed .aside-menu {
  pointer-events: none;
  opacity: 0;
}

@media (max-width: 760px) {
  .layout-header {
    padding: 0 12px 0 16px;
  }

  .brand-copy,
  .user-copy,
  .role-tag {
    display: none;
  }

  .layout-aside {
    width: 76px !important;
    flex-basis: 76px;
  }

  .activity-bar {
    display: none;
  }

  .aside-menu {
    padding: 16px 8px;
  }

  .aside-collapse-button {
    display: none;
  }

  .menu-group-label {
    padding: 0;
    text-align: center;
    font-size: 9px;
    letter-spacing: 0;
  }

  .aside-menu .el-menu-item {
    justify-content: center;
    padding: 0 !important;
  }

  .aside-menu .el-menu-item .el-icon {
    margin-right: 0;
  }

  .aside-menu .el-menu-item span:not(.el-icon) {
    display: none;
  }

  .workbench-tabs {
    flex-basis: 36px;
  }

  .workbench-tab {
    min-width: 0;
    padding: 0 13px;
  }

  .workbench-context {
    display: none;
  }

  .workbench-statusbar {
    flex-basis: 20px;
    gap: 10px;
    padding: 0 10px;
    font-size: 9px;
  }
}
</style>
