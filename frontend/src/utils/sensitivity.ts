/** 经济适配权重 ±10% 后，推荐户型会不会换成另一套。维度分数不变，只改权重再归一。 */
export interface WeightedScore {
  houseTypeId: number
  total: number
  dimensions: { code: string; weight: number; score: number | null }[]
}

export function sensitivityNote(cols: WeightedScore[]): string {
  if (cols.length < 2) return ''
  const base = bestId(cols, 1)
  const down = bestId(cols, 0.9)
  const up = bestId(cols, 1.1)
  if (down === base && up === base) return '经济权重 ±10% 后推荐不变。'
  return '经济权重 ±10% 会改推荐。'
}

function bestId(cols: WeightedScore[], costFactor: number): number {
  let id = cols[0].houseTypeId
  let top = Number.NEGATIVE_INFINITY
  for (const col of cols) {
    const score = retotal(col, costFactor)
    if (score > top) {
      top = score
      id = col.houseTypeId
    }
  }
  return id
}

function retotal(col: WeightedScore, costFactor: number): number {
  let weightSum = 0
  let acc = 0
  for (const dim of col.dimensions) {
    if (dim.score == null) continue
    const weight = dim.weight * (dim.code === 'COST' ? costFactor : 1)
    weightSum += weight
    acc += weight * dim.score
  }
  return weightSum === 0 ? col.total : acc / weightSum
}
