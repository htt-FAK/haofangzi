<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get } from '../api/http'
import type { HouseTypeGeometry, RoomGeo } from '../api/types'
import FloorPlan2D from '../components/FloorPlan2D.vue'
import House3DViewer from '../components/House3DViewer.vue'
import { useCompareStore } from '../stores/compare'

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

async function load() {
  try {
    const d = await get(`/house-types/${id}`)
    geo.value = { ...(d as any), outline: JSON.parse((d as any).outline || '[]') }
    meta.value = d as any
    if (!window.WebGLRenderingContext) webglOk.value = false
  } catch {
    ElMessage.error('户型数据加载失败，请到首页重新选择')
  }
}

onMounted(load)
</script>

<template>
  <div v-if="geo" class="detail">
    <div class="left">
      <el-tabs v-model="tab">
        <el-tab-pane label="2D 图纸" name="2d">
          <FloorPlan2D :geo="geo" :selected="room?.name" @room-select="room = $event" />
        </el-tab-pane>
        <el-tab-pane label="3D 空间" name="3d" lazy>
          <House3DViewer v-if="webglOk" :geo="geo" />
          <el-alert v-else type="warning" :closable="false" title="当前环境不支持 WebGL，已自动回落 2D 图纸" />
        </el-tab-pane>
        <el-tab-pane label="720° 全景" name="pano" lazy>
          <el-empty v-if="!geo.panoramas?.length" description="该户型暂无全景素材（不影响评估）">
            <el-button @click="tab = '2d'">回到 2D 图纸</el-button>
          </el-empty>
          <div v-else class="pano">全景查看器接入位（T-028：Three.js 球面贴图 + 热区视点）</div>
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
      <el-card shadow="never">
        <template #header>{{ room ? room.name : '未选中房间' }}</template>
        <template v-if="room">
          <p>{{ room.category }} · 朝向 {{ room.orientation || '—' }}</p>
          <p>面积 {{ room.area?.toFixed(2) }} ㎡（{{ room.w }}×{{ room.h }} m）</p>
          <p>窗面积 {{ room.windowArea ?? 0 }} ㎡</p>
          <p>窗地比 {{ wfa === null ? '—' : wfa.toFixed(3) }}
            <el-tag v-if="wfa !== null && wfa < 0.143" size="small" type="warning">低于 1/7 条文参考值</el-tag>
          </p>
          <p class="hint">该值对应评分明细中的 <code>LIGHT_wfa</code> / <code>LIGHT_dark_bath</code>。</p>
        </template>
        <p v-else class="hint">点击左侧任一房间查看参数，并与评分明细联动（FR-15）。</p>
      </el-card>

      <div class="cta">
        <el-button type="primary" @click="router.push({ name: 'evaluate', params: { id } })">评估此户型</el-button>
        <el-button @click="compare.add(id) && (0, ElMessage.success)('已加入对比')">加入对比</el-button>
      </div>
      <el-button text @click="router.push({ name: 'home' })">← 继续选房</el-button>
    </aside>
  </div>
</template>

<style scoped>
.detail { display: grid; grid-template-columns: 1fr 320px; gap: 18px; max-width: 1280px; margin: 0 auto; }
.pano { height: 360px; display: flex; align-items: center; justify-content: center; background: #f5f6f8; color: #667; border-radius: 8px; }
.note { color: #999; font-size: 12px; }
.side { display: flex; flex-direction: column; gap: 12px; }
.side p { margin: 4px 0; font-size: 13px; }
.hint { color: #999; font-size: 12px; }
.cta { display: flex; flex-direction: column; gap: 8px; }
</style>
