-- ===========================================================
-- 肇庆市"好房子"在线选房与户型智能评估系统 · 物理设计（MySQL 8.0 / InnoDB / utf8mb4）
-- 依据：specs/000-program/data-model.md（唯一事实来源）、constitution.md 第五条
-- 执行：mysql -uroot -p < database/schema.sql && mysql -uroot -p haofangzi < database/seed-data.sql
-- ===========================================================
CREATE DATABASE IF NOT EXISTS haofangzi DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE haofangzi;

-- ---------- 1. 用户与权限（spec 001） ----------
CREATE TABLE sys_user (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  phone             VARCHAR(64)  NOT NULL COMMENT '登录账号；注销后置为 SHA256 串',
  password          VARCHAR(100) NOT NULL COMMENT 'BCrypt cost>=10，禁止明文',
  nickname          VARCHAR(40)           DEFAULT NULL,
  role              VARCHAR(16)  NOT NULL DEFAULT 'BUYER' COMMENT 'BUYER/CONSULTANT/ADMIN/NULL_ROLE',
  status            TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
  budget_min        DECIMAL(12,2)         DEFAULT NULL COMMENT '元',
  budget_max        DECIMAL(12,2)         DEFAULT NULL,
  family_structure  VARCHAR(32)           DEFAULT NULL COMMENT 'SINGLE/COUPLE/FAMILY_3/FAMILY_4_2GEN/FAMILY_5_3GEN',
  must_rooms        INT                   DEFAULT NULL COMMENT '最少居室数 1-6',
  prefer_orientation VARCHAR(64)          DEFAULT NULL COMMENT 'JSON 数组串 ["S","SE"]',
  prefer_tags       VARCHAR(255)          DEFAULT NULL COMMENT 'JSON 数组串，≤5 项',
  consultant_id     BIGINT                DEFAULT NULL COMMENT '绑定顾问（自关联）',
  created_at        DATETIME(0)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME(0)  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted           TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_phone (phone),
  KEY idx_user_role_status (role, status),
  KEY idx_user_consultant (consultant_id)
) ENGINE=InnoDB COMMENT='用户（购房者/顾问/管理员）';

-- ---------- 2. 楼盘与楼栋 ----------
CREATE TABLE hf_project (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL, district VARCHAR(32) NOT NULL, address VARCHAR(128) DEFAULT NULL,
  lng DECIMAL(10,6) DEFAULT NULL, lat DECIMAL(10,6) DEFAULT NULL,
  developer VARCHAR(64) DEFAULT NULL, avg_price DECIMAL(12,2) DEFAULT NULL COMMENT '元/㎡',
  delivery_year INT DEFAULT NULL, tag VARCHAR(128) DEFAULT NULL COMMENT '逗号分隔：地铁口,学区房,低密',
  cover_url VARCHAR(255) DEFAULT NULL, summary VARCHAR(512) DEFAULT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_project_district (district, deleted)
) ENGINE=InnoDB COMMENT='楼盘项目';

CREATE TABLE hf_building (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL, code VARCHAR(32) NOT NULL COMMENT '如 3 栋',
  total_floor INT NOT NULL, units_per_floor INT NOT NULL, elevator_count INT NOT NULL DEFAULT 1,
  south_occlusion DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT '1=无遮挡；低层采光修正用',
  green_rate DECIMAL(5,2) DEFAULT NULL COMMENT '关联绿化率 %',
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_building (project_id, code, deleted),
  CONSTRAINT fk_building_project FOREIGN KEY (project_id) REFERENCES hf_project (id)
) ENGINE=InnoDB COMMENT='楼栋';

-- ---------- 3. 户型与房间构件（004 指标的唯一数据源） ----------
CREATE TABLE hf_house_type (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  code VARCHAR(32) NOT NULL COMMENT '如 A1-98',
  name VARCHAR(64) NOT NULL,
  gfa DECIMAL(8,2) NOT NULL COMMENT '建筑面积 ㎡ 30~300',
  private_area DECIMAL(8,2) NOT NULL COMMENT '套内面积 ㎡',
  rooms INT NOT NULL, halls INT NOT NULL DEFAULT 1, baths INT NOT NULL DEFAULT 1,
  orientation VARCHAR(8) NOT NULL COMMENT 'S/SE/SW/E/W/N/NS',
  bay DECIMAL(6,2) NOT NULL COMMENT '面宽 m',
  depth DECIMAL(6,2) NOT NULL COMMENT '进深 m',
  ceiling_height DECIMAL(4,2) NOT NULL DEFAULT 3.00,
  balcony_count INT NOT NULL DEFAULT 1,
  window_area DECIMAL(8,2) DEFAULT NULL COMMENT '外窗总面积 ㎡（通风/采光修正）',
  kitchen_area DECIMAL(6,2) DEFAULT NULL, bath_area DECIMAL(6,2) DEFAULT NULL,
  master_bedroom_area DECIMAL(6,2) DEFAULT NULL,
  circulation_len DECIMAL(6,2) DEFAULT NULL COMMENT '主动线长度 m',
  corridor_ratio DECIMAL(4,3) DEFAULT NULL COMMENT '走道面积占比',
  irr_ratio DECIMAL(4,3) DEFAULT NULL COMMENT '异形空间占比',
  storage_wall_len DECIMAL(6,2) DEFAULT NULL COMMENT '收纳墙长度 m',
  near_road TINYINT NOT NULL DEFAULT 0 COMMENT '卧室朝向与临路侧同向',
  adjacent_elevator TINYINT NOT NULL DEFAULT 0 COMMENT '主卧贴电梯井/管井',
  style VARCHAR(64) DEFAULT NULL COMMENT '精装标准/毛坯',
  plan_svg_url VARCHAR(255) DEFAULT NULL, model_glb_url VARCHAR(255) DEFAULT NULL, pano_url VARCHAR(255) DEFAULT NULL,
  outline_json VARCHAR(512) DEFAULT NULL COMMENT '外墙轮廓折线 [[x,y],..]',
  ext_json VARCHAR(2048) DEFAULT NULL COMMENT '全景视点、标签、备用指标位',
  price_ref DECIMAL(12,2) DEFAULT NULL COMMENT '参考总价 元',
  remark VARCHAR(512) DEFAULT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_ht_code (project_id, code, deleted),
  KEY idx_ht_filter (gfa, rooms, orientation, deleted),
  CONSTRAINT fk_ht_project FOREIGN KEY (project_id) REFERENCES hf_project (id)
) ENGINE=InnoDB COMMENT='户型（评估主体）';

CREATE TABLE hf_room (
  id BIGINT NOT NULL AUTO_INCREMENT,
  house_type_id BIGINT NOT NULL,
  name VARCHAR(32) NOT NULL,
  category VARCHAR(16) NOT NULL COMMENT 'LIVING/DINING/MASTER/SECOND/STUDY/KITCHEN/BATH/BALCONY/CORRIDOR/UTILITY/ENTRY',
  x DECIMAL(6,2) NOT NULL, y DECIMAL(6,2) NOT NULL,
  w DECIMAL(6,2) NOT NULL, h DECIMAL(6,2) NOT NULL,
  area DECIMAL(6,2) NOT NULL COMMENT '受控冗余：与 w*h 偏差 ≤5%（校验在 service）',
  orientation VARCHAR(8) DEFAULT NULL COMMENT '该空间主要采光面；NONE=无采光面',
  window_area DECIMAL(6,2) NOT NULL DEFAULT 0.00 COMMENT '0 → 暗房间',
  window_openable_ratio DECIMAL(4,3) DEFAULT 0.600,
  doors_json VARCHAR(1024) DEFAULT NULL COMMENT '[{x,y,width,wall,swing}]',
  windows_json VARCHAR(1024) DEFAULT NULL COMMENT '[{x,y,width,wall,sill}]',
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_room_ht (house_type_id, deleted),
  CONSTRAINT fk_room_ht FOREIGN KEY (house_type_id) REFERENCES hf_house_type (id)
) ENGINE=InnoDB COMMENT='房间构件（2D/3D/指标共用几何）';

-- ---------- 4. 房源（销控）与行为 ----------
CREATE TABLE hf_house (
  id BIGINT NOT NULL AUTO_INCREMENT,
  building_id BIGINT NOT NULL, house_type_id BIGINT NOT NULL,
  unit_code VARCHAR(16) NOT NULL DEFAULT '1单元',
  floor_no INT NOT NULL, room_no VARCHAR(8) NOT NULL COMMENT '如 03',
  direction VARCHAR(8) DEFAULT NULL,
  area DECIMAL(8,2) NOT NULL, unit_price DECIMAL(12,2) NOT NULL, total_price DECIMAL(12,2) NOT NULL,
  sale_status VARCHAR(12) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/LOCKED/RESERVED/SOLD',
  view_level VARCHAR(16) DEFAULT NULL COMMENT '江景/园景/无',
  noise_level TINYINT DEFAULT NULL COMMENT '1 低 ~ 5 高',
  lock_expire_at DATETIME(0) DEFAULT NULL,
  sold_at DATETIME(0) DEFAULT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_house (building_id, unit_code, floor_no, room_no, deleted),
  KEY idx_house_status (sale_status, deleted),
  KEY idx_house_ht_status (house_type_id, sale_status),
  KEY idx_house_floor (building_id, floor_no),
  CONSTRAINT fk_house_building FOREIGN KEY (building_id) REFERENCES hf_building (id),
  CONSTRAINT fk_house_ht FOREIGN KEY (house_type_id) REFERENCES hf_house_type (id)
) ENGINE=InnoDB COMMENT='房源（一套房）';

CREATE TABLE hf_favorite (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL, target_type VARCHAR(16) NOT NULL COMMENT 'HOUSE_TYPE/HOUSE', target_id BIGINT NOT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_fav (user_id, target_type, target_id, deleted),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB COMMENT='收藏（上限 50，service 校验）';

CREATE TABLE hf_house_lock (
  id BIGINT NOT NULL AUTO_INCREMENT,
  house_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
  intention_no VARCHAR(20) DEFAULT NULL COMMENT 'YX+yyyyMMdd+6 位',
  status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/CONVERTED/CANCELED',
  active_flag TINYINT GENERATED ALWAYS AS (IF(`status` = 'ACTIVE', 1, NULL)) STORED
    COMMENT '仅 ACTIVE 时为 1，其余 NULL —— 让"一房一有效锁"的唯一索引可以被后续锁复用',
  expire_at DATETIME(0) NOT NULL, renew_count INT NOT NULL DEFAULT 0,
  create_snapshot VARCHAR(512) DEFAULT NULL COMMENT '锁定时房源快照 JSON（FR-34）',
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_lock_active (house_id, active_flag) COMMENT '并发兜底：一房同时只能有一条 ACTIVE 锁（AC-21 保护 3）',
  UNIQUE KEY uk_lock_intention (intention_no),
  KEY idx_lock_expire (status, expire_at),
  CONSTRAINT fk_lock_house FOREIGN KEY (house_id) REFERENCES hf_house (id)
) ENGINE=InnoDB COMMENT='模拟选房锁/意向';

-- ---------- 5. 评估域（spec 004） ----------
CREATE TABLE eval_dimension (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(8) NOT NULL COMMENT 'LIGHT/VENT/CIRC/UTIL/QUIET/GREEN/COST',
  name VARCHAR(16) NOT NULL, weight_default DECIMAL(4,3) NOT NULL, order_idx INT NOT NULL DEFAULT 0,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_dim (code)
) ENGINE=InnoDB COMMENT='评估维度字典（全局）';

CREATE TABLE eval_rule_set (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL, version VARCHAR(16) NOT NULL COMMENT 'v1 / v1.1',
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE；PUBLISHED 只读',
  source VARCHAR(8) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AI；AI 草案必须人工确认才能发布',
  confirmed TINYINT NOT NULL DEFAULT 0 COMMENT 'AI 草案人工审阅标记',
  active TINYINT NOT NULL DEFAULT 0 COMMENT '全局仅一个 ACTIVE=1（service 保证）',
  template_code VARCHAR(24) NOT NULL DEFAULT 'GENERAL' COMMENT '人群模板',
  level_threshold_json VARCHAR(128) DEFAULT NULL COMMENT '{"excellent":85,"good":75,"fair":60}',
  note VARCHAR(255) DEFAULT NULL, published_at DATETIME(0) DEFAULT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_set (name, version, deleted), KEY idx_set_active (active, status)
) ENGINE=InnoDB COMMENT='规则集版本';

CREATE TABLE eval_rule_set_dim (
  id BIGINT NOT NULL AUTO_INCREMENT,
  set_id BIGINT NOT NULL, dimension_id BIGINT NOT NULL, weight DECIMAL(4,3) NOT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_set_dim (set_id, dimension_id, deleted),
  CONSTRAINT fk_rsd_set FOREIGN KEY (set_id) REFERENCES eval_rule_set (id),
  CONSTRAINT fk_rsd_dim FOREIGN KEY (dimension_id) REFERENCES eval_dimension (id)
) ENGINE=InnoDB COMMENT='规则集内维度权重（可覆写默认，实现动态权重 I1）';

CREATE TABLE eval_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  set_id BIGINT NOT NULL, dimension_id BIGINT NOT NULL,
  metric_code VARCHAR(40) NOT NULL COMMENT '必须能命中已注册 MetricCalculator',
  metric_name VARCHAR(64) NOT NULL,
  source_fields VARCHAR(255) DEFAULT NULL COMMENT 'JSON 数组：依赖字段，用于缺数据归因',
  operator VARCHAR(8) NOT NULL COMMENT 'LT/LE/GT/GE/BETWEEN/EQ/BOOL',
  unit VARCHAR(8) DEFAULT NULL,
  internal_weight DECIMAL(4,3) NOT NULL COMMENT '维度内权重，Σ=1',
  higher_is_better TINYINT NOT NULL DEFAULT 1,
  tier_json VARCHAR(2048) NOT NULL COMMENT '分档数组，左闭右开，见 scoring-rule.schema.json',
  basis VARCHAR(255) DEFAULT NULL COMMENT '依据条文，NFR-11 必填',
  suggestion VARCHAR(255) DEFAULT NULL COMMENT '改进建议模板，支持 {value}/{need} 占位',
  order_idx INT NOT NULL DEFAULT 0,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_rule (set_id, metric_code, deleted),
  KEY idx_rule_dim (set_id, dimension_id),
  CONSTRAINT fk_rule_set FOREIGN KEY (set_id) REFERENCES eval_rule_set (id),
  CONSTRAINT fk_rule_dim FOREIGN KEY (dimension_id) REFERENCES eval_dimension (id)
) ENGINE=InnoDB COMMENT='评估规则（最小判定单元）';

CREATE TABLE evaluation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL COMMENT '预览态可为空',
  house_type_id BIGINT NOT NULL, house_id BIGINT DEFAULT NULL,
  set_id BIGINT NOT NULL, set_version VARCHAR(16) NOT NULL COMMENT '版本冻结',
  template_code VARCHAR(24) NOT NULL DEFAULT 'GENERAL',
  total_score DECIMAL(5,1) NOT NULL, level VARCHAR(8) NOT NULL COMMENT '优/良/中/差',
  missing_count INT NOT NULL DEFAULT 0 COMMENT '数据不足指标数',
  detail_json JSON NOT NULL COMMENT '完整快照：指标值/命中的档/证据/权重，用于历史复现',
  ai_note VARCHAR(1024) DEFAULT NULL COMMENT 'AI 解读文字（不参与打分）',
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_eval_user (user_id, created_at),
  KEY idx_eval_ht (house_type_id, set_version, template_code),
  CONSTRAINT fk_eval_ht FOREIGN KEY (house_type_id) REFERENCES hf_house_type (id)
) ENGINE=InnoDB COMMENT='评测记录（快照不可变）';

CREATE TABLE eval_sample_stat (
  id BIGINT NOT NULL AUTO_INCREMENT,
  metric_code VARCHAR(40) NOT NULL, sorted_values VARCHAR(512) NOT NULL COMMENT '20 个样本升序，用于分位对比',
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_stat (metric_code)
) ENGINE=InnoDB COMMENT='指标分位样本（FR-54）';

-- ---------- 6. 对比报告（spec 005） ----------
CREATE TABLE compare_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL, title VARCHAR(64) NOT NULL,
  house_type_ids VARCHAR(64) NOT NULL COMMENT 'JSON 数组 2-4 个',
  set_version VARCHAR(16) NOT NULL, template_code VARCHAR(24) NOT NULL DEFAULT 'GENERAL',
  matrix_json JSON NOT NULL COMMENT '行=维度/指标/绝对量，含 best/worst/缺失',
  conclusion VARCHAR(2048) DEFAULT NULL COMMENT '推荐/理由/优缺点/风险 结构 JSON',
  ai_generated TINYINT NOT NULL DEFAULT 0,
  hallucination_dropped INT NOT NULL DEFAULT 0,
  share_token VARCHAR(24) DEFAULT NULL, expire_at DATETIME(0) DEFAULT NULL, view_count INT NOT NULL DEFAULT 0,
  consultant_note VARCHAR(255) DEFAULT NULL, shown TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_share (share_token), KEY idx_rep_user (user_id, created_at),
  CONSTRAINT fk_rep_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB COMMENT='对比报告（只读快照）';

CREATE TABLE compare_report_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_id BIGINT NOT NULL, house_type_id BIGINT NOT NULL, evaluation_id BIGINT DEFAULT NULL,
  total_score DECIMAL(5,1) NOT NULL, dims_json VARCHAR(1024) NOT NULL, order_idx INT NOT NULL DEFAULT 0,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_item_report (report_id),
  CONSTRAINT fk_item_report FOREIGN KEY (report_id) REFERENCES compare_report (id)
) ENGINE=InnoDB COMMENT='报告对比项（具化多对多并保留当时分数）';

-- ---------- 7. 预约（spec 006） ----------
CREATE TABLE hf_slot_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL, visit_date DATE NOT NULL, slot VARCHAR(13) NOT NULL COMMENT '09:00-10:00',
  capacity INT NOT NULL DEFAULT 6, enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_slot (project_id, visit_date, slot, deleted)
) ENGINE=InnoDB COMMENT='时段容量配置';

CREATE TABLE appointment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL, consultant_id BIGINT DEFAULT NULL,
  project_id BIGINT NOT NULL, house_type_id BIGINT DEFAULT NULL, house_id BIGINT DEFAULT NULL,
  intention_no VARCHAR(20) DEFAULT NULL,
  visit_date DATE NOT NULL, visit_slot VARCHAR(13) NOT NULL, party_size INT NOT NULL DEFAULT 2,
  contact_phone VARCHAR(20) NOT NULL COMMENT '出口一律脱敏',
  remark VARCHAR(200) DEFAULT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/ARRIVED/COMPLETED/CANCELED',
  cancel_reason VARCHAR(32) DEFAULT NULL, audit_remark VARCHAR(255) DEFAULT NULL, audit_by BIGINT DEFAULT NULL,
  notified_at DATETIME(0) DEFAULT NULL COMMENT '提前 1 天提醒幂等标记',
  reminded_2h_at DATETIME(0) DEFAULT NULL COMMENT '提前 2 小时提醒幂等标记',
  focus_tags VARCHAR(128) DEFAULT NULL COMMENT '到访后回写的关注维度',
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_ap_query (project_id, visit_date, status, deleted),
  KEY idx_ap_consultant (consultant_id, status),
  KEY idx_ap_user (user_id, visit_date),
  CONSTRAINT fk_ap_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_ap_project FOREIGN KEY (project_id) REFERENCES hf_project (id)
) ENGINE=InnoDB COMMENT='预约看房单';

-- ---------- 8. 系统与横切 ----------
CREATE TABLE sys_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL, type VARCHAR(24) NOT NULL COMMENT 'APPOINT_1D/APPOINT_2H/APPOINT_AUDIT/LOCK_RELEASE/AI_NOTICE',
  title VARCHAR(64) NOT NULL, content VARCHAR(512) NOT NULL, read_flag TINYINT NOT NULL DEFAULT 0,
  biz_id VARCHAR(32) DEFAULT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_msg_user (user_id, read_flag, created_at)
) ENGINE=InnoDB COMMENT='站内通知';

CREATE TABLE sys_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL, action VARCHAR(40) NOT NULL COMMENT 'LOGIN/LOCK/RELEASE/PUBLISH_RULE/IMPORT_HOUSE/AUDIT_APPOINT/…',
  target_type VARCHAR(24) DEFAULT NULL, target_id VARCHAR(32) DEFAULT NULL,
  ip VARCHAR(64) DEFAULT NULL, ua VARCHAR(255) DEFAULT NULL, detail_json VARCHAR(1024) DEFAULT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_audit (user_id, action, created_at)
) ENGINE=InnoDB COMMENT='审计日志（NFR-06/09）';

CREATE TABLE ai_call_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL, prompt_key VARCHAR(40) NOT NULL, prompt_version VARCHAR(8) NOT NULL,
  model VARCHAR(40) DEFAULT NULL, latency_ms INT DEFAULT NULL, tokens INT DEFAULT NULL,
  ok TINYINT NOT NULL DEFAULT 1, fallback TINYINT NOT NULL DEFAULT 0,
  hallucination_dropped INT NOT NULL DEFAULT 0, payload_digest VARCHAR(64) DEFAULT NULL COMMENT '不记录完整 prompt（脱敏）',
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_ai (prompt_key, created_at)
) ENGINE=InnoDB COMMENT='AI 调用日志（FR-120）';

CREATE TABLE ai_chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL COMMENT 'user/assistant',
  content TEXT NOT NULL,
  created_at DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chat_user (user_id, id)
) ENGINE=InnoDB COMMENT='顾问对话记忆（按用户）';

-- ---------- 9. 视图（统计与看板；报告 §4.3 提及） ----------
CREATE OR REPLACE VIEW v_house_sale_stat AS
SELECT b.project_id, COUNT(*) AS total,
       SUM(h.sale_status='AVAILABLE') AS available,
       SUM(h.sale_status='LOCKED')    AS locked,
       SUM(h.sale_status='RESERVED')  AS reserved,
       SUM(h.sale_status='SOLD')      AS sold
FROM hf_house h JOIN hf_building b ON b.id = h.building_id
WHERE h.deleted = 0 GROUP BY b.project_id;

CREATE OR REPLACE VIEW v_ht_latest_score AS
SELECT e.* FROM evaluation e
JOIN (SELECT house_type_id, template_code, MAX(id) AS mid FROM evaluation WHERE deleted=0 GROUP BY house_type_id, template_code) t
  ON t.mid = e.id;

CREATE OR REPLACE VIEW v_appointment_stat AS
SELECT project_id,
       COUNT(*) AS submitted,
       SUM(status IN ('CONFIRMED','ARRIVED','COMPLETED')) AS confirmed,
       SUM(status IN ('ARRIVED','COMPLETED')) AS arrived,
       SUM(status='CANCELED') AS canceled
FROM appointment WHERE deleted=0 GROUP BY project_id;

-- ---------- 10. 设计与校验说明 ----------
-- 1) 全表统一 id / created_at / updated_at / deleted（NFR-12），MyBatis-Plus @TableLogic 处理 deleted；
-- 2) 唯一索引包含 deleted 列，避免软删后无法复用业务编号（同业务号删除后重建的常见演示场景）；
-- 3) 金额 DECIMAL(12,2)、面积与尺寸 DECIMAL(6~8,2)、比率 DECIMAL(4,3)，禁止 FLOAT（数据字典 DI-01~09）；
-- 4) evaluation / compare_report 的 JSON 快照列保证"历史不可篡改 + 重开一致"（AC-32/44）；
-- 5) 外键仅建在强一致性关系上（户型-房间、房源-楼栋），报表类关联走索引 + service 校验，便于批量导入；
-- 6) 演示数据规模：3 楼盘 / 12 楼栋 / 20 户型 / ~180 房间构件 / 600 房源 / 规则集 3 版本 21 规则 / 预约 12 条；
--    数据量增长预估（3 学期）：评测 1.2 万、报告 2 千、预约 3 千、审计 5 万行 —— 单表无分库分表需求；
-- 7) 备份与重置：mysqldump 每日；异常时执行文件头两条命令 5 分钟内恢复演示环境。

