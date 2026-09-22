import { describe, expect, it } from 'vitest'
import { bbox, fitScale } from './geometry'

const geo = {
  outline: [[0, 0], [10, 0], [10, 8], [0, 8]] as [number, number][],
  rooms: [{ x: 0, y: 0, w: 4, h: 3, name: '客厅', category: 'LIVING' }],
}

describe('geometry', () => {
  it('包围盒取外轮廓', () => {
    expect(bbox(geo as any)).toEqual({ minX: 0, minY: 0, w: 10, h: 8 })
  })

  it('画布放得下时比例尺为正', () => {
    expect(fitScale(geo as any, 400, 300)).toBeGreaterThan(0)
  })
})
