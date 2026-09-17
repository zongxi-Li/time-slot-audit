// 文件职责：前端业务模块的 index 入口、页面、组件或类型定义。
// 接口：导出本模块的页面、store、API 或类型。
export { useAuthStore } from '@/stores/auth'
export { useUserAdminStore } from '@/stores/userAdmin'
export { authApi, adminUsersApi, departmentsApi, myViolationsApi } from '@/shared/api'
