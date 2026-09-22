<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post, put } from '../../api/http'

const route = useRoute()
const router = useRouter()
const role = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('hf-user') || 'null')?.role as string | undefined
  } catch {
    return undefined
  }
})
const isAdmin = computed(() => role.value === 'ADMIN')
const tab = ref<'rules' | 'houses' | 'users' | 'shares' | 'usage'>('houses')
const shares = ref<any[]>([])
const usage = ref<{ summary: any[]; recent: any[] }>({ summary: [], recent: [] })
const visitForm = reactive({ id: 0, note: '', shown: false })

const rules = ref<any[]>([])
const activeVersion = ref('')
const weightDraft = reactive<Record<string, number>>({})
const editingId = ref<number | null>(null)
const trialId = ref<number | undefined>()
const trialText = ref('')

const houses = ref<any[]>([])
const houseTotal = ref(0)
const housePage = ref(1)
const statusForm = reactive({ id: 0, status: 'SOLD', reason: '' })

const users = ref<any[]>([])
const userTotal = ref(0)
const resetPwd = reactive({ id: 0, password: '' })

const DIMS = [
  ['LIGHT', '采光'],
  ['VENT', '通风'],
  ['CIRC', '动线'],
  ['UTIL', '实用'],
  ['QUIET', '静谧'],
  ['GREEN', '绿色'],
  ['COST', '经济'],
] as const

function pickTab() {
  const raw = String(route.params.rest || 'houses').split('/')[0]
  const allowed = raw === 'rules' || raw === 'users' || raw === 'houses' || raw === 'shares' || raw === 'usage'
  const next = (allowed ? raw : 'houses') as typeof tab.value
  tab.value = !isAdmin.value && next !== 'houses' && next !== 'shares' ? 'houses' : next
}

async function loadRules() {
  if (!isAdmin.value) return
  const d = await get<{ items: any[]; activeVersion: string }>('/admin/rule-sets')
  rules.value = d.items || []
  activeVersion.value = d.activeVersion
}

function startEdit(row: any) {
  if (row.status === 'PUBLISHED' || row.status === 'FILE' || row.id === 0) {
    ElMessage.warning('已发布或文件种子只读，请先复制为草案')
    return
  }
  editingId.value = row.id
  for (const [code] of DIMS) {
    const found = (row.weights || []).find((w: any) => w.code === code)
    weightDraft[code] = Number(found?.weight ?? 0)
  }
}

const weightSum = computed(() =>
  DIMS.reduce((s, [code]) => s + (Number(weightDraft[code]) || 0), 0),
)

async function saveWeights() {
  if (editingId.value == null) return
  if (Math.abs(weightSum.value - 1) > 0.001) {
    ElMessage.error('维度权重之和必须为 1')
    return
  }
  const weights: Record<string, number> = {}
  for (const [code] of DIMS) weights[code] = Number(weightDraft[code])
  await put(`/admin/rule-sets/${editingId.value}/weights`, { weights })
  ElMessage.success('权重已保存')
  editingId.value = null
  await loadRules()
}

async function copyDraft(source: 'MANUAL' | 'AI') {
  await post('/admin/rule-sets/draft', { source })
  ElMessage.success(source === 'AI' ? '已生成 AI 草案，发布前必须勾选确认' : '已复制为草案')
  await loadRules()
}

async function publish(row: any, confirm: boolean) {
  await post(`/admin/rule-sets/${row.id}/publish`, { confirm })
  ElMessage.success('已发布，下一次评估即使用该版本')
  await loadRules()
}

async function trial() {
  if (!trialId.value) return
  const d = await post<any>('/admin/rule-sets/trial', { houseTypeId: trialId.value })
  trialText.value = `${d.houseTypeName || trialId.value}：${Number(d.total).toFixed(1)} 分 · ${d.level} · 规则 ${d.setVersion}`
}

async function loadHouses() {
  const d = await get<{ items: any[]; total: number }>('/admin/houses', { page: housePage.value, size: 20 })
  houses.value = (d.items || []).map((row: any) => ({
    id: row.id,
    buildingCode: row.buildingCode ?? row.building_code,
    floorNo: row.floorNo ?? row.floor_no,
    roomNo: row.roomNo ?? row.room_no,
    saleStatus: row.saleStatus ?? row.sale_status,
    totalPrice: row.totalPrice ?? row.total_price,
  }))
  houseTotal.value = d.total || 0
}

async function changeStatus() {
  if (!statusForm.id || !statusForm.reason.trim()) {
    ElMessage.warning('请填写房源编号和原因')
    return
  }
  await post(`/admin/houses/${statusForm.id}/status`, { status: statusForm.status, reason: statusForm.reason })
  ElMessage.success('销控状态已更新')
  statusForm.reason = ''
  await loadHouses()
}

async function downloadTemplate() {
  const token = localStorage.getItem('hf-token')
  const res = await fetch('/api/admin/houses/template', { headers: { Authorization: `Bearer ${token}` } })
  if (!res.ok) {
    ElMessage.error('模板下载失败')
    return
  }
  const blob = await res.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'house-import.xlsx'
  a.click()
}

async function onImport(file: File) {
  const body = new FormData()
  body.append('file', file)
  const token = localStorage.getItem('hf-token')
  const res = await fetch('/api/admin/houses/import', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body,
  })
  const json = await res.json()
  if (json.code !== 0) {
    ElMessage.error(json.message || '导入失败')
    return false
  }
  const errs = json.data?.errors || []
  ElMessage.success(`导入 ${json.data.imported} 条` + (errs.length ? `，${errs.length} 行失败` : ''))
  if (errs.length) ElMessage.warning(errs.map((e: any) => `第 ${e.line} 行 ${e.message}`).join('；'))
  await loadHouses()
  return false
}

async function loadUsers() {
  if (!isAdmin.value) return
  const d = await get<{ items: any[]; total: number }>('/admin/users', { page: 1, size: 20 })
  users.value = d.items || []
  userTotal.value = d.total || 0
}

async function toggleUser(row: any) {
  const next = row.status === 1 || row.status === '1' ? 0 : 1
  await post(`/admin/users/${row.id}/status`, { status: next })
  ElMessage.success(next === 1 ? '已启用' : '已停用')
  await loadUsers()
}

async function doReset() {
  if (!resetPwd.id) return
  await post(`/admin/users/${resetPwd.id}/reset-password`, { password: resetPwd.password })
  ElMessage.success('口令已重置')
  resetPwd.password = ''
}

async function loadShares() {
  shares.value = await get<any[]>('/admin/shares')
}

async function loadUsage() {
  if (!isAdmin.value) return
  usage.value = await get('/admin/ai-usage')
}

async function saveVisit() {
  if (!visitForm.id) return
  await post(`/admin/shares/${visitForm.id}/visit`, { note: visitForm.note, shown: visitForm.shown })
  ElMessage.success(visitForm.shown ? '已标为已带看' : '备注已保存')
  await loadShares()
}

function switchTab(name: 'rules' | 'houses' | 'users' | 'shares' | 'usage') {
  tab.value = name
  router.replace('/admin/' + name)
}

onMounted(async () => {
  pickTab()
  await loadHouses()
  await loadShares()
  if (isAdmin.value) {
    await loadUsage()
    await loadRules()
    await loadUsers()
  }
})
</script>

<template>
  <div class="admin">
    <p class="hf-kicker">管理</p>
    <h1>管理后台</h1>
    <p class="sub">规则发布、销控和账号停用。顾问只能处理销控，预约审核仍在「预约」页。</p>
    <el-button @click="router.push({ name: 'appointments' })">前往预约处理</el-button>

    <el-radio-group :model-value="tab" class="tabs" @change="switchTab($event as any)">
      <el-radio-button v-if="isAdmin" value="rules">规则</el-radio-button>
      <el-radio-button value="houses">销控</el-radio-button>
      <el-radio-button v-if="isAdmin" value="users">用户</el-radio-button>
      <el-radio-button value="shares">分享</el-radio-button>
      <el-radio-button v-if="isAdmin" value="usage">调用量</el-radio-button>
    </el-radio-group>

    <section v-if="tab === 'rules' && isAdmin" class="card">
      <div class="row">
        <span>当前评估版本 {{ activeVersion || '—' }}</span>
        <el-button size="small" @click="copyDraft('MANUAL')">复制为草案</el-button>
        <el-button size="small" @click="copyDraft('AI')">生成 AI 草案</el-button>
      </div>
      <el-table :data="rules" size="small" border>
        <el-table-column prop="version" label="版本" width="140" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="source" label="来源" width="90" />
        <el-table-column label="权重合计" width="100">
          <template #default="{ row }">{{ row.weightSum }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button v-if="row.id && row.status === 'DRAFT'" size="small" text @click="startEdit(row)">改权重</el-button>
            <el-button v-if="row.id && row.status === 'DRAFT' && row.source !== 'AI'" size="small" text type="primary"
              @click="publish(row, true)">发布</el-button>
            <el-button v-if="row.id && row.status === 'DRAFT' && row.source === 'AI'" size="small" text type="primary"
              @click="publish(row, true)">确认并发布</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-if="rules[0]?.note" class="note">{{ rules[0].note }}</p>

      <div v-if="editingId" class="weights">
        <h2>草案 #{{ editingId }} 维度权重 <b :class="{ bad: Math.abs(weightSum - 1) > 0.001 }">合计 {{ weightSum.toFixed(3) }}</b></h2>
        <div class="grid">
          <label v-for="[code, label] in DIMS" :key="code">{{ label }}
            <el-input-number v-model="weightDraft[code]" :min="0" :max="1" :step="0.01" :precision="3" />
          </label>
        </div>
        <el-button type="primary" @click="saveWeights">保存权重</el-button>
      </div>

      <div class="trial">
        <el-input-number v-model="trialId" :min="1" placeholder="户型 ID" />
        <el-button @click="trial">试算</el-button>
        <span>{{ trialText }}</span>
      </div>
    </section>

    <section v-else-if="tab === 'houses'" class="card">
      <div class="row">
        <el-button size="small" @click="downloadTemplate">下载导入模板</el-button>
        <el-upload :show-file-list="false" :before-upload="onImport" accept=".xlsx,.xls">
          <el-button size="small" type="primary">导入 Excel</el-button>
        </el-upload>
      </div>
      <div class="status-form">
        <el-input-number v-model="statusForm.id" :min="1" placeholder="房源 ID" />
        <el-select v-model="statusForm.status" style="width: 140px">
          <el-option label="可售" value="AVAILABLE" />
          <el-option label="锁定" value="LOCKED" />
          <el-option label="已预留" value="RESERVED" />
          <el-option label="已售" value="SOLD" />
        </el-select>
        <el-input v-model="statusForm.reason" placeholder="变更原因（必填）" style="max-width: 240px" />
        <el-button type="primary" @click="changeStatus">更新状态</el-button>
      </div>
      <el-table :data="houses" size="small" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="buildingCode" label="楼栋" width="80" />
        <el-table-column prop="floorNo" label="楼层" width="70" />
        <el-table-column prop="roomNo" label="房号" width="80" />
        <el-table-column prop="saleStatus" label="状态" width="110" />
        <el-table-column prop="totalPrice" label="总价" />
      </el-table>
      <el-pagination layout="prev, pager, next" :total="houseTotal" :page-size="20" :current-page="housePage"
        @current-change="(p: number) => { housePage = p; loadHouses() }" />
    </section>

    <section v-else-if="tab === 'users' && isAdmin" class="card">
      <p class="note">共 {{ userTotal }} 人。手机号已脱敏。</p>
      <el-table :data="users" size="small" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="role" label="角色" width="120" />
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" text @click="toggleUser(row)">{{ row.status == 1 ? '停用' : '启用' }}</el-button>
            <el-button size="small" text @click="resetPwd.id = row.id">重置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="resetPwd.id" class="status-form">
        <span>用户 #{{ resetPwd.id }} 新口令</span>
        <el-input v-model="resetPwd.password" show-password placeholder="至少 8 位，含字母和数字" style="max-width: 260px" />
        <el-button type="primary" @click="doReset">确认重置</el-button>
      </div>
    </section>

    <section v-else-if="tab === 'shares'" class="card">
      <p class="note">客户打开分享链接后，打开次数会加一。</p>
      <el-table :data="shares" size="small" border>
        <el-table-column prop="title" label="报告" />
        <el-table-column prop="viewCount" label="打开次数" width="100" />
        <el-table-column prop="shown" label="已带看" width="90">
          <template #default="{ row }">{{ row.shown ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="consultantNote" label="备注" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" text @click="visitForm.id = row.id; visitForm.note = row.consultantNote || ''; visitForm.shown = !!row.shown">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="visitForm.id" class="status-form">
        <span>报告 #{{ visitForm.id }}</span>
        <el-input v-model="visitForm.note" maxlength="255" placeholder="顾问备注" style="max-width: 280px" />
        <el-checkbox v-model="visitForm.shown">已带看</el-checkbox>
        <el-button type="primary" @click="saveVisit">保存</el-button>
      </div>
    </section>

    <section v-else-if="tab === 'usage' && isAdmin" class="card">
      <el-table :data="usage.summary" size="small" border>
        <el-table-column prop="promptKey" label="能力" />
        <el-table-column prop="calls" label="次数" width="90" />
        <el-table-column prop="fallbacks" label="模板次数" width="110" />
        <el-table-column prop="avgLatency" label="平均耗时(ms)" width="130" />
      </el-table>
      <el-table :data="usage.recent" size="small" border>
        <el-table-column prop="createdAt" label="时间" />
        <el-table-column prop="promptKey" label="能力" />
        <el-table-column prop="latencyMs" label="耗时" width="90" />
        <el-table-column prop="fallback" label="模板" width="80" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.admin { max-width: 980px; margin: 0 auto; }
h1 { font-size: 28px; letter-spacing: -0.03em; margin: 8px 0 10px; }
.sub { color: var(--hf-text-2); font-size: 15px; max-width: 42em; margin: 0 0 14px; line-height: 1.7; }
.tabs { margin: 18px 0; }
.card { display: grid; gap: 12px; }
.row, .status-form, .trial, .grid { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.note { color: var(--hf-text-3); font-size: 13px; }
.weights { padding: 12px; border: 1px solid var(--hf-border); border-radius: var(--hf-radius-m); background: var(--hf-surface); }
.weights h2 { font-size: 15px; margin: 0 0 10px; }
.bad { color: var(--hf-bad); }
.grid label { display: grid; gap: 4px; font-size: 12px; color: var(--hf-text-2); }
</style>
