import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createRouter, createWebHistory } from 'vue-router'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import App from './App.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue') },
    { path: '/', name: 'home', component: () => import('./views/HomeView.vue') },
    { path: '/house-type/:id', name: 'house-type', component: () => import('./views/HouseTypeDetailView.vue') },
    { path: '/house-type/:id/evaluate', name: 'evaluate', component: () => import('./views/EvaluateView.vue'), meta: { auth: true } },
    { path: '/compare', name: 'compare', component: () => import('./views/CompareView.vue'), meta: { auth: true } },
    { path: '/selection/:intentionNo', name: 'selection', component: () => import('./views/SelectionConfirmView.vue'), meta: { auth: true } },
    { path: '/appointments', name: 'appointments', component: () => import('./views/AppointmentsView.vue'), meta: { auth: true } },
    { path: '/ai/advisor', name: 'advisor', component: () => import('./views/AiAdvisorView.vue'), meta: { auth: true } },
    { path: '/share/reports/:token', name: 'share', component: () => import('./views/ReportShareView.vue') },
    { path: '/admin/:rest(.*)', name: 'admin', component: () => import('./views/admin/AdminShell.vue'), meta: { roles: ['ADMIN', 'CONSULTANT'] } },
  ],
})

function currentRole(): string | null {
  try {
    const u = JSON.parse(localStorage.getItem('hf-user') || 'null')
    return u?.role ?? null
  } catch {
    return null
  }
}

/** 演示环境免密自登录辅助：确保使用者打开任何页面无需手动输入密码 */
async function autoLoginDemoUser(): Promise<boolean> {
  try {
    // 先触发演示短信固定验证码通道（123456）
    await fetch('/api/auth/code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: '13800000001' }),
    })
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: '13800000001', smsCode: '123456' }),
    })
    const json = await res.json()
    if (json.code === 0 && json.data?.token) {
      localStorage.setItem('hf-token', json.data.token)
      if (json.data.refreshToken) localStorage.setItem('hf-refresh', json.data.refreshToken)
      localStorage.setItem('hf-user', JSON.stringify(json.data.user))
      return true
    }
  } catch {
    // 降级兜底预填
  }
  return false
}

// 路由守卫：优先自动静默免密登入演示账号（满足“不要设置密码”需求）
router.beforeEach(async (to) => {
  let token = localStorage.getItem('hf-token')
  const roles = to.meta.roles as string[] | undefined
  if ((to.meta.auth || roles) && !token) {
    const ok = await autoLoginDemoUser()
    if (ok) {
      token = localStorage.getItem('hf-token')
    } else {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (roles && !roles.includes(currentRole() || '')) return { name: 'home' }
  return true
})

// 初始启动时若无登录态则静默就绪
if (!localStorage.getItem('hf-token')) {
  void autoLoginDemoUser()
}

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
