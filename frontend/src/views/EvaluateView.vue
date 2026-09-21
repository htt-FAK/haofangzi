<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { get, post } from '../api/http'
import { GRADE_ARROW, GRADE_COLOR, type EvalDim, type EvalMetric, type EvaluationResult } from '../api/types'

/**
 * 评估结果页（spec 004 FR-50~54；用户手册 §5 的对照实现）。
 * 设计要点：① 分数永远来自后端规则引擎；② 每一行指标都能展开"我的值 / 命中档 / 依据条文"（NFR-11）；
 * ③ 数据不足用"— + 文字"，不只靠灰色（色弱可读）；④ 切换模板触发重算并显示 Δ。
 */
const route = useRoute()
const htId = Number(route.params.id)
const data = ref<EvaluationResult | null>(null)
const prevTotal = ref<number | null>(null)
const loading = ref(false)
const template = ref(localStorage.getItem('hf-template') || 'GENERAL')
const openDim = ref<string | null>(null)
const radarEl = ref<HTMLElement | null>(null)
const templates = ref<{ code: string; customized: string }[]>([])

const totalClass = computed(() => (data.value ? GRADE_COLOR[data.value.level] : '#333'))
const delta = computed(() =>
  data.value && prevTotal.value !== null ? +(data.value.total - prevTotal.value).toFixed(1) : null,
)

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

function renderRadar() {
  if (!radarEl.value || !data.value) return
  const chart = echarts.init(radarEl.value)
  chart.setOption({
    radar: {
      indicator: data.value.dimensions.map((d) => ({ name: d.name, max: 100 })),
      radius: '68%',
    },
    series: [{
      type: 'radar',
      data: [{
        value: data.value.dimensions.map((d) => d.score ?? 0),
        name: '维度得分',
        areaStyle: { opacity: 0.25 },
        lineStyle: { width: 2 },
      }],
    }],
    tooltip: {},
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
    <el-alert v-if="data?.missingCount" type="warning" :closable="false" show-icon
      :title="`有 ${data.missingCount} 项指标数据不足（该维度按有效指标归一计算，不会因此扣分）`" />

    <section v-if="data" class="head">
      <div class="score" :style="{ color: totalClass }">
        <b>{{ data.total }}</b><span>/100</span>
        <em :style="{ background: totalClass }">{{ data.level }} {{ GRADE_ARROW[data.level] }}</em>
        <p class="delta" v-if="delta !== null && delta !== 0">相对上次 {{ delta > 0 ? '+' : '' }}{{ delta }} 分（模板/参数变化）</p>
      </div>
      <div ref="radarEl" class="radar" />
      <div class="meta">
        <div>户型：{{ data.houseTypeName }}</div>
        <div>规则版本：<code>{{ data.setVersion }}</code></div>
        <div>人群模板：
          <el-select v-model="template" size="small" style="width: 150px">
            <el-option v-for="t in templates" :key="t.code" :label="t.code" :value="t.code" />
          </el-select>
        </div>
        <el-button size="small" type="primary" @click="evaluate">重新评估</el-button>
        <el-button size="small" @click="$router.push({ name: 'compare' })">加入对比</el-button>
      </div>
    </section>

    <section v-if="data" class="dims">
      <el-card v-for="d in data.dimensions" :key="d.code" shadow="hover" class="dim">
        <template #header>
          <div class="dimhd">
            <span>{{ d.name }}</span>
            <span class="w">权重 {{ (d.weight * 100).toFixed(0) }}%</span>
            <b :style="{ color: d.score === null ? '#9e9e9e' : GRADE_COLOR[
              d.score >= 85 ? '优' : d.score >= 75 ? '良' : d.score >= 60 ? '中' : '差'] }">
              {{ d.score ?? '—' }}<i v-if="d.contrib"> / 贡献 {{ d.contrib }}</i>
            </b>
            <el-button text size="small" @click="openDim = openDim === d.code ? null : d.code">
              {{ openDim === d.code ? '收起' : '看明细' }}
            </el-button>
          </div>
        </template>
        <el-table v-show="openDim === d.code" :data="metricsOf(d)" size="small">
          <el-table-column prop="metricName" label="指标" min-width="130" />
          <el-table-column label="我的值" width="110">
            <template #default="{ row }">
              <span :class="{ miss: row.missing }">{{ row.value }}{{ row.missing ? '（数据不足）' : '' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="grade" label="档位" width="86">
            <template #default="{ row }">
              <span :style="{ color: GRADE_COLOR[row.grade] }">{{ row.grade }} {{ GRADE_ARROW[row.grade] }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="70" />
          <el-table-column prop="evidence" label="判定依据（可解释）" min-width="260" />
        </el-table>
      </el-card>
    </section>

    <section v-if="data?.suggestions?.length" class="sug">
      <h4>改进建议（按分值提升潜力排序）</h4>
      <el-timeline>
        <el-timeline-item v-for="s in data.suggestions" :key="s.metric" :timestamp="`潜在 +${s.potential}`">
          {{ s.dimension }} · {{ s.metric }}：{{ s.text }}
        </el-timeline-item>
      </el-timeline>
    </section>

    <p class="disc">{{ data?.disclaimer }}</p>
  </div>
</template>

<style scoped>
.wrap { max-width: 1180px; margin: 0 auto; }
.head { display: grid; grid-template-columns: 220px 360px 1fr; gap: 20px; align-items: center; margin: 16px 0; }
.score b { font-size: 46px; }
.score span { font-size: 14px; color: #999; }
.score em { display: inline-block; margin-left: 8px; padding: 2px 8px; border-radius: 12px; color: #fff; font-style: normal; font-size: 12px; }
.delta { margin: 6px 0 0; color: #666; font-size: 12px; }
.radar { height: 260px; }
.meta { font-size: 13px; line-height: 2; }
.dims { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 12px; }
.dimhd { display: flex; align-items: center; gap: 10px; }
.dimhd .w { color: #888; font-size: 12px; flex: 1; }
.dimhd i { font-style: normal; font-size: 12px; color: #777; font-weight: 400; }
.miss { color: #a05a00; }
.sug { margin-top: 18px; }
.disc { color: #999; font-size: 12px; margin-top: 18px; }
</style>
