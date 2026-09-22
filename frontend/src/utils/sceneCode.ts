import type { HouseTypeGeometry } from '../api/types'

/** 按房间几何拼出 Three.js 片段。只给人复制，页面不执行这段字符串。 */
export function sceneCode(geo: HouseTypeGeometry): string {
  const when = new Date().toISOString()
  const outline = (geo.outline ?? []).map((p) => `[${p[0]}, ${p[1]}]`).join(', ')
  const rooms = (geo.rooms ?? []).map((room) => {
    const ceiling = geo.ceiling || 2.8
    return [
      `  // ${room.name} ${room.category}`,
      `  group.add(box(${room.w}, ${ceiling}, ${room.h}, ${room.x}, ${room.y}))`,
    ].join('\n')
  }).join('\n')
  return [
    `// generatedAt: ${when}`,
    `// promptKey: scene-code`,
    `// outline: [${outline}]`,
    `// 不执行。复制后由人工贴进 House3DViewer。`,
    'function buildHouseScene(geom) {',
    '  const group = new THREE.Group()',
    '  function box(w, h, d, x, z) {',
    '    const mesh = new THREE.Mesh(',
    '      new THREE.BoxGeometry(w, h, d),',
    '      new THREE.MeshLambertMaterial({ color: 0xd9d3c5 })',
    '    )',
    '    mesh.position.set(x + w / 2, h / 2, z + d / 2)',
    '    return mesh',
    '  }',
    rooms || '  // 这个户型还没有房间',
    '  return group',
    '}',
  ].join('\n')
}
