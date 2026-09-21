<!--
  文件职责：TimeSlot 项目基础文件。
  接口：供对应工具链加载。
-->
<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Calendar,
  Close,
  DArrowLeft,
  DArrowRight,
  Menu,
  Tickets,
  OfficeBuilding,
  Odometer,
  List,
  Management,
  Monitor,
  Tools,
  User,
  AlarmClock,
  Reading,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useSystemTimeStore } from '@/stores/systemTime'
import NotificationBell from '@/modules/meeting/components/NotificationBell.vue'
import RejectionBanner from '@/modules/meeting/components/RejectionBanner.vue'
import SystemTimeControl from '@/components/SystemTimeControl.vue'
import timeSlotLogo from '../../assets/timeSlot.png'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const systemTime = useSystemTimeStore()

const userMenus = [
  { path: '/board', label: '预约看板', icon: Calendar },
  { path: '/my', label: '我的预约', icon: Tickets },
  { path: '/meetings', label: '我的会议', icon: AlarmClock },
  { path: '/rooms', label: '会议室', icon: OfficeBuilding },
  { path: '/manual', label: '说明书', icon: Reading },
]

const adminMenus = [
  { path: '/admin/dashboard', label: '运营分析', icon: Odometer },
  { path: '/admin/bookings', label: '预约审批', icon: List },
  { path: '/admin/rooms', label: '会议室管理', icon: Management },
  { path: '/admin/users', label: '用户与信用管理', icon: User },
  { path: '/admin/repairs', label: '报修工单', icon: Tools },
  { path: '/admin/monitor', label: '操作审计', icon: Monitor },
]

type WorkspaceTab = {
  path: string
  label: string
  icon: Component
}

const showAdminMenus = computed(() => auth.isAdmin)
const navigationMenus = computed(() => (auth.isAdmin ? [...userMenus, ...adminMenus] : userMenus))
const currentMenu = computed(() => navigationMenus.value.find((item) => item.path === route.path))
const SIDEBAR_COLLAPSED_KEY = 'timeslot.sidebar.collapsed'
const WORKSPACE_TABS_KEY = 'timeslot.workspace.tabs'
const isSidebarCollapsed = ref(readBooleanPreference(SIDEBAR_COLLAPSED_KEY))
const isMobile = ref(false)
const isMobileSidebarOpen = ref(false)
const workspaceTabs = ref<WorkspaceTab[]>([])
const tabListRef = ref<HTMLElement | null>(null)

// 标签多到横向溢出时，切换后把激活标签滚进可视区
watch(
  () => route.path,
  async () => {
    await nextTick()
    tabListRef.value
      ?.querySelector('.workbench-tab.is-active')
      ?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  },
  { immediate: true },
)
let mobileMediaQuery: MediaQueryList | null = null

function readBooleanPreference(key: string) {
  if (typeof window === 'undefined') return false
  return window.localStorage.getItem(key) === '1'
}

function readStoredTabPaths() {
  if (typeof window === 'undefined') return []

  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(WORKSPACE_TABS_KEY) ?? '[]')
    return Array.isArray(value) ? value.filter((path): path is string => typeof path === 'string') : []
  } catch {
    return []
  }
}

onMounted(async () => {
  mobileMediaQuery = window.matchMedia('(max-width: 760px)')
  isMobile.value = mobileMediaQuery.matches
  mobileMediaQuery.addEventListener('change', handleMobileMediaChange)

  await auth.initialize()
  systemTime.start()
  restoreWorkspaceTabs()
})

onBeforeUnmount(() => {
  mobileMediaQuery?.removeEventListener('change', handleMobileMediaChange)
  systemTime.stop()
})

function findMenu(path: string) {
  return navigationMenus.value.find((item) => item.path === path)
}

function ensureWorkspaceTab(path: string) {
  const menu = findMenu(path)
  if (!menu || workspaceTabs.value.some((tab) => tab.path === path)) return

  workspaceTabs.value.push({
    path: menu.path,
    label: menu.label,
    icon: menu.icon,
  })
}

function activateWorkspaceTab(path: string) {
  ensureWorkspaceTab(path)
  isMobileSidebarOpen.value = false
  void router.push(path)
}

function closeWorkspaceTab(path: string) {
  if (workspaceTabs.value.length <= 1) return

  const tabIndex = workspaceTabs.value.findIndex((tab) => tab.path === path)
  if (tabIndex < 0) return

  const isActiveTab = route.path === path
  workspaceTabs.value.splice(tabIndex, 1)

  if (isActiveTab) {
    const nextTab = workspaceTabs.value[tabIndex] ?? workspaceTabs.value[tabIndex - 1]
    if (nextTab) void router.push(nextTab.path)
  }
}

watch(
  () => route.path,
  (path) => ensureWorkspaceTab(path),
)

function persistWorkspaceTabs() {
  if (typeof window === 'undefined') return
  const paths = workspaceTabs.value.map((tab) => tab.path)
  window.localStorage.setItem(WORKSPACE_TABS_KEY, JSON.stringify(paths))
}

watch(
  workspaceTabs,
  persistWorkspaceTabs,
  { deep: true },
)

function restoreWorkspaceTabs() {
  for (const path of readStoredTabPaths()) ensureWorkspaceTab(path)
  ensureWorkspaceTab(route.path)
  persistWorkspaceTabs()
}

function handleMobileMediaChange(event: MediaQueryListEvent) {
  isMobile.value = event.matches
  if (!event.matches) isMobileSidebarOpen.value = false
}

function toggleSidebar() {
  if (isMobile.value) {
    isMobileSidebarOpen.value = !isMobileSidebarOpen.value
    return
  }

  isSidebarCollapsed.value = !isSidebarCollapsed.value
  window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, isSidebarCollapsed.value ? '1' : '0')
}

function closeMobileSidebar() {
  isMobileSidebarOpen.value = false
}

function handleMenuSelect() {
  if (isMobile.value) closeMobileSidebar()
}

function onUserCommand(command: string) {
  if (command === 'logout') {
    auth.logout()
    void router.push('/login')
    ElMessage.success('已退出登录')
  }
}
</script>

<template>
  <el-container
    class="layout"
    :class="{
      'sidebar-collapsed': isSidebarCollapsed && !isMobile,
      'is-mobile': isMobile,
      'mobile-sidebar-open': isMobileSidebarOpen
    }"
  >
    <el-header class="layout-header" height="68px">
      <div class="brand">
        <button
          v-if="isMobile"
          type="button"
          class="mobile-nav-toggle"
          aria-label="打开导航栏"
          title="打开导航栏"
          @click="isMobileSidebarOpen = true"
        >
          <el-icon><Menu /></el-icon>
        </button>
        <img class="brand-logo" :src="timeSlotLogo" alt="TimeSlot logo" />
        <div class="brand-copy">
          <span class="brand-name">智会会议室预约系统</span>
          <span class="brand-kicker">TIME / SLOT WORKSPACE</span>
        </div>
      </div>

      <div class="header-right">
        <SystemTimeControl v-if="auth.isAdmin" />
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
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      </div>
    </el-header>

    <!-- 登录后存在未读驳回通知时，在顶栏下方展示醒目横幅（可关闭，关闭即标记已读） -->
    <RejectionBanner />

    <el-container class="layout-body">
      <button
        v-if="isMobile && isMobileSidebarOpen"
        type="button"
        class="mobile-sidebar-backdrop"
        aria-label="关闭导航栏"
        @click="closeMobileSidebar"
      />

      <el-aside width="206px" class="layout-aside">
        <div class="sidebar-header">
          <span v-if="!isSidebarCollapsed || isMobile" class="sidebar-title">工作台</span>
          <button
            type="button"
            class="sidebar-toggle"
            :aria-label="
              isMobile ? '关闭导航栏' : (isSidebarCollapsed ? '展开侧边栏' : '隐藏侧边栏')
            "
            :title="
              isMobile ? '关闭导航栏' : (isSidebarCollapsed ? '展开侧边栏' : '隐藏侧边栏')
            "
            @click="isMobile ? closeMobileSidebar() : toggleSidebar()"
          >
            <el-icon>
              <component :is="isMobile ? Close : (isSidebarCollapsed ? DArrowRight : DArrowLeft)" />
            </el-icon>
          </button>
        </div>

        <el-menu
          :default-active="route.path"
          router
          class="aside-menu"
          :collapse="!isMobile && isSidebarCollapsed"
          :collapse-transition="false"
          @select="handleMenuSelect"
        >
          <el-menu-item v-for="item in userMenus" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="!isSidebarCollapsed || isMobile">{{ item.label }}</span>
          </el-menu-item>

          <template v-if="showAdminMenus">
            <div v-if="!isSidebarCollapsed || isMobile" class="menu-group-label admin-label">系统管理</div>
            <el-menu-item v-for="item in adminMenus" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span v-if="!isSidebarCollapsed || isMobile">{{ item.label }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <div class="workbench-shell">
          <div class="workbench-tabs" role="tablist" aria-label="工作区标签">
            <div ref="tabListRef" class="workbench-tab-list thin-scroll">
              <div
                v-for="tab in workspaceTabs"
                :key="tab.path"
                class="workbench-tab"
                :class="{ 'is-active': tab.path === route.path }"
                role="tab"
                :aria-selected="tab.path === route.path"
                :tabindex="tab.path === route.path ? 0 : -1"
                @click="activateWorkspaceTab(tab.path)"
                @keydown.enter="activateWorkspaceTab(tab.path)"
                @keydown.space.prevent="activateWorkspaceTab(tab.path)"
              >
                <el-icon><component :is="tab.icon" /></el-icon>
                <span class="workbench-tab-label">{{ tab.label }}</span>
                <button
                  v-if="workspaceTabs.length > 1"
                  type="button"
                  class="workbench-tab-close"
                  :aria-label="`关闭${tab.label}`"
                  :title="`关闭${tab.label}`"
                  @click.stop="closeWorkspaceTab(tab.path)"
                >
                  <el-icon><Close /></el-icon>
                </button>
              </div>
            </div>
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

.mobile-nav-toggle {
  display: none;
  place-items: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--text-secondary);
  background: transparent;
  border: 0;
  border-radius: 9px;
  cursor: pointer;
}

.mobile-nav-toggle:hover {
  color: var(--el-color-primary);
  background: rgba(0, 113, 227, 0.08);
}

.brand-logo {
  display: block;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  object-fit: contain;
  filter: drop-shadow(0 6px 10px rgba(0, 113, 227, 0.18));
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
  position: relative;
  height: calc(100% - 68px);
  min-height: 0;
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
  transition: width 180ms ease, flex-basis 180ms ease;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding: 10px 12px 8px 25px;
  border-bottom: 1px solid rgba(29, 29, 31, 0.06);
}

.sidebar-title {
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.sidebar-toggle {
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

.sidebar-toggle:hover {
  color: var(--el-color-primary);
  background: rgba(0, 113, 227, 0.08);
}

.mobile-sidebar-backdrop {
  display: none;
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

.workbench-tab-list {
  display: flex;
  flex: 1 1 auto; /* 占满标签行剩余宽度，少时标签得以拉伸 */
  min-width: 0;
  overflow-x: auto;
}

.workbench-tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 0%; /* 等宽分摊行宽：少时拉长到上限，多时压到下限后出滚动条 */
  min-width: 164px; /* 保证最长的“用户与信用管理”不省略 */
  max-width: 260px;
  padding: 0 8px 0 17px;
  color: var(--text-muted);
  border-right: 1px solid var(--border-light);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
}

.workbench-tab:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.64);
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

.workbench-tab-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-tab-close {
  display: grid;
  flex: 0 0 22px;
  place-items: center;
  width: 22px;
  height: 22px;
  margin-left: auto;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  opacity: 0;
}

.workbench-tab:hover .workbench-tab-close,
.workbench-tab.is-active .workbench-tab-close {
  opacity: 1;
}

.workbench-tab-close:hover {
  color: var(--text-primary);
  background: rgba(29, 29, 31, 0.08);
}

.workbench-tab-close .el-icon {
  color: currentColor;
  font-size: 13px;
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
  width: 56px !important;
  flex-basis: 56px;
}

.sidebar-collapsed .sidebar-header {
  justify-content: center;
  padding: 10px 4px 8px;
}

.sidebar-collapsed .aside-menu {
  width: 56px !important;
  padding: 18px 4px;
}

.sidebar-collapsed .aside-menu .el-menu-item {
  justify-content: center;
  padding: 0 !important;
}

.sidebar-collapsed .aside-menu .el-menu-item .el-icon {
  margin-right: 0;
}

.sidebar-collapsed .aside-menu .el-menu-item span {
  display: none;
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

  .mobile-nav-toggle {
    display: grid;
  }

  .layout-aside {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 20;
    width: min(280px, calc(100vw - 32px)) !important;
    flex-basis: min(280px, calc(100vw - 32px));
    box-shadow: 14px 0 32px rgba(29, 29, 31, 0.14);
    transform: translateX(-105%);
    transition: transform 180ms ease;
  }

  .mobile-sidebar-open .layout-aside {
    transform: translateX(0);
  }

  .mobile-sidebar-backdrop {
    position: absolute;
    inset: 0;
    z-index: 15;
    display: block;
    padding: 0;
    background: rgba(29, 29, 31, 0.2);
    border: 0;
    cursor: pointer;
  }

  .sidebar-header {
    justify-content: space-between;
    padding: 10px 12px 8px 25px;
  }

  .sidebar-title {
    display: block;
  }

  .aside-menu {
    padding: 16px 8px;
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

  .workbench-tabs {
    flex-basis: 36px;
  }

  .workbench-tab {
    min-width: 0;
    padding: 0 13px;
  }

  .workbench-tab-close {
    opacity: 1;
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
