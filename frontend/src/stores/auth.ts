// 文件职责：Pinia store，维护登录用户、JWT 和角色状态。
// 接口：调用 authApi；角色与权限以后端 /users/me 返回为准。
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/shared/api'
import { TOKEN_STORAGE_KEY } from '@/shared/api/config'
import type { CurrentUser, Role } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const role = ref<Role>('USER')
  const currentUser = ref<CurrentUser>({ id: '', name: '未登录', department: '', role: undefined })
  const initialized = ref(false)

  const isAdmin = computed(() => role.value === 'ADMIN')

  /** 未登录时保留一个只用于渲染的匿名占位，不授予管理员权限。 */
  function anonymous() {
    currentUser.value = { id: '', name: '未登录', department: '', role: undefined }
    role.value = 'USER'
  }

  async function initialize() {
    if (initialized.value) return
    initialized.value = true
    try {
      const user = await authApi.me()
      currentUser.value = user
      role.value = user.role ?? 'USER'
    } catch {
      anonymous()
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
    anonymous()
  }

  return { role, currentUser, initialized, isAdmin, initialize, login, logout }
})
