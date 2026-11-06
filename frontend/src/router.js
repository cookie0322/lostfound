import { createRouter, createWebHistory } from 'vue-router'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'items', component: () => import('./views/Items.vue') },
    { path: '/claims', name: 'claims', component: () => import('./views/Claims.vue') },
    { path: '/admin', name: 'admin', component: () => import('./views/Admin.vue') },
    { path: '/login', name: 'login', component: () => import('./views/Login.vue') },
    // 模拟学校认证页与回跳落地页（对应 IF-01 的跳转与票据回跳）
    { path: '/sso', name: 'sso', component: () => import('./views/Sso.vue') },
    { path: '/sso/callback', name: 'ssoCallback', component: () => import('./views/SsoCallback.vue') }
  ]
})
