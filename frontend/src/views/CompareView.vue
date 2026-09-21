<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { get, post } from '../api/http'
import { GRADE_COLOR, type EvaluationResult } from '../api/types'
import { useCompareStore } from '../stores/compare'

/**
 * 多户型比较（spec 005 / FR-30~36）。客户端聚合各户型评估结果生成矩阵：
 * 行 = 总分 + 7 维 + 关键规格；列 = 候选户型；best/worst 高亮并给出与最优差值（AC-19）。
 * AI 结论优先，失败或降级时回落到确定性差异文案（FR-35）。
 */
const router = useRouter()
const store = useCompareStore()
const rows = ref<{ label: string; key: string; higherBetter: boolean; fmt?: (v: number) => string }[]>([
  { label: '总分', key: 'total', higherBetter: true, fmt: (v) => v.toFixed(1) },
  { label: '采光与日照', key: 'LIGHT', higherBetter: true },
  { label: '通风与对流', key: 'VENT', higherBetter: true },
  { label: '动线与分区', key: 'CIRC', higherBetter: true },
  { label: '实用与得房', key: 'UTIL', higherBetter: true },
  { label: '静谧与干扰', key: 'QUIET', higherBetter: true },
  { label: '绿色与舒适', key: 'GREEN', higherBetter: true },
  { label: '经济适配', key: 'COST', higherBetter: true },
])
const cols = ref<EvaluationResult[]>([])
const loading = ref(false)
const conclusion = ref<{ text: string; source: 'ai' | 'fallback' } | null>(null)
const share = ref<{ url: string; expireAt: string } | null>(null)

const cell = (row: (typeof rows.value)[number], col: EvaluationResult) => {
  if (row.key === 'total') return col.total
  const dim = col.dimensions.find((d) => d.code === row.key)
  return dim?.score ?? 0
}

function matrixOf(row: (typeof rows.value)[number]) {
  const vals = cols.value.map((c) => ({ id: c.houseTypeId, v: cell(row, c) }))
  const best = Math.max(...vals.map((x) => x.v))
  const worst = Math.min(...vals.map((x) => x.v))
  return (id: number, v: number) => {
    if (best === worst) return ''
    if (row.higherBetter && v === best) return 'best'
    if (!row.higherBetter && v === worst) return 'best'
    if (row.higherBetter && v === worst) return 'worst'
    return ''
  }
}

const bestId = computed(() => {
  if (!cols.value.length) return null
  return cols.value.reduce((a, b) => (a.total >= b.total ? a : b)).houseTypeId
})

const gap = (col: EvaluationResult) => {
  const best = cols.value.reduce((a, b) => (a.total >= b.total ? a : b))
  const d = +(col.total - best.total).toFixed(1)
  return d === 0 ? '最优' : `${d.toFixed(1)} 分`
}

function localSummary() {
  const top = cols.value.find((c) => c.houseTypeId === bestId.value)
  const dim = top?.dimensions.filter((d) => d.score !== null).sort((a, b) => b.score! - a.score!)[0]
  const low = top?.dimensions.filter((d) => d.score !== null).sort((a, b) => a.score! - b.score!)[0]
  return {
    text: `综合表现推荐 ${top?.houseTypeName}（${top?.total} 分 / ${top?.level}）。`
      + `最强项：${dim?.name} ${(dim?.score ?? 0).toFixed(1)}；最弱项：${low?.name} ${(low?.score ?? 0).toFixed(1)}。`
      + `关注：${top?.suggestions.slice(0, 2).map((s) => s.metric).join('、') || '无'}。`
      + `规则版本 ${top?.setVersion}，${top?.missingCount ?? 0} 项数据不足；分数由规则引擎确定性计算。`,
    source: 'fallback' as const,
  }
}

async function loadAll() {
  loading.value = true
  try {
    const results = await Promise.all(store.ids.map(async (id) => {
      const d = await get<EvaluationResult>(`/house-types/${id}/evaluation/latest`)
      return d
    }))
    cols.value = results.filter(Boolean) as EvaluationResult[]
    if (!cols.value.length) {
      ElMessage.warning('还没有评估结果，先给候选打分')
      return
    }
    await summarize()
  } finally {
    loading.value = false
  }
}

async function summarize() {
  if (cols.value.length < 2) {
    conclusion.value = null
    return
  }
  try {
    const payload = { ids: cols.value.map((c) => c.houseTypeId), scores: cols.value.map((c) => ({ id: c.houseTypeId, name: c.houseTypeName, total: c.total, dims: c.dimensions })) }
    const d = await post<{ text: string; source?: string }>('/compare/conclusion', payload)
    conclusion.value = { text: d.text, source: d.source === 'ai' ? 'ai' : 'fallback' }
  } catch {
    conclusion.value = localSummary()
  }
}

async function createShare() {
  const d = await post<{ url: string; shareToken?: string; expireAt: string; source: string }>('/compare/share', {
    ids: cols.value.map((c) => c.houseTypeId), summary: conclusion.value,
  })
  share.value = { url: `${location.origin}/#/share/${d.shareToken ?? ''}?src=${d.source}`.replace(/#\/share\/\?/,'#/share?'), expireAt: d.expireAt }
}

watch(() => store.ids.length, (n) => { if (n >= 1) loadAll() }, { immediate: true })
</script>

<template>
  <div class="cmp">
    <div class="top">
      <h1>多方案比较（{{ cols.length }}）</h1>
      <el-button size="small" :disabled="cols.length < 2" @click="createShare">生成分享链接（3 分钟）</el-button>
      <el-button size="small" @click="loadAll">刷新</el-button>
      <el-button size="small" text @click="router.push('/')">继续选房</el-button>
    </div>
    <p class="tip">候选来自收藏与评估历史；至少 2 个才生成对比结论（FR-30）。分数全部来自规则引擎，AI 仅写文字。</p>

    <div v-if="share" class="share">分享链接：<code>{{ share.url }}</code><span class="exp">有效期至 {{ share.expireAt }}</span></div>

    <el-table v-loading="loading" :data="rows" border class="mtx" size="small">
      <el-table-column prop="label" label="指标" width="130" fixed />
      <el-table-column v-for="c in cols" :key="c.houseTypeId" :label="c.houseTypeName" min-width="150">
        <template #header>
          <div class="ch">
            <b>{{ c.houseTypeName }}</b>
            <el-tag v-if="c.houseTypeId === bestId" size="small" type="success" effect="dark">推荐</el-tag>
            <div class="score" :style="{ color: GRADE_COLOR[c.level] }">{{ c.total.toFixed(1) }} · {{ c.level }}
              <i>（{{ gap(c) }}）</i>
            </div>
            <div class="sub">建面 {{ (c as any).gfa ?? '—' }}㎡ · 规则 {{ c.setVersion }}</div>
          </div>
        </template>
        <template #default="{ row }">
          <span :class="matrixOf(row)(c.houseTypeId, cell(row, c))">{{ cell(row, c).toFixed(1) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-card v-if="conclusion" class="concl" shadow="never">
      <template #header>对比结论 <el-tag size="small" :type="conclusion.source === 'ai' ? 'primary' : 'info'">{{ conclusion.source === 'ai' ? 'AI 生成' : '模板回落' }}</el-tag></template>
      <!-- 结论为纯文本插值渲染（不 v-html），防 XSS：spec 007 FR 与宪法安全基线 -->
      <p class="txt">{{ conclusion.text }}</p>
      <p v-if="conclusion.source === 'ai'" class="hint">内容由大模型生成，仅供参考，不构成购房建议（FR-35）。</p>
    </el-card>

    <el-empty v-if="!loading && !cols.length" description="还没有已评估的候选户型">
      <el-button type="primary" @click="router.push('/')">去选房并评估</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.cmp { max-width: 1200px; margin: 0 auto; }
.top { display: flex; align-items: center; gap: 10px; margin: 6px 0 2px; }
.top h1 { font-size: 20px; margin: 0; flex: 1; }
.tip { color: #777; font-size: 12px; margin: 2px 0 10px; }
.share { background: #f0f7ff; border: 1px solid #d6e8ff; padding: 8px 10px; border-radius: 6px; font-size: 13px; margin-bottom: 10px; }
.share .exp { color: #888; font-size: 12px; margin-left: 8px; }
.mtx { margin-bottom: 14px; }
.ch { line-height: 1.35; }
.ch .score { font-size: 15px; }
.ch .sub { color: #999; font-size: 11px; }
.best { color: var(--hf-good); font-weight: 600; }
.worst { color: var(--hf-bad); }
.concl p { margin: 6px 0; font-size: 14px; }
.concl .hint { color: #a05a00; font-size: 12px; }
</style>
