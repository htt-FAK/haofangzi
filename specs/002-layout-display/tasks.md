# TASKS 002 · 户型展示

## Layer 1：契约与几何数据
- [x] T-021 冻结 `GET /api/house-types`、`/api/house-types/{id}` 契约与几何 JSON Schema（`contracts/geometry.schema.json`）→ FR-11/12
- [ ] T-022 建模 `hf_room` 表 + `HouseTypeDetailVO`（含 rooms / doors / windows 嵌套）；补 `seed-data.sql` 20 个户型的房间构件
- [ ] T-023 [P] 样例素材：8 个 SVG 户型图、2 个 GLB（可占位）、2 个 720° 全景图，放 `frontend/public/demo/`

## Layer 2：前端 2D/3D/全景
- [ ] T-024 `utils/geometry.ts`：包围盒、scale、坐标变换、`hitTest`（含 4 个 vitest 用例）→ AC-10/11
- [ ] T-025 `FloorPlan2D.vue`：墙体/房间/门窗/三类标注、指北针旋转、比例尺 → FR-12/13/14
- [ ] T-026 房间高亮与侧栏联动（emit `room-select` → 详情抽屉，含窗地比）→ FR-15/19
- [ ] T-027 `House3DViewer.vue`：GLB 优先 + 参数化盒体回落 + Orbit/漫游双相机 + `dispose` → FR-16/17, AC-12~14
- [ ] T-028 `PanoViewer.vue`：球面贴图 + 热区 + 全屏 + 降级 → FR-18
- [ ] T-029 `BuildingSection.vue` 楼栋剖面与遮挡示意 → FR-20
- [ ] T-030 `ParamTable.vue` 参数表（≥18 项）与免责提示、评分徽标 → FR-19
- [ ] T-031 `views/HouseTypesView.vue` 户型列表（筛选 + 分页 + 骨架屏 + 空态）→ FR-11, AC-16
- [ ] T-032 统一降级层 `useMediaFallback`（WebGL/图片/全景任一失败均有回退）→ FR-21

## Layer 3：管理端
- [ ] T-033 户型录入表单 + 房间构件子表（可拖动/输入坐标），越界错误定位 → FR-22, AC-15
- [ ] T-034 素材上传（SVG/GLB/全景）与签名 URL 接口 `GET /api/media/sign`

## Layer 4：验证与文档
- [ ] T-035 性能：列表 p95 采样脚本 + 3D 帧率记录（≥30fps）→ NFR-01/02
- [ ] T-036 `docs/02-需求分析` DFD-1.2 与 DD（户型/房间构件条目）；`docs/04-详细设计` §3.2 2D 绘制流程图
- [ ] T-037 `docs/03-概要设计` 展示服务模块 + 类图（`docs/diagrams/housetype-class.mmd`）
- [ ] T-038 `docs/05-用户手册` §3 三种视图操作说明 + `docs/06` TC-D-01~08

## 追溯
FR-11~22 → T-021~034；AC-10~16 → TC-D-01~08；NFR-01/02/21 → T-032/035。
