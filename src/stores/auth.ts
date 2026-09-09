import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { CurrentUser, Role } from '@/types'

/**
 * 演示用身份/权限模型（无真实鉴权）：
 * 通过右上角头像菜单切换视角，菜单、路由与操作权限随之变化。
 */
const profiles: Record<Role, CurrentUser> = {
  user: { id: 'u1001', name: '李明', department: '软件学院', role: 'user' },
  admin: { id: 'u9001', name: '王建国', department: '信息中心', role: 'admin' },
}

export const useAuthStore = defineStore('auth', () => {
  const role = ref<Role>('user')

  const currentUser = computed<CurrentUser>(() => profiles[role.value])
  const isAdmin = computed(() => role.value === 'admin')

  function switchRole(): Role {
    role.value = role.value === 'user' ? 'admin' : 'user'
    return role.value
  }

  return { role, currentUser, isAdmin, switchRole }
})
