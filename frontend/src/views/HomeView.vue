<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get } from '../api/http'
import type { HouseTypeCard, Page } from '../api/types'
import { useCompareStore } from '../stores/compare'

/** 户型检索（spec 002 FR-11 / 003 FR-31）。画像有值时默认回填预算与居室（AC-03）。 */
const router = useRouter()
const compare = useCompareStore()
const list = ref<HouseTypeCard[]>([])
const total = ref(0)
const loading = ref(false)
const q = reactive({ rooms: undefined as number | undefined, minArea: undefined as number | undefined, maxArea: undefined as number | undefined, orientation: '', page: 1, size: 12 })

async function load() {
  loading.value = true
  try {
    const p = await get<Page<HouseTypeCard>>('/house-types', { ...q })
    list.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function addToCompare(id: number) {
  const r = compare.add(id)
  if (!r.ok) ElMessage.warning(r.reason ?? '无法加入对比')
}

onMounted(load)
</script>

<template>
  <div class="home">
    <header class="hero">
      <h1>看清楚每一套"好房子"</h1>
      <p>按采光、通风、动线、实用、静谧、绿色、经济 7 个维度可解释打分 · 数据为课程演示样例</p>
    </header>

    <el-card class="filters" shadow="never">
      <el-form inline @submit.prevent="load">
        <el-form-item label="居室">
          <el-select v-model="q.rooms" clearable placeholder="不限" style="width: 110px" @change="load">
            <el-option v-for="n in [2,3,4,5]" :key="n" :label="n + ' 房及以上'" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="建面">
          <el-input-number v-model="q.minArea" :min="30" :max="300" :step="10" controls-position="right" style="width: 110px" @change="load" />
          <span class="dash">—</span>
          <el-input-number v-model="q.maxArea" :min="30" :max="300" :step="10" controls-position="right" style="width: 110px" @change="load" />
          <span class="unit">㎡</span>
        </el-form-item>
        <el-form-item label="朝向">
          <el-select v-model="q.orientation" clearable placeholder="不限" style="width: 120px" @change="load">
            <el-option label="南北" value="NS" /><el-option label="正南" value="S" />
            <el-option label="东南" value="SE" /><el-option label="西南" value="SW" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button text @click="Object.assign(q, { rooms: undefined, minArea: undefined, maxArea: undefined, orientation: '', page: 1 }), load()">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-loading="loading" class="grid">
      <el-card v-for="h in list" :key="h.id" shadow="hover" class="card">
        <div class="thumb">{{ h.code }}</div>
        <h3>{{ h.name }}</h3>
        <div class="row"><span>建面 {{ h.gfa }}㎡</span><span>{{ h.rooms }} 房</span><span>{{ h.orientation }}</span></div>
        <div class="row price">{{ h.priceRef }}</div>
        <div class="row">
          <el-tag v-if="h.latestScore" size="small" type="success">{{ h.latestScore }} 分 · {{ h.latestLevel }}</el-tag>
          <el-tag v-else size="small" type="info">未评估</el-tag>
        </div>
        <div class="ops">
          <el-button size="small" @click="router.push({ name: 'house-type', params: { id: h.id } })">看户型</el-button>
          <el-button size="small" type="primary" @click="router.push({ name: 'evaluate', params: { id: h.id } })">评估</el-button>
          <el-button size="small" text @click="addToCompare(h.id)">＋对比</el-button>
        </div>
      </el-card>
      <el-empty v-if="!loading && !list.length" description="没有符合条件的户型，试着放宽面积或朝向" />
    </div>

    <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="prev, pager, next" class="pager" @current-change="load" />
  </div>
</template>

<style scoped>
.home { max-width: 1180px; margin: 0 auto; }
.hero { padding: 18px 0 6px; }
.hero h1 { margin: 0; font-size: 26px; }
.hero p { color: #666; margin: 6px 0 14px; font-size: 13px; }
.filters { margin-bottom: 14px; }
.dash { margin: 0 6px; color: #999; }
.unit { margin-left: 6px; color: #999; font-size: 12px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 14px; min-height: 200px; }
.card .thumb { height: 96px; border-radius: 6px; background: linear-gradient(135deg, #e8f0fb, #f6f7f9); display: flex; align-items: center; justify-content: center; color: #4b6fa8; font-size: 18px; letter-spacing: 1px; }
.card h3 { font-size: 15px; margin: 10px 0 6px; }
.row { display: flex; gap: 10px; color: #666; font-size: 12px; margin-bottom: 4px; }
.price { color: #c62828; }
.ops { margin-top: 8px; display: flex; gap: 6px; align-items: center; }
.pager { margin: 18px 0; justify-content: center; }
</style>
