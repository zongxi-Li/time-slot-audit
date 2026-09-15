import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminUsersApi, departmentsApi } from '@/shared/api'
import type { AdminUserResponse, DepartmentResponse, ViolationResponse } from '@/shared/api'

/**
 * 用户与信用治理（管理员）：
 * 视图发起操作，本 store 只负责列表缓存与统一的接口调用，
 * 返回更新后的记录由视图替换或触发刷新。
 */
export const useUserAdminStore = defineStore('userAdmin', () => {
  const users = ref<AdminUserResponse[]>([])
  const departments = ref<DepartmentResponse[]>([])
  const loading = ref(false)

  async function fetchUsers(params?: { keyword?: string; status?: number }) {
    loading.value = true
    try {
      users.value = await adminUsersApi.list(params)
    } finally {
      loading.value = false
    }
  }

  async function loadDepartments(force = false) {
    if (!force && departments.value.length > 0) return
    departments.value = await departmentsApi.list()
  }

  async function fetchViolations(userId: number | string): Promise<ViolationResponse[]> {
    return adminUsersApi.violations(userId)
  }

  async function fetchQualification(userId: number | string) {
    return adminUsersApi.qualification(userId)
  }

  /** 用接口返回的最新记录替换列表中的同条数据 */
  function replaceUser(updated: AdminUserResponse) {
    const index = users.value.findIndex((u) => String(u.id) === String(updated.id))
    if (index >= 0) users.value[index] = updated
    else users.value.unshift(updated)
  }

  async function createUser(payload: Parameters<typeof adminUsersApi.create>[0]) {
    const created = await adminUsersApi.create(payload)
    users.value.unshift(created)
    return created
  }

  async function updateUser(id: number | string, payload: Parameters<typeof adminUsersApi.update>[1]) {
    const updated = await adminUsersApi.update(id, payload)
    replaceUser(updated)
    return updated
  }

  async function updateStatus(id: number | string, status: number, reason?: string) {
    const updated = await adminUsersApi.updateStatus(id, { status, reason })
    replaceUser(updated)
    return updated
  }

  async function resetPassword(id: number | string, password: string) {
    await adminUsersApi.resetPassword(id, password)
  }

  async function adjustCredit(id: number | string, creditChange: number, reason: string) {
    const updated = await adminUsersApi.adjustCredit(id, { creditChange, reason })
    replaceUser(updated)
    return updated
  }

  async function setRestriction(id: number | string, reason: string, restrictedUntil?: string) {
    const updated = await adminUsersApi.setRestriction(id, { reason, restrictedUntil })
    replaceUser(updated)
    return updated
  }

  async function createDepartment(payload: Parameters<typeof departmentsApi.create>[0]) {
    const created = await departmentsApi.create(payload)
    departments.value.push(created)
    return created
  }

  async function updateDepartment(id: number | string, payload: Parameters<typeof departmentsApi.update>[1]) {
    const updated = await departmentsApi.update(id, payload)
    const index = departments.value.findIndex((d) => String(d.id) === String(id))
    if (index >= 0) departments.value[index] = updated
    // 部门名冗余在用户行上，改名后刷新列表保持一致
    await fetchUsers(lastQuery.value)
    return updated
  }

  /** 最近一次用户查询条件，部门改名后刷新用 */
  const lastQuery = ref<{ keyword?: string; status?: number }>({})

  return {
    users,
    departments,
    loading,
    lastQuery,
    fetchUsers,
    loadDepartments,
    fetchViolations,
    fetchQualification,
    createUser,
    updateUser,
    updateStatus,
    resetPassword,
    adjustCredit,
    setRestriction,
    createDepartment,
    updateDepartment,
  }
})
