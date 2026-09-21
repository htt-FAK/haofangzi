# TASKS 005 · 对比报告

- [ ] T-089 `compare_report` 表 + `CompareReport` 实体 + `matrix_json` 结构定稿（`contracts/report-matrix.md`）→ FR-76
- [ ] T-090 `CompareService.buildMatrix()`（best/worst、差值口径、缺失单元格）→ FR-72/73，AC-42/47
- [ ] T-091 缺失评估时同步调 004 生成（同 template + version）→ FR-71
- [ ] T-092 数量/边界校验（2~4、`40016`）、未登录引导 → FR-70，AC-40/41
- [ ] T-093 前端候选池 Pinia store + 列表/收藏/详情三处入口 → FR-70
- [ ] T-094 `CompareView` + `CompareMatrix.vue`（sticky 首列、色 + 箭头双编码、点击理由定位矩阵行）→ FR-72
- [ ] T-095 结论渲染卡（推荐/优缺点/风险/适配人群/AI 角标）→ FR-74/75，AC-43
- [ ] T-096 报告保存与我的报告列表（`setVersion` 标注、查看次数）→ FR-76
- [ ] T-097 分享令牌生成/撤销/过期 + 免登录只读接口 + PII 剥离 → FR-78，AC-45/46
- [ ] T-098 PDF 服务端导出（模板 + 中文字体 + 两页排版 + 降级 HTML）→ FR-77
- [ ] T-099 灵敏度提示（调 004 FR-59 结果，权重 ±10% 是否翻转推荐）→ FR-79
- [ ] T-100 顾问备注/已带看 + 客户打开通知 → FR-80
- [ ] T-101 单测（buildMatrix、token、AI 证据校验）+ MockMvc（AC-40/41/44/45）+ PDF 快照 → 宪法第四条
- [ ] T-102 文档：`docs/03` 报告模块与结构图叶子、`docs/04` §5 矩阵与结论伪代码 + 时序图、`docs/05` §7、`docs/06` TC-R-01~09

**追溯**：FR-70~80 → T-089~102；AC-40~47 → TC-R-xx。
