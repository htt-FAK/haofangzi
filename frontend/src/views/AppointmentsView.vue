<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post } from '../api/http'

/**
 * 我的预约 + 新建预约（spec 006 US-60~62、FR-90~97）。
 * 状态机在前端只做"可点性"控制，权威流转永远由后端判定（AC-56 双重防护）。
 */
const route = useRoute()
const router = useRouter()
const list = ref<Record<string, any>[]>([])
const dialog = ref(false)
const loading = ref(false)
const slots = ref<{ date: string; list: { slot: string; left: number }[] }[]>([])
const form = reactive({
  projectId: Number(route.query.projectId ?? 1), houseTypeId: Number(route.query.houseTypeId ?? 0),
  visitDate: '', visitSlot: '', partySize: 2, contactPhone: '', remark: '', intentionNo: String(route.query.intentionNo ?? ''),
})

const NEXT: Record<string, string[]> = {
  PENDING: ['CONFIRMED'], CONFIRMED: ['ARRIVED'], ARRIVED: ['COMPLETED'], COMPLETED: [], CANCELED: [],
}
const TAG: Record<string, string> = { PENDING: 'warning', CONFIRMED: 'primary', ARRIVED: 'success', COMPLETED: 'info', CANCELED: 'danger' }
const DOT: Record<string, string> = {
  PENDING: '待确认', CONFIRMED: '已确认', ARRIVED: '已到访', COMPLETED: '已完成', CANCELED: '已取消',
}

async function load() {
  loading.value = true
  try {
    list.value = await get('/appointments')
    slots.value = await get('/appointment-slots', { projectId: form.projectId })
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.visitDate || !form.visitSlot) return ElMessage.warning('请选择看房日期与时段')
  try {
    await post('/appointments', form)
    ElMessage.success('看房预约已提交，置业顾问确认后将提醒您')
    dialog.value = false
    await load()
  } catch (e: any) {
    if (e?.code === 40018) ElMessage.error('该时段已约满，请换时段')
    else if (e?.code === 40019) ElMessage.error('同一天已有未完成的预约，请先取消或改期')
  }
}

async function act(row: Record<string, any>, target: string) {
  const reason = target === 'CANCELED'
    ? (await ElMessageBox.prompt('请输入取消原因（将记入跟进流水）', '取消预约确认', { inputPattern: /.+/, inputErrorMessage: '必填' })).value
    : undefined
  await post(`/appointments/${row.id}/status`, { target, reason })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="ap-container">
    <header class="ap-hero">
      <div class="ap-left">
        <div class="hf-kicker">
          <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor">
            <path d="M3.5 0a.5.5 0 0 1 .5.5V1h8V.5a.5.5 0 0 1 1 0V1h1a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V3a2 2 0 0 1 2-2h1V.5a.5.5 0 0 1 .5-.5zM1 4v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V4H1z"/>
          </svg>
          SITE VISIT APPOINTMENT · 实地看房预约
        </div>
        <h1 class="ap-title">我的看房预约与行程</h1>
        <p class="ap-desc">
          实地勘测看房全程免服务费。置业顾问双向跟进，联系电话全程脱敏保护隐私。
        </p>
      </div>

      <div class="ap-actions">
        <el-button size="large" class="pill-btn" @click="load">
          刷新
        </el-button>
        <el-button type="primary" size="large" class="pill-btn" @click="dialog = true">
          + 新建看房预约
        </el-button>
      </div>
    </header>

    <div class="table-card">
      <el-table v-loading="loading" :data="list" border class="ap-table" size="default">
        <el-table-column prop="visitDate" label="预约日期" width="130">
          <template #default="{ row }">
            <span class="hf-num font-bold">{{ row.visitDate }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="visitSlot" label="时间段" width="130">
          <template #default="{ row }">
            <span class="hf-num">{{ row.visitSlot }}</span>
          </template>
        </el-table-column>

        <el-table-column label="预约楼盘与户型" min-width="220">
          <template #default="{ row }">
            <div class="project-cell">
              <span class="proj-name">{{ row.projectName }}</span>
              <span class="house-name">{{ row.houseTypeName || '全盘参观' }}</span>
              <el-tag v-if="row.intentionNo" size="small" type="info" effect="plain" class="intent-tag">
                意向单号 #{{ row.intentionNo }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="partySize" label="随行人数" width="90" align="center">
          <template #default="{ row }">
            <span class="hf-num font-bold">{{ row.partySize }}</span> 人
          </template>
        </el-table-column>

        <el-table-column prop="contactPhone" label="联系手机" width="140">
          <template #default="{ row }">
            <span class="hf-num">{{ row.contactPhone }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="(TAG[row.status] as any) || 'info'" size="default" effect="light" class="status-pill">
              <span class="status-dot" :class="row.status"></span>
              {{ DOT[row.status] || row.statusName || row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="auditRemark" label="顾问确认备注" min-width="160" />

        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <div class="row-ops">
              <el-button
                v-for="n in NEXT[row.status] ?? []"
                :key="n"
                size="small"
                type="primary"
                plain
                @click="act(row, n)"
              >
                {{ n === 'CONFIRMED' ? '提醒确认' : n === 'ARRIVED' ? '确认到场' : '完成带看' }}
              </el-button>
              <el-button
                v-if="['PENDING', 'CONFIRMED'].includes(row.status)"
                size="small"
                text
                class="cancel-btn"
                @click="act(row, 'CANCELED')"
              >
                取消
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty-wrap">
            <div class="empty-icon">📅</div>
            <h4>暂无看房预约记录</h4>
            <p>选中心仪户型后，可随时发起预约，置业顾问将为您安排实地接待与样板间讲解。</p>
            <el-button type="primary" class="pill-btn" @click="dialog = true">立即发起预约</el-button>
          </div>
        </template>
      </el-table>
    </div>

    <!-- 预约弹窗 -->
    <el-dialog v-model="dialog" title="预约实地看房接待" width="560px" class="ap-dialog">
      <el-form label-width="96px" class="ap-form">
        <el-form-item label="楼盘编号">
          <el-input :model-value="'#' + form.projectId + ' · 肇庆星湖品质示范区'" disabled />
        </el-form-item>

        <el-form-item label="看房日期">
          <el-select v-model="form.visitDate" placeholder="请选择计划看房日期" style="width: 100%">
            <el-option v-for="d in slots" :key="d.date" :label="d.date" :value="d.date" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.visitDate" label="预约时段">
          <el-radio-group v-model="form.visitSlot" class="slot-group">
            <el-radio
              v-for="s in slots.find(x => x.date === form.visitDate)?.list ?? []"
              :key="s.slot"
              :value="s.slot"
              :disabled="s.left <= 0"
              border
              size="default"
              class="slot-radio"
            >
              <span>{{ s.slot }}</span>
              <span class="slot-left hf-num">余{{ s.left }}位</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="随行人数">
          <el-input-number v-model="form.partySize" :min="1" :max="6" />
        </el-form-item>

        <el-form-item label="联系手机">
          <el-input v-model="form.contactPhone" maxlength="11" placeholder="用于接收预约短信通知" />
        </el-form-item>

        <el-form-item label="需求备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" show-word-limit placeholder="如有老人同行、轮椅通行或重点考察空间，可在备注文明" />
        </el-form-item>

        <el-form-item v-if="form.intentionNo" label="关联意向号">
          <el-tag size="default" type="success">意向锁定 #{{ form.intentionNo }}</el-tag>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">确认提交预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ap-container {
  max-width: 1240px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Hero */
.ap-hero {
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

.ap-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 56em;
}

.ap-title {
  font-size: clamp(26px, 3.2vw, 36px);
  font-weight: 800;
  color: var(--hf-ink);
  letter-spacing: -0.03em;
  margin: 0;
}

.ap-desc {
  font-size: 15px;
  color: var(--hf-text-2);
  line-height: 1.6;
}

.ap-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pill-btn {
  border-radius: 9999px !important;
  font-weight: 600 !important;
}

/* Table Card */
.table-card {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  padding: 20px;
  overflow: hidden;
}

.ap-table {
  border-radius: var(--hf-radius-m);
  overflow: hidden;
}

.project-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.proj-name {
  font-weight: 700;
  color: var(--hf-ink);
  font-size: 14px;
}

.house-name {
  font-size: 12.5px;
  color: var(--hf-text-2);
}

.intent-tag {
  width: fit-content;
  margin-top: 2px;
}

.font-bold {
  font-weight: 700;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  border-radius: 9999px;
  padding: 2px 10px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.row-ops {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.cancel-btn {
  color: var(--hf-bad) !important;
}

/* Slot Group */
.slot-group {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
  width: 100%;
}

.slot-radio {
  margin: 0 !important;
  width: 100%;
  border-radius: var(--hf-radius-s) !important;
}

.slot-radio :deep(.el-radio__label) {
  display: flex;
  justify-content: space-between;
  width: 100%;
  padding-left: 6px;
}

.slot-left {
  font-size: 11px;
  color: var(--hf-text-3);
}

/* Empty */
.empty-wrap {
  padding: 60px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 4px;
}

.empty-wrap h4 {
  font-size: 18px;
  font-weight: 800;
  color: var(--hf-ink);
  margin: 0;
}

.empty-wrap p {
  color: var(--hf-text-3);
  font-size: 13.5px;
  max-width: 26em;
  margin-bottom: 12px;
}
</style>
