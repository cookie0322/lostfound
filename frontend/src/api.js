import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求层。
 * 后端所有 JSON 响应都是 { code, message, data }，这里直接把 data 拆出来给页面用，
 * 页面里就不用到处写 res.data.data。
 * 出错时统一弹提示，并把 401 交给路由守卫处理。
 */
const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 10000
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined') {
      if (body.code === 0) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (err) => {
    const body = err.response && err.response.data
    ElMessage.error((body && body.message) || '网络异常，请稍后重试')
    return Promise.reject(err)
  }
)

export const api = {
  // F1 认证
  login: (loginName) => http.post('/auth/login', { loginName }),
  me: () => http.get('/auth/me'),
  mockSso: (studentNo, realName) => http.post('/auth/mock-sso', { studentNo, realName }),
  bind: (ticket) => http.post('/auth/bind', { ticket }),
  unbind: () => http.post('/auth/unbind', { confirm: true }),
  appeal: (studentNo, reason) => http.post('/auth/appeal', { studentNo, reason }),

  // M2 只读台账
  items: () => http.get('/items'),

  // F2 认领
  apply: (itemId, features) => http.post('/claims', { itemId, features }),
  myClaims: () => http.get('/claims/mine'),
  claim: (id) => http.get(`/claims/${id}`),
  review: (id, action, reason) => http.post(`/claims/${id}/review`, { action, reason }),
  confirm: (id) => http.post(`/claims/${id}/confirm`),
  withdraw: (id) => http.post(`/claims/${id}/withdraw`, { confirm: true }),
  arbitrate: (id, reason) => http.post(`/claims/${id}/arbitrate`, { reason }),
  uploadVoucher: (id, file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post(`/claims/${id}/voucher`, fd)
  },

  // 通知
  notices: () => http.get('/notices'),
  readNotice: (id) => http.post(`/notices/${id}/read`),

  // 管理端
  appeals: () => http.get('/admin/appeals'),
  adminClaims: (state) => http.get('/admin/claims', { params: { state } }),
  resolveAppeal: (id, action, reason) =>
    http.post(`/admin/appeals/${id}/resolve`, { action, reason }),
  verdict: (id, verdict, reason) =>
    http.post(`/admin/claims/${id}/verdict`, { verdict, reason }),
  adminUnbind: (userId) => http.post(`/admin/appeal/${userId}/unbind`, { confirm: true })
}

export default http
