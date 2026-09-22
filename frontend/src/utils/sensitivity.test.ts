import { describe, expect, it } from 'vitest'
import { sensitivityNote, type WeightedScore } from './sensitivity'

function col(id: number, cost: number, light: number): WeightedScore {
  return {
    houseTypeId: id,
    total: light * 0.9 + cost * 0.1,
    dimensions: [
      { code: 'LIGHT', weight: 0.9, score: light },
      { code: 'COST', weight: 0.1, score: cost },
    ],
  }
}

describe('sensitivityNote', () => {
  it('keeps the recommendation when cost is not decisive', () => {
    const note = sensitivityNote([col(1, 60, 90), col(2, 100, 70)])
    expect(note).toBe('经济权重 ±10% 后推荐不变。')
  })

  it('flags a flip when the cheaper plan wins only after raising cost weight', () => {
    const wide = (id: number, light: number, cost: number): WeightedScore => ({
      houseTypeId: id,
      total: light * 0.8 + cost * 0.2,
      dimensions: [
        { code: 'LIGHT', weight: 0.8, score: light },
        { code: 'COST', weight: 0.2, score: cost },
      ],
    })
    const note = sensitivityNote([wide(1, 91, 30), wide(2, 74, 95)])
    expect(note).toBe('经济权重 ±10% 会改推荐。')
  })
})
