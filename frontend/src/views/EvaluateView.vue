<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { get, post } from '../api/http'
import { GRADE_ARROW, GRADE_COLOR, type EvalDim, type EvalMetric, type EvaluationResult } from '../api/types'
import { useCompareStore } from '../stores/compare'
import AppIcon from '../components/AppIcon.vue'

/**
 * 评估结果页（spec 004 FR-50~54；用户手册 §5 的对照实现）。
 * 设计要点：① 分数永远来自后端规则引擎；② 每一行指标都能展开"我的值 / 命中档 / 依据条文"（NFR-11）；
 * ③ 数据不足用"— + 文字"，不只靠灰色（色弱可读）；④ 切换模板触发重算并显示 Δ。
 */
const route = useRoute()
const router = useRouter()
const compare = useCompareStore()
const htId = Number(route.params.id)
const data = ref<EvaluationResult | null>(null)
const prevTotal = ref<number | null>(null)
const loading = ref(false)
const template = ref(localStorage.getItem('hf-template') || 'GENERAL')
const openDim = ref<string | null>(null)
const radarEl = ref<HTMLElement | null>(null)
const templates = ref<{ code: string; customized: string }[]>([])
let radarChart: echarts.ECharts | null = null

const totalClass = computed(() => (data.value ? GRADE_COLOR[data.value.level] : '#333'))
const delta = computed(() =>
  data.value && prevTotal.value !== null ? +(data.value.total - prevTotal.value).toFixed(1) : null,
)

const gradeOf = (score: number | null) =>
  score === null ? '数据不足' : score >= 85 ? '优' : score >= 75 ? '良' : score >= 60 ? '中' : '差'
const dimColor = (d: EvalDim) => GRADE_COLOR[gradeOf(d.score)]
const dimPct = (d: EvalDim) => (d.score == null ? 0 : Math.max(4, Math.min(100, d.score)))

async function evaluate() {
  loading.value = true
  try {
    const next = await post<EvaluationResult>('/evaluations', { houseTypeId: htId, templateCode: template.value })
    prevTotal.value = data.value?.total ?? null
    data.value = next
    localStorage.setItem('hf-template', template.value)
    renderRadar()
  } finally {
    loading.value = false
  }
}

function metricsOf(dim: EvalDim): EvalMetric[] {
  return (data.value?.metrics ?? []).filter((m) => m.metricCode.startsWith(dim.code))
}

function addCompare() {
  const r = compare.add(htId)
  if (!r.ok) {
    ElMessage.warning(r.reason ?? '无法加入对比')
    return
  }
  ElMessage.success('已加入对比池')
  router.push({ name: 'compare' })
}

function renderRadar() {
  if (!radarEl.value || !data.value) return
  radarChart?.dispose()
  radarChart = echarts.init(radarEl.value)
  radarChart.setOption({
    radar: {
      indicator: data.value.dimensions.map((d) => ({ name: d.name, max: 100 })),
      radius: '68%',
      axisName: { color: '#475569', fontSize: 12, fontWeight: 600 },
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      splitArea: { areaStyle: { color: ['#ffffff', '#f8fafc'] } },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: data.value.dimensions.map((d) => d.score ?? 0),
        name: '维度得分',
        symbolSize: 6,
        lineStyle: { width: 2.5, color: '#155e75' },
        itemStyle: { color: '#155e75' },
        areaStyle: { color: 'rgba(21, 94, 117, 0.18)' },
      }],
    }],
    tooltip: { trigger: 'item' },
  })
}

onMounted(async () => {
  templates.value = await get('/evaluation-templates')
  await evaluate()
})
watch(template, evaluate)
</script>

<template>
  <div v-loading="loading" class="wrap">
    <!-- 面包屑与顶部引言 -->
    <div class="breadcrumb-bar">
      <router-link :to="{ name: 'house-type', params: { id: htId } }" class="back-link">
        <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10 13L5 8l5-5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        返回户型详情
      </router-link>
      <span class="bread-sep">/</span>
      <span class="bread-curr">智能量化评估报告</span>
    </div>

    <header class="eval-hero">
      <div class="hero-left">
        <div class="hf-kicker">
          <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor">
            <path d="M8 1a7 7 0 1 0 7 7A7 7 0 0 0 8 1zm0 2.5a4.5 4.5 0 0 1 4.5 4.5H8z"/>
          </svg>
          EXPLAINABLE EVALUATION REPORT · 户型量化评估报告
        </div>
        <h1 class="eval-title">{{ data?.houseTypeName?.replace(/^建面[\d.]+㎡\s*/, '') || '户型智能评估' }}</h1>
        <p class="eval-desc">
          基于外置 JSON DSL 规则引擎计算。分值由几何构件实测、标准条文与加权体系联合判定，全过程无黑盒。
        </p>
      </div>

      <div class="hero-right">
        <el-button type="primary" size="large" class="pill-btn" @click="evaluate">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M2 8a6 6 0 1 1 1.76 4.24M2 8V4m0 4h4" />
          </svg>
          重新核算
        </el-button>
        <el-button size="large" class="pill-btn" @click="addCompare">
          加入对比池
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="data?.missingCount"
      type="warning"
      :closable="false"
      show-icon
      class="miss-alert"
      :title="`有 ${data.missingCount} 项指标因公开图纸缺标注暂未录入（该维度按已录入有效指标归一化折算，不惩罚扣分）`"
    />

    <!-- 总分卡片与雷达图 -->
    <section v-if="data" class="overview-grid">
      <div class="score-card">
        <div class="score-header">
          <span class="score-label">综合住宅性能指数</span>
          <span class="score-level-badge" :style="{ background: totalClass }">
            {{ data.level }} {{ GRADE_ARROW[data.level] }}
          </span>
        </div>

        <div class="score-val-wrap">
          <span class="score-num hf-num" :style="{ color: totalClass }">{{ data.total }}</span>
          <span class="score-denom">/ 100 分</span>
        </div>

        <div class="delta-box" :class="{ positive: delta && delta > 0, negative: delta && delta < 0 }">
          <template v-if="delta !== null && delta !== 0">
            <span class="delta-sign">{{ delta > 0 ? '+' : '' }}{{ delta }}</span>
            <span class="delta-text">分（人群偏好模板加权调整结果）</span>
          </template>
          <span v-else class="delta-muted">切换下方人群模板可查看个性化权重得分变动</span>
        </div>

        <div class="meta-section">
          <div class="meta-item">
            <span class="m-k">对应户型</span>
            <span class="m-v font-bold">{{ data.houseTypeName }}</span>
          </div>
          <div class="meta-item">
            <span class="m-k">规则引擎版本</span>
            <span class="m-v"><code>{{ data.setVersion }}</code></span>
          </div>
          <div class="meta-item">
            <span class="m-k">人群偏好模板</span>
            <div class="m-v template-select">
              <el-select v-model="template" size="default" style="width: 150px">
                <el-option v-for="t in templates" :key="t.code" :label="t.code" :value="t.code" />
              </el-select>
            </div>
          </div>
        </div>
      </div>

      <div class="radar-card">
        <div class="card-inner-title">7 维雷达图分布谱系</div>
        <div ref="radarEl" class="radar" />
      </div>
    </section>

    <!-- 7 大维度卡片与展开条文明细 -->
    <section v-if="data" class="dims-section">
      <div class="section-title-row">
        <h3>各维度量化评估与实测条文（点击展开依据）</h3>
        <span class="sub-hint">点击卡片「展开依据」查看几何参数、规范条文与命中档位</span>
      </div>

      <div class="dims-grid">
        <div
          v-for="d in data.dimensions"
          :key="d.code"
          class="dim-card"
          :class="{ active: openDim === d.code }"
        >
          <div class="dim-card-header">
            <div class="dim-info">
              <span class="dim-title">{{ d.name }}</span>
              <span class="dim-weight">权重 {{ (d.weight * 100).toFixed(0) }}%</span>
            </div>
            
            <div class="dim-score-box">
              <span class="dim-score-val hf-num" :style="{ color: dimColor(d) }">
                {{ d.score ?? '—' }}
              </span>
              <span v-if="d.contribution" class="dim-contrib hf-num">贡献 {{ d.contribution }} 分</span>
            </div>
          </div>

          <!-- 进度条 -->
          <div class="progress-track" aria-hidden="true">
            <div class="progress-fill" :style="{ width: dimPct(d) + '%', background: dimColor(d) }" />
          </div>

          <!-- 底部展开按钮 -->
          <div class="dim-card-footer">
            <button
              type="button"
              class="expand-btn"
              @click="openDim = openDim === d.code ? null : d.code"
            >
              <span>{{ openDim === d.code ? '收起判定明细' : '展开条文依据' }}</span>
              <svg
                viewBox="0 0 16 16"
                width="12"
                height="12"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                class="arrow-svg"
                :class="{ rotated: openDim === d.code }"
              >
                <path d="M4 6l4 4 4-4" />
              </svg>
            </button>
          </div>

          <!-- 展开条文表格 -->
          <div v-if="openDim === d.code" class="expanded-table-wrap">
            <el-table :data="metricsOf(d)" size="small" class="dim-table" stripe>
              <el-table-column prop="metricName" label="判定指标" min-width="120">
                <template #default="{ row }">
                  <span class="font-bold">{{ row.metricName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="实测几何值" width="130">
                <template #default="{ row }">
                  <span :class="{ 'miss-txt': row.missing }" class="hf-num">
                    {{ row.value }}{{ row.missing ? ' (数据不足)' : '' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="grade" label="档位" width="90">
                <template #default="{ row }">
                  <span class="grade-cell hf-num" :style="{ color: GRADE_COLOR[row.grade] }">
                    {{ row.grade }} {{ GRADE_ARROW[row.grade] }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="score" label="得分" width="70">
                <template #default="{ row }">
                  <span class="hf-num font-bold">{{ row.score }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="evidence" label="评判依据（国家规范/条文对照）" min-width="240" />
            </el-table>
          </div>
        </div>
      </div>
    </section>

    <!-- 改进建议 -->
    <section v-if="data?.suggestions?.length" class="suggestions-section">
      <div class="sug-header">
        <AppIcon name="trending-up" :size="18" />
        <h4>户型优化潜力点与建议（按提分空间排序）</h4>
      </div>
      <div class="sug-cards-list">
        <div v-for="s in data.suggestions" :key="s.metric" class="sug-card">
          <div class="potential-pill">
            <span class="pot-plus">+</span>
            <span class="pot-num hf-num">{{ s.potential }}</span>
            <span class="pot-unit">分空间</span>
          </div>
          <div class="sug-content">
            <div class="sug-tag">{{ s.dimension }} · {{ s.metric }}</div>
            <p class="sug-text">{{ s.text }}</p>
          </div>
        </div>
      </div>
    </section>

    <p class="disclaimer-text">
      {{ data?.disclaimer || '评估结果由外置规则引擎根据国家住宅性能标准与户型图纸自动测算，不构成置业承诺。' }}
    </p>
  </div>
</template>

<style scoped>
.wrap {
  max-width: 1240px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Breadcrumb */
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--hf-text-3);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--hf-text-2);
  text-decoration: none;
  font-weight: 600;
  transition: color var(--hf-dur);
}
.back-link:hover { color: var(--hf-primary); }
.bread-sep { color: var(--hf-border-strong); }
.bread-curr { color: var(--hf-text-3); }

/* Hero */
.eval-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  flex-wrap: wrap;
}

.hero-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 60em;
}

.eval-title {
  font-size: clamp(26px, 3.2vw, 36px);
  font-weight: 800;
  color: var(--hf-ink);
  letter-spacing: -0.03em;
  margin: 0;
}

.eval-desc {
  font-size: 15px;
  color: var(--hf-text-2);
  line-height: 1.6;
}

.hero-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pill-btn {
  border-radius: 9999px !important;
  font-weight: 600 !important;
}

.miss-alert {
  border-radius: var(--hf-radius-m);
}

/* Overview Grid */
.overview-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
}

.score-card, .radar-card {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  padding: 24px;
}

.score-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.score-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--hf-text-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.score-level-badge {
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  padding: 3px 12px;
  border-radius: 9999px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}

.score-val-wrap {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.score-num {
  font-size: 64px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.04em;
}

.score-denom {
  font-size: 15px;
  font-weight: 600;
  color: var(--hf-text-3);
}

.delta-box {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--hf-text-2);
  background: var(--hf-canvas-subtle);
  padding: 8px 12px;
  border-radius: var(--hf-radius-s);
  margin-bottom: 20px;
}

.delta-box.positive {
  background: #f0fdf4;
  color: #15803d;
  font-weight: 700;
}

.delta-box.negative {
  background: #fef2f2;
  color: #b91c1c;
  font-weight: 700;
}

.meta-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px dashed var(--hf-border);
}

.meta-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.m-k {
  color: var(--hf-text-3);
  font-weight: 500;
}

.m-v {
  color: var(--hf-text);
}

.radar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
}

.card-inner-title {
  position: absolute;
  top: 18px;
  left: 24px;
  font-size: 13px;
  font-weight: 700;
  color: var(--hf-text-3);
}

.radar {
  width: 100%;
  height: 320px;
}

/* Dimensions Section */
.dims-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.section-title-row h3 {
  font-size: 18px;
  font-weight: 800;
  color: var(--hf-ink);
}

.sub-hint {
  font-size: 13px;
  color: var(--hf-text-3);
}

.dims-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.dim-card {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-xs);
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: all var(--hf-dur) var(--hf-ease);
}

.dim-card.active {
  border-color: var(--hf-primary);
  box-shadow: var(--hf-shadow-md);
  grid-column: 1 / -1;
}

.dim-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dim-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dim-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--hf-ink);
}

.dim-weight {
  font-size: 11.5px;
  color: var(--hf-text-3);
  font-weight: 600;
}

.dim-score-box {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.dim-score-val {
  font-size: 22px;
  font-weight: 800;
  line-height: 1.1;
}

.dim-contrib {
  font-size: 11px;
  color: var(--hf-text-3);
}

.progress-track {
  height: 6px;
  background: var(--hf-canvas-subtle);
  border-radius: 9999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 9999px;
  transition: width 0.5s var(--hf-ease);
}

.dim-card-footer {
  display: flex;
  justify-content: flex-end;
}

.expand-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--hf-text-2);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all var(--hf-dur);
}

.expand-btn:hover {
  color: var(--hf-primary);
  background: var(--hf-primary-soft);
}

.arrow-svg {
  transition: transform var(--hf-dur);
}

.arrow-svg.rotated {
  transform: rotate(180deg);
}

.expanded-table-wrap {
  margin-top: 10px;
  border-top: 1px solid var(--hf-border);
  padding-top: 12px;
}

.miss-txt {
  color: var(--hf-warn);
}

.font-bold {
  font-weight: 700;
}

/* Suggestions Section */
.suggestions-section {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  padding: 24px;
}

.sug-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--hf-primary);
  margin-bottom: 16px;
}

.sug-header h4 {
  font-size: 16px;
  font-weight: 700;
  color: var(--hf-ink);
  margin: 0;
}

.sug-cards-list {
  display: grid;
  gap: 12px;
}

.sug-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 14px 18px;
  background: var(--hf-canvas);
  border: 1px solid var(--hf-border);
  border-left: 4px solid var(--hf-primary);
  border-radius: var(--hf-radius-m);
}

.potential-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--hf-primary-soft);
  color: var(--hf-primary);
  padding: 6px 12px;
  border-radius: 8px;
  flex: none;
  min-width: 64px;
}

.pot-plus {
  font-size: 11px;
  line-height: 1;
}

.pot-num {
  font-size: 18px;
  font-weight: 900;
  line-height: 1;
}

.pot-unit {
  font-size: 10px;
  margin-top: 2px;
}

.sug-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sug-tag {
  font-size: 12px;
  font-weight: 700;
  color: var(--hf-primary);
}

.sug-text {
  font-size: 14px;
  color: var(--hf-text);
  line-height: 1.5;
}

.disclaimer-text {
  font-size: 12px;
  color: var(--hf-text-3);
  text-align: center;
  margin: 12px 0 24px;
}

@media (max-width: 900px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .radar {
    height: 280px;
  }
}
</style>
