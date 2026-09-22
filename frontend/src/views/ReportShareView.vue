<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '../api/http'
import { GRADE_COLOR } from '../api/types'
import AppIcon from '../components/AppIcon.vue'

/**
 * 分享快照页（spec 005 FR-37、AC-46）：
 * token 校验（7 天内、只读）→ 命中则直接返回冻结的 matrix/conclusion（不再重算、不暴露 PII）；
 * 不存在或过期 → 明确提示并引导回访官方入口。
 */
const route = useRoute()
const token = String(route.params.token ?? route.query.token ?? '')
const src = String(route.query.src ?? '')
const data = ref<Record<string, any> | null>(null)
const state = ref<'loading' | 'ok' | 'expired' | 'error'>('loading')

async function load() {
  if (!token && !src) {
    state.value = 'expired'
    return
  }
  try {
    // 走免鉴权只读端点；离线演示时后端返回模板快照
    data.value = await get(`/share/reports/${encodeURIComponent(token || src)}`)
    state.value = 'ok'
  } catch (e: any) {
    state.value = [40304, 40420].includes(e?.code) ? 'expired' : 'error'
  }
}

function goHome() {
  window.location.href = '/'
}

function printReport() {
  window.open(`/api/share/reports/${encodeURIComponent(token)}/print`, '_blank')
}

onMounted(load)
</script>

<template>
  <div class="share">
    <template v-if="state === 'ok' && data">
      <header>
        <div class="brand"><span class="mark"><AppIcon name="house" :size="18" /></span>肇庆好房子 · 选房对比报告</div>
        <div class="meta">分享时间 {{ data.createTime ?? '—' }} · 只读快照 · 有效期 7 天<span v-if="data.source === 'ai'">（含 AI 结论）</span><span v-if="data.shown"> · 顾问已带看</span></div>
        <p v-if="data.consultantNote" class="meta">顾问备注：{{ data.consultantNote }}</p>
        <el-button class="print" size="small" @click="printReport">打印 / 另存 PDF</el-button>
      </header>

      <section v-if="data.summary" class="card sum">
        <h2><AppIcon name="message" :size="16" /> 结论</h2>
        <p>{{ data.summary }}</p>
      </section>

      <section class="card mtx">
        <h2><AppIcon name="compare" :size="16" /> 对比明细</h2>
        <el-table :data="data.rows ?? []" border size="small">
          <el-table-column prop="dim" label="维度" width="120" />
          <el-table-column prop="metric" label="指标" min-width="130" />
          <el-table-column v-for="c in data.columns ?? []" :key="c.id" :label="c.name" width="120">
            <template #default="{ row }">
              <span :class="[c.best ? 'best' : '', row.cls]">{{ row[c.key] ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="120">
            <template #default="{ row }"><span class="evid" :title="row.evidence">{{ row.note }}</span></template>
          </el-table-column>
        </el-table>
        <p v-if="(data.metrics ?? []).length" class="detail">
          <span v-for="m in data.metrics" :key="m.metricCode" class="chip"
            :style="{ borderColor: GRADE_COLOR[m.grade], color: GRADE_COLOR[m.grade] }">{{ m.metricName }} {{ m.value }} · {{ m.grade }}</span>
        </p>
      </section>

      <footer>
        <p>分数由规则引擎计算（可解释、可复现）；本页不含联系电话等隐私信息。</p>
        <p>想自己动手评估：打开「肇庆好房子在线选房与户型智能评估」系统。</p>
        <b class="disc">评估结果仅供参考，不构成购房或投资建议。</b>
      </footer>
    </template>

    <el-result v-else-if="state === 'expired'" icon="warning" title="链接已过期或无效"
      sub-title="分享链接有效期 7 天，且为只读快照。请让分享者重新生成，或直接进入系统自行对比。">
      <template #extra><el-button type="primary" @click="goHome">去系统首页</el-button></template>
    </el-result>

    <el-result v-else-if="state === 'error'" icon="error" title="加载失败"
      sub-title="服务暂不可用，请稍后重试。" :loading="false">
      <template #extra><el-button @click="load">重试</el-button></template>
    </el-result>

    <div v-else class="loading">
      <span class="spin" aria-hidden="true" />
      加载中…
    </div>
  </div>
</template>

<style scoped>
.share { max-width: 900px; margin: 0 auto; padding: 28px 16px 48px; }

header { margin-bottom: 18px; }
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 700;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--hf-primary);
}
.brand .mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #fff;
  background: var(--hf-ink);
}
.meta { color: var(--hf-text-3); font-size: 12px; margin-top: 8px; }

.card {
  background: var(--hf-surface);
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-m);
  padding: 18px 20px;
  margin-bottom: 16px;
  box-shadow: var(--hf-shadow-sm);
}
h2 { font-size: 15px; margin: 0 0 12px; display: flex; align-items: center; gap: 8px; }
h2 .app-icon { color: var(--hf-primary); }
.sum p { font-size: 14px; line-height: 1.9; white-space: pre-wrap; }
.best { color: var(--hf-good); font-weight: 700; }
.evid { color: var(--hf-text-3); font-size: 12px; }
.detail { margin-top: 12px; }
.chip {
  display: inline-block;
  border: 1px solid var(--hf-border);
  background: var(--hf-surface);
  border-radius: 999px;
  padding: 2px 10px;
  margin: 0 6px 6px 0;
  font-size: 12px;
  font-weight: 500;
}

footer {
  margin-top: 24px;
  color: var(--hf-text-3);
  font-size: 12px;
  line-height: 1.9;
  border-top: 1px solid var(--hf-border);
  padding-top: 14px;
}
.disc { color: var(--hf-warn); }

.loading {
  padding: 60px;
  text-align: center;
  color: var(--hf-text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.spin {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid var(--hf-primary-soft);
  border-top-color: var(--hf-primary);
  animation: hf-spin 800ms linear infinite;
}
@keyframes hf-spin {
  to { transform: rotate(360deg); }
}
</style>
