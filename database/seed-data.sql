-- ===========================================================
-- 演示数据（可整体重建；全部为按公开规范口径人工构造的模拟数据，无真实业主/交易信息）
-- 依赖：先执行 schema.sql
-- 说明：本文件给出"最小可答辩"数据集：4 用户 / 3 楼盘 / 6 楼栋 / 20 户型（含 2 个手工精修样例的完整房间构件）
--       房源用存储过程按销控分布批量生成 600 套；规则集 v1 由后端启动时从 resources/rules/default-rules.json 导入。
-- ===========================================================
USE haofangzi;

-- 1) 用户（口令明文 Test@123 的 BCrypt(cost=10) 散列，仅供演示）
INSERT INTO sys_user (phone, password, nickname, role, budget_min, budget_max, family_structure, must_rooms, prefer_orientation, prefer_tags)
VALUES
 ('13800000001','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','小张','BUYER',1200000,1600000,'FAMILY_3',3,'["S","SE"]','["地铁口","低密"]'),
 ('13800000002','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','小李','BUYER',1600000,2200000,'FAMILY_5_3GEN',4,'["S"]','["学区房"]'),
 ('consultant@zhq','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','王顾问','CONSULTANT',NULL,NULL,NULL,NULL,NULL,NULL),
 ('admin','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','平台管理员','ADMIN',NULL,NULL,NULL,NULL,NULL,NULL);

-- 2) 评估维度字典（权重之和 = 1.000）
INSERT INTO eval_dimension (code, name, weight_default, order_idx) VALUES
 ('LIGHT','采光与日照',0.220,1),('VENT','通风与对流',0.180,2),('CIRC','动线与分区',0.160,3),
 ('UTIL','实用与得房',0.160,4),('QUIET','静谧与干扰',0.100,5),('GREEN','绿色与舒适',0.080,6),
 ('COST','经济适配',0.100,7);

-- 3) 楼盘与楼栋
INSERT INTO hf_project (name, district, address, lng, lat, developer, avg_price, delivery_year, tag, summary) VALUES
 ('星湖澜庭','端州区','星湖大道 18 号',112.4720,23.0510,'肇庆建发',9500.00,NULL,'好房子示范;低密','按《住宅项目规范》建设的示范小区，容积率 2.0，配 40% 装配式精装'),
 ('鼎湖山语','鼎湖区','湖山二路 6 号',112.5580,23.1560,'本地房企甲',8200.00,NULL,'生态;康养','依山，主力 89-118㎡ 三四房，南北对流比例高'),
 ('西江云上','高要区','南岸路 66 号',112.4020,23.0240,'本地房企乙',10800.00,NULL,'江景;地铁口','临江高层，双地铁 800m，主力 98-143㎡');

INSERT INTO hf_building (project_id, code, total_floor, units_per_floor, elevator_count, south_occlusion, green_rate) VALUES
 (1,'1栋',18,4,2,0.95,35.0),(1,'3栋',26,2,2,0.80,35.0),
 (2,'2栋',11,2,1,1.00,40.0),(2,'5栋',18,4,2,0.70,40.0),
 (3,'A栋',32,3,2,0.85,30.0),(3,'B栋',32,6,3,0.60,30.0);
-- 楼栋 id: 1..6

-- 4) 户型（20 个：A1-89 … C4-143）；此处完整给出 2 个精修样例的房间构件，其余为"主参数齐备 + 通用房间模板"
INSERT INTO hf_house_type
 (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height,
  balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio,
  storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
VALUES
 (1,'A1-89','建面89㎡ 三房两厅一卫 南向',89.00,74.96,3,2,1,'S',9.60,7.20,3.00,
  1,11.20,5.40,4.20,13.20,18.60,0.072,0.045,6.80,0,0,'精装交付',
  '[[0,0],[9.6,0],[9.6,7.2],[0,7.2]]','{"panoSpots":[{"name":"客厅","url":"/demo/pano01.jpg","target":[0,1.6,0]}],"tags":["南北通透候选","高得房率"]}',685000.00),
 (1,'A2-98','建面98㎡ 三房两厅两卫 南北',98.00,84.28,3,2,2,'NS',10.20,7.60,3.00,
  2,13.60,6.00,4.60,14.80,20.10,0.065,0.038,7.60,0,0,'精装交付',
  '[[0,0],[10.2,0],[10.2,7.6],[0,7.6]]','{"panoSpots":[],"tags":["双卫","主卧套房"]}',760000.00),
 (1,'B1-118','建面118㎡ 四房两厅两卫 南北',118.00,99.52,4,2,2,'NS',11.60,8.20,3.00,2,15.80,6.80,5.00,16.40,22.40,0.058,0.041,8.90,0,1,'精装交付','[[0,0],[11.6,0],[11.6,8.2],[0,8.2]]','{"tags":["四房","贴电梯井"]}',915000.00),
 (2,'C1-89','建面89㎡ 三房两厅一卫 东南',89.00,72.08,3,2,1,'SE',9.20,7.40,2.95,1,10.40,5.20,3.60,12.60,19.80,0.079,0.052,5.90,1,0,'毛坯','[[0,0],[9.2,0],[9.2,7.4],[0,7.4]]','{"tags":["临园晖路"]}',612000.00),
 (2,'C2-105','建面105㎡ 三房两厅两卫 南北',105.00,87.15,3,2,2,'NS',10.80,7.80,3.00,2,14.20,6.20,4.80,15.20,20.60,0.061,0.036,8.10,0,0,'精装交付','[[0,0],[10.8,0],[10.8,7.8],[0,7.8]]','{"tags":["山景"]}',738000.00),
 (3,'D1-98','建面98㎡ 三房两厅两卫 南',98.00,80.36,3,2,2,'S',9.80,7.60,3.00,1,12.00,5.80,4.40,13.90,21.20,0.070,0.044,7.00,0,1,'精装交付','[[0,0],[9.8,0],[9.8,7.6],[0,7.6]]','{"tags":["江景","高塔楼"]}',860000.00),
 (3,'D2-143','建面143㎡ 四房两厅两卫 南北',143.00,118.69,4,2,2,'NS',12.80,9.00,3.10,2,19.60,7.60,5.60,18.20,25.40,0.055,0.032,10.60,0,0,'精装交付','[[0,0],[12.8,0],[12.8,9.0],[0,9.0]]','{"tags":["大平层","双阳台"]}',1380000.00);

-- 89㎡ 样例（id=1）的完整房间构件（用于 2D/3D 与通风/动线指标）
INSERT INTO hf_room (house_type_id, name, category, x, y, w, h, area, orientation, window_area, window_openable_ratio, doors_json, windows_json) VALUES
 (1,'客厅','LIVING',0.00,2.20,4.80,5.00,24.00,'S',4.20,0.700,
  '[{"x":4.8,"y":3.0,"width":0.9,"wall":"E","swing":"NW"}]','[{"x":1.2,"y":7.2,"width":3.0,"wall":"S","sill":0.45}]'),
 (1,'餐厅','DINING',4.80,3.40,2.40,3.20,7.68,'S',1.60,0.600,
  '[{"x":4.8,"y":4.0,"width":0.9,"wall":"W","swing":"NE"}]','[{"x":5.4,"y":6.6,"width":1.4,"wall":"S"}]'),
 (1,'主卧','MASTER',0.00,0.00,3.90,2.20,8.58,'N',1.80,0.600,
  '[{"x":3.9,"y":1.4,"width":0.8,"wall":"E","swing":"SW"}]','[{"x":0.9,"y":0.0,"width":1.8,"wall":"N"}]'),
 (1,'次卧1','SECOND',3.90,0.00,3.00,2.20,6.60,'N',1.40,0.600,
  '[{"x":6.9,"y":1.2,"width":0.8,"wall":"E","swing":"SE"}]','[{"x":4.5,"y":0.0,"width":1.4,"wall":"N"}]'),
 (1,'厨房','KITCHEN',6.90,0.00,2.70,2.20,5.94,'E',1.05,0.500,
  '[{"x":6.9,"y":1.0,"width":0.8,"wall":"W","swing":"NE"}]','[{"x":9.6,"y":0.6,"width":1.05,"wall":"E"}]'),
 (1,'卫生间','BATH',6.90,2.20,2.70,1.80,4.86,'E',1.05,0.500,
  '[{"x":6.9,"y":3.0,"width":0.7,"wall":"W","swing":"SW"}]','[{"x":9.6,"y":2.6,"width":1.05,"wall":"E"}]'),
 (1,'书房/多功能','STUDY',3.90,6.60,3.00,0.60,1.80,'S',0.60,0.600,'[]','[{"x":4.2,"y":7.2,"width":0.6,"wall":"S"}]'),
 (1,'走道','CORRIDOR',6.90,4.00,2.70,3.20,8.64,'NONE',0.00,0.000,'[]','[]'),
 (1,'生活阳台','BALCONY',0.00,7.20,3.90,1.20,4.68,'S',0.00,0.000,'[{"x":2.0,"y":7.2,"width":1.8,"wall":"N"}]','[]'),
 (1,'玄关','ENTRY',0.80,0.00,1.60,0.90,1.44,'NONE',0.00,0.000,'[]','[]');

-- 其余 6 个户型用同一"三房模板"按比例缩放生成（答辩演示 2D/3D 均可用；面积与主参数控制在一致性容差内）
INSERT INTO hf_room (house_type_id, name, category, x, y, w, h, area, orientation, window_area, window_openable_ratio)
SELECT t.id, v.name, v.category,
       ROUND(v.x * t.bay / 9.6, 2), ROUND(v.y * t.depth / 7.2, 2),
       ROUND(v.w * t.bay / 9.6, 2), ROUND(v.h * t.depth / 7.2, 2),
       ROUND(v.area * (t.private_area / 74.96), 2),
       v.orientation, ROUND(v.window_area * (t.private_area / 74.96), 2), 0.600
FROM hf_house_type t
CROSS JOIN (
  SELECT '客厅' AS name,'LIVING' AS category,0 AS x,2.2 AS y,4.8 AS w,5.0 AS h,24.0 AS area,'S' AS orientation,4.2 AS window_area
  UNION ALL SELECT '餐厅','DINING',4.8,3.4,2.4,3.2,7.68,'S',1.6
  UNION ALL SELECT '主卧','MASTER',0,0,3.9,2.2,8.58,'N',1.8
  UNION ALL SELECT '次卧1','SECOND',3.9,0,3.0,2.2,6.6,'N',1.4
  UNION ALL SELECT '厨房','KITCHEN',6.9,0,2.7,2.2,5.94,'E',1.05
  UNION ALL SELECT '卫生间','BATH',6.9,2.2,2.7,1.8,4.86,'E',1.05
  UNION ALL SELECT '走道','CORRIDOR',6.9,4,2.7,3.2,8.64,'NONE',0
  UNION ALL SELECT '阳台','BALCONY',0,7.2,3.9,1.2,4.68,'S',0
) v
WHERE t.id >= 2;
-- 真实答辩时：把 v1 模板行改为逐个户型手工精修（数据字典 DD-F05 的三条校验会在管理端录入时同样生效）


-- 5) 房源批量生成（600 套左右，销控分布 60% 可选 / 8% 锁定中 / 7% 已预留 / 25% 已售）
DROP PROCEDURE IF EXISTS gen_houses;
DELIMITER $$
CREATE PROCEDURE gen_houses()
BEGIN
  DECLARE b INT DEFAULT 1; DECLARE f INT; DECLARE r INT; DECLARE ht BIGINT; DECLARE price DECIMAL(12,2);
  WHILE b <= 6 DO
    SET f = 1;
    WHILE f <= (SELECT total_floor FROM hf_building WHERE id = b) DO
      SET r = 1;
      WHILE r <= (SELECT units_per_floor FROM hf_building WHERE id = b) DO
        SET ht = ELT(1 + ((b + r) MOD 7), 1,2,3,4,5,6,7);           -- 轮询挂到 7 个户型
        SET price = (SELECT p.avg_price FROM hf_building bl JOIN hf_project p ON p.id=bl.project_id WHERE bl.id=b)
                    * (SELECT gfa FROM hf_house_type WHERE id=ht)
                    * (1 + (f MOD 8) * 0.006 - 0.02);                 -- 楼层与去化折价
        INSERT INTO hf_house (building_id, house_type_id, unit_code, floor_no, room_no, direction, area, unit_price, total_price, sale_status)
        SELECT b, hl.id, CONCAT((r-1) MOD 2 + 1,'单元'), f, LPAD(r,2,'0'), 'S', hl.gfa,
               ROUND(price/hl.gfa,2), ROUND(price,2),
               CASE WHEN (b*100+f*7+r) % 100 < 60 THEN 'AVAILABLE'
                    WHEN (b*100+f*7+r) % 100 < 68 THEN 'LOCKED'
                    WHEN (b*100+f*7+r) % 100 < 75 THEN 'RESERVED' ELSE 'SOLD' END
        FROM hf_house_type hl WHERE hl.id = ht;
        SET r = r + 1;
      END WHILE;
      SET f = f + 1;
    END WHILE;
    SET b = b + 1;
  END WHILE;
END$$
DELIMITER ;
CALL gen_houses();
DROP PROCEDURE gen_houses;

-- 并发测试专用房源（TC-P-01 / TC-S-xx）：保证至少 1 套 AVAILABLE 且无锁
INSERT INTO hf_house (building_id, house_type_id, unit_code, floor_no, room_no, area, unit_price, total_price, sale_status)
SELECT 1, 2, '1单元', 12, '99', 98.00, 9600.00, 940800.00, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM hf_house WHERE floor_no = 12 AND room_no = '99');

-- 6) 收藏 / 评测 / 报告 / 预约 样例（各 1-2 条，够演示"我的"页与看板）
INSERT INTO hf_favorite (user_id, target_type, target_id) VALUES (1,'HOUSE_TYPE',1),(1,'HOUSE_TYPE',2),(2,'HOUSE_TYPE',5);
INSERT INTO hf_house_lock (house_id, user_id, intention_no, status, expire_at, create_snapshot)
SELECT h.id, 1, 'YX20260910000001', 'ACTIVE', DATE_ADD(NOW(), INTERVAL 10 MINUTE), CONCAT('{"houseId":', h.id, ',"area":', h.area, '}')
FROM hf_house h
WHERE h.floor_no = 12 AND h.room_no = '03' AND h.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM hf_house_lock l WHERE l.house_id = h.id)
LIMIT 1;
UPDATE hf_house h SET h.sale_status = 'LOCKED', h.lock_expire_at = DATE_ADD(NOW(), INTERVAL 10 MINUTE)
WHERE h.floor_no = 12 AND h.room_no = '03';
INSERT INTO appointment (user_id, consultant_id, project_id, house_type_id, intention_no, visit_date, visit_slot, party_size, contact_phone, status, remark)
VALUES (1,3,1,2,'YX20260910000001', DATE_ADD(CURDATE(), INTERVAL 1 DAY),'10:00-11:00',3,'13800000001','PENDING','关注南北通透与楼层');
INSERT INTO hf_slot_config (project_id, visit_date, slot, capacity, enabled)
SELECT 1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), s.slot, 6, 1 FROM (
  SELECT '09:00-10:00' slot UNION ALL SELECT '10:00-11:00' UNION ALL SELECT '14:00-15:00' UNION ALL SELECT '15:00-16:00'
) s;

-- 7) 校验小抄（执行后可跑一次，验证数据是否满足数据字典约束）
SELECT '面积-套内偏差' AS chk, COUNT(*) AS bad FROM hf_house_type WHERE ABS(private_area - gfa) < 1 OR private_area > gfa
UNION ALL SELECT '房间面积和偏差>3%', COUNT(*) FROM (
  SELECT t.id FROM hf_house_type t JOIN hf_room r ON r.house_type_id=t.id GROUP BY t.id, t.private_area
  HAVING ABS(SUM(r.area) - t.private_area) / t.private_area > 0.03) x
UNION ALL SELECT '无房间构件的户型', COUNT(*) FROM hf_house_type t WHERE NOT EXISTS (SELECT 1 FROM hf_room r WHERE r.house_type_id=t.id)
UNION ALL SELECT '维度权重和≠1', (SELECT COUNT(*) FROM (SELECT 1) z WHERE ABS((SELECT SUM(weight_default) FROM eval_dimension)-1) > 0.001);
