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
const dimPct = (d: EvalDim) => (d.score == null ? 0 : Math.max(3, Math.min(100, d.score)))

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

// 原实现只跳转对比页而不入池，与"加入对比"文案不符；这里按文案补上入池（AC-40/41 由 store 拦截）
function addCompare() {
  const r = compare.add(htId)
  if (!r.ok) {
    ElMessage.warning(r.reason ?? '无法加入对比')
    return
  }
  router.push({ name: 'compare' })
}

function renderRadar() {
  if (!radarEl.value || !data.value) return
  radarChart?.dispose()
  radarChart = echarts.init(radarEl.value)
  radarChart.setOption({
    radar: {
      indicator: data.value.dimensions.map((d) => ({ name: d.name, max: 100 })),
      radius: '66%',
      axisName: { color: '#5c5c56', fontSize: 12 },
      splitLine: { lineStyle: { color: '#e4e2da' } },
      splitArea: { areaStyle: { color: ['#ffffff', '#f3f7f9'] } },
      axisLine: { lineStyle: { color: '#e4e2da' } },
    },
    series: [{
      type: 'radar',
      data: [{
        value: data.value.dimensions.map((d) => d.score ?? 0),
        name: '维度得分',
        symbolSize: 4,
        lineStyle: { width: 2, color: '#215e7a' },
        itemStyle: { color: '#215e7a' },
        areaStyle: { color: 'rgba(33, 94, 122, 0.16)' },
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
    <header class="intro">
      <p class="hf-kicker">评估</p>
      <h1>{{ data?.houseTypeName?.replace(/^建面[\d.]+㎡\s*/, '') || '户型评估' }}</h1>
      <p class="hf-lead">分数来自规则引擎。每一项都能展开对照条文。</p>
    </header>
    <el-alert v-if="data?.missingCount" type="warning" :closable="false" show-icon class="miss-alert"
      :title="`有 ${data.missingCount} 项指标数据不足（该维度按有效指标归一计算，不会因此扣分）`" />

    <section v-if="data" class="head">
      <el-card shadow="never" class="score-card">
        <div class="score" :style="{ color: totalClass }">
          <b class="hf-num">{{ data.total }}</b><span class="of">/100</span>
          <em :style="{ background: totalClass }">{{ data.level }} {{ GRADE_ARROW[data.level] }}</em>
        </div>
        <p class="delta hf-num" v-if="delta !== null && delta !== 0">相对上次 {{ delta > 0 ? '+' : '' }}{{ delta }} 分（模板/参数变化）</p>
        <p class="delta muted" v-else>切换人群模板可查看分数变化</p>
        <div class="score-meta">
          <div class="meta-row"><span class="k">户型</span><span class="v">{{ data.houseTypeName }}</span></div>
          <div class="meta-row"><span class="k">规则版本</span><span class="v"><code>{{ data.setVersion }}</code></span></div>
          <div class="meta-row"><span class="k">人群模板</span>
            <el-select v-model="template" size="small" style="width: 130px">
              <el-option v-for="t in templates" :key="t.code" :label="t.code" :value="t.code" />
            </el-select>
          </div>
          <div class="meta-ops">
            <el-button size="small" type="primary" @click="evaluate">重新评估</el-button>
            <el-button size="small" @click="addCompare">加入对比</el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="radar-card">
        <div ref="radarEl" class="radar" />
      </el-card>
    </section>

    <section v-if="data" class="dims">
      <el-card v-for="d in data.dimensions" :key="d.code" shadow="never" class="dim" :class="{ open: openDim === d.code }">
        <template #header>
          <div class="dimhd">
            <span class="dname">{{ d.name }}</span>
            <span class="w">权重 {{ (d.weight * 100).toFixed(0) }}%</span>
            <b class="dscore hf-num" :style="{ color: dimColor(d) }">
              {{ d.score ?? '—' }}<i v-if="d.contribution">贡献 {{ d.contribution }}</i>
            </b>
            <el-button text size="small" class="detail-btn" @click="openDim = openDim === d.code ? null : d.code">
              {{ openDim === d.code ? '收起' : '看明细' }}
              <AppIcon name="arrow-right" :size="13" :class="{ flipped: openDim === d.code }" />
            </el-button>
          </div>
        </template>
        <div class="bar" aria-hidden="true">
          <div class="fill" :style="{ width: dimPct(d) + '%', background: dimColor(d) }" />
        </div>
        <el-table v-show="openDim === d.code" :data="metricsOf(d)" size="small" class="dim-table">
          <el-table-column prop="metricName" label="指标" min-width="130" />
          <el-table-column label="我的值" width="130">
            <template #default="{ row }">
              <span :class="{ miss: row.missing }">{{ row.value }}{{ row.missing ? '（数据不足）' : '' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="grade" label="档位" width="96">
            <template #default="{ row }">
              <span class="grade-cell hf-num" :style="{ color: GRADE_COLOR[row.grade] }">{{ row.grade }} {{ GRADE_ARROW[row.grade] }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="70" />
          <el-table-column prop="evidence" label="判定依据（可解释）" min-width="260" />
        </el-table>
      </el-card>
    </section>

    <section v-if="data?.suggestions?.length" class="sug">
      <h4 class="sug-title"><AppIcon name="trending-up" :size="16" /> 改进建议（按分值提升潜力排序）</h4>
      <div class="sug-list">
        <div v-for="s in data.suggestions" :key="s.metric" class="sug-item">
          <span class="pot hf-num">+{{ s.potential }}</span>
          <div class="sug-body">
            <div class="sug-meta">{{ s.dimension }} · {{ s.metric }}</div>
            <div class="sug-text">{{ s.text }}</div>
          </div>
        </div>
      </div>
    </section>

    <p class="disc">{{ data?.disclaimer }}</p>
  </div>
</template>

<style scoped>
.wrap { max-width: 1200px; margin: 0 auto; }
.intro { margin-bottom: 18px; }
.intro h1 { font-size: 28px; letter-spacing: -0.03em; margin: 6px 0 8px; }

.miss-alert { margin-bottom: 16px; border-radius: var(--hf-radius-m); }

/* ── 总分 + 雷达 ── */
.head { display: grid; grid-template-columns: 330px 1fr; gap: 16px; margin: 4px 0 20px; }
.score-card { text-align: left; }
.score { display: flex; align-items: baseline; flex-wrap: wrap; gap: 2px; }
.score b { font-size: 56px; font-weight: 800; line-height: 1; letter-spacing: -0.02em; }
.score .of { font-size: 14px; color: var(--hf-text-3); margin-right: 10px; }
.score em {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  color: #fff;
  font-style: normal;
  font-size: 12px;
  font-weight: 600;
}
.delta { margin: 10px 0 0; color: var(--hf-text-2); font-size: 12px; }
.delta.muted { color: var(--hf-text-3); }
.score-meta { margin-top: 16px; padding-top: 14px; border-top: 1px dashed var(--hf-border); display: grid; gap: 10px; }
.meta-row { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.meta-row .k { color: var(--hf-text-3); width: 60px; flex: none; }
.meta-row .v { color: var(--hf-text); min-width: 0; overflow-wrap: anywhere; }
.meta-ops { display: flex; gap: 8px; margin-top: 2px; }
.radar-card { display: flex; align-items: center; justify-content: center; padding: 8px; }
.radar { width: 100%; height: 300px; }

/* ── 维度卡 ── */
.dims { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; align-items: start; }
.dim { border-radius: var(--hf-radius-m); transition: border-color var(--hf-dur) var(--hf-ease), box-shadow var(--hf-dur) var(--hf-ease); }
.dim.open { border-color: var(--hf-primary); box-shadow: var(--hf-shadow-sm); }
.dimhd { display: flex; align-items: center; gap: 10px; }
.dimhd .dname { font-weight: 700; font-size: 14px; }
.dimhd .w { color: var(--hf-text-3); font-size: 12px; flex: 1; }
.dimhd .dscore { font-size: 17px; font-weight: 800; }
.dimhd .dscore i { font-style: normal; font-size: 11px; color: var(--hf-text-3); font-weight: 400; margin-left: 6px; }
.detail-btn { color: var(--hf-text-2); padding: 4px 6px; }
.detail-btn:hover { color: var(--hf-primary); background: var(--hf-primary-soft); }
.detail-btn .app-icon { transition: transform var(--hf-dur) var(--hf-ease); }
.detail-btn .app-icon.flipped { transform: rotate(90deg); }
.bar { height: 6px; border-radius: 3px; background: var(--hf-border); overflow: hidden; margin-bottom: 4px; }
.bar .fill { height: 100%; border-radius: 3px; transition: width 400ms var(--hf-ease); }
.dim-table { margin-top: 8px; }
.grade-cell { font-weight: 600; }
.miss { color: var(--hf-warn); }

/* ── 建议 ── */
.sug { margin-top: 24px; }
.sug-title { display: flex; align-items: center; gap: 8px; font-size: 15px; margin-bottom: 12px; }
.sug-title .app-icon { color: var(--hf-primary); }
.sug-list { display: grid; gap: 10px; }
.sug-item {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 12px 16px;
  background: var(--hf-surface);
  border: 1px solid var(--hf-border);
  border-left: 3px solid var(--hf-primary);
  border-radius: var(--hf-radius-s);
  transition: box-shadow var(--hf-dur) var(--hf-ease);
}
.sug-item:hover { box-shadow: var(--hf-shadow-sm); }
.pot {
  flex: none;
  min-width: 46px;
  text-align: center;
  padding: 3px 0;
  border-radius: 6px;
  background: var(--hf-primary-soft);
  color: var(--hf-primary-strong);
  font-weight: 700;
  font-size: 13px;
}
.sug-meta { font-size: 12px; color: var(--hf-text-3); }
.sug-text { font-size: 13px; color: var(--hf-text); margin-top: 2px; }

.disc { color: var(--hf-text-3); font-size: 12px; margin-top: 20px; }

@media (max-width: 900px) {
  .head { grid-template-columns: 1fr; }
  .radar { height: 260px; }
}
</style>
