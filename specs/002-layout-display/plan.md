# PLAN 002 · 户型展示（HOW）

**改动面**: `HouseTypeController`、`HouseTypeService`、`domain/dto/HouseTypeDetailVO`、
前端 `views/HouseTypeDetailView.vue` + `components/{FloorPlan2D,House3DViewer,PanoViewer,ParamTable,BuildingSection}.vue`、`utils/geometry.ts`

## 1. 契约
`GET /api/house-types`（分页筛选）、`GET /api/house-types/{id}`（详情含 rooms）、
`GET /api/house-types/{id}/rooms`、`GET /api/buildings/{id}/section`（剖面数据）、
`POST/PUT /api/admin/house-types`（管理端录入）、`GET /api/media/sign?url=`（签名资源）。

## 2. 几何数据契约（前端 2D 与 3D 共用）
```json
{ "outline": [[0,0],[9.8,0],[9.8,7.6],[0,7.6]],
  "orientation": "S", "bay": 9.8, "depth": 7.6, "ceiling": 3.0,
  "rooms": [ {"name":"客厅","category":"LIVING","x":0.4,"y":0,"w":4.8,"h":5.2,
              "area":25.0,"orientation":"S","windowArea":4.2,
              "doors":[{"x":4.8,"y":2.4,"width":0.9,"swing":"NE"}],
              "windows":[{"x":2.0,"y":0,"width":2.4}] } ] }
```
坐标单位米，原点为包围盒西北角；`orientation` 表示该房间**主要采光面**朝向。

## 3. 关键算法

### 3.1 2D 绘制（Canvas 2D，`FloorPlan2D.vue`）
1. 计算包围盒 → `scale = min(canvasW/outlineW, canvasH/outlineH) * 0.88`（留标注边距）。
2. 三层绘制：墙体（轮廓 stroke 6px）→ 房间 fill（分类色板，透明度 0.18）→ 构件（门：弧线 + 门槛缺口；窗：三线）。
3. 标注层：房间中心绘制 `名称\n面积`；长边中点绘制尺寸线（`4800`）；比例尺与指北针固定右下角，指北针随 `orientation` 旋转。
4. 交互：逆变换 `hitTest(px,py) → room`（矩形包含判定），高亮 = 重绘该房间 fill 0.42 + 描边 2px。
5. 性能：`devicePixelRatio` 缩放、`requestAnimationFrame` 合帧；命中判定用房间数组线性扫描（≤12 项，无需四叉树）。

### 3.2 3D 参数化生成（Three.js，`House3DViewer.vue`）
- 每个房间 → `BoxGeometry(w, ceiling, h)` 墙体描边（`EdgesGeometry` + `LineSegments`）+ 半透明地面 `PlaneGeometry`。
- 门窗开洞用贴图遮罩（演示精度足够，避免 CSG 布尔运算复杂度 —— 宪法第六条）。
- 相机：`OrbitControls`（俯视）/ `PointerLockControls` 简化版（漫游，WASD + 高度固定 1.6m）。
- 资源：优先 `GLTFLoader.load(model_glb_url)`，`onError` → 回落参数化；`useFallBack` 状态上报埋点。
- 卸载时 `dispose()` 几何/材质/纹理，防内存泄漏（答辩演示 10 分钟不卡）。

### 3.3 全景（`PanoViewer.vue`）
`SphereGeometry(500,60,40)` + `scale.x=-1` 内翻 + `MeshBasicMaterial(map=panoUrl, side=BackSide)`；
视点信息 `[{name,target:[x,y,z]}]` 由后端 `ext_json.panoSpots` 提供，热区用 `CSS2DRenderer` 叠加。
无全景素材时 `fallback`：三张环视照片序列帧 + 提示。

### 3.4 楼栋剖面（`BuildingSection.vue`）
CSS/SVG 生成：`total_floor` 层矩形堆叠，高亮本层；`south_occlusion` 画前方遮挡体（比例 = 系数），
说明"低楼层采光衰减"与 004 的 `LIGHT_floor_occlusion` 指标同源。

## 4. 状态与降级
`useAsyncState` 风格：`loading / empty / error / degrade`；任一 WebGL 上下文创建失败 → `degrade=true` 并强制回落 SVG。
图片 `loading="lazy"` + 占位骨架；接口失败展示"重试"而不是空表格。

## 5. 管理端录入校验
后端 `@Valid`：面积 = w×h（±5%）、坐标和 ≤ 包围盒、房间数与 `rooms` 字段一致、`windowArea ≤ 墙面积`；
越界返回 `40012` 带 `roomIndex`（AC-15）。

## 6. 与 AI（007）的接缝
`/api/ai/scene-code?houseTypeId=` 返回 LLM 生成的 Three.js 场景初始化代码（不落库运行，仅在管理端"代码预览 + 一键应用到本地调试"），
演示时说明"AI 生成 → 人工审阅 → 采用"，避免黑盒（宪法第三条）。

## 7. 测试
- 单元（前端）：`geometry.hitTest`、scale 计算、面积求和偏差（vitest）。
- 后端：详情 VO 组装、录入校验矩阵、签名 URL 过期。
- 手测：AC-10~16 全部脚本化写入 `docs/06-测试计划` TC-D-01~08；低端机帧率单独记录表。
