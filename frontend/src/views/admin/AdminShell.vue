<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { get, post } from '../../api/http'

/**
 * 管理端骨架（spec 002-T-033 / 004-T-079 / 006-T-110 / 007-T-122）。
 * 只有 3 个真实调用的管理动作：规则版本切换（试算 + 发布）、预约审核、反馈发布；
 * 其余管理页在 specs/00X/tasks.md 中登记为待办，不放"假按钮"。
 */
const tab = ref('rule')
const rule = ref<Record<string, any>>({ version: '', active: false, levels: { excellent: 85, good: 75, fair: 60 } })
const dryRun = ref<Record<string, any> | null>(null)
const dryHouse = ref<number>(0)
const appoints = ref<Record<string, any>[]>([])
const feedback = ref<Record<string, any>[]>([])
const loading = ref(false)

async function loadRule() {
  rule.value = await get('/admin/eval/rules/active')
}

async function publish() {
  await post('/admin/eval/rules/publish', { version: rule.value.version })
  ElMessage.success(`已发布 ${rule.value.version}（下一次评估生效，历史记录不变）`)
  await loadRule()
}

async function tryCalc() {
  loading.value = true
  try {
    dryRun.value = await post('/admin/eval/try', { houseTypeId: dryHouse.value, templateCode: 'GENERAL' })
  } finally {
    loading.value = false
  }
}

async function loadAppoints() {
  appoints.value = await get('/admin/appointments', { status: 'PENDING' })
}

async function audit(row: Record<string, any>, target: string) {
  await post(`/appointments/${row.id}/status`, { target })
  ElMessage.success('状态已更新，客户会收到通知')
  await loadAppoints()
}

async function loadFeedback() {
  feedback.value = await get('/admin/feedback')
}

async function toggle(row: Record<string, any>) {
  await post(`/admin/feedback/${row.id}/publish`, { publish: !row.published })
  await loadFeedback()
}
</script>

<template>
  <div class="admin">
    <h1>管理端</h1>
    <el-tabs v-model="tab" @tab-change="(t: string) => (t === 'rule' ? loadRule() : t === 'ap' ? loadAppoints() : loadFeedback())">
      <el-tab-pane label="评估规则" name="rule">
        <el-card shadow="never">
          <p class="tip">改维度权重或分档边界走 DB（eval_rule_set / eval_rule），发布即时生效；此处演示版本与试算。</p>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="当前版本">{{ rule.version || '—' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ rule.active ? 'ACTIVE' : 'DRAFT' }}</el-descriptions-item>
            <el-descriptions-item label="等级门槛">优 ≥ {{ rule.levels?.excellent }} / 良 ≥ {{ rule.levels?.good }} / 中 ≥ {{ rule.levels?.fair }}</el-descriptions-item>
            <el-descriptions-item label="维度权重">
              <span v-for="d in rule.dims ?? []" :key="d.code" class="w">{{ d.name }} {{ (d.weight * 100).toFixed(0) }}%</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="ops">
            <el-input-number v-model="dryHouse" :min="1" placeholder="户型 ID" style="width: 140px" />
            <el-button :loading="loading" :disabled="!dryHouse" @click="tryCalc">试算该户型</el-button>
            <el-button type="primary" @click="publish">发布当前版本</el-button>
          </div>
          <el-alert v-if="dryRun" class="dry" type="info" :closable="false"
            :title="`试算结果：${dryRun.total} 分 / ${dryRun.level}（版本 ${dryRun.setVersion}，${dryRun.missingCount} 项数据不足）`" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="预约审核" name="ap">
        <el-table :data="appoints" border>
          <el-table-column prop="visitDate" label="日期" width="110" />
          <el-table-column prop="visitSlot" label="时段" width="130" />
          <el-table-column prop="customer" label="客户（脱敏）" width="150" />
          <el-table-column prop="houseTypeName" label="意向户型" min-width="180" />
          <el-table-column prop="intentionNo" label="意向号" width="150" />
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="audit(row, 'CONFIRMED')">确认</el-button>
              <el-button size="small" @click="audit(row, 'ARRIVED')">到场登记</el-button>
              <el-button size="small" text @click="audit(row, 'CANCELED')">取消</el-button>
            </template>
          </el-table-column>
          <template #empty>暂无待确认预约</template>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="反馈发布" name="feedback">
        <el-table :data="feedback" border>
          <el-table-column prop="content" label="用户反馈" min-width="260" />
          <el-table-column prop="houseTypeName" label="相关户型" width="180" />
          <el-table-column prop="createTime" label="时间" width="170" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="toggle(row)">{{ row.published ? '下架' : '发布' }}</el-button>
            </template>
          </el-table-column>
          <template #empty>暂无反馈（提交入口：户型详情「反馈评估」）</template>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.admin { max-width: 1100px; margin: 0 auto; }
h1 { font-size: 20px; margin: 0 0 8px; }
.tip { color: #777; font-size: 12px; margin: 0 0 10px; }
.ops { display: flex; gap: 10px; margin-top: 12px; }
.w { display: inline-block; margin-right: 8px; font-size: 12px; }
.dry { margin-top: 12px; }
</style>
