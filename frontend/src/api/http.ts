import axios, { type AxiosInstance } from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import type { ApiResponse } from './types'

/**
 * 统一 http 层（与后端 ApiResponse{code,message,traceId,data} 对应）。
 * 分流规则（docs/03 §7）：
 *  - code=0        → resolve(data)
 *  - 40101/40305   → 跳登录（带回跳）/ 提示剩余锁定秒
 *  - 40910/40911   → 由调用方处理（resolve 原响应，弹"备选房源"冲突窗，AC-20）
 *  - 50310         → AI 降级：resolve（黄色提示条，不弹错误，AC-43/66）
 *  - 其它          → toast + reject
 */
export const CONFLICT_CODES = [40910, 40911, 40912, 40920, 40921, 40931]

export const http: AxiosInstance = axios.create({ baseURL: '/api', timeout: 50000 })

http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('hf-token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

http.interceptors.response.use(
  async (res) => {
    const body = res.data as ApiResponse<unknown>
    if (body?.code === 0) return body.data as any

    const cfg = res.config as typeof res.config & { _retried?: boolean }
    if (body.code === 40101 && !String(cfg.url || '').includes('/auth/refresh') && !cfg._retried) {
      const rt = localStorage.getItem('hf-refresh')
      if (rt) {
        try {
          const refreshed = await axios.post('/api/auth/refresh', { refreshToken: rt })
          const payload = refreshed.data as ApiResponse<{ token: string }>
          if (payload.code === 0 && payload.data?.token) {
            localStorage.setItem('hf-token', payload.data.token)
            localStorage.removeItem('hf-refresh')
            cfg._retried = true
            cfg.headers.Authorization = `Bearer ${payload.data.token}`
            return http(cfg)
          }
        } catch {
          /* 刷新失败则回到登录 */
        }
      }
    }
    if (body.code === 40101) {
      const redirect = encodeURIComponent(location.pathname + location.search)
      localStorage.removeItem('hf-token')
      localStorage.removeItem('hf-user')
      location.href = `/login?redirect=${redirect}`
      return Promise.reject(body)
    }
    if (CONFLICT_CODES.includes(body.code)) return Promise.reject(body)   // 调用方给冲突 UI
    if (body.code === 50310) {
      ElNotification({ type: 'warning', title: 'AI 暂不可用', message: body.message, duration: 4000 })
      return body.data as any
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(body)
  },
  (err) => {
    ElMessage.error(err?.message === 'Network Error' ? '网络不可用，请检查后端是否启动' : '服务异常')
    return Promise.reject(err)
  },
)

export const get = <T>(url: string, params?: object) => http.get<T, T>(url, { params })
export const post = <T>(url: string, body?: object) => http.post<T, T>(url, body)
export const put = <T>(url: string, body?: object) => http.put<T, T>(url, body)
export const del = <T>(url: string) => http.delete<T, T>(url)
