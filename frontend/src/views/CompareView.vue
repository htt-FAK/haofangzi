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
  { label: '综合性能总分', key: 'total', higherBetter: true, fmt: (v) => v.toFixed(1) },
  { label: '采光与日照', key: 'LIGHT', higherBetter: true },
  { label: '通风与对流', key: 'VENT', higherBetter: true },
  { label: '动线与分区', key: 'CIRC', higherBetter: true },
  { label: '实用与得房', key: 'UTIL', higherBetter: true },
  { label: '静谧与干扰', key: 'QUIET', higherBetter: true },
  { label: '绿色与舒适', key: 'GREEN', higherBetter: true },
  { label: '经济适配度', key: 'COST', higherBetter: true },
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
  return d === 0 ? '全场最优' : `${d.toFixed(1)} 分`
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
  ElMessage.success('已生成只读分享报告链接')
}

async function copyShare() {
  if (!share.value) return
  try {
    await navigator.clipboard.writeText(share.value.url)
    ElMessage.success('链接已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择链接复制')
  }
}

function removeCandidate(id: number) {
  store.remove(id)
  cols.value = cols.value.filter((c) => c.houseTypeId !== id)
}

watch(() => store.ids.length, (n) => { if (n >= 1) loadAll() }, { immediate: true })
</script>

<template>
  <div class="cmp">
    <!-- 标头 -->
    <header class="cmp-hero">
      <div class="cmp-left">
        <div class="hf-kicker">
          <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor">
            <path d="M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v11A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5v-11zM9 2.5A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v11a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 13.5v-11z"/>
          </svg>
          MATRIX COMPARISON · 多户型横向对标
        </div>
        <h1 class="cmp-title">多方案全维对比矩阵</h1>
        <p class="cmp-desc">
          候选方案汇总自您的评估历史。至少选择 2 套户型可自动生成 AI 综合选房结论与敏感度分析。
        </p>
      </div>

      <div class="cmp-actions">
        <el-button
          type="primary"
          size="large"
          class="pill-btn"
          :disabled="cols.length < 2"
          @click="createShare"
        >
          <AppIcon name="link" :size="15" />
          生成分享报告
        </el-button>
        <el-button size="large" class="pill-btn" @click="loadAll">
          刷新数据
        </el-button>
        <el-button size="large" text class="pill-btn" @click="router.push('/')">
          ← 继续选房
        </el-button>
      </div>
    </header>

    <!-- 分享链接浮层 -->
    <div v-if="share" class="share-box">
      <div class="share-left">
        <div class="share-icon">
          <AppIcon name="link" :size="16" />
        </div>
        <div class="share-info">
          <span class="share-label">对外只读分享链接</span>
          <code class="share-url">{{ share.url }}</code>
        </div>
      </div>
      <div class="share-right">
        <span class="share-exp hf-num">有效至 {{ share.expireAt }}</span>
        <el-button size="small" type="primary" class="copy-btn" @click="copyShare">
          复制链接
        </el-button>
      </div>
    </div>

    <!-- 矩阵表格 -->
    <div v-if="cols.length" class="matrix-card">
      <div class="matrix-header-hint">
        <span class="legend-item"><span class="legend-box best"></span>该项最优</span>
        <span class="legend-item"><span class="legend-box worst"></span>待提升项</span>
        <span class="legend-sep">|</span>
        <span class="legend-note">数值与档位严格来源于规则引擎计算结果</span>
      </div>

      <el-table v-loading="loading" :data="rows" border class="mtx-table" size="default">
        <el-table-column prop="label" label="对比指标" width="160" fixed>
          <template #default="{ row }">
            <span class="row-label">{{ row.label }}</span>
          </template>
        </el-table-column>

        <el-table-column
          v-for="c in cols"
          :key="c.houseTypeId"
          :label="c.houseTypeName"
          min-width="190"
        >
          <template #header>
            <div class="candidate-header">
              <div class="cand-top">
                <span class="cand-name" :title="c.houseTypeName">{{ c.houseTypeName }}</span>
                <button
                  type="button"
                  class="cand-close"
                  title="从对比中移除"
                  @click.stop="removeCandidate(c.houseTypeId)"
                >
                  ✕
                </button>
              </div>

              <div class="cand-score-row">
                <span class="cand-score hf-num" :style="{ color: GRADE_COLOR[c.level] }">
                  {{ c.total.toFixed(1) }}
                </span>
                <span class="cand-level-pill" :style="{ background: GRADE_COLOR[c.level] }">
                  {{ c.level }}
                </span>
                <span v-if="c.houseTypeId === bestId" class="rec-badge">
                  ★ 优选
                </span>
              </div>

              <div class="cand-gap hf-num">
                差距：{{ gap(c) }}
              </div>
              <div class="cand-sub">
                建面 {{ c.gfa ?? '—' }}㎡ · 规则 {{ c.setVersion }}
              </div>
            </div>
          </template>

          <template #default="{ row }">
            <div class="cell-wrap">
              <span
                class="cell-val hf-num"
                :class="matrixOf(row)(c.houseTypeId, cell(row, c))"
              >
                {{ cell(row, c).toFixed(1) }}
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- AI 对比综合结论卡片 -->
    <div v-if="conclusion" class="conclusion-card">
      <div class="concl-header">
        <div class="concl-badge">
          <AppIcon name="message" :size="16" />
          <span>选房智能对比结论</span>
        </div>
        <el-tag size="small" :type="conclusion.source === 'ai' ? 'primary' : 'info'" effect="light">
          {{ conclusion.source === 'ai' ? '通义千问 AI 生成' : '确定性规则兜底' }}
        </el-tag>
      </div>

      <p class="concl-text">{{ conclusion.text }}</p>
      
      <div v-if="sensitivity" class="sensitivity-box">
        <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M8 1v14M1 8h14" />
        </svg>
        <span>敏感度提示：{{ sensitivity }}</span>
      </div>

      <div class="concl-footer">
        <span>声明：结论由大模型综合 7 维打分矩阵生成，仅供选房策略参考，不构成购房承诺。</span>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && !cols.length" class="empty-state">
      <div class="empty-icon">📊</div>
      <h3>对比池暂无户型数据</h3>
      <p>请返回首页或户型详情页，点击「加入对比」将 2 套以上户型加入对比池。</p>
      <el-button type="primary" size="large" class="pill-btn" @click="router.push('/')">
        立即前往选房图册
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.cmp {
  max-width: 1240px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Hero */
.cmp-hero {
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

.cmp-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 56em;
}

.cmp-title {
  font-size: clamp(26px, 3.2vw, 36px);
  font-weight: 800;
  color: var(--hf-ink);
  letter-spacing: -0.03em;
  margin: 0;
}

.cmp-desc {
  font-size: 15px;
  color: var(--hf-text-2);
  line-height: 1.6;
}

.cmp-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pill-btn {
  border-radius: 9999px !important;
  font-weight: 600 !important;
}

/* Share Box */
.share-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 14px 20px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: var(--hf-radius-m);
}

.share-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.share-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #0284c7;
  color: #ffffff;
  display: grid;
  place-items: center;
}

.share-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.share-label {
  font-size: 11.5px;
  font-weight: 700;
  color: #0369a1;
}

.share-url {
  background: transparent;
  border: none;
  padding: 0;
  color: var(--hf-ink);
  font-size: 13px;
  word-break: break-all;
}

.share-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.share-exp {
  font-size: 12px;
  color: #64748b;
}

.copy-btn {
  border-radius: 9999px !important;
}

/* Matrix Card */
.matrix-card {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  padding: 20px;
  overflow: hidden;
}

.matrix-header-hint {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12.5px;
  color: var(--hf-text-3);
  margin-bottom: 14px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--hf-text-2);
  font-weight: 500;
}

.legend-box {
  width: 14px;
  height: 14px;
  border-radius: 3px;
}
.legend-box.best { background: #dcfce7; border: 1px solid #86efac; }
.legend-box.worst { background: #fee2e2; border: 1px solid #fca5a5; }

.legend-sep { color: var(--hf-border); }

.mtx-table {
  border-radius: var(--hf-radius-m);
  overflow: hidden;
}

.row-label {
  font-weight: 700;
  color: var(--hf-ink);
}

.candidate-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px 0;
}

.cand-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cand-name {
  font-size: 14px;
  font-weight: 800;
  color: var(--hf-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cand-close {
  border: none;
  background: transparent;
  color: var(--hf-text-3);
  cursor: pointer;
  padding: 2px 6px;
  font-size: 12px;
  border-radius: 4px;
  transition: all var(--hf-dur);
}
.cand-close:hover {
  background: #fee2e2;
  color: #ef4444;
}

.cand-score-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cand-score {
  font-size: 20px;
  font-weight: 900;
}

.cand-level-pill {
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 9999px;
}

.rec-badge {
  background: #fef08a;
  color: #854d0e;
  font-size: 11px;
  font-weight: 800;
  padding: 1px 6px;
  border-radius: 4px;
}

.cand-gap {
  font-size: 11.5px;
  color: var(--hf-text-3);
  font-weight: 600;
}

.cand-sub {
  font-size: 11px;
  color: var(--hf-text-3);
}

.cell-wrap {
  display: flex;
  justify-content: center;
}

.cell-val {
  display: inline-block;
  min-width: 60px;
  padding: 4px 10px;
  border-radius: 6px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--hf-ink);
}

.cell-val.best {
  background: #dcfce7;
  color: #15803d;
  font-weight: 800;
}

.cell-val.worst {
  background: #fee2e2;
  color: #b91c1c;
  font-weight: 700;
}

/* Conclusion Card */
.conclusion-card {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-left: 4px solid var(--hf-primary);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.concl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.concl-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 800;
  color: var(--hf-ink);
}

.concl-badge :deep(.app-icon) {
  color: var(--hf-primary);
}

.concl-text {
  font-size: 15px;
  color: var(--hf-text);
  line-height: 1.8;
}

.sensitivity-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #b45309;
  background: #fffbeb;
  padding: 8px 14px;
  border-radius: 6px;
  border: 1px solid #fde68a;
}

.concl-footer {
  font-size: 12px;
  color: var(--hf-text-3);
  border-top: 1px dashed var(--hf-border);
  padding-top: 10px;
}

/* Empty State */
.empty-state {
  padding: 80px 20px;
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
  font-size: 40px;
  margin-bottom: 4px;
}

.empty-state h3 {
  font-size: 20px;
  color: var(--hf-ink);
}

.empty-state p {
  color: var(--hf-text-2);
  font-size: 14px;
  max-width: 26em;
  margin-bottom: 12px;
}
</style>
