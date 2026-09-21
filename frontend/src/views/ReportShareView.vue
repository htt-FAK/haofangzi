<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '../api/http'
import { GRADE_COLOR } from '../api/types'

/**
 * 分享快照页（spec 005 FR-37、AC-46）：
 * token 校验（≤30 分钟、只读）→ 命中则直接返回冻结的 matrix/conclusion（不再重算、不暴露 PII）；
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
    data.value = await get(`/share/${encodeURIComponent(token || src)}`)
    state.value = 'ok'
  } catch (e: any) {
    state.value = [40304, 40420].includes(e?.code) ? 'expired' : 'error'
  }
}

onMounted(load)
</script>

<template>
  <div class="share">
    <template v-if="state === 'ok' && data">
      <header>
        <div class="brand">肇庆好房子 · 选房对比报告</div>
        <div class="meta">分享时间 {{ data.createTime ?? '—' }} · 只读快照 · 有效期 30 分钟<span v-if="data.source === 'ai'">（含 AI 结论）</span></div>
      </header>

      <section v-if="data.summary" class="sum">
        <h2>结论</h2>
        <p>{{ data.summary }}</p>
      </section>

      <section class="mtx">
        <h2>对比明细</h2>
        <el-table :data="data.rows ?? []" border size="small">
          <el-table-column prop="dim" label="维度" width="120" />
          <el-table-column prop="metric" label="指标" min-width="130" /></el-table-column>
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
            :style="{ borderColor: GRADE_COLOR[m.grade] }">{{ m.metricName }} {{ m.value }} · {{ m.grade }}</span>
        </p>
      </section>

      <footer>
        分数由规则引擎计算（可解释、可复现）；本页不含联系电话等隐私信息。<br />
        想自己动手评估：打开「肇庆好房子在线选房与户型智能评估」系统。<br />
        <b class="disc">评估结果仅供参考，不构成购房或投资建议。</b>
      </footer>
    </template>

    <el-result v-else-if="state === 'expired'" icon="warning" title="链接已过期或无效"
      sub-title="分享链接有效期 30 分钟，且为只读快照。请让分享者重新生成，或直接进入系统自行对比。">
      <template #extra><el-button type="primary" @dismiss="() => (location.href = '/')">去系统首页</el-button></template>
    </el-result>

    <el-result v-else-if="state === 'error'" icon="error" title="加载失败"
      sub-title="服务暂不可用，请稍后重试。" :loading="false">
      <template #extra><el-button @click="load">重试</el-button></template>
    </el-result>

    <div v-else class="loading">加载中…</div>
  </div>
</template>

<style scoped>
.share { max-width: 860px; margin: 0 auto; padding: 22px 16px 40px; }
header { border-bottom: 2px solid var(--hf-primary); padding-bottom: 10px; margin-bottom: 14px; }
.brand { font-size: 20px; font-weight: 600; }
.meta { color: #888; font-size: 12px; margin-top: 4px; }
h2 { font-size: 15px; margin: 18px 0 8px; }
.sum p { font-size: 14px; line-height: 1.8; white-space: pre-wrap; }
.best { color: var(--hf-good); font-weight: 600; }
.evid { color: #666; font-size: 12px; }
.detail { margin-top: 10px; }
.chip { display: inline-block; border: 1px solid #ddd; border-radius: 12px; padding: 1px 8px; margin: 2px 4px 2px 0; font-size: 12px; }
footer { margin-top: 26px; color: #999; font-size: 12px; line-height: 1.8; border-top: 1px solid #eee; padding-top: 12px; }
.disc { color: #a05a00; }
.loading { padding: 40px; text-align: center; color: #999; }
</style>
