import type { HouseTypeGeometry, RoomGeo } from '../api/types'

/**
 * 户型 2D/3D 共用的几何工具（docs/04 §3.2 算法的 TS 落点；任务 T-024 的单测对象）。
 * 纯函数、无 DOM 依赖，便于 vitest 直接断言。
 */

export interface ViewBox {
  minX: number
  minY: number
  w: number
  h: number
}

export function bbox(geo: HouseTypeGeometry): ViewBox {
  const pts = geo.outline?.length ? geo.outline : geo.rooms.map((r) => [r.x, r.y]).concat(geo.rooms.map((r) => [r.x + r.w, r.y + r.h]))
  const xs = pts.map((p) => Number(p[0]))
  const ys = pts.map((p) => Number(p[1]))
  const minX = Math.min(...xs)
  const minY = Math.min(...ys)
  return { minX, minY, w: Math.max(...xs) - minX, h: Math.max(...ys) - minY }
}

/** 留出标注边距后的缩放系数（比例尺 ≥1:100 的等价的"尽量填满"） */
export function fitScale(geo: HouseTypeGeometry, canvasW: number, canvasH: number, margin = 48): number {
  const b = bbox(geo)
  if (b.w <= 0 || b.h <= 0) return 40
  return Math.min((canvasW - 2 * margin) / b.w, (canvasH - 2 * margin) / b.h)
}

export interface Transform {
  scale: number
  ox: number
  oy: number
  minX: number
  minY: number
}

export function makeTransform(geo: HouseTypeGeometry, canvasW: number, canvasH: number): Transform {
  const b = bbox(geo)
  const scale = fitScale(geo, canvasW, canvasH)
  return { scale, ox: (canvasW - b.w * scale) / 2, oy: (canvasH - b.h * scale) / 2, minX: b.minX, minY: b.minY }
}

export function toCanvas(p: [number, number], t: Transform): [number, number] {
  return [t.ox + (p[0] - t.minX) * t.scale, t.oy + (p[1] - t.minY) * t.scale]
}

/** 画布像素 → 世界坐标（命中测试用） */
export function toWorld(p: [number, number], t: Transform): [number, number] {
  return [(p[0] - t.ox) / t.scale + t.minX, (p[1] - t.oy) / t.scale + t.minY]
}

/** 逆序返回最上层房间（阳台/走道常后画，点击应命中它而不是底图，AC-11） */
export function hitTest(rooms: RoomGeo[], px: number, py: number, t: Transform): RoomGeo | null {
  const [wx, wy] = toWorld([px, py], t)
  for (let i = rooms.length - 1; i >= 0; i--) {
    const r = rooms[i]
    if (wx >= r.x && wx <= r.x + r.w && wy >= r.y && wy <= r.y + r.h) return r
  }
  return null
}

/** 房间面积和与套内面积偏差（AC-10：≤3%；也是录入校验 DD-F05 的前置检查） */
export function areaSumDeviation(geo: HouseTypeGeometry): number | null {
  if (!geo.privateArea) return null
  const sum = geo.rooms.reduce((s, r) => s + r.area, 0)
  return Math.abs(sum - geo.privateArea) / geo.privateArea
}

/** 指北针旋转：主朝向决定图纸上"北"的方向（默认 S 朝下 → N 朝上） */
export function northRotationDeg(orientation: string): number {
  const map: Record<string, number> = { NS: 0, S: 0, SE: 45, SW: -45, E: 90, W: -90, N: 180 }
  return map[orientation] ?? 0
}

export const ROOM_COLOR: Record<string, string> = {
  LIVING: '#4f83c4', DINING: '#6aa9d0', MASTER: '#c98f3a', SECOND: '#d8b169', STUDY: '#9a86c4',
  KITCHEN: '#4f9e7c', BATH: '#3f9fa8', BALCONY: '#8ab86b', CORRIDOR: '#b0b0b0', UTILITY: '#a58f7a', ENTRY: '#9e9e9e',
}
