<script setup lang="ts">
import { onMounted, ref, watch, nextTick } from 'vue'
import type { HouseTypeGeometry, RoomGeo } from '../api/types'
import { ROOM_COLOR, areaSumDeviation, fitScale, hitTest, makeTransform, northRotationDeg } from '../utils/geometry'

/**
 * 参数化 2D 户型图（spec 002 FR-12~15；算法见 docs/04 §3.2）。
 * 数据源是房间构件（geometry.schema.json），不是位图，所以缩放后尺寸标注仍清晰、可点选联动。
 */
const props = defineProps<{ geo: HouseTypeGeometry; selected?: string; height?: number }>()
const emit = defineEmits<{ (e: 'room-select', r: RoomGeo | null): void }>()

const canvas = ref<HTMLCanvasElement | null>(null)
const labelMode = ref<'area' | 'dim' | 'none'>('area')
const zoom = ref(1)

function draw() {
  const el = canvas.value
  if (!el || !props.geo?.rooms?.length) return
  const dpr = window.devicePixelRatio || 1
  const W = el.clientWidth
  const H = props.height ?? 520
  el.width = W * dpr
  el.height = H * dpr
  const ctx = el.getContext('2d')!
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, W, H)

  const scale = fitScale(props.geo, W, H) * zoom.value
  const t = { ...makeTransform(props.geo, W, H), scale }
  const pt = (x: number, y: number): [number, number] => [
    t.ox + (x - t.minX) * scale,
    t.oy + (y - t.minY) * scale,
  ]

  // ① 外墙轮廓
  ctx.lineWidth = 6
  ctx.strokeStyle = '#1f2937'
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ;(props.geo.outline ?? []).forEach(([x, y], i) => {
    const [cx, cy] = pt(x, y)
    i === 0 ? ctx.moveTo(cx, cy) : ctx.lineTo(cx, cy)
  })
  ctx.closePath()
  ctx.stroke()

  // ② 房间填充 + 描边 + 选中高亮
  props.geo.rooms.forEach((r) => {
    const [x0, y0] = pt(r.x, r.y)
    const w = r.w * scale
    const h = r.h * scale
    const selected = props.selected === r.name
    ctx.globalAlpha = selected ? 0.42 : 0.18
    ctx.fillStyle = ROOM_COLOR[r.category] ?? '#888'
    ctx.fillRect(x0, y0, w, h)
    ctx.globalAlpha = 1
    ctx.lineWidth = selected ? 2 : 1
    ctx.strokeStyle = selected ? '#215e7a' : '#8a8a84'
    ctx.strokeRect(x0, y0, w, h)

    // ③ 窗：墙上三线；门：留缺口 + 1/4 弧
    ;(r.windows ?? []).forEach((win) => {
      const [sx, sy] = pt(win.x, r.y + (win.wall === 'N' ? 0 : r.h))
      ctx.beginPath()
      ctx.moveTo(sx, sy)
      ctx.lineTo(sx + win.width * scale, sy)
      ctx.lineWidth = 3
      ctx.strokeStyle = '#215e7a'
      ctx.stroke()
    })
    ;(r.doors ?? []).forEach((d) => {
      const [dx, dy] = pt(d.x, r.y)
      ctx.beginPath()
      ctx.arc(dx, dy, Math.max(6, (d.width ?? 0.9) * scale), 0, Math.PI / 2)
      ctx.lineWidth = 1
      ctx.strokeStyle = '#999'
      ctx.stroke()
    })

    // ④ 标注
    if (labelMode.value === 'area') {
      ctx.fillStyle = '#111'
      ctx.font = '12px system-ui'
      ctx.textAlign = 'center'
      ctx.fillText(r.name, x0 + w / 2, y0 + h / 2 - 6)
      ctx.fillText(`${r.area.toFixed(1)}㎡`, x0 + w / 2, y0 + h / 2 + 10)
    } else if (labelMode.value === 'dim' && w > 46 && h > 30) {
      ctx.fillStyle = '#33507a'
      ctx.font = '11px system-ui'
      ctx.textAlign = 'center'
      ctx.fillText(`${Math.round(r.w * 1000)}×${Math.round(r.h * 1000)}`, x0 + w / 2, y0 + h - 8)
    }
  })

  // ⑤ 比例尺与指北针
  ctx.fillStyle = '#333'
  ctx.fillRect(W - 120, H - 26, scale, 4)
  ctx.font = '11px system-ui'
  ctx.textAlign = 'left'
  ctx.fillText('1 m', W - 120, H - 32)
  ctx.save()
  ctx.translate(W - 34, 34)
  ctx.rotate((northRotationDeg(props.geo.orientation) * Math.PI) / 180)
  ctx.beginPath()
  ctx.moveTo(0, -16)
  ctx.lineTo(5, 8)
  ctx.lineTo(-5, 8)
  ctx.closePath()
  ctx.fillStyle = '#c62828'
  ctx.fill()
  ctx.fillText('N', -4, -20)
  ctx.restore()

  const dev = areaSumDeviation(props.geo)
  if (dev !== null && dev > 0.03) console.warn(`房间面积和与套内偏差 ${(dev * 100).toFixed(1)}%（AC-10 要求 ≤3%）`)
}

function onClick(e: MouseEvent) {
  const el = canvas.value!
  const rect = el.getBoundingClientRect()
  const t = makeTransform(props.geo, rect.width, props.height ?? 520)
  t.scale = fitScale(props.geo, rect.width, props.height ?? 520) * zoom.value
  emit('room-select', hitTest(props.geo.rooms, e.clientX - rect.left, e.clientY - rect.top, t))
}

onMounted(() => nextTick(draw))
watch(() => [props.geo, labelMode.value, zoom.value], () => nextTick(draw), { deep: true })
</script>

<template>
  <div class="plan-container">
    <div class="control-bar">
      <div class="bar-left">
        <span class="bar-label">标注模式</span>
        <el-radio-group v-model="labelMode" size="small">
          <el-radio-button value="area">房间面积</el-radio-button>
          <el-radio-button value="dim">开间进深(mm)</el-radio-button>
          <el-radio-button value="none">纯图纸</el-radio-button>
        </el-radio-group>
      </div>
      
      <div class="bar-right">
        <span class="bar-label">缩放</span>
        <el-button-group size="small">
          <el-button @click="zoom = Math.max(0.6, +(zoom - 0.2).toFixed(1))">－</el-button>
          <el-button disabled class="zoom-indicator hf-num">{{ Math.round(zoom * 100) }}%</el-button>
          <el-button @click="zoom = Math.min(2.4, +(zoom + 0.2).toFixed(1))">＋</el-button>
        </el-button-group>
        <span class="tip">
          <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8">
            <circle cx="8" cy="8" r="7" />
            <line x1="8" y1="7" x2="8" y2="12" />
            <circle cx="8" cy="4" r="0.8" fill="currentColor" />
          </svg>
          点击房间联动右侧窗地比明细
        </span>
      </div>
    </div>
    <div class="canvas-wrap">
      <canvas ref="canvas" :style="{ height: (height ?? 540) + 'px' }" @click="onClick" />
    </div>
  </div>
</template>

<style scoped>
.plan-container {
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-m);
  background: var(--hf-surface);
  overflow: hidden;
  box-shadow: var(--hf-shadow-xs);
}

.control-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--hf-border);
  background: #ffffff;
  flex-wrap: wrap;
}

.bar-left, .bar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.bar-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--hf-text-3);
}

.zoom-indicator {
  font-size: 11px !important;
  padding: 0 8px !important;
  color: var(--hf-text-2) !important;
  background: var(--hf-canvas-subtle) !important;
}

.tip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--hf-text-3);
  font-size: 12px;
}

.canvas-wrap {
  position: relative;
  background-color: var(--hf-canvas);
  background-image: 
    linear-gradient(rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.12) 1px, transparent 1px);
  background-size: 20px 20px;
}

canvas {
  width: 100%;
  display: block;
  cursor: crosshair;
}
</style>
