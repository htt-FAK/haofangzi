<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post } from '../api/http'
import type { LockResult } from '../api/types'
import AppIcon from '../components/AppIcon.vue'
import PlanThumb from '../components/PlanThumb.vue'
import type { PlanBox } from '../components/PlanThumb.vue'

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

// 步骤指示：1 选房源 → 2 确认锁定 → 3 锁定期 → 4 转意向/预约
const step = computed(() => (lock.value ? (lock.value.renewCount >= 2 ? 4 : 3) : confirmed.value ? 2 : 1))
const steps = ['选择房源', '确认锁定', '锁定期', '转意向 / 预约']
const plan = computed<PlanBox[]>(() =>
  (info.value?.rooms ?? []).map((r: Record<string, unknown>) => ({
    x: Number(r.x),
    y: Number(r.y),
    w: Number(r.w),
    h: Number(r.h),
    category: String(r.category ?? ''),
  })),
)
// 锁定期总时长按续期次数放大（默认 10 分钟，续 2 次累计 ≤20 分钟）
const remainPct = computed(() => {
  const total = lock.value ? 600 * (lock.value.renewCount + 1) : 600
  return Math.max(0, Math.min(100, (remain.value / total) * 100))
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
    remain.value = lock.value?.remainSeconds ?? 0
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
    remain.value = lock.value?.remainSeconds ?? 0
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
  const q = new URLSearchParams({
    houseId: String(houseId),
    houseTypeId: String(htId),
    intentionNo: lock.value?.intentionNo ?? '',
    projectId: String(info.value?.projectId ?? 1),
  })
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
    <p class="hf-kicker">锁房</p>
    <el-page-header content="模拟选房（临时锁定）" @back="router.back()" />
    <el-alert type="warning" :closable="false" class="disc"
      title="锁定只是保留意向（默认 10 分钟，可续 2 次，累计 ≤20 分钟），不是认购、不产生任何费用或权属。" />

    <ol class="steps" aria-label="选房进度">
      <li v-for="(s, i) in steps" :key="s" :class="{ done: step > i + 1, current: step === i + 1 }">
        <span class="step-no hf-num">
          <AppIcon v-if="step > i + 1" name="check" :size="12" />
          <template v-else>{{ i + 1 }}</template>
        </span>
        <span class="step-label">{{ s }}</span>
      </li>
    </ol>

    <div v-if="info" class="card">
      <div class="thumb"><PlanThumb :plan="plan" /></div>
      <div class="meta">
        <h2>{{ info.name }}</h2>
        <p>房源 ID {{ houseId }} · 建面 {{ info.gfa }}㎡ · 朝向 {{ info.orientation }}</p>
      </div>
      <div class="code-tag hf-num">{{ info.code }}</div>
    </div>

    <el-card v-if="!lock" shadow="never" class="act">
      <el-checkbox v-model="confirmed">我已知悉：该房源对其他人显示"锁定中"，过期自动释放</el-checkbox>
      <div>
        <el-button type="primary" :loading="loading" :disabled="!confirmed || !houseId" @click="doLock">确认锁定该房源</el-button>
        <el-button @click="router.push({ name: 'evaluate', params: { id: htId } })">先看评估</el-button>
      </div>
      <el-alert v-if="conflict" type="error" :closable="false" class="conf"
        :title="`抢房失败：该房源已被他人锁定，可参考下列备选`">
        <div class="alts">
          <el-button v-for="a in conflict.alternatives ?? []" :key="a.houseId" size="small"
            @click="router.push({ name: 'selection', query: { houseId: a.houseId, houseTypeId: htId } })">
            楼层 {{ a.floorNo }} · {{ a.totalPriceWan }}
          </el-button>
        </div>
      </el-alert>
    </el-card>

    <el-card v-else shadow="never" class="locked">
      <div class="cd" :class="{ warn: remain < 120 }">
        <b class="hf-num">{{ mmss }}</b><span>后自动释放</span>
      </div>
      <div class="remain-bar" aria-hidden="true">
        <div class="remain-fill" :class="{ warn: remain < 120 }" :style="{ width: remainPct + '%' }" />
      </div>
      <p class="no">意向/锁单号：<code>{{ lock.intentionNo }}</code> 已续期 {{ lock.renewCount }} 次</p>
      <div class="ops">
        <el-button @click="renew">续期</el-button>
        <el-button type="primary" @click="toIntention" :disabled="lock.renewCount >= 2 && remain < 60">转意向（24h）</el-button>
        <el-button type="success" @click="bookVisit">去预约实地看房</el-button>
        <el-button text class="release" @click="cancel">释放房源</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page { max-width: 900px; margin: 0 auto; }
.disc { margin: 14px 0; border-radius: var(--hf-radius-m); }

/* ── 步骤条 ── */
.steps {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 0 0 18px;
  padding: 14px 18px;
  background: var(--hf-surface);
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-m);
}
.steps li { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--hf-text-3); }
.step-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  background: var(--hf-border);
  color: var(--hf-text-2);
}
.steps li.current { color: var(--hf-primary-strong); font-weight: 600; }
.steps li.current .step-no { background: var(--hf-primary); color: #fff; }
.steps li.done { color: var(--hf-text-2); }
.steps li.done .step-no { background: var(--hf-good-soft); color: var(--hf-good); }

/* ── 房源卡 ── */
.card {
  display: flex;
  gap: 14px;
  align-items: center;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-m);
  padding: 14px 16px;
  margin-bottom: 14px;
  background: var(--hf-surface);
  box-shadow: var(--hf-shadow-sm);
}
.thumb {
  width: 96px;
  height: 66px;
  border-radius: var(--hf-radius-s);
  overflow: hidden;
  border: 1px solid var(--hf-border);
  background: var(--hf-plan-fill);
  flex: none;
}
.meta { flex: 1; min-width: 0; }
.meta h2 { margin: 0 0 4px; font-size: 17px; }
.meta p { margin: 0; color: var(--hf-text-2); font-size: 13px; }
.code-tag {
  flex: none;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--hf-primary-soft);
  color: var(--hf-primary-strong);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.05em;
}

/* ── 操作区 ── */
.act { display: flex; flex-direction: column; gap: 14px; align-items: flex-start; }
.conf { margin-top: 4px; width: 100%; }
.alts { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }

/* ── 锁定中 ── */
.locked { text-align: center; padding: 28px 20px; }
.cd b { font-size: 52px; font-weight: 800; letter-spacing: 0.02em; color: var(--hf-primary); font-variant-numeric: tabular-nums; }
.cd.warn b { color: var(--hf-bad); }
.cd span { color: var(--hf-text-3); font-size: 13px; margin-left: 8px; }
.remain-bar { max-width: 420px; margin: 14px auto 0; height: 6px; border-radius: 3px; background: var(--hf-border); overflow: hidden; }
.remain-fill { height: 100%; border-radius: 3px; background: var(--hf-primary); transition: width 1s linear; }
.remain-fill.warn { background: var(--hf-bad); }
.no { color: var(--hf-text-2); font-size: 13px; margin-top: 14px; }
.ops { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; margin-top: 14px; }
.release { color: var(--hf-text-3); }
.release:hover { color: var(--hf-bad); background: var(--hf-bad-soft); }
</style>
