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
const role = computed(() => {
  sessionTick.value
  try {
    return JSON.parse(localStorage.getItem('hf-user') || 'null')?.role as string | undefined
  } catch {
    return undefined
  }
})
const isStaff = computed(() => role.value === 'ADMIN' || role.value === 'CONSULTANT')
const bare = computed(() => route.name === 'share' || route.name === 'login')

interface NavItem { name: string; label: string; to: string; badge?: number; staffOnly?: boolean }
const nav = computed<NavItem[]>(() => [
  { name: 'home', label: '选房', to: '/' },
  { name: 'compare', label: '对比', to: '/compare', badge: compare.count },
  { name: 'advisor', label: 'AI 顾问', to: '/ai/advisor' },
  { name: 'appointments', label: '预约', to: '/appointments' },
  { name: 'admin', label: '管理', to: '/admin/rules', staffOnly: true },
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
          <span class="mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7">
              <rect x="3.5" y="4" width="10" height="7.5" rx="0.6" />
              <rect x="13.5" y="4" width="7" height="4.5" rx="0.6" />
              <rect x="13.5" y="9" width="7" height="11" rx="0.6" />
              <rect x="3.5" y="12" width="10" height="8" rx="0.6" />
            </svg>
          </span>
          <span class="brand-text">肇庆<em>好房子</em></span>
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
            {{ item.label }}
            <span v-if="item.badge" class="dot hf-num">{{ item.badge }}</span>
          </router-link>
        </nav>
        <div class="actions">
          <button v-if="loggedIn" type="button" class="text-btn" @click="logout">退出</button>
          <el-button v-else type="primary" @click="router.push({ name: 'login' })">登录</el-button>
        </div>
      </div>
    </header>

    <main class="main"><router-view /></main>

    <footer class="foot">
      <div class="bar-inner foot-inner">
        <span class="foot-brand">肇庆好房子</span>
        <span class="foot-note">课程演示 · 模拟数据 · 评估结果仅供参考，不构成购房或投资建议</span>
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
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: saturate(1.2) blur(12px);
  border-bottom: 1px solid var(--hf-border);
}

.bar-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 28px;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--hf-ink);
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: -0.02em;
}
.brand:hover { color: var(--hf-ink); }

.brand .mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--hf-primary);
  background: var(--hf-plan-fill);
  border: 1px solid var(--hf-border);
}

.brand em {
  font-style: normal;
  color: var(--hf-primary);
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.nav::-webkit-scrollbar { display: none; }

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 64px;
  padding: 0 12px;
  font-size: 14px;
  color: var(--hf-text-2);
  text-decoration: none;
  white-space: nowrap;
  transition: color var(--hf-dur) var(--hf-ease);
}
.nav-link:hover { color: var(--hf-ink); }
.nav-link.active {
  color: var(--hf-ink);
  font-weight: 600;
}
.nav-link.active::after {
  content: "";
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 2px;
  background: var(--hf-ink);
  border-radius: 1px;
}

.nav-link .dot {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--hf-ink);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
}

.actions { display: flex; align-items: center; gap: 8px; }

.text-btn {
  border: 0;
  background: transparent;
  color: var(--hf-text-2);
  font-size: 14px;
  cursor: pointer;
  min-height: 44px;
  padding: 0 10px;
}
.text-btn:hover { color: var(--hf-ink); }

.main {
  flex: 1;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 28px 72px;
}

.foot {
  border-top: 1px solid var(--hf-border);
  background: rgba(255, 255, 255, 0.7);
}

.foot-inner {
  height: auto;
  min-height: 56px;
  padding-top: 12px;
  padding-bottom: 12px;
  flex-wrap: wrap;
  gap: 4px 16px;
  font-size: 12px;
  color: var(--hf-text-3);
}

.foot-brand { font-weight: 600; color: var(--hf-text-2); letter-spacing: 0.04em; }
.foot-note { flex: 1; }

@media (max-width: 768px) {
  .bar-inner { padding: 0 16px; gap: 12px; }
  .brand-text { display: none; }
  .main { padding: 20px 16px 48px; }
  .nav-link { padding: 0 10px; }
}
</style>
