import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createRouter, createWebHistory } from 'vue-router'
import 'element-plus/dist/index.css'
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

// 路由守卫：未登录跳登录并带回跳（AC-28）；角色不符回首页
router.beforeEach((to) => {
  const token = localStorage.getItem('hf-token')
  if (to.meta.auth && !token) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.roles && !token) return { name: 'login' }
  return true
})

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
