<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post } from '../api/http'
import type { LockResult } from '../api/types'

/**
 * 选房（锁定）流程（spec 003 FR-31~36、AC-20/21）：
 * 收藏页或房源详情点"模拟选房"进入本页 → 拉房源信息 → 二次确认 → 锁定 → 倒计时 → 转意向 / 生成预约。
 * 服务端是唯一权威（锁 TTL 由后端返回），前端只显示服务端剩余秒并本地递减。
 */
const route = useRoute()
const router = useRouter()
const houseId = Number(route.query.houseId ?? 0)
const htId = Number(route.query.houseTypeId ?? 0)
const loading = ref(false)
const confirmed = ref(false)
const lock = ref<LockResult | null>(null)
const remain = ref(0)
const conflict = ref<LockResult | null>(null)
let timer = 0

const info = ref<Record<string, any> | null>(null)
const mmss = computed(() => {
  const m = Math.floor(remain.value / 60)
  const s = String(remain.value % 60).padStart(2, '0')
  return `${m}:${s}`
})

async function loadHouse() {
  if (!htId) return
  info.value = await get(`/house-types/${htId}`)
}

async function doLock() {
  if (!confirmed.value) return ElMessage.warning('请先阅读并确认锁房规则')
  loading.value = true
  try {
    lock.value = await post('/selection/locks', { houseId, confirm: true })
    conflict.value = null
    remain.value = lock.value.remainSeconds
    timer = window.setInterval(() => {
      remain.value = Math.max(0, remain.value - 1)
      if (remain.value === 0) window.clearInterval(timer)
    }, 1000)
  } catch (e: any) {
    if (e?.code === 40910) conflict.value = e.data ?? {}     // 他人已锁：展示备选（AC-20）
    else if (e?.code === 40911) ElMessage.error('该房源当前不可选')
    else if (e?.code === 40013) ElMessage.error('请先勾选确认')
  } finally {
    loading.value = false
  }
}

async function renew() {
  try {
    lock.value = await post(`/selection/locks/${lock.value!.intentionNo}/renew`, {})
    remain.value = lock.value.remainSeconds
  } catch (e: any) {
    if (e?.code === 40912) ElMessage.error('续期次数已用完，请转意向或释放')
  }
}

async function cancel() {
  await ElMessageBox.confirm('释放后房源立即回到"可选"，需重新抢锁，确定吗？', '释放房源', { type: 'warning' })
  await post(`/selection/locks/${lock.value!.intentionNo}/cancel`, {})
  lock.value = null
  window.clearInterval(timer)
}

async function toIntention() {
  lock.value = await post(`/selection/locks/${lock.value!.intentionNo}/convert`, {})
  ElMessage.success(`已转意向（24 小时预留），意向号 ${lock.value?.intentionNo}`)
}

function bookVisit() {
  const q = new URLSearchParams({ houseId: String(houseId), intentionNo: lock.value?.intentionNo ?? '' })
  router.push(`/appointments?${q}`)
}

onMounted(async () => {
  await loadHouse()
  const cur = await get<LockResult | null>('/selection/locks/current')
  if (cur) {
    lock.value = cur
    remain.value = cur.remainSeconds
  }
})
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div class="page">
    <el-page-header content="模拟选房（临时锁定）" @back="router.back()" />
    <el-alert type="warning" :closable="false" class="disc"
      title="锁定只是保留意向（默认 10 分钟，可续 2 次，累计 ≤20 分钟），不是认购、不产生任何费用或权属。" />

    <div v-if="info" class="card">
      <div class="thumb">{{ info.code }}</div>
      <div class="meta">
        <h2>{{ info.name }}</h2>
        <p>房源 ID {{ houseId }} · 建面 {{ info.gfa }}㎡ · 朝向 {{ info.orientation }}</p>
      </div>
    </div>

    <el-card v-if="!lock" shadow="never" class="act">
      <el-checkbox v-model="confirmed">我已知悉：该房源对其他人显示"锁定中"，过期自动释放</el-checkbox>
      <div>
        <el-button type="primary" :loading="loading" :disabled="!confirmed || !houseId" @click="doLock">确认锁定该房源</el-button>
        <el-button @click="router.push({ name: 'evaluate', params: { id: htId } })">先看评估</el-button>
      </div>
      <el-alert v-if="conflict" type="error" :closable="false" class="conf"
        :title="`抢房失败：该房源已被他人锁定，可参考下列备选`">
        <el-button v-for="a in conflict.alternatives ?? []" :key="a.houseId" size="small"
          @click="router.push({ name: 'selection', query: { houseId: a.houseId, houseTypeId: htId } })">
          楼层 {{ a.floorNo }} · {{ a.totalPriceWan }}
        </el-button>
      </el-alert>
    </el-card>

    <el-card v-else shadow="never" class="locked">
      <div class="cd" :class="{ warn: remain < 120 }">
        <b>{{ mmss }}</b><span>后自动释放</span>
      </div>
      <p class="no">意向/锁单号：<code>{{ lock.intentionNo }}</code> 已续期 {{ lock.renewCount }} 次</p>
      <div class="ops">
        <el-button @click="renew">续期</el-button>
        <el-button type="primary" @click="toIntention" :disabled="lock.renewCount >= 2 && remain < 60">转意向（24h）</el-button>
        <el-button type="success" @click="bookVisit">去预约实地看房</el-button>
        <el-button text @click="cancel">释放房源</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page { max-width: 900px; margin: 0 auto; }
.disc { margin: 12px 0; }
.card { display: flex; gap: 14px; align-items: center; border: 1px solid #eee; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.thumb { width: 96px; height: 64px; border-radius: 6px; background: #eef3fb; color: #4b6fa8; display: flex; align-items: center; justify-content: center; }
.meta h2 { margin: 0 0 4px; font-size: 17px; }
.meta p { margin: 0; color: #666; font-size: 13px; }
.act { display: flex; flex-direction: column; gap: 12px; }
.conf { margin-top: 4px; display: flex; gap: 8px; }
.locked { text-align: center; }
.cd b { font-size: 42px; letter-spacing: 1px; color: var(--hf-primary); }
.cd.warn b { color: var(--hf-bad); }
.cd span { color: #888; font-size: 13px; margin-left: 6px; }
.no { color: #666; font-size: 13px; }
.ops { display: flex; gap: 10px; justify-content: center; margin-top: 8px; }
</style>
