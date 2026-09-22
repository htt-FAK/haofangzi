<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get } from '../api/http'
import { GRADE_COLOR, type HouseTypeCard, type Page } from '../api/types'
import { useCompareStore } from '../stores/compare'
import PlanThumb from '../components/PlanThumb.vue'

/** 户型检索（spec 002 FR-11 / 003 FR-31）。画像有值时默认回填预算与居室（AC-03）。 */
const router = useRouter()
const compare = useCompareStore()
const list = ref<HouseTypeCard[]>([])
const total = ref(0)
const loading = ref(false)
const q = reactive({
  rooms: undefined as number | undefined,
  minArea: undefined as number | undefined,
  maxArea: undefined as number | undefined,
  orientation: undefined as string | undefined,
  page: 1,
  size: 12,
})

const ORIENT: Record<string, string> = { NS: '南北', S: '正南', SE: '东南', SW: '西南', N: '北', E: '东', W: '西' }
const ORIENT_CHIPS: { key: string; label: string }[] = [
  { key: 'NS', label: '南北' },
  { key: 'S', label: '正南' },
  { key: 'SE', label: '东南' },
  { key: 'SW', label: '西南' },
]

function shortName(h: HouseTypeCard) {
  return h.name.replace(/^建面[\d.]+㎡\s*/, '').replace(/\s+/g, ' ').trim()
}

function formatPrice(raw: string | number) {
  const n = Number(raw)
  if (!Number.isFinite(n) || n <= 0) return String(raw || '询价')
  if (n >= 10000) {
    const wan = n / 10000
    const text = Number.isInteger(wan) ? String(wan) : wan.toFixed(1).replace(/\.0$/, '')
    return `${text} 万`
  }
  return `${n.toLocaleString('zh-CN')} 元`
}

async function load() {
  loading.value = true
  try {
    const p = await get<Page<HouseTypeCard>>('/house-types', { ...q })
    list.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function toggleRooms(n: number) {
  q.rooms = q.rooms === n ? undefined : n
  q.page = 1
  load()
}

function toggleOrient(v: string) {
  q.orientation = q.orientation === v ? undefined : v
  q.page = 1
  load()
}

function reset() {
  Object.assign(q, { rooms: undefined, minArea: undefined, maxArea: undefined, orientation: undefined, page: 1 })
  load()
}

function addToCompare(id: number) {
  const r = compare.add(id)
  if (!r.ok) ElMessage.warning(r.reason ?? '无法加入对比')
}

onMounted(load)
</script>

<template>
  <div class="home">
    <header class="intro">
      <p class="hf-kicker">肇庆 · 户型图册</p>
      <h1>看清楚每一套好房子</h1>
      <p class="hf-lead">采光、通风、动线、实用、静谧、绿色、经济 — 七个维度可解释打分。点进户型，先看图纸。</p>
    </header>

    <section class="filters" aria-label="筛选户型">
      <div class="group">
        <span class="glabel">居室</span>
        <button v-for="n in [2, 3, 4, 5]" :key="n" type="button" class="chip" :class="{ on: q.rooms === n }" @click="toggleRooms(n)">
          {{ n }} 房+
        </button>
      </div>
      <div class="group">
        <span class="glabel">朝向</span>
        <button v-for="o in ORIENT_CHIPS" :key="o.key" type="button" class="chip" :class="{ on: q.orientation === o.key }" @click="toggleOrient(o.key)">
          {{ o.label }}
        </button>
      </div>
      <div class="group area">
        <span class="glabel">建面</span>
        <el-input-number v-model="q.minArea" :min="30" :max="300" :step="10" controls-position="right" size="small" placeholder="最小" @change="load" />
        <span class="dash">—</span>
        <el-input-number v-model="q.maxArea" :min="30" :max="300" :step="10" controls-position="right" size="small" placeholder="最大" @change="load" />
        <span class="unit">㎡</span>
      </div>
      <button type="button" class="reset" @click="reset">重置</button>
    </section>

    <div class="grid" :aria-busy="loading">
      <template v-if="loading && !list.length">
        <div v-for="i in 6" :key="'s' + i" class="skel" aria-hidden="true" />
      </template>
      <router-link
        v-for="h in list"
        :key="h.id"
        class="listing"
        :to="{ name: 'house-type', params: { id: h.id } }"
      >
        <div class="cover">
          <PlanThumb :plan="h.plan" />
          <span class="code hf-num">{{ h.code }}</span>
          <span v-if="h.latestScore" class="badge hf-num" :style="{ background: GRADE_COLOR[h.latestLevel] || 'var(--hf-ink)' }">
            {{ h.latestScore }} · {{ h.latestLevel || '未评' }}
          </span>
        </div>
        <div class="body">
          <h3>{{ shortName(h) }}</h3>
          <p class="meta">{{ h.gfa }}㎡ · {{ h.rooms }} 房 · {{ ORIENT[h.orientation] || h.orientation }}</p>
          <p class="price hf-num">{{ formatPrice(h.priceRef) }}</p>
          <div class="ops" @click.stop>
            <el-button type="primary" size="small" @click="router.push({ name: 'evaluate', params: { id: h.id } })">评估</el-button>
            <button type="button" class="ghost" @click="addToCompare(h.id)">加入对比</button>
          </div>
        </div>
      </router-link>
      <el-empty v-if="!loading && !list.length" description="没有符合条件的户型，试着放宽面积或朝向" />
    </div>

    <el-pagination
      v-model:current-page="q.page"
      :page-size="q.size"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.intro { margin-bottom: 28px; max-width: 36em; }
.intro h1 { font-size: clamp(28px, 4vw, 40px); margin: 8px 0 10px; letter-spacing: -0.04em; }

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
  padding: 12px 16px;
  margin-bottom: 28px;
  background: var(--hf-surface);
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-m);
}
.group { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.glabel { font-size: 12px; color: var(--hf-text-3); font-weight: 600; margin-right: 2px; }
.chip {
  min-height: 44px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--hf-border);
  background: #fff;
  color: var(--hf-text-2);
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--hf-dur) var(--hf-ease), background-color var(--hf-dur) var(--hf-ease), color var(--hf-dur) var(--hf-ease);
}
.chip:hover { border-color: var(--hf-ink); color: var(--hf-ink); }
.chip.on {
  background: var(--hf-ink);
  border-color: var(--hf-ink);
  color: #fff;
}
.area :deep(.el-input-number) { width: 108px; }
.dash { color: var(--hf-text-3); }
.unit { font-size: 12px; color: var(--hf-text-3); }
.reset {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: var(--hf-text-3);
  cursor: pointer;
  min-height: 36px;
  font-size: 13px;
}
.reset:hover { color: var(--hf-ink); }

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px 16px;
  min-height: 220px;
}
.grid :deep(.el-empty) { grid-column: 1 / -1; }

.skel {
  height: 280px;
  border-radius: var(--hf-radius-m);
  background: linear-gradient(90deg, #eceae3 25%, #f7f6f2 50%, #eceae3 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s ease infinite;
}
@keyframes shimmer { to { background-position: -200% 0; } }

.listing {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  min-width: 0;
  text-decoration: none;
  color: inherit;
}
.listing:focus-visible { outline: 2px solid var(--hf-primary); outline-offset: 4px; border-radius: 12px; }

.cover {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--hf-border);
  background: var(--hf-plan-fill);
  padding: 10px;
  transition: box-shadow var(--hf-dur) var(--hf-ease), transform var(--hf-dur) var(--hf-ease);
}
.listing:hover .cover {
  transform: translateY(-2px);
  box-shadow: var(--hf-shadow-md);
}
.code {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--hf-primary-strong);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.badge {
  position: absolute;
  left: 10px;
  bottom: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.body { padding: 12px 2px 0; }
.body h3 { font-size: 16px; font-weight: 600; letter-spacing: -0.02em; }
.meta { margin-top: 4px; font-size: 13px; color: var(--hf-text-2); }
.price { margin-top: 8px; font-size: 18px; font-weight: 700; letter-spacing: -0.03em; color: var(--hf-ink); }
.ops { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
.ghost {
  border: 0;
  background: transparent;
  color: var(--hf-text-2);
  font-size: 13px;
  cursor: pointer;
  min-height: 32px;
  padding: 0 4px;
}
.ghost:hover { color: var(--hf-ink); text-decoration: underline; }

.pager { margin: 32px 0 0; justify-content: center; }

@media (max-width: 1024px) {
  .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px 16px; }
}
@media (max-width: 640px) {
  .grid { grid-template-columns: 1fr; }
  .reset { margin-left: 0; }
  .intro h1 { font-size: 26px; }
}

@media (prefers-reduced-motion: reduce) {
  .skel { animation: none; }
  .listing:hover .cover { transform: none; }
}
</style>
