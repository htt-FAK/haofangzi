<script setup lang="ts">
import { computed } from 'vue'
import { ROOM_COLOR } from '../utils/geometry'

/** 与列表接口 plan[]、详情 rooms 矩形同一口径（米，西北角为原点）。 */
export interface PlanBox {
  x: number
  y: number
  w: number
  h: number
  category?: string
}

const props = defineProps<{ plan?: PlanBox[] | null }>()

const layout = computed(() => {
  const rooms = (props.plan ?? [])
    .map((r) => ({
      x: Number(r.x),
      y: Number(r.y),
      w: Number(r.w),
      h: Number(r.h),
      category: r.category ?? '',
    }))
    .filter((r) => Number.isFinite(r.w) && Number.isFinite(r.h) && r.w > 0 && r.h > 0)
  if (!rooms.length) return null
  const minX = Math.min(...rooms.map((r) => r.x))
  const minY = Math.min(...rooms.map((r) => r.y))
  const maxX = Math.max(...rooms.map((r) => r.x + r.w))
  const maxY = Math.max(...rooms.map((r) => r.y + r.h))
  const bw = Math.max(0.1, maxX - minX)
  const bh = Math.max(0.1, maxY - minY)
  const W = 200
  const H = 150
  const pad = 12
  const scale = Math.min((W - pad * 2) / bw, (H - pad * 2) / bh)
  const ox = (W - bw * scale) / 2
  const oy = (H - bh * scale) / 2
  return rooms.map((r) => ({
    x: ox + (r.x - minX) * scale,
    y: oy + (r.y - minY) * scale,
    w: r.w * scale,
    h: r.h * scale,
    fill: ROOM_COLOR[r.category] ?? '#8a8a84',
  }))
})
</script>

<template>
  <svg class="plan" viewBox="0 0 200 150" preserveAspectRatio="xMidYMid meet" role="img" aria-hidden="true">
    <rect width="200" height="150" fill="#f4f7f8" />
    <template v-if="layout">
      <rect
        v-for="(b, i) in layout"
        :key="i"
        :x="b.x"
        :y="b.y"
        :width="b.w"
        :height="b.h"
        :fill="b.fill"
        fill-opacity="0.38"
        stroke="#1a1a1a"
        stroke-width="1.6"
      />
    </template>
  </svg>
</template>

<style scoped>
.plan {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
