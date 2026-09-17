// 文件职责：定义页面路由、登录拦截和管理员权限拦截。
// 接口：/login、/board、/my、/meetings、/rooms 及 /admin/* 路由。
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import MainLayout from '@/layouts/MainLayout.vue'
import LoginView from '@/views/LoginView.vue'
import { useMock } from '@/shared/api/config'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
    {
      path: '/',
      component: MainLayout,
      redirect: '/board',
      children: [
        {
          path: 'board',
          name: 'board',
          component: () => import('@/views/ReservationBoard.vue'),
          meta: { title: '预约看板' },
        },
        {
          path: 'my',
          name: 'my',
          component: () => import('@/views/MyReservations.vue'),
          meta: { title: '我的预约' },
        },
        {
          path: 'meetings',
          name: 'my-meetings',
          component: () => import('@/modules/meeting/views/MyMeetings.vue'),
          meta: { title: '我的会议' },
        },
        {
          path: 'rooms',
          name: 'rooms',
          component: () => import('@/views/MeetingRooms.vue'),
          meta: { title: '会议室' },
        },

        /* —— 管理员（需要 admin 角色，守卫中校验） —— */
        {
          path: 'admin/dashboard',
          name: 'admin-dashboard',
          component: () => import('@/modules/administration/OperationsDashboard.vue'),
          meta: { title: '运营分析', requiresAdmin: true },
        },
        {
          path: 'admin/bookings',
          name: 'admin-bookings',
          component: () => import('@/modules/administration/AdministrationReservations.vue'),
          meta: { title: '预约审批', requiresAdmin: true },
        },
        {
          path: 'admin/rooms',
          name: 'admin-rooms',
          component: () => import('@/views/admin/RoomAdmin.vue'),
          meta: { title: '会议室管理', requiresAdmin: true },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/UserAdmin.vue'),
          meta: { title: '用户与信用管理', requiresAdmin: true },
        },
        {
          path: 'admin/repairs',
          name: 'admin-repairs',
          component: () => import('@/views/admin/RepairAdmin.vue'),
          meta: { title: '报修工单', requiresAdmin: true },
        },
        {
          path: 'admin/monitor',
          name: 'admin-monitor',
          component: () => import('@/modules/administration/AuditLogs.vue'),
          meta: { title: '操作审计', requiresAdmin: true },
        },
      ],
    },
  ],
})

/** 权限守卫：非管理员访问管理路由时拦截（Demo 用前端角色模拟权限） */
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.initialize()
  if (!useMock && to.path !== '/login' && !auth.currentUser.id) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin) {
    if (!auth.isAdmin) {
      ElMessage.warning('需要管理员权限')
      return { path: '/board' }
    }
  }
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 智会会议室预约系统` : '智会会议室预约系统'
})

export default router
