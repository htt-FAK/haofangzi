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
  const W = 220
  const H = 160
  const pad = 16
  const scale = Math.min((W - pad * 2) / bw, (H - pad * 2) / bh)
  const ox = (W - bw * scale) / 2
  const oy = (H - bh * scale) / 2
  return rooms.map((r) => ({
    x: ox + (r.x - minX) * scale,
    y: oy + (r.y - minY) * scale,
    w: r.w * scale,
    h: r.h * scale,
    fill: ROOM_COLOR[r.category] ?? '#94a3b8',
  }))
})
</script>

<template>
  <div class="plan-wrapper">
    <svg class="plan" viewBox="0 0 220 160" preserveAspectRatio="xMidYMid meet" role="img" aria-hidden="true">
      <defs>
        <!-- 精密建筑方格网 -->
        <pattern id="hf-cad-grid" width="10" height="10" patternUnits="userSpaceOnUse">
          <path d="M 10 0 L 0 0 0 10" fill="none" stroke="rgba(148, 163, 184, 0.2)" stroke-width="0.5" />
        </pattern>
        <!-- 蓝图微渐变背景 -->
        <linearGradient id="hf-blueprint-grad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#f8fafc" />
          <stop offset="100%" stop-color="#f1f5f9" />
        </linearGradient>
      </defs>

      <!-- 底板背景与制图网格 -->
      <rect width="220" height="160" fill="url(#hf-blueprint-grad)" />
      <rect width="220" height="160" fill="url(#hf-cad-grid)" />

      <!-- 制图定位十字对齐线 (Registration Marks) -->
      <g stroke="#cbd5e1" stroke-width="0.8">
        <line x1="8" y1="12" x2="16" y2="12" />
        <line x1="12" y1="8" x2="12" y2="16" />
        <line x1="204" y1="12" x2="212" y2="12" />
        <line x1="208" y1="8" x2="208" y2="16" />
        <line x1="8" y1="148" x2="16" y2="148" />
        <line x1="12" y1="144" x2="12" y2="152" />
        <line x1="204" y1="148" x2="212" y2="148" />
        <line x1="208" y1="144" x2="208" y2="152" />
      </g>

      <!-- 指北针标识 -->
      <g transform="translate(198, 22)">
        <circle cx="0" cy="0" r="8" fill="#ffffff" stroke="#94a3b8" stroke-width="0.8" />
        <path d="M 0 -6 L 2.5 1 L 0 -1 L -2.5 1 Z" fill="#0f172a" />
        <path d="M 0 6 L 2.5 1 L 0 -1 L -2.5 1 Z" fill="#cbd5e1" />
        <text x="0" y="-8" font-size="6" font-weight="700" fill="#64748b" text-anchor="middle" font-family="sans-serif">N</text>
      </g>

      <!-- 房间构件渲染 -->
      <template v-if="layout">
        <g class="rooms-group">
          <rect
            v-for="(b, i) in layout"
            :key="i"
            :x="b.x"
            :y="b.y"
            :width="b.w"
            :height="b.h"
            :fill="b.fill"
            fill-opacity="0.45"
            stroke="#0f172a"
            stroke-width="1.8"
            stroke-linejoin="round"
            rx="1.5"
          />
        </g>
      </template>

      <!-- 空白占位提示 -->
      <template v-else>
        <g transform="translate(110, 80)" text-anchor="middle">
          <text y="0" font-size="11" font-weight="600" fill="#94a3b8" font-family="sans-serif">平面图绘制中</text>
          <text y="14" font-size="8.5" fill="#cbd5e1" font-family="sans-serif">CAD BLUEPRINT</text>
        </g>
      </template>

      <!-- 比例尺说明水印 -->
      <g transform="translate(14, 150)">
        <line x1="0" y1="0" x2="24" y2="0" stroke="#94a3b8" stroke-width="1" />
        <line x1="0" y1="-2" x2="0" y2="2" stroke="#94a3b8" stroke-width="1" />
        <line x1="24" y1="-2" x2="24" y2="2" stroke="#94a3b8" stroke-width="1" />
        <text x="28" y="2" font-size="6.5" font-weight="600" fill="#94a3b8" font-family="sans-serif">1:100</text>
      </g>
    </svg>
  </div>
</template>

<style scoped>
.plan-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--hf-canvas);
}

.plan {
  display: block;
  width: 100%;
  height: 100%;
  transition: transform 0.4s var(--hf-ease);
}

.plan-wrapper:hover .plan {
  transform: scale(1.03);
}

.rooms-group rect {
  transition: fill-opacity 0.2s ease;
}

.rooms-group rect:hover {
  fill-opacity: 0.65;
}
</style>
