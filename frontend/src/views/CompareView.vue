<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { get, post } from '../api/http'
import { sensitivityNote } from '../utils/sensitivity'
import { GRADE_COLOR, type EvaluationResult } from '../api/types'
import { useCompareStore } from '../stores/compare'
import AppIcon from '../components/AppIcon.vue'

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

const sensitivity = computed(() => sensitivityNote(cols.value.map((c) => ({
  houseTypeId: c.houseTypeId,
  total: c.total,
  dimensions: c.dimensions.map((d) => ({ code: d.code, weight: d.weight, score: d.score })),
}))))

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
    conclusion.value = cols.value.length >= 2 ? localSummary() : null
    void summarize()
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
    const payload = { ids: cols.value.map((c) => c.houseTypeId), scores: cols.value.map((c) => ({ id: c.houseTypeId, name: c.houseTypeName, total: c.total })) }
    const d = await post<{ text: string; source?: string }>('/ai/report-conclusion', payload)
    conclusion.value = { text: d.text, source: d.source === 'ai' ? 'ai' : 'fallback' }
  } catch {
    conclusion.value = localSummary()
  }
}

async function createShare() {
  const report = await post<{ id: number }>('/compare-reports', {
    houseTypeIds: cols.value.map((c) => c.houseTypeId),
    summary: conclusion.value,
  })
  const d = await post<{ token?: string; url?: string; expireAt: string }>(`/compare-reports/${report.id}/share`)
  share.value = { url: `${location.origin}/share/reports/${d.token ?? ''}`, expireAt: d.expireAt }
}

async function copyShare() {
  if (!share.value) return
  try {
    await navigator.clipboard.writeText(share.value.url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择链接复制')
  }
}

watch(() => store.ids.length, (n) => { if (n >= 1) loadAll() }, { immediate: true })
</script>

<template>
  <div class="cmp">
    <header class="intro">
      <p class="hf-kicker">对比</p>
      <div class="top">
        <h1>多方案比较</h1>
        <el-button :disabled="cols.length < 2" @click="createShare">生成分享链接</el-button>
        <el-button @click="loadAll">刷新</el-button>
        <el-button text @click="router.push('/')">继续选房</el-button>
      </div>
      <p class="hf-lead">候选来自收藏与评估历史；至少 2 个才生成对比结论。分数来自规则引擎，AI 只写文字。</p>
    </header>

    <div v-if="share" class="share">
      <AppIcon name="link" :size="15" />
      <code class="url">{{ share.url }}</code>
      <span class="exp hf-num">有效期至 {{ share.expireAt }}</span>
      <el-button size="small" text class="copy-btn" @click="copyShare">复制</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border class="mtx" size="small">
      <el-table-column prop="label" label="指标" width="130" fixed />
      <el-table-column v-for="c in cols" :key="c.houseTypeId" :label="c.houseTypeName" min-width="150">
        <template #header>
          <div class="ch">
            <b>{{ c.houseTypeName }}</b>
            <el-tag v-if="c.houseTypeId === bestId" size="small" type="success" effect="dark" class="rec-tag">推荐</el-tag>
            <div class="score hf-num" :style="{ color: GRADE_COLOR[c.level] }">{{ c.total.toFixed(1) }} · {{ c.level }}
              <i>（{{ gap(c) }}）</i>
            </div>
            <div class="sub">建面 {{ c.gfa ?? '—' }}㎡ · 规则 {{ c.setVersion }}</div>
          </div>
        </template>
        <template #default="{ row }">
          <span class="cell-val hf-num" :class="matrixOf(row)(c.houseTypeId, cell(row, c))">{{ cell(row, c).toFixed(1) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-card v-if="conclusion" class="concl" shadow="never">
      <template #header>
        <span class="concl-title"><AppIcon name="message" :size="15" /> 对比结论</span>
        <el-tag size="small" :type="conclusion.source === 'ai' ? 'primary' : 'info'">{{ conclusion.source === 'ai' ? 'AI 生成' : '模板回落' }}</el-tag>
      </template>
      <!-- 结论为纯文本插值渲染（不 v-html），防 XSS：spec 007 FR 与宪法安全基线 -->
      <p class="txt">{{ conclusion.text }}</p>
      <p v-if="sensitivity" class="hint">{{ sensitivity }}</p>
      <p v-if="conclusion.source === 'ai'" class="hint">内容由大模型生成，仅供参考，不构成购房建议（FR-35）。</p>
    </el-card>

    <el-empty v-if="!loading && !cols.length" description="还没有已评估的候选户型">
      <el-button type="primary" @click="router.push('/')">去选房并评估</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.cmp { max-width: 1200px; margin: 0 auto; }
.intro { margin-bottom: 22px; }
.intro h1 { font-size: 28px; letter-spacing: -0.03em; }
.top { display: flex; align-items: center; gap: 10px; margin: 8px 0 10px; flex-wrap: wrap; }
.top h1 { flex: 1; margin: 0; }

.share {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  background: var(--hf-primary-softer);
  border: 1px solid var(--hf-primary-soft);
  padding: 9px 12px;
  border-radius: var(--hf-radius-s);
  font-size: 13px;
  margin-bottom: 12px;
  color: var(--hf-primary-strong);
}
.share .url { background: transparent; border: none; padding: 0; color: var(--hf-primary-strong); overflow-wrap: anywhere; }
.share .exp { color: var(--hf-text-3); font-size: 12px; }
.copy-btn { margin-left: auto; color: var(--hf-primary); }

.mtx { margin-bottom: 16px; border-radius: var(--hf-radius-s); overflow: hidden; }
.ch { line-height: 1.4; }
.ch b { font-size: 13px; }
.rec-tag { margin-left: 6px; }
.ch .score { font-size: 16px; font-weight: 700; margin-top: 2px; }
.ch .score i { font-style: normal; font-size: 11px; font-weight: 400; color: var(--hf-text-3); }
.ch .sub { color: var(--hf-text-3); font-size: 11px; margin-top: 1px; }

/* 最优/最差：底色 + 字重双编码，不只靠颜色（色弱可读） */
.cell-val { display: block; text-align: center; border-radius: 5px; padding: 2px 0; font-weight: 500; }
.cell-val.best { color: var(--hf-good); font-weight: 700; background: var(--hf-good-soft); }
.cell-val.worst { color: var(--hf-bad); background: var(--hf-bad-soft); }

.concl { border-left: 3px solid var(--hf-primary); }
.concl :deep(.el-card__header) { display: flex; align-items: center; gap: 8px; }
.concl-title { display: inline-flex; align-items: center; gap: 6px; font-weight: 700; }
.concl-title .app-icon { color: var(--hf-primary); }
.concl p { margin: 6px 0; font-size: 14px; }
.concl .txt { line-height: 1.8; }
.concl .hint { color: var(--hf-warn); font-size: 12px; }
</style>
