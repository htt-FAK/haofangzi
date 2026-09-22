PRAGMA foreign_keys = ON;

CREATE TABLE sys_user (
id INTEGER PRIMARY KEY,
  phone             TEXT  NOT NULL ,
  password          TEXT NOT NULL ,
  nickname          TEXT           DEFAULT NULL,
  role              TEXT  NOT NULL DEFAULT 'BUYER' ,
  status            INTEGER      NOT NULL DEFAULT 1 ,
  budget_min        REAL         DEFAULT NULL ,
  budget_max        REAL         DEFAULT NULL,
  family_structure  TEXT           DEFAULT NULL ,
  must_rooms        INTEGER                   DEFAULT NULL ,
  prefer_orientation TEXT          DEFAULT NULL ,
  prefer_tags       TEXT          DEFAULT NULL ,
  consultant_id     INTEGER                DEFAULT NULL ,
  created_at        TEXT  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TEXT  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted           INTEGER      NOT NULL DEFAULT 0,
  UNIQUE (phone)
);

CREATE TABLE hf_project (
id INTEGER PRIMARY KEY,
  name TEXT NOT NULL, district TEXT NOT NULL, address TEXT DEFAULT NULL,
  lng REAL DEFAULT NULL, lat REAL DEFAULT NULL,
  developer TEXT DEFAULT NULL, avg_price REAL DEFAULT NULL ,
  delivery_year INTEGER DEFAULT NULL, tag TEXT DEFAULT NULL ,
  cover_url TEXT DEFAULT NULL, summary TEXT DEFAULT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE hf_building (
id INTEGER PRIMARY KEY,
  project_id INTEGER NOT NULL, code TEXT NOT NULL ,
  total_floor INTEGER NOT NULL, units_per_floor INTEGER NOT NULL, elevator_count INTEGER NOT NULL DEFAULT 1,
  south_occlusion REAL NOT NULL DEFAULT 1.00 ,
  green_rate REAL DEFAULT NULL ,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (project_id, code, deleted),
  CONSTRAINT fk_building_project FOREIGN KEY (project_id) REFERENCES hf_project (id)
);

CREATE TABLE hf_house_type (
id INTEGER PRIMARY KEY,
  project_id INTEGER NOT NULL,
  code TEXT NOT NULL ,
  name TEXT NOT NULL,
  gfa REAL NOT NULL ,
  private_area REAL NOT NULL ,
  rooms INTEGER NOT NULL, halls INTEGER NOT NULL DEFAULT 1, baths INTEGER NOT NULL DEFAULT 1,
  orientation TEXT NOT NULL ,
  bay REAL NOT NULL ,
  depth REAL NOT NULL ,
  ceiling_height REAL NOT NULL DEFAULT 3.00,
  balcony_count INTEGER NOT NULL DEFAULT 1,
  window_area REAL DEFAULT NULL ,
  kitchen_area REAL DEFAULT NULL, bath_area REAL DEFAULT NULL,
  master_bedroom_area REAL DEFAULT NULL,
  circulation_len REAL DEFAULT NULL ,
  corridor_ratio REAL DEFAULT NULL ,
  irr_ratio REAL DEFAULT NULL ,
  storage_wall_len REAL DEFAULT NULL ,
  near_road INTEGER NOT NULL DEFAULT 0 ,
  adjacent_elevator INTEGER NOT NULL DEFAULT 0 ,
  style TEXT DEFAULT NULL ,
  plan_svg_url TEXT DEFAULT NULL, model_glb_url TEXT DEFAULT NULL, pano_url TEXT DEFAULT NULL,
  outline_json TEXT DEFAULT NULL ,
  ext_json TEXT DEFAULT NULL ,
  price_ref REAL DEFAULT NULL ,
  remark TEXT DEFAULT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (project_id, code, deleted),
  CONSTRAINT fk_ht_project FOREIGN KEY (project_id) REFERENCES hf_project (id)
);

CREATE TABLE hf_room (
id INTEGER PRIMARY KEY,
  house_type_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  category TEXT NOT NULL ,
  x REAL NOT NULL, y REAL NOT NULL,
  w REAL NOT NULL, h REAL NOT NULL,
  area REAL NOT NULL ,
  orientation TEXT DEFAULT NULL ,
  window_area REAL NOT NULL DEFAULT 0.00 ,
  window_openable_ratio REAL DEFAULT 0.600,
  doors_json TEXT DEFAULT NULL ,
  windows_json TEXT DEFAULT NULL ,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT fk_room_ht FOREIGN KEY (house_type_id) REFERENCES hf_house_type (id)
);

CREATE TABLE hf_house (
id INTEGER PRIMARY KEY,
  building_id INTEGER NOT NULL, house_type_id INTEGER NOT NULL,
  unit_code TEXT NOT NULL DEFAULT '1单元',
  floor_no INTEGER NOT NULL, room_no TEXT NOT NULL ,
  direction TEXT DEFAULT NULL,
  area REAL NOT NULL, unit_price REAL NOT NULL, total_price REAL NOT NULL,
  sale_status TEXT NOT NULL DEFAULT 'AVAILABLE' ,
  view_level TEXT DEFAULT NULL ,
  noise_level INTEGER DEFAULT NULL ,
  lock_expire_at TEXT DEFAULT NULL,
  sold_at TEXT DEFAULT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0,
  UNIQUE (building_id, unit_code, floor_no, room_no, deleted),
  CONSTRAINT fk_house_building FOREIGN KEY (building_id) REFERENCES hf_building (id),
  CONSTRAINT fk_house_ht FOREIGN KEY (house_type_id) REFERENCES hf_house_type (id)
);

CREATE TABLE hf_favorite (
id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL, target_type TEXT NOT NULL , target_id INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (user_id, target_type, target_id, deleted),
  CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE TABLE hf_house_lock (
id INTEGER PRIMARY KEY,
  house_id INTEGER NOT NULL, user_id INTEGER NOT NULL,
  intention_no TEXT DEFAULT NULL ,
  status TEXT NOT NULL DEFAULT 'ACTIVE' ,
  active_flag INTEGER GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED
    ,
  expire_at TEXT NOT NULL, renew_count INTEGER NOT NULL DEFAULT 0,
  create_snapshot TEXT DEFAULT NULL ,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0,
  UNIQUE (house_id, active_flag) ,
  UNIQUE (intention_no),
  CONSTRAINT fk_lock_house FOREIGN KEY (house_id) REFERENCES hf_house (id)
);

CREATE TABLE eval_dimension (
id INTEGER PRIMARY KEY,
  code TEXT NOT NULL ,
  name TEXT NOT NULL, weight_default REAL NOT NULL, order_idx INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (code)
);

CREATE TABLE eval_rule_set (
id INTEGER PRIMARY KEY,
  name TEXT NOT NULL, version TEXT NOT NULL ,
  status TEXT NOT NULL DEFAULT 'DRAFT' ,
  source TEXT NOT NULL DEFAULT 'MANUAL' ,
  confirmed INTEGER NOT NULL DEFAULT 0 ,
  active INTEGER NOT NULL DEFAULT 0 ,
  template_code TEXT NOT NULL DEFAULT 'GENERAL' ,
  level_threshold_json TEXT DEFAULT NULL ,
  note TEXT DEFAULT NULL, published_at TEXT DEFAULT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (name, version, deleted)
);

CREATE TABLE eval_rule_set_dim (
id INTEGER PRIMARY KEY,
  set_id INTEGER NOT NULL, dimension_id INTEGER NOT NULL, weight REAL NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (set_id, dimension_id, deleted),
  CONSTRAINT fk_rsd_set FOREIGN KEY (set_id) REFERENCES eval_rule_set (id),
  CONSTRAINT fk_rsd_dim FOREIGN KEY (dimension_id) REFERENCES eval_dimension (id)
);

CREATE TABLE eval_rule (
id INTEGER PRIMARY KEY,
  set_id INTEGER NOT NULL, dimension_id INTEGER NOT NULL,
  metric_code TEXT NOT NULL ,
  metric_name TEXT NOT NULL,
  source_fields TEXT DEFAULT NULL ,
  operator TEXT NOT NULL ,
  unit TEXT DEFAULT NULL,
  internal_weight REAL NOT NULL ,
  higher_is_better INTEGER NOT NULL DEFAULT 1,
  tier_json TEXT NOT NULL ,
  basis TEXT DEFAULT NULL ,
  suggestion TEXT DEFAULT NULL ,
  order_idx INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (set_id, metric_code, deleted),
  CONSTRAINT fk_rule_set FOREIGN KEY (set_id) REFERENCES eval_rule_set (id),
  CONSTRAINT fk_rule_dim FOREIGN KEY (dimension_id) REFERENCES eval_dimension (id)
);

CREATE TABLE evaluation (
id INTEGER PRIMARY KEY,
  user_id INTEGER DEFAULT NULL ,
  house_type_id INTEGER NOT NULL, house_id INTEGER DEFAULT NULL,
  set_id INTEGER NOT NULL, set_version TEXT NOT NULL ,
  template_code TEXT NOT NULL DEFAULT 'GENERAL',
  total_score REAL NOT NULL, level TEXT NOT NULL ,
  missing_count INTEGER NOT NULL DEFAULT 0 ,
  detail_json TEXT NOT NULL ,
  ai_note TEXT DEFAULT NULL ,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT fk_eval_ht FOREIGN KEY (house_type_id) REFERENCES hf_house_type (id)
);

CREATE TABLE eval_sample_stat (
id INTEGER PRIMARY KEY,
  metric_code TEXT NOT NULL, sorted_values TEXT NOT NULL ,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (metric_code)
);

CREATE TABLE compare_report (
id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL, title TEXT NOT NULL,
  house_type_ids TEXT NOT NULL ,
  set_version TEXT NOT NULL, template_code TEXT NOT NULL DEFAULT 'GENERAL',
  matrix_json TEXT NOT NULL ,
  conclusion TEXT DEFAULT NULL ,
  ai_generated INTEGER NOT NULL DEFAULT 0,
  hallucination_dropped INTEGER NOT NULL DEFAULT 0,
  share_token TEXT DEFAULT NULL, expire_at TEXT DEFAULT NULL, view_count INTEGER NOT NULL DEFAULT 0,
  consultant_note TEXT DEFAULT NULL, shown INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (share_token),
  CONSTRAINT fk_rep_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE TABLE compare_report_item (
id INTEGER PRIMARY KEY,
  report_id INTEGER NOT NULL, house_type_id INTEGER NOT NULL, evaluation_id INTEGER DEFAULT NULL,
  total_score REAL NOT NULL, dims_json TEXT NOT NULL, order_idx INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT fk_item_report FOREIGN KEY (report_id) REFERENCES compare_report (id)
);

CREATE TABLE hf_slot_config (
id INTEGER PRIMARY KEY,
  project_id INTEGER NOT NULL, visit_date DATE NOT NULL, slot TEXT NOT NULL ,
  capacity INTEGER NOT NULL DEFAULT 6, enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0, UNIQUE (project_id, visit_date, slot, deleted)
);

CREATE TABLE appointment (
id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL, consultant_id INTEGER DEFAULT NULL,
  project_id INTEGER NOT NULL, house_type_id INTEGER DEFAULT NULL, house_id INTEGER DEFAULT NULL,
  intention_no TEXT DEFAULT NULL,
  visit_date DATE NOT NULL, visit_slot TEXT NOT NULL, party_size INTEGER NOT NULL DEFAULT 2,
  contact_phone TEXT NOT NULL ,
  remark TEXT DEFAULT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING' ,
  cancel_reason TEXT DEFAULT NULL, audit_remark TEXT DEFAULT NULL, audit_by INTEGER DEFAULT NULL,
  notified_at TEXT DEFAULT NULL ,
  reminded_2h_at TEXT DEFAULT NULL ,
  focus_tags TEXT DEFAULT NULL ,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT fk_ap_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_ap_project FOREIGN KEY (project_id) REFERENCES hf_project (id)
);

CREATE TABLE sys_message (
id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL, type TEXT NOT NULL ,
  title TEXT NOT NULL, content TEXT NOT NULL, read_flag INTEGER NOT NULL DEFAULT 0,
  biz_id TEXT DEFAULT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE sys_audit_log (
id INTEGER PRIMARY KEY,
  user_id INTEGER DEFAULT NULL, action TEXT NOT NULL ,
  target_type TEXT DEFAULT NULL, target_id TEXT DEFAULT NULL,
  ip TEXT DEFAULT NULL, ua TEXT DEFAULT NULL, detail_json TEXT DEFAULT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE ai_call_log (
id INTEGER PRIMARY KEY,
  user_id INTEGER DEFAULT NULL, prompt_key TEXT NOT NULL, prompt_version TEXT NOT NULL,
  model TEXT DEFAULT NULL, latency_ms INTEGER DEFAULT NULL, tokens INTEGER DEFAULT NULL,
  ok INTEGER NOT NULL DEFAULT 1, fallback INTEGER NOT NULL DEFAULT 0,
  hallucination_dropped INTEGER NOT NULL DEFAULT 0, payload_digest TEXT DEFAULT NULL ,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE ai_chat_message (
id INTEGER PRIMARY KEY,
  user_id INTEGER NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE VIEW v_house_sale_stat AS
SELECT b.project_id, COUNT(*) AS total,
       SUM(CASE WHEN h.sale_status='AVAILABLE' THEN 1 ELSE 0 END) AS available,
       SUM(CASE WHEN h.sale_status='LOCKED' THEN 1 ELSE 0 END)    AS locked,
       SUM(CASE WHEN h.sale_status='RESERVED' THEN 1 ELSE 0 END)  AS reserved,
       SUM(CASE WHEN h.sale_status='SOLD' THEN 1 ELSE 0 END)      AS sold
FROM hf_house h JOIN hf_building b ON b.id = h.building_id
WHERE h.deleted = 0 GROUP BY b.project_id;

CREATE VIEW v_ht_latest_score AS
SELECT e.* FROM evaluation e
JOIN (SELECT house_type_id, template_code, MAX(id) AS mid FROM evaluation WHERE deleted=0 GROUP BY house_type_id, template_code) t
  ON t.mid = e.id;

CREATE VIEW v_appointment_stat AS
SELECT project_id,
       COUNT(*) AS submitted,
       SUM(CASE WHEN status IN ('CONFIRMED','ARRIVED','COMPLETED') THEN 1 ELSE 0 END) AS confirmed,
       SUM(CASE WHEN status IN ('ARRIVED','COMPLETED') THEN 1 ELSE 0 END) AS arrived,
       SUM(CASE WHEN status='CANCELED' THEN 1 ELSE 0 END) AS canceled
FROM appointment WHERE deleted=0 GROUP BY project_id;
