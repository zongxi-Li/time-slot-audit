import { API_BASE_URL, TOKEN_STORAGE_KEY } from './config'
import type { ApiErrorBody, ApiResponse } from './types'
import { ApiError } from './types'

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
}

function clearToken() {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | ApiErrorBody | null

  if (!response.ok) {
    if (response.status === 401) clearToken()
    const error = payload && 'code' in payload
      ? payload
      : { code: 'INTERNAL_ERROR', message: response.statusText || '请求失败', data: null }
    throw new ApiError(response.status, error.code, error.message, error.data)
  }

  if (!payload || !('data' in payload) || payload.code !== 'SUCCESS') {
    throw new ApiError(response.status, payload && 'code' in payload ? payload.code : 'INTERNAL_ERROR', payload && 'message' in payload ? payload.message : '响应格式错误')
  }
  return payload.data as T
}
