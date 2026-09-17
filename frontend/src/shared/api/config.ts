// 文件职责：集中管理 API 前缀、Mock 开关和 Token 存储键。
// 接口：读取 VITE_USE_MOCK 等 Vite 环境变量。
export const API_BASE_URL = '/api'

/** 默认走真实 Spring Boot；只有显式设置 VITE_USE_MOCK=true 才启用 Demo 数据。 */
export const useMock = import.meta.env.VITE_USE_MOCK === 'true'

export const TOKEN_STORAGE_KEY = 'timeslot.access_token'
