<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCompareStore } from './stores/compare'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

const loggedIn = computed(() => !!localStorage.getItem('hf-token'))
// 分享页与登录页不套导航壳（只读沉浸，NFR-10）
const bare = computed(() => route.name === 'share' || route.name === 'login')

function logout() {
  localStorage.removeItem('hf-token')
  router.push({ name: 'login' })
}
</script>

<template>
  <div v-if="bare" class="bare"><router-view /></div>
  <el-container v-else class="app">
    <el-header class="hd">
      <div class="brand">肇庆<span>好房子</span>· 在线选房与户型智能评估</div>
      <el-menu mode="horizontal" :default-active="String(route.name)" router :ellipsis="false">
        <el-menu-item index="home" to="/">选房</el-menu-item>
        <el-menu-item index="compare" to="/compare">对比（{{ compare.count }}）</el-menu-item>
        <el-menu-item index="advisor" to="/ai/advisor">AI 顾问</el-menu-item>
        <el-menu-item index="appointments" to="/appointments">我的预约</el-menu-item>
        <el-menu-item v-if="loggedIn" index="admin" to="/admin/rules">管理后台</el-menu-item>
      </el-menu>
      <el-button v-if="loggedIn" text @click="logout">退出</el-button>
      <el-button v-else type="primary" @click="router.push({ name: 'login' })">登录</el-button>
    </el-header>
    <el-main><router-view /></el-main>
    <el-footer class="ft">
      课程设计与演示系统 · 数据均为模拟样例 · 评估结果仅供参考，不构成购房或投资建议
    </el-footer>
  </el-container>
</template>

<style>
:root { --hf-primary: #1668dc; --hf-good: #0a8a4a; --hf-bad: #c62828; }
html, body, #app { height: 100%; margin: 0; font-family: system-ui, "PingFang SC", "Microsoft YaHei", sans-serif; }
.app { min-height: 100vh; }
.hd { display: flex; align-items: center; gap: 16px; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; white-space: nowrap; }
.brand span { color: var(--hf-primary); }
.el-menu { flex: 1; border-bottom: none !important; }
.ft { color: #888; font-size: 12px; display: flex; align-items: center; }
.bare { min-height: 100vh; }
.empty-placeholder { color: #999; padding: 24px; }
</style>
