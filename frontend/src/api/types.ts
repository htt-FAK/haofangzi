/** 前端类型（与 openapi.yaml / geometry.schema.json 同构；T-021 起可用 openapi-typescript 自动生成替换手写部分） */

export interface ApiResponse<T> {
  code: number
  message: string
  traceId?: string
  data: T
}

export interface Page<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** ── 002 几何契约（单位 m，原点=包围盒西北角）── */
export interface Opening {
  x: number
  y?: number
  width: number
  wall?: 'N' | 'S' | 'E' | 'W'
  swing?: 'NW' | 'NE' | 'SW' | 'SE'
}

export interface RoomGeo {
  name: string
  category: 'LIVING' | 'DINING' | 'MASTER' | 'SECOND' | 'STUDY' | 'KITCHEN' | 'BATH' | 'BALCONY' | 'CORRIDOR' | 'UTILITY' | 'ENTRY'
  x: number
  y: number
  w: number
  h: number
  area: number
  orientation?: string
  windowArea?: number
  doors?: Opening[]
  windows?: Opening[]
}

export interface HouseTypeGeometry {
  code: string
  orientation: string
  bay: number
  depth: number
  ceiling: number
  gfa?: number
  privateArea?: number
  outline: [number, number][]
  rooms: RoomGeo[]
  panoramas?: { name: string; url: string; target?: number[] }[]
  tags?: string[]
}

export interface HouseTypeCard {
  id: number
  code: string
  name: string
  gfa: number
  rooms: number
  orientation: string
  priceRef: string
  latestScore: string | number | ''
  latestLevel: string
}

/** ── 004 评分 ── */
export interface EvalMetric {
  metricCode: string
  metricName: string
  value: string
  grade: string
  score: number
  internalWeight: number
  evidence: string
  basis?: string
  missing: boolean
  sourceFields?: string[]
}

export interface EvalDim {
  code: string
  name: string
  weight: number
  score: number | null
  contribution: number | null
}

export interface EvaluationResult {
  evaluationId?: number
  houseTypeId: number
  houseTypeName: string
  houseId?: number | null
  setVersion: string
  total: number
  level: '优' | '良' | '中' | '差'
  dimensions: EvalDim[]
  metrics: EvalMetric[]
  suggestions: { dimension: string; metric: string; text: string; potential: number }[]
  missingCount: number
  disclaimer: string
}

/** ── 003 锁房 ── */
export interface HouseSnap {
  houseId?: number
  buildingCode?: string
  floorNo?: number
  roomNo?: string
  area?: string
  totalPriceWan?: string
  houseTypeName?: string
  saleStatus?: string
}

export interface LockResult {
  lockNo: string
  intentionNo: string
  expireAt: string
  renewCount: number
  remainSeconds: number
  snapshot?: HouseSnap
  alternatives?: HouseSnap[]
}

/** 统一等级色板：颜色 + 箭头双编码（NFR-10 色弱可读） */
export const GRADE_COLOR: Record<string, string> = {
  优: '#0a8a4a', 良: '#1e88e5', 中: '#ef9f2d', 差: '#c62828', 数据不足: '#9e9e9e',
}
export const GRADE_ARROW: Record<string, string> = {
  优: '↑↑', 良: '↑', 中: '→', 差: '↓', 数据不足: '—',
}
