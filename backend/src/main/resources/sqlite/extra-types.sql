INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 1, 'A3-68', '建面68㎡ 两房一厅一卫 南向', 68.00, 56.44, 2, 1, 1, 'S', 8.40, 6.40, 2.90, 1, 8.20, 4.20, 3.40, 11.20, 14.80, 0.080, 0.050, 4.80, 0, 0, '精装交付', '[[0,0],[8.4,0],[8.4,6.4],[0,6.4]]', '{"tags":["刚需两房"]}', 498000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'A3-68');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 1, 'A4-75', '建面75㎡ 两房两厅一卫 东南', 75.00, 62.25, 2, 2, 1, 'SE', 8.80, 6.80, 2.90, 1, 9.10, 4.60, 3.60, 12.00, 15.60, 0.076, 0.048, 5.20, 0, 0, '精装交付', '[[0,0],[8.8,0],[8.8,6.8],[0,6.8]]', '{"tags":["东南两房"]}', 548000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'A4-75');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 1, 'B2-128', '建面128㎡ 四房两厅两卫 南北', 128.00, 106.24, 4, 2, 2, 'NS', 12.20, 8.60, 3.00, 2, 16.80, 7.20, 5.20, 17.40, 23.60, 0.056, 0.038, 9.40, 0, 1, '精装交付', '[[0,0],[12.2,0],[12.2,8.6],[0,8.6]]', '{"tags":["四房改善"]}', 992000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'B2-128');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 2, 'B3-136', '建面136㎡ 四房两厅两卫 南北', 136.00, 112.88, 4, 2, 2, 'NS', 12.60, 8.80, 3.00, 2, 17.60, 7.40, 5.40, 18.00, 24.40, 0.054, 0.036, 9.80, 0, 0, '精装交付', '[[0,0],[12.6,0],[12.6,8.8],[0,8.8]]', '{"tags":["山景四房"]}', 980000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'B3-136');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 2, 'C3-78', '建面78㎡ 两房一厅一卫 南向', 78.00, 64.74, 2, 1, 1, 'S', 8.90, 6.90, 2.95, 1, 9.40, 4.80, 3.70, 12.40, 16.20, 0.074, 0.049, 5.40, 1, 0, '毛坯', '[[0,0],[8.9,0],[8.9,6.9],[0,6.9]]', '{"tags":["两房毛坯"]}', 520000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'C3-78');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 2, 'C4-112', '建面112㎡ 三房两厅两卫 南北', 112.00, 92.96, 3, 2, 2, 'NS', 11.20, 8.00, 3.00, 2, 15.20, 6.60, 5.00, 15.80, 21.80, 0.060, 0.037, 8.60, 0, 0, '精装交付', '[[0,0],[11.2,0],[11.2,8.0],[0,8.0]]', '{"tags":["三房双卫"]}', 806000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'C4-112');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 3, 'D3-90', '建面90㎡ 三房两厅一卫 南向', 90.00, 74.70, 3, 2, 1, 'S', 9.70, 7.30, 3.00, 1, 11.40, 5.50, 4.30, 13.40, 18.90, 0.070, 0.044, 6.90, 0, 1, '精装交付', '[[0,0],[9.7,0],[9.7,7.3],[0,7.3]]', '{"tags":["江景三房"]}', 780000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'D3-90');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 3, 'D4-168', '建面168㎡ 五房两厅三卫 南北', 168.00, 139.44, 5, 2, 3, 'NS', 14.20, 9.60, 3.10, 2, 22.40, 8.40, 6.20, 19.60, 28.20, 0.050, 0.030, 12.40, 0, 0, '精装交付', '[[0,0],[14.2,0],[14.2,9.6],[0,9.6]]', '{"tags":["五房"]}', 1680000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'D4-168');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 3, 'E1-72', '建面72㎡ 两房一厅一卫 东南', 72.00, 59.76, 2, 1, 1, 'SE', 8.60, 6.60, 2.90, 1, 8.60, 4.40, 3.50, 11.60, 15.20, 0.078, 0.051, 5.00, 0, 0, '精装交付', '[[0,0],[8.6,0],[8.6,6.6],[0,6.6]]', '{"tags":["小两房"]}', 648000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'E1-72');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 1, 'E2-96', '建面96㎡ 三房两厅一卫 南北', 96.00, 79.68, 3, 2, 1, 'NS', 10.00, 7.50, 3.00, 1, 12.80, 5.80, 4.40, 14.20, 19.60, 0.066, 0.040, 7.20, 0, 0, '精装交付', '[[0,0],[10.0,0],[10.0,7.5],[0,7.5]]', '{"tags":["南北三房"]}', 730000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'E2-96');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 2, 'E3-122', '建面122㎡ 四房两厅两卫 南向', 122.00, 101.26, 4, 2, 2, 'S', 11.80, 8.30, 3.00, 2, 16.20, 7.00, 5.10, 16.80, 22.80, 0.057, 0.039, 9.10, 0, 1, '精装交付', '[[0,0],[11.8,0],[11.8,8.3],[0,8.3]]', '{"tags":["南向四房"]}', 890000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'E3-122');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 1, 'E4-158', '建面158㎡ 五房两厅两卫 南北', 158.00, 131.14, 5, 2, 2, 'NS', 13.60, 9.40, 3.10, 2, 21.00, 8.00, 5.80, 18.80, 26.80, 0.052, 0.031, 11.60, 0, 0, '精装交付', '[[0,0],[13.6,0],[13.6,9.4],[0,9.4]]', '{"tags":["五房南北"]}', 1420000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'E4-158');

INSERT INTO hf_house_type (project_id, code, name, gfa, private_area, rooms, halls, baths, orientation, bay, depth, ceiling_height, balcony_count, window_area, kitchen_area, bath_area, master_bedroom_area, circulation_len, corridor_ratio, irr_ratio, storage_wall_len, near_road, adjacent_elevator, style, outline_json, ext_json, price_ref)
SELECT 2, 'E5-84', '建面84㎡ 两房两厅一卫 西南', 84.00, 69.72, 2, 2, 1, 'SW', 9.10, 7.10, 2.95, 1, 10.00, 5.00, 3.80, 12.80, 16.80, 0.072, 0.047, 5.60, 1, 0, '毛坯', '[[0,0],[9.1,0],[9.1,7.1],[0,7.1]]', '{"tags":["西南两房"]}', 588000.00
WHERE NOT EXISTS (SELECT 1 FROM hf_house_type WHERE code = 'E5-84');

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
WHERE t.code <> 'A1-89'
  AND NOT EXISTS (SELECT 1 FROM hf_room r WHERE r.house_type_id = t.id AND r.deleted = 0);

INSERT INTO hf_house (building_id, house_type_id, unit_code, floor_no, room_no, direction, area, unit_price, total_price, sale_status)
SELECT 1, t.id, '补' || t.code, f.n, '01', 'S', t.gfa, 9500, ROUND(t.gfa * 9500, 2), 'AVAILABLE'
FROM hf_house_type t
JOIN (
  SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
) f
WHERE t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM hf_house h WHERE h.house_type_id = t.id AND h.deleted = 0);
