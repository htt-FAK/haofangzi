<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { get, post } from '../api/http'

/**
 * 我的预约 + 新建预约（spec 006 US-60~62、FR-90~97）。
 * 状态机在前端只做"可点性"控制，权威流转永远由后端判定（AC-56 双重防护）。
 */
const route = useRoute()
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
// 状态点：颜色 + 文案双编码，不只靠颜色（色弱可读）
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
    ElMessage.success('已提交，顾问确认后会通知你')
    dialog.value = false
    await load()
  } catch (e: any) {
    if (e?.code === 40018) ElMessage.error('该时段已约满，请换时段')
    else if (e?.code === 40019) ElMessage.error('同一天已有未完成的预约，请先取消或改期')
  }
}

async function act(row: Record<string, any>, target: string) {
  const reason = target === 'CANCELED'
    ? (await ElMessageBox.prompt('取消原因（会记入跟进）', '取消预约', { inputPattern: /.+/, inputErrorMessage: '必填' })).value
    : undefined
  await post(`/appointments/${row.id}/status`, { target, reason })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="ap">
    <header class="intro">
      <p class="hf-kicker">预约</p>
      <div class="top">
        <h1>我的看房预约</h1>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="dialog = true">新建预约</el-button>
      </div>
      <p class="hf-lead">实地看房不收费。顾问只能看到自己名下客户，手机号一律脱敏。</p>
    </header>

    <el-table v-loading="loading" :data="list" border class="tbl">
      <el-table-column prop="visitDate" label="日期" width="110" />
      <el-table-column prop="visitSlot" label="时段" width="130" />
      <el-table-column label="对象" min-width="200">
        <template #default="{ row }">
          <div class="obj">{{ row.projectName }}</div>
          <span class="sub">{{ row.houseTypeName || '未指定户型' }}</span>
          <el-tag v-if="row.intentionNo" size="small" effect="plain" class="tag">意向 {{ row.intentionNo }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="partySize" label="人数" width="60" />
      <el-table-column prop="contactPhone" label="联系电话" width="130" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="(TAG[row.status] as any) || 'info'" size="small" effect="light" class="status-tag">
            <i class="sdot" :class="row.status" />{{ DOT[row.status] || row.statusName || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditRemark" label="顾问备注" min-width="160" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button v-for="n in NEXT[row.status] ?? []" :key="n" size="small" @click="act(row, n)">
            {{ n === 'CONFIRMED' ? '提醒顾问确认' : n === 'ARRIVED' ? '我到场了' : '完成看房' }}
          </el-button>
          <el-button v-if="['PENDING', 'CONFIRMED'].includes(row.status)" size="small" text class="cancel-btn" @click="act(row, 'CANCELED')">取消</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="还没有预约，点右上角新建" /></template>
    </el-table>

    <el-dialog v-model="dialog" title="预约实地看房" width="520px">
      <el-form label-width="86px">
        <el-form-item label="楼盘">
          <el-input :model-value="'#' + form.projectId + '（来自房源页，可后台改）'" disabled />
        </el-form-item>
        <el-form-item label="看房日期">
          <el-select v-model="form.visitDate" placeholder="选择日期" style="width: 100%">
            <el-option v-for="d in slots" :key="d.date" :label="d.date" :value="d.date" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.visitDate" label="时段">
          <el-radio-group v-model="form.visitSlot" class="slot-group">
            <el-radio v-for="s in slots.find(x => x.date === form.visitDate)?.list ?? []" :key="s.slot"
              :value="s.slot" :disabled="s.left <= 0" border size="small" class="slot-radio">
              {{ s.slot }}<i class="left hf-num">余{{ s.left }}</i>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="随行人数"><el-input-number v-model="form.partySize" :min="1" :max="6" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" maxlength="11" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item v-if="form.intentionNo" label="意向号">
          <el-tag size="small">{{ form.intentionNo }}</el-tag><span class="sub"> 由模拟选房带入（FR-90）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">提交预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ap { max-width: 1200px; margin: 0 auto; }
.intro { margin-bottom: 22px; }
.intro h1 { font-size: 28px; letter-spacing: -0.03em; }
.top { display: flex; align-items: center; gap: 10px; margin: 8px 0 10px; flex-wrap: wrap; }
.top h1 { flex: 1; margin: 0; }

.tbl { border-radius: var(--hf-radius-s); overflow: hidden; }
.obj { font-weight: 600; font-size: 13px; }
.sub { color: var(--hf-text-3); font-size: 12px; margin-left: 6px; }
.tag { margin-left: 6px; }
.status-tag { display: inline-flex; align-items: center; gap: 5px; }
.sdot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; flex: none; }
.cancel-btn { color: var(--hf-text-3); }
.cancel-btn:hover { color: var(--hf-bad); background: var(--hf-bad-soft); }

/* 时段选择：卡片化 radio，满位时置灰 */
.slot-group { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 8px; width: 100%; }
.slot-radio { margin: 0; width: 100%; }
.slot-radio :deep(.el-radio__label) { display: flex; justify-content: space-between; width: 100%; padding-left: 8px; }
.left { font-style: normal; color: var(--hf-text-3); font-size: 11px; }
</style>
