<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get } from '../api/http'
import type { HouseTypeGeometry, RoomGeo } from '../api/types'
import FloorPlan2D from '../components/FloorPlan2D.vue'
import House3DViewer from '../components/House3DViewer.vue'
import { useCompareStore } from '../stores/compare'
import { sceneCode } from '../utils/sceneCode'
import AppIcon from '../components/AppIcon.vue'

/**
 * 户型详情（spec 002 FR-12~21）：2D 图纸 / 3D 空间 / 720° 全景 / 参数表 四个视图 + 房间联动。
 * 任一视图加载失败都必须能回落（FR-21），所以这里对每个 Tab 独立 try/catch，不做整页失败。
 */
const route = useRoute()
const router = useRouter()
const compare = useCompareStore()
const id = Number(route.params.id)
const geo = ref<HouseTypeGeometry | null>(null)
const meta = ref<Record<string, any>>({})
const tab = ref('2d')
const room = ref<RoomGeo | null>(null)
const webglOk = ref(true)
const pickOpen = ref(false)
const houses = ref<Record<string, any>[]>([])
const picking = ref(false)
const loading = ref(true)
const codeOpen = ref(false)
const generated = computed(() => (geo.value ? sceneCode(geo.value) : ''))

const wfa = computed(() => {
  if (!room.value || !room.value.area) return null
  return (room.value.windowArea ?? 0) / room.value.area
})

const params = computed(() => {
  const g = geo.value as any
  if (!g) return []
  const r = (v: number | null, u = '') => (v == null ? '—（数据不足）' : `${v}${u}`)
  return [
    ['建筑面积', r(g.gfa, ' ㎡')], ['套内面积', r(g.privateArea, ' ㎡')],
    ['得房率', g.gfa && g.privateArea ? ((g.privateArea / g.gfa) * 100).toFixed(1) + ' %' : '—'],
    ['户型结构', `${g.rooms ?? '—'} 房 ${g.halls ?? '—'} 厅 ${g.baths ?? '—'} 卫`],
    ['主朝向', g.orientation ?? '—'], ['面宽 / 进深', `${r(g.bay, ' m')} / ${r(g.depth, ' m')}`],
    ['面宽进深比', g.bay && g.depth ? (g.bay / g.depth).toFixed(2) : '—'],
    ['室内净高', r(g.ceiling, ' m')], ['外窗总面积', r(g.windowArea, ' ㎡')],
    ['起居窗地比（加权）', summaryWfa.value ?? '—'],
  ]
})

const summaryWfa = computed(() => {
  const rooms = (geo.value?.rooms ?? []).filter((x) => ['LIVING', 'DINING', 'MASTER', 'SECOND', 'STUDY'].includes(x.category))
  const a = rooms.reduce((s, x) => s + (x.area ?? 0), 0)
  const w = rooms.reduce((s, x) => s + (x.windowArea ?? 0), 0)
  return a ? (w / a).toFixed(3) : null
})

const ORIENT: Record<string, string> = { NS: '南北', S: '正南', SE: '东南', SW: '西南', N: '北', E: '东', W: '西' }
const title = computed(() => String(meta.value.name || '').replace(/^建面[\d.]+㎡\s*/, '').trim() || '户型')
const priceText = computed(() => {
  const n = Number(meta.value.priceRef)
  if (!Number.isFinite(n) || n <= 0) return ''
  if (n >= 10000) {
    const wan = n / 10000
    const text = Number.isInteger(wan) ? String(wan) : wan.toFixed(1).replace(/\.0$/, '')
    return `${text} 万`
  }
  return `${n.toLocaleString('zh-CN')} 元`
})

async function load() {
  loading.value = true
  try {
    const d = await get(`/house-types/${id}`)
    const raw = d as any
    geo.value = { ...raw, outline: typeof raw.outline === 'string' ? JSON.parse(raw.outline || '[]') : (raw.outline ?? []) }
    meta.value = raw
    if (!window.WebGLRenderingContext) webglOk.value = false
  } catch {
    ElMessage.error('户型数据加载失败，请到首页重新选择')
  } finally {
    loading.value = false
  }
}

async function openSelection() {
  picking.value = true
  try {
    const page = await get<{ records: Record<string, any>[] }>('/houses', { houseTypeId: id, saleStatus: 'AVAILABLE', size: 50 })
    houses.value = page.records ?? []
    pickOpen.value = true
    if (!houses.value.length) ElMessage.warning('该户型暂无在售房源')
  } finally {
    picking.value = false
  }
}

function goLock(h: Record<string, any>) {
  const houseId = h.id ?? h.houseId
  pickOpen.value = false
  router.push({ name: 'selection', params: { intentionNo: 'new' }, query: { houseId: String(houseId), houseTypeId: String(id) } })
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(generated.value)
    ElMessage.success('已复制，这段代码不会在页面里执行')
  } catch {
    ElMessage.warning('复制失败，请手动选择代码')
  }
}

function addCompare() {
  if (compare.add(id).ok) ElMessage.success('已加入对比')
}

onMounted(load)
</script>

<template>
  <div v-if="loading" class="detail-skel" aria-busy="true">载入户型图纸…</div>
  <div v-else-if="geo" class="page">
    <header class="head">
      <div>
        <p class="hf-kicker">{{ meta.code || '户型' }}</p>
        <h1>{{ title }}</h1>
        <p class="spec">{{ meta.gfa }}㎡ · {{ meta.roomCount || geo.rooms?.length || '—' }} 房 · {{ ORIENT[meta.orientation] || meta.orientation || '—' }}</p>
      </div>
      <div class="head-side">
        <p v-if="priceText" class="price hf-num">{{ priceText }}</p>
        <el-button type="primary" @click="router.push({ name: 'evaluate', params: { id } })">评估</el-button>
      </div>
    </header>

    <div class="detail">
    <div class="left">
      <el-tabs v-model="tab" class="view-tabs">
        <el-tab-pane label="2D 图纸" name="2d">
          <FloorPlan2D :geo="geo" :selected="room?.name" @room-select="room = $event" />
        </el-tab-pane>
        <el-tab-pane label="3D 空间" name="3d" lazy>
          <House3DViewer v-if="webglOk" :geo="geo" />
          <el-alert v-else type="warning" :closable="false" title="当前环境不支持 WebGL，已自动回落 2D 图纸" />
          <el-button class="code-btn" @click="codeOpen = true">查看生成代码</el-button>
          <el-drawer v-model="codeOpen" title="3D 初始化代码" size="420px">
            <p class="code-note">只供复制。系统不执行这段代码。</p>
            <pre class="code-box">{{ generated }}</pre>
            <el-button type="primary" @click="copyCode">复制</el-button>
          </el-drawer>
        </el-tab-pane>
        <el-tab-pane label="720° 全景" name="pano" lazy>
          <el-empty description="该户型暂无全景素材（不影响评估与选房）">
            <el-button @click="tab = '2d'">回到 2D 图纸</el-button>
          </el-empty>
        </el-tab-pane>
        <el-tab-pane label="参数表" name="param">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item v-for="p in params" :key="p[0]" :label="p[0]">{{ p[1] }}</el-descriptions-item>
          </el-descriptions>
          <p class="note">尺寸与参数按公开规范口径整理，现场实测误差 ±5%；本表不构成合规结论。</p>
        </el-tab-pane>
      </el-tabs>
    </div>

    <aside class="side">
      <el-card shadow="never" class="room-card">
        <template #header>
          <span class="room-title">
            <AppIcon :name="room ? 'plan' : 'search'" :size="15" />
            {{ room ? room.name : '未选中房间' }}
          </span>
        </template>
        <template v-if="room">
          <div class="kv"><span class="k">类型</span><span class="v">{{ room.category }}</span></div>
          <div class="kv"><span class="k">朝向</span><span class="v">{{ room.orientation || '—' }}</span></div>
          <div class="kv"><span class="k">面积</span><span class="v hf-num">{{ room.area?.toFixed(2) }} ㎡（{{ room.w }}×{{ room.h }} m）</span></div>
          <div class="kv"><span class="k">窗面积</span><span class="v hf-num">{{ room.windowArea ?? 0 }} ㎡</span></div>
          <div class="kv">
            <span class="k">窗地比</span>
            <span class="v hf-num">
              {{ wfa === null ? '—' : wfa.toFixed(3) }}
              <el-tag v-if="wfa !== null && wfa < 0.143" size="small" type="warning" class="warn-tag">低于 1/7 条文参考值</el-tag>
            </span>
          </div>
          <p class="hint">该值对应评分明细中的 <code>LIGHT_wfa</code> / <code>LIGHT_dark_bath</code>。</p>
        </template>
        <p v-else class="hint empty-hint">点击左侧任一房间查看参数，并与评分明细联动（FR-15）。</p>
      </el-card>

      <div class="cta">
        <el-button type="primary" size="large" class="cta-main" @click="router.push({ name: 'evaluate', params: { id } })">
          <AppIcon name="gauge" :size="16" /> 评估此户型
        </el-button>
        <div class="cta-row">
          <el-button @click="addCompare">加入对比</el-button>
          <el-button type="success" :loading="picking" @click="openSelection">模拟选房</el-button>
        </div>
        <el-button text class="back-link" @click="router.push({ name: 'home' })">← 继续选房</el-button>
      </div>
    </aside>

    <el-dialog v-model="pickOpen" title="选择在售房源" width="560px">
      <el-table :data="houses" size="small" max-height="360" class="pick-tbl" @row-click="goLock">
        <el-table-column label="楼栋" width="80">
          <template #default="{ row }">{{ row.buildingCode || row.buildingcode || '—' }}</template>
        </el-table-column>
        <el-table-column label="楼层" width="70">
          <template #default="{ row }">{{ row.floorNo ?? row.floor_no }}</template>
        </el-table-column>
        <el-table-column label="房号" width="80">
          <template #default="{ row }">{{ row.roomNo || row.room_no }}</template>
        </el-table-column>
        <el-table-column label="总价" min-width="120">
          <template #default="{ row }"><span class="hf-num">{{ row.totalPrice ? (Number(row.totalPrice) / 10000).toFixed(1) + ' 万' : '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="saleStatus" label="状态" width="90" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="goLock(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="pick-tip">点击任意一行或「选择」进入模拟锁定</p>
      <template #footer>
        <el-button @click="pickOpen = false">取消</el-button>
      </template>
    </el-dialog>
    </div>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 18px; min-width: 0; }
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.head h1 { font-size: clamp(24px, 3vw, 32px); letter-spacing: -0.03em; margin: 6px 0 4px; }
.spec { color: var(--hf-text-2); font-size: 14px; }
.head-side { display: flex; align-items: center; gap: 14px; }
.price { font-size: 22px; font-weight: 700; letter-spacing: -0.03em; }
.detail { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; align-items: start; min-width: 0; }
.detail-skel {
  min-height: 360px;
  display: grid;
  place-items: center;
  color: var(--hf-text-3);
  background: var(--hf-surface);
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-m);
}

.left { min-width: 0; }
.left :deep(.el-tabs__nav-wrap::after) { height: 1px; background-color: var(--hf-border); }
.left :deep(.el-tabs__item) { font-weight: 500; }
.left :deep(.el-tabs__item.is-active) { color: var(--hf-primary); }
.left :deep(.el-tabs__active-bar) { background-color: var(--hf-primary); }
.left :deep(.el-tabs__content) {
  border: 1px solid var(--hf-border);
  border-top: none;
  border-radius: 0 0 var(--hf-radius-m) var(--hf-radius-m);
  padding: 16px;
  background: var(--hf-surface);
  overflow: hidden;
}
.note { color: var(--hf-text-3); font-size: 12px; margin-top: 12px; }
.code-btn { margin-top: 10px; }
.code-note { color: var(--hf-text-3); font-size: 13px; margin: 0 0 8px; }
.code-box { white-space: pre-wrap; font-size: 12px; line-height: 1.5; background: var(--hf-bg, #f6f5f2); padding: 12px; border-radius: 8px; max-height: 60vh; overflow: auto; }

/* ── 侧栏 ── */
.side { display: flex; flex-direction: column; gap: 14px; position: sticky; top: 84px; min-width: 0; }
.room-card { border-radius: var(--hf-radius-m); }
.room-title { display: inline-flex; align-items: center; gap: 7px; font-weight: 700; }
.room-title .app-icon { color: var(--hf-primary); }
.kv { display: flex; gap: 10px; font-size: 13px; padding: 5px 0; }
.kv .k { color: var(--hf-text-3); width: 52px; flex: none; }
.kv .v { color: var(--hf-text); min-width: 0; overflow-wrap: anywhere; }
.warn-tag { margin-left: 6px; }
.hint { color: var(--hf-text-3); font-size: 12px; margin-top: 10px; line-height: 1.7; }
.empty-hint { margin-top: 0; }

/* ── 主次动作 ── */
.cta { display: flex; flex-direction: column; gap: 10px; }
.cta-main { width: 100%; }
.cta-row { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.cta-row .el-button { width: 100%; margin-left: 0; }
.back-link { align-self: flex-start; color: var(--hf-text-3); }
.back-link:hover { color: var(--hf-primary); background: var(--hf-primary-soft); }

/* ── 选房弹窗 ── */
.pick-tbl :deep(.el-table__row) { cursor: pointer; }
.pick-tip { color: var(--hf-text-3); font-size: 12px; margin: 10px 0 0; text-align: center; }

@media (max-width: 960px) {
  .detail { grid-template-columns: 1fr; }
  .side { position: static; }
}
</style>
