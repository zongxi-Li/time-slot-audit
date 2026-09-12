export const API_BASE_URL = '/api'

/** 默认走真实 Spring Boot；只有显式设置 VITE_USE_MOCK=true 才启用 Demo 数据。 */
export const useMock = import.meta.env.VITE_USE_MOCK === 'true'

export const TOKEN_STORAGE_KEY = 'timeslot.access_token'
