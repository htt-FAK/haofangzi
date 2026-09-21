<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post } from '../api/http'
import { GRADE_COLOR } from '../api/types'

/**
 * AI 智能顾问（spec 007 I3 + 选房意向问答）。
 * 硬约束在客户端也复述一遍：推荐必须来自"可buyable 户型候选集"，分数永远由规则引擎给出；
 * 后端不可用/降级时使用模板结果并明确标注。
 */
const q = ref('我家三代同住，预算 160 万内，想要 3-4 房，重视通风')
const ans = ref<{ text: string; source: 'ai' | 'fallback'; recommend?: { houseTypeId: number; name: string; total: number }[] } | null>(null)
const loading = ref(false)
const candidates = ref<{ id: number; name: string; total: number }[]>([])
const history = ref<{ q: string; a: string }[]>([])

const hasResult = computed(() => !!ans.value?.recommend?.length)

async function ask() {
  if (!q.value.trim()) return
  loading.value = true
  try {
    const d = await post<any>('/ai/advisor', { question: q.value, topN: 3 })
    ans.value = {
      text: String(d.answer ?? '').replace(/</g, '&lt;'),
      source: d.source === 'ai' ? 'ai' : 'fallback',
      recommend: (d.recommend ?? []).map((r: any) => ({
        houseTypeId: Number(r.houseTypeId ?? r.id), name: String(r.name ?? `#${r.houseTypeId}`), total: Number(r.total ?? 0),
      })),
    }
    history.value.unshift({ q: q.value, a: ans.value.text })
  } catch (e: any) {
    ans.value = { text: 'AI 服务暂不可用。可先按"对比页"的确定性差异矩阵决策；模板兜底建议需在后台开启 haofangzi.ai.enabled。', source: 'fallback' }
  } finally {
    loading.value = false
  }
}

async function loadCandidates() {
  const d = await get<any[]>('/house-types', { size: 20 })
  candidates.value = (Array.isArray(d) ? d : d?.records ?? [])
    .filter((x: any) => x.latestScore)
    .map((x: any) => ({ id: x.id, name: x.name, total: Number(x.latestScore) }))
    .sort((a, b) => b.total - a.total)
    .slice(0, 3)
}

onMounted(loadCandidates)
</script>

<template>
  <div class="ai">
    <h1>AI 选房顾问</h1>
    <p class="tip">
      输入你的家庭结构、预算与关注点。模型只做<b>解释与推荐排序建议</b>，分数来自规则引擎（宪法：可解释优先）；
      涉及购房资格、税费、贷款额度等问题，系统一律拒绝并以模板说明，不给出财务建议。
    </p>

    <div class="ask">
      <el-input v-model="q" type="textarea" :rows="3" maxlength="300" show-word-limit
        placeholder="例如：三代同住，预算 160 万，需要 3-4 房，重视通风与楼层" />
      <div class="ops">
        <el-button type="primary" :loading="loading" @click="ask">提问</el-button>
        <el-button :disabled="!candidates.length" @click="q = `请在候选中排序并说明理由：${candidates.map(c => c.name).join('、')}`">
          用我的已评估候选提问
        </el-button>
        <span v-if="ans" class="src">
          <el-tag size="small" :type="ans.source === 'ai' ? 'primary' : 'info'">{{ ans.source === 'ai' ? 'AI 生成' : '模板回落' }}</el-tag>
        </span>
      </div>
    </div>

    <div v-if="ans">
      <el-card shadow="never" class="ans">
        <p class="txt">{{ ans.text || '（无内容）' }}</p>
        <div v-if="hasResult" class="recs">
          <div v-for="r in ans.recommend" :key="r.houseTypeId" class="rec">
            <span>{{ r.name }}</span>
            <b :style="{ color: GRADE_COLOR[`${r.total >= 85 ? '优' : r.total >= 75 ? '良' : r.total >= 60 ? '中' : '差'}`] }">{{ r.total.toFixed(1) }}</b>
            <el-button size="small" text @click="() => {}">查看评估</el-button>
          </div>
        </div>
        <el-alert v-if="ans.source === 'ai'" type="info" :closable="false" class="hint"
          title="内容由大模型生成，仅供参考，不构成投资或购房建议。" />
      </el-card>
    </div>

    <el-empty v-else description="问我一个问题，或先到对比页看确定性差异" />

    <el-card v-if="history.length" shadow="never" class="his">
      <template #header>本次会话</template>
      <div v-for="(h, i) in history" :key="i" class="row">
        <div class="qq">问：{{ h.q }}</div>
        <div class="aa">答：{{ h.a }}</div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.ai { max-width: 900px; margin: 0 auto; }
h1 { font-size: 20px; margin: 0 0 6px; }
.tip { color: #666; font-size: 13px; }
.ask { display: flex; flex-direction: column; gap: 10px; margin: 10px 0 14px; }
.ops { display: flex; align-items: center; gap: 10px; }
.src { margin-left: auto; }
.ans .txt { white-space: pre-wrap; font-size: 14px; line-height: 1.7; }
.recs { display: flex; gap: 12px; margin-top: 8px; flex-wrap: wrap; }
.rec { display: flex; align-items: center; gap: 8px; border: 1px solid #eee; padding: 6px 10px; border-radius: 6px; font-size: 13px; }
.rec b { font-size: 15px; }
.hint { margin-top: 10px; }
.his { margin-top: 14px; }
.his .row { border-top: 1px dashed #eee; padding: 8px 0; font-size: 13px; }
.his .qq { color: #333; }
.his .aa { color: #666; margin-top: 4px; }
</style>
