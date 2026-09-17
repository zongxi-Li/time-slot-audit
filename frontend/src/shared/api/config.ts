// 文件职责：集中管理 API 前缀和 Token 存储键。
// 接口：读取 Vite 环境变量（VITE_API_PROXY_TARGET 在 vite.config.ts 中使用）。
export const API_BASE_URL = '/api'

export const TOKEN_STORAGE_KEY = 'timeslot.access_token'
