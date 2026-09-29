<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get } from '../api/http'
import { GRADE_COLOR, type HouseTypeCard, type Page } from '../api/types'
import { useCompareStore } from '../stores/compare'
import PlanThumb from '../components/PlanThumb.vue'
import AppIcon from '../components/AppIcon.vue'

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
  { key: 'NS', label: '南北通透' },
  { key: 'S', label: '正南' },
  { key: 'SE', label: '东南' },
  { key: 'SW', label: '西南' },
]

const AREA_PRESETS = [
  { label: '90㎡ 以下', min: undefined, max: 90 },
  { label: '90-120㎡', min: 90, max: 120 },
  { label: '120-150㎡', min: 120, max: 150 },
  { label: '150㎡ 以上', min: 150, max: undefined },
]

function isAreaPresetActive(p: { min?: number; max?: number }) {
  return q.minArea === p.min && q.maxArea === p.max
}

function selectAreaPreset(p: { min?: number; max?: number }) {
  if (isAreaPresetActive(p)) {
    q.minArea = undefined
    q.maxArea = undefined
  } else {
    q.minArea = p.min
    q.maxArea = p.max
  }
  q.page = 1
  load()
}

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

function toggleRooms(n?: number) {
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
  if (!r.ok) {
    ElMessage.warning(r.reason ?? '无法加入对比')
  } else {
    ElMessage.success('已加入多户型对比池')
  }
}

onMounted(load)
</script>

<template>
  <div class="home">
    <!-- 建筑图谱主题 Hero 标头 -->
    <header class="hero-section">
      <div class="hero-content">
        <div class="hf-kicker">
          <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor">
            <path d="M8 0L1 4v2h14V4L8 0zm-5 7v6h2V7H3zm4 0v6h2V7H7zm4 0v6h2V7h-2zM1 14v2h14v-2H1z"/>
          </svg>
          ZHAOQING ARCHITECTURAL CATALOG · 肇庆星湖宜居图册
        </div>
        <h1 class="hero-title">
          看清每套好房子的<span class="gradient-text">空间骨架与光影</span>
        </h1>
        <p class="hero-desc">
          采光 · 通风 · 动线 · 实用 · 静谧 · 绿色 · 经济 — 基于国家标准与几何图纸的 7 维全透明可解释打分。
        </p>

        <!-- 关键特性亮点药丸 -->
        <div class="hero-tags">
          <span class="feat-pill"><span class="pill-dot"></span>2D CAD制图标注与3D空间仿真</span>
          <span class="feat-pill"><span class="pill-dot"></span>外置 DSL 规则引擎透明溯源</span>
          <span class="feat-pill"><span class="pill-dot"></span>10 分钟模拟意向锁房防超卖</span>
          <span class="feat-pill"><span class="pill-dot"></span>通义千问 AI 顾问与差异结论</span>
        </div>
      </div>
    </header>

    <!-- 交互式精密筛选控制板 -->
    <section class="filter-panel" aria-label="筛选户型">
      <div class="filter-row">
        <!-- 居室筛选 -->
        <div class="filter-group">
          <span class="group-title">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.6">
              <rect x="2" y="2" width="12" height="12" rx="2" />
              <line x1="8" y1="2" x2="8" y2="14" />
              <line x1="2" y1="8" x2="14" y2="8" />
            </svg>
            居室结构
          </span>
          <div class="chips-wrap">
            <button
              type="button"
              class="chip-btn"
              :class="{ on: q.rooms === undefined }"
              @click="toggleRooms(undefined)"
            >
              全部
            </button>
            <button
              v-for="n in [2, 3, 4, 5]"
              :key="n"
              type="button"
              class="chip-btn"
              :class="{ on: q.rooms === n }"
              @click="toggleRooms(n)"
            >
              {{ n }} 房{{ n === 5 ? '+' : '' }}
            </button>
          </div>
        </div>

        <!-- 朝向筛选 -->
        <div class="filter-group">
          <span class="group-title">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.6">
              <circle cx="8" cy="8" r="6" />
              <path d="M8 2v4M8 10v4M2 8h4M10 8h4" />
            </svg>
            主采光朝向
          </span>
          <div class="chips-wrap">
            <button
              v-for="o in ORIENT_CHIPS"
              :key="o.key"
              type="button"
              class="chip-btn"
              :class="{ on: q.orientation === o.key }"
              @click="toggleOrient(o.key)"
            >
              {{ o.label }}
            </button>
          </div>
        </div>
      </div>

      <div class="filter-row filter-row-secondary">
        <!-- 面积预设与范围输入 -->
        <div class="filter-group area-group">
          <span class="group-title">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.6">
              <path d="M2 14V2h12v12H2z" />
              <path d="M2 6h12M6 2v12" />
            </svg>
            建筑面积
          </span>
          <div class="chips-wrap">
            <button
              v-for="(p, idx) in AREA_PRESETS"
              :key="idx"
              type="button"
              class="chip-btn chip-sm"
              :class="{ on: isAreaPresetActive(p) }"
              @click="selectAreaPreset(p)"
            >
              {{ p.label }}
            </button>
          </div>
          <div class="area-inputs">
            <el-input-number
              v-model="q.minArea"
              :min="30"
              :max="300"
              :step="10"
              controls-position="right"
              size="default"
              placeholder="最小"
              @change="load"
            />
            <span class="dash">—</span>
            <el-input-number
              v-model="q.maxArea"
              :min="30"
              :max="300"
              :step="10"
              controls-position="right"
              size="default"
              placeholder="最大"
              @change="load"
            />
            <span class="unit">㎡</span>
          </div>
        </div>

        <!-- 筛选反馈与重置 -->
        <div class="filter-actions">
          <span class="result-count">
            共找到 <strong class="hf-num">{{ total }}</strong> 套房型
          </span>
          <button type="button" class="reset-btn" @click="reset">
            <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.6">
              <path d="M2 8a6 6 0 1 1 1.76 4.24M2 8V4m0 4h4" />
            </svg>
            重置筛选
          </button>
        </div>
      </div>
    </section>

    <!-- 房型卡片网格列表 -->
    <div class="grid" :aria-busy="loading">
      <!-- 骨架占位 -->
      <template v-if="loading && !list.length">
        <div v-for="i in 6" :key="'s' + i" class="skel-card" aria-hidden="true">
          <div class="skel-thumb"></div>
          <div class="skel-body">
            <div class="skel-line w-60"></div>
            <div class="skel-line w-40"></div>
            <div class="skel-line w-30"></div>
          </div>
        </div>
      </template>

      <!-- 真实卡片 -->
      <router-link
        v-for="h in list"
        :key="h.id"
        class="listing-card"
        :to="{ name: 'house-type', params: { id: h.id } }"
      >
        <!-- 蓝图封面与浮动标签 -->
        <div class="cover-box">
          <PlanThumb :plan="h.plan" />
          <span class="blueprint-code hf-num">{{ h.code }}</span>
          <span
            v-if="h.latestScore"
            class="score-pill hf-num"
            :class="'level-' + (h.latestLevel || '').toLowerCase()"
            :style="{ '--score-color': GRADE_COLOR[h.latestLevel] || 'var(--hf-ink)' }"
          >
            <span class="score-num">{{ h.latestScore }}</span>
            <span class="score-level">{{ h.latestLevel || '已评' }}</span>
          </span>
          <span v-else class="score-pill unrated">未评测</span>
        </div>

        <!-- 卡片内容区 -->
        <div class="card-body">
          <div class="card-head">
            <h3 class="card-title">{{ shortName(h) }}</h3>
          </div>

          <div class="card-specs">
            <span class="spec-tag hf-num">{{ h.gfa }} ㎡</span>
            <span class="spec-tag">{{ h.rooms }} 房</span>
            <span class="spec-tag">{{ ORIENT[h.orientation] || h.orientation }}向</span>
          </div>

          <div class="card-price-row">
            <div class="price-wrap">
              <span class="price-label">参考总价</span>
              <span class="price-val hf-num">{{ formatPrice(h.priceRef) }}</span>
            </div>
            
            <div class="card-actions" @click.stop>
              <el-button
                type="primary"
                size="small"
                class="eval-btn"
                @click="router.push({ name: 'evaluate', params: { id: h.id } })"
              >
                <AppIcon name="gauge" :size="13" />
                打分
              </el-button>
              <button
                type="button"
                class="compare-btn"
                title="加入多户型对比"
                @click="addToCompare(h.id)"
              >
                <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M8 3v10M3 8h10" />
                </svg>
                对比
              </button>
            </div>
          </div>
        </div>
      </router-link>

      <!-- 空状态 -->
      <div v-if="!loading && !list.length" class="empty-state">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
        </div>
        <h4>未找到符合条件的户型图纸</h4>
        <p>您可以尝试放宽建筑面积范围或选择更多居室与朝向。</p>
        <el-button type="primary" plain @click="reset">重置筛选条件</el-button>
      </div>
    </div>

    <!-- 分页器 -->
    <div v-if="total > q.size" class="pagination-wrap">
      <el-pagination
        v-model:current-page="q.page"
        :page-size="q.size"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* Hero Section */
.hero-section {
  position: relative;
  padding: 12px 0 6px;
}

.hero-title {
  font-size: clamp(30px, 4.2vw, 44px);
  font-weight: 800;
  letter-spacing: -0.035em;
  color: var(--hf-ink);
  margin: 0 0 14px;
  line-height: 1.2;
}

.gradient-text {
  background: linear-gradient(135deg, var(--hf-primary) 0%, #0284c7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-desc {
  font-size: 16px;
  color: var(--hf-text-2);
  line-height: 1.6;
  max-width: 52em;
  margin-bottom: 20px;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.feat-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--hf-text-2);
  background: #ffffff;
  padding: 5px 12px;
  border-radius: 9999px;
  border: 1px solid var(--hf-border);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--hf-primary);
}

/* Filter Panel */
.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px;
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px 32px;
}

.filter-row-secondary {
  padding-top: 14px;
  border-top: 1px solid var(--hf-border-light);
  justify-content: space-between;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.group-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--hf-ink);
  white-space: nowrap;
}

.chips-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chip-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 16px;
  border-radius: 9999px;
  border: 1px solid var(--hf-border);
  background: var(--hf-surface);
  color: var(--hf-text-2);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--hf-dur) var(--hf-ease);
}

.chip-btn:hover {
  border-color: var(--hf-primary);
  color: var(--hf-primary);
  background: var(--hf-primary-softer);
}

.chip-btn.on {
  background: var(--hf-primary);
  border-color: var(--hf-primary);
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(21, 94, 117, 0.25);
}

.chip-sm {
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
}

.area-group {
  flex: 1;
}

.area-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.area-inputs :deep(.el-input-number) {
  width: 110px;
}

.dash {
  color: var(--hf-text-3);
  font-weight: 600;
}

.unit {
  font-size: 13px;
  font-weight: 600;
  color: var(--hf-text-3);
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.result-count {
  font-size: 13px;
  color: var(--hf-text-3);
}

.result-count strong {
  color: var(--hf-primary);
  font-size: 15px;
  font-weight: 700;
}

.reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--hf-text-3);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 6px;
  transition: all var(--hf-dur);
}

.reset-btn:hover {
  color: var(--hf-ink);
  background: var(--hf-canvas-subtle);
}

/* Listing Grid */
.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px 24px;
}

/* Listing Card */
.listing-card {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: var(--hf-radius-l);
  border: 1px solid var(--hf-border);
  overflow: hidden;
  text-decoration: none;
  color: inherit;
  box-shadow: var(--hf-shadow-sm);
  transition: transform var(--hf-dur) var(--hf-ease), box-shadow var(--hf-dur) var(--hf-ease), border-color var(--hf-dur);
}

.listing-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--hf-shadow-hover);
  border-color: rgba(21, 94, 117, 0.3);
}

.listing-card:focus-visible {
  outline: 2px solid var(--hf-primary);
  outline-offset: 4px;
}

.cover-box {
  position: relative;
  aspect-ratio: 4 / 3;
  width: 100%;
  background: var(--hf-canvas-subtle);
  border-bottom: 1px solid var(--hf-border);
  overflow: hidden;
}

.blueprint-code {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 3px 9px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #ffffff;
  background: rgba(15, 23, 42, 0.85);
  backdrop-filter: blur(8px);
  letter-spacing: 0.05em;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.15);
}

.score-pill {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 9999px;
  font-size: 12.5px;
  font-weight: 700;
  color: #ffffff;
  background: var(--score-color, var(--hf-ink));
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18);
}

.score-pill.unrated {
  background: rgba(148, 163, 184, 0.9);
  font-size: 11px;
}

.score-num {
  font-size: 13px;
}

.score-level {
  font-size: 11px;
  opacity: 0.95;
}

/* Card Body */
.card-body {
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.card-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--hf-ink);
  line-height: 1.35;
  transition: color var(--hf-dur);
}

.listing-card:hover .card-title {
  color: var(--hf-primary);
}

.card-specs {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.spec-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--hf-text-2);
  background: var(--hf-canvas-subtle);
  padding: 3px 8px;
  border-radius: 6px;
}

.card-price-row {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px dashed var(--hf-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.price-wrap {
  display: flex;
  flex-direction: column;
}

.price-label {
  font-size: 11px;
  color: var(--hf-text-3);
  font-weight: 500;
}

.price-val {
  font-size: 19px;
  font-weight: 800;
  color: var(--hf-ink);
  line-height: 1.2;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.eval-btn {
  border-radius: 9999px !important;
  font-size: 12px !important;
  padding: 6px 14px !important;
}

.compare-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--hf-border);
  background: var(--hf-surface);
  color: var(--hf-text-2);
  font-size: 12px;
  font-weight: 600;
  border-radius: 9999px;
  cursor: pointer;
  transition: all var(--hf-dur);
}

.compare-btn:hover {
  border-color: var(--hf-ink);
  color: var(--hf-ink);
  background: var(--hf-canvas-subtle);
}

/* Skeletons */
.skel-card {
  height: 340px;
  border-radius: var(--hf-radius-l);
  background: #ffffff;
  border: 1px solid var(--hf-border);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.skel-thumb {
  aspect-ratio: 4 / 3;
  background: linear-gradient(90deg, #f1f5f9 25%, #f8fafc 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease infinite;
}

.skel-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skel-line {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f1f5f9 25%, #f8fafc 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease infinite;
}
.skel-line.w-60 { width: 60%; }
.skel-line.w-40 { width: 40%; }
.skel-line.w-30 { width: 30%; }

@keyframes shimmer {
  to { background-position: -200% 0; }
}

/* Empty State */
.empty-state {
  grid-column: 1 / -1;
  padding: 60px 20px;
  text-align: center;
  background: #ffffff;
  border: 1px dashed var(--hf-border-strong);
  border-radius: var(--hf-radius-l);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--hf-primary-soft);
  color: var(--hf-primary);
  display: grid;
  place-items: center;
  margin-bottom: 4px;
}

.empty-state h4 {
  font-size: 18px;
  color: var(--hf-ink);
}

.empty-state p {
  color: var(--hf-text-2);
  font-size: 14px;
  max-width: 24em;
  margin-bottom: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

@media (max-width: 1080px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .filter-panel {
    padding: 16px;
  }
  .filter-actions {
    margin-left: 0;
    width: 100%;
    justify-content: space-between;
  }
}
</style>
