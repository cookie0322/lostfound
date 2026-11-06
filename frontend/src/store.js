import { reactive } from 'vue'
import { api } from './api'

/**
 * 极简全局状态：只放"我是谁"和通知，不引 Pinia（DC-06 控制代码量）。
 */
export const state = reactive({
  user: null,
  notices: [],
  unread: 0
})

export async function refreshMe() {
  try {
    state.user = await api.me()
  } catch (e) {
    state.user = null
  }
  return state.user
}

export async function refreshNotices() {
  if (!state.user) {
    state.notices = []
    state.unread = 0
    return
  }
  state.notices = await api.notices()
  state.unread = state.notices.filter((n) => !n.readAt).length
}

export function logoutLocal() {
  state.user = null
  state.notices = []
  state.unread = 0
}
