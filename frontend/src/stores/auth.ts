// 文件职责：Pinia store，维护登录用户、JWT 和角色状态。
// 接口：调用 authApi。
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/shared/api'
import { TOKEN_STORAGE_KEY, useMock } from '@/shared/api/config'
import type { CurrentUser, Role } from '@/types'

/**
 * 演示用身份/权限模型（无真实鉴权）：
 * 通过右上角头像菜单切换视角，菜单、路由与操作权限随之变化。
 */
const profiles: Record<Role, CurrentUser> = {
  USER: { id: 'u1001', name: '李明', department: '软件学院', role: 'USER' },
  ADMIN: { id: 'u9001', name: '王建国', department: '信息中心', role: 'ADMIN' },
}

export const useAuthStore = defineStore('auth', () => {
  const role = ref<Role>('USER')
  const currentUser = ref<CurrentUser>(profiles.USER)
  const initialized = ref(false)

  const isAdmin = computed(() => role.value === 'ADMIN')

  async function initialize() {
    if (initialized.value) return
    initialized.value = true
    if (useMock) return
    try {
      const user = await authApi.me()
      currentUser.value = user
      role.value = user.role ?? 'USER'
    } catch {
      // 未登录时保留一个只用于渲染的匿名占位，不授予管理员权限。
      currentUser.value = { id: '', name: '未登录', department: '', role: undefined }
      role.value = 'USER'
    }
  }

  async function login(username: string, password: string) {
    const result = await authApi.login({ username, password })
    currentUser.value = result.currentUser
    role.value = result.currentUser.role ?? 'USER'
    initialized.value = true
    return result.currentUser
  }

  function logout() {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    initialized.value = false
    currentUser.value = profiles.USER
    role.value = 'USER'
  }

  function switchRole(): Role {
    if (!useMock) return role.value
    role.value = role.value === 'USER' ? 'ADMIN' : 'USER'
    currentUser.value = profiles[role.value]
    return role.value
  }

  return { role, currentUser, initialized, isAdmin, initialize, login, logout, switchRole }
})
