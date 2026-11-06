<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { state, refreshMe, refreshNotices } from './store'
import { api } from './api'

const router = useRouter()
const route = useRoute()

// 认证页与回跳页要占满整页，不套导航框架
const isSsoPage = computed(() => route.path.startsWith('/sso'))
const drawer = ref(false)

// QR-07：字号可放大到 200%。
// 注意：只改 html 的 font-size 对 Element Plus 这种用 px 写死的组件无效
// （按钮高度、内边距都不跟着变），所以这里用 zoom 整体缩放。
// 注意要作用在 html 上而不是某个 div：根元素缩放会像"网页缩放"一样重新排版，
// 不会像 transform: scale 那样留下空白，也不会溢出到屏幕外。
const scale = ref(Number(localStorage.getItem('lf-scale') || 1))

function applyZoom() {
  document.documentElement.style.zoom = String(scale.value)
}

function zoom(delta) {
  scale.value = Math.min(2, Math.max(0.9, Number((scale.value + delta).toFixed(2))))
  localStorage.setItem('lf-scale', String(scale.value))
  applyZoom()
}

onMounted(async () => {
  applyZoom()
  await refreshMe()
  await refreshNotices()
})

async function openNotices() {
  drawer.value = true
  await refreshNotices()
}

async function markRead(n) {
  if (!n.readAt) {
    await api.readNotice(n.noticeId)
    await refreshNotices()
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="app">
    <header v-if="!isSsoPage" class="topbar">
      <div class="brand">校园失物招领</div>

      <nav class="nav">
        <router-link to="/">招领信息</router-link>
        <router-link to="/claims">我的认领</router-link>
        <router-link v-if="state.user && state.user.role === 'ADMIN'" to="/admin">管理端</router-link>
      </nav>

      <div class="right">
        <el-button size="small" @click="zoom(-0.1)">A-</el-button>
        <el-button size="small" @click="zoom(0.1)">A+</el-button>

        <el-badge :value="state.unread" :hidden="!state.unread" class="notice-badge">
          <el-button size="small" @click="openNotices">通知</el-button>
        </el-badge>

        <template v-if="state.user">
          <el-tag :type="state.user.authState === 1 ? 'success' : 'info'" effect="plain">
            {{ state.user.realNameMasked || state.user.loginName }}
            {{ state.user.authState === 1 ? '已认证' : '未认证' }}
          </el-tag>
          <el-button v-if="state.user.authState === 1" size="small" @click="goLogin">切换账号</el-button>
          <el-button v-else size="small" type="warning" @click="goLogin">去认证</el-button>
        </template>
        <el-button v-else size="small" type="primary" @click="goLogin">登录</el-button>
      </div>
    </header>

    <main :class="['main', { 'main-sso': isSsoPage }]">
      <!-- 未认证用户在每个页面都能看到，不用自己去找入口 -->
      <el-alert
        v-if="state.user && state.user.authState !== 1 && route.path !== '/login' && !isSsoPage"
        type="warning"
        :closable="false"
        show-icon
        class="auth-tip"
      >
        <template #title>
          你还没有完成统一身份认证，无法发起或核验认领。
          <router-link to="/login" class="tip-link">前往认证</router-link>
        </template>
      </el-alert>

      <router-view />
    </main>

    <el-drawer v-model="drawer" title="站内通知" size="420px">
      <el-empty v-if="!state.notices.length" description="暂无通知" />
      <div v-for="n in state.notices" :key="n.noticeId" class="notice" @click="markRead(n)">
        <div class="notice-head">
          <strong>{{ n.title }}</strong>
          <el-tag v-if="!n.readAt" size="small" type="warning">未读</el-tag>
        </div>
        <div class="notice-body">{{ n.content }}</div>
        <div class="notice-time">{{ n.createdAt }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<style>
:root {
  --brand: #1f6feb;
}
body {
  margin: 0;
  font-family: system-ui, -apple-system, 'Microsoft YaHei', sans-serif;
  color: #1f2328;
  background: #f6f8fa;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.brand {
  font-weight: 700;
  font-size: 1.05rem;
  color: var(--brand);
}
.nav {
  display: flex;
  gap: 14px;
  flex: 1;
}
.nav a {
  color: #444;
  text-decoration: none;
  padding: 4px 2px;
}
.nav a.router-link-active {
  color: var(--brand);
  border-bottom: 2px solid var(--brand);
}
.right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.main {
  /* 放大到 200% 时不要顶出屏幕：用 100% 兜底 */
  max-width: min(1100px, 100%);
  margin: 20px auto;
  padding: 0 16px;
}
.auth-tip {
  margin-bottom: 16px;
}
.main-sso {
  max-width: none;
  margin: 0;
  padding: 0;
}
.tip-link {
  color: #b26a00;
  font-weight: 600;
  margin-left: 6px;
}
.notice {
  border-bottom: 1px solid #eee;
  padding: 10px 0;
  cursor: pointer;
}
.notice-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.notice-body {
  color: #555;
  margin: 4px 0;
}
.notice-time {
  color: #999;
  font-size: 0.85rem;
}
</style>
