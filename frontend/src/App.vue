<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCompareStore } from './stores/compare'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

const sessionTick = ref(0)
router.afterEach(() => { sessionTick.value++ })

const loggedIn = computed(() => {
  sessionTick.value
  return !!localStorage.getItem('hf-token')
})
const user = computed(() => {
  sessionTick.value
  try {
    return JSON.parse(localStorage.getItem('hf-user') || 'null')
  } catch {
    return null
  }
})
const role = computed(() => user.value?.role as string | undefined)
const nickname = computed(() => user.value?.nickname as string | undefined)
const isStaff = computed(() => role.value === 'ADMIN' || role.value === 'CONSULTANT')
const bare = computed(() => route.name === 'share' || route.name === 'login')

interface NavItem { name: string; label: string; to: string; badge?: number; staffOnly?: boolean; icon?: string }
const nav = computed<NavItem[]>(() => [
  { name: 'home', label: '选房图册', to: '/' },
  { name: 'compare', label: '户型对比', to: '/compare', badge: compare.count },
  { name: 'advisor', label: 'AI 顾问', to: '/ai/advisor' },
  { name: 'appointments', label: '看房预约', to: '/appointments' },
  { name: 'admin', label: '后台管理', to: '/admin/rules', staffOnly: true },
])
const active = computed(() => {
  const n = String(route.name)
  if (n === 'house-type' || n === 'evaluate' || n === 'selection') return 'home'
  return n
})

function logout() {
  localStorage.removeItem('hf-token')
  localStorage.removeItem('hf-refresh')
  localStorage.removeItem('hf-user')
  sessionTick.value++
  router.push({ name: 'login' })
}
</script>

<template>
  <div v-if="bare" class="bare"><router-view /></div>
  <div v-else class="shell">
    <header class="topbar">
      <div class="bar-inner">
        <router-link class="brand" to="/" aria-label="肇庆好房子首页">
          <div class="mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z" stroke-linejoin="round" />
              <path d="M9 21V12h6v9" stroke-linejoin="round" stroke-linecap="round" />
            </svg>
          </div>
          <div class="brand-text-wrap">
            <span class="brand-text">肇庆 · <em>好房子</em></span>
            <span class="brand-sub">智能选房与评估</span>
          </div>
        </router-link>

        <nav class="nav" aria-label="主导航">
          <router-link
            v-for="item in nav"
            :key="item.name"
            v-show="!item.staffOnly || isStaff"
            :to="item.to"
            class="nav-link"
            :class="{ active: active === item.name }"
            :aria-current="active === item.name ? 'page' : undefined"
          >
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="item.badge" class="dot hf-num">{{ item.badge }}</span>
          </router-link>
        </nav>

        <div class="actions">
          <template v-if="loggedIn">
            <div class="user-badge">
              <span class="user-avatar">{{ (nickname || '用')[0] }}</span>
              <span class="user-name">{{ nickname || '用户' }}</span>
              <span class="role-tag" :class="role?.toLowerCase()">{{ isStaff ? (role === 'ADMIN' ? '管理员' : '顾问') : '购房者' }}</span>
            </div>
            <button type="button" class="text-btn logout-btn" @click="logout">退出</button>
          </template>
          <el-button v-else type="primary" size="default" class="login-btn" @click="router.push({ name: 'login' })">
            登录 / 演示账号
          </el-button>
        </div>
      </div>
    </header>

    <main class="main"><router-view /></main>

    <footer class="foot">
      <div class="bar-inner foot-inner">
        <div class="foot-left">
          <span class="foot-brand">🏛️ 肇庆好房子 · 在线选房与户型智能评估系统</span>
          <span class="foot-note">课程设计 · 规格先行 · 结果可解释</span>
        </div>
        <div class="foot-status">
          <span class="status-indicator"><span class="status-dot"></span>DSL 规则引擎 v1.0 就绪</span>
          <span class="status-divider">/</span>
          <span>通义千问 AI 顾问网关</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style>
.bare { min-height: 100dvh; }

.shell {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: var(--hf-z-nav);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(16px) saturate(1.8);
  -webkit-backdrop-filter: blur(16px) saturate(1.8);
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03);
}

.bar-inner {
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 32px;
  height: 68px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  color: var(--hf-ink);
  transition: opacity var(--hf-dur);
}
.brand:hover { opacity: 0.9; }

.brand .mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #ffffff;
  background: linear-gradient(135deg, var(--hf-primary) 0%, #0891b2 100%);
  box-shadow: 0 4px 10px rgba(21, 94, 117, 0.25);
}

.brand-text-wrap {
  display: flex;
  flex-direction: column;
}

.brand-text {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--hf-ink);
  line-height: 1.2;
}

.brand-text em {
  font-style: normal;
  color: var(--hf-primary);
  background: linear-gradient(120deg, var(--hf-primary), #0284c7);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-sub {
  font-size: 11px;
  color: var(--hf-text-3);
  font-weight: 500;
  letter-spacing: 0.02em;
}

.nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  background: rgba(241, 245, 249, 0.7);
  padding: 4px 6px;
  border-radius: 9999px;
  border: 1px solid rgba(226, 232, 240, 0.6);
}

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 16px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--hf-text-2);
  text-decoration: none;
  white-space: nowrap;
  border-radius: 9999px;
  transition: all var(--hf-dur) var(--hf-ease);
}

.nav-link:hover {
  color: var(--hf-ink);
  background: rgba(255, 255, 255, 0.6);
}

.nav-link.active {
  color: var(--hf-primary);
  background: #ffffff;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}

.nav-link .dot {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--hf-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  background: var(--hf-surface);
  border: 1px solid var(--hf-border);
  border-radius: 9999px;
  box-shadow: var(--hf-shadow-xs);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--hf-primary-soft);
  color: var(--hf-primary);
  font-weight: 700;
  font-size: 13px;
  display: grid;
  place-items: center;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--hf-text);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--hf-canvas-subtle);
  color: var(--hf-text-2);
  font-weight: 600;
}
.role-tag.admin { background: #fef3c7; color: #b45309; }
.role-tag.consultant { background: #e0f2fe; color: #0369a1; }

.logout-btn {
  border: 0;
  background: transparent;
  color: var(--hf-text-3);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 6px;
  transition: all var(--hf-dur);
}
.logout-btn:hover {
  color: var(--hf-bad);
  background: var(--hf-bad-soft);
}

.login-btn {
  border-radius: 9999px !important;
  padding: 8px 18px !important;
}

.main {
  flex: 1;
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  padding: 36px 32px 80px;
}

.foot {
  border-top: 1px solid var(--hf-border);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
}

.foot-inner {
  height: auto;
  min-height: 64px;
  padding-top: 16px;
  padding-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 24px;
  font-size: 12.5px;
  color: var(--hf-text-3);
}

.foot-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.foot-brand {
  font-weight: 700;
  color: var(--hf-text-2);
}

.foot-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--hf-good);
  font-weight: 600;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--hf-good);
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.2);
}

.status-divider {
  color: var(--hf-border-strong);
}

@media (max-width: 900px) {
  .bar-inner { padding: 0 16px; gap: 12px; }
  .brand-sub { display: none; }
  .main { padding: 24px 16px 56px; }
  .nav-link { padding: 0 10px; font-size: 12.5px; }
}
</style>
