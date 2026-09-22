package com.zhq.haofangzi.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 按楼栋层数生成销控房源。分布与原来的 MySQL 过程一致：约六成可售。 */
@Component
public class SqliteHouseSeed {

    public void fillIfEmpty(Connection c) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM hf_house")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        Map<Integer, double[]> buildings = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT b.id, b.total_floor, b.units_per_floor, p.avg_price
                     FROM hf_building b JOIN hf_project p ON p.id = b.project_id
                     """)) {
            while (rs.next()) {
                buildings.put(rs.getInt(1), new double[]{rs.getInt(2), rs.getInt(3), rs.getDouble(4)});
            }
        }
        Map<Integer, Double> gfa = new HashMap<>();
        List<Integer> typeIds = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, gfa FROM hf_house_type WHERE deleted = 0 ORDER BY id")) {
            while (rs.next()) {
                gfa.put(rs.getInt(1), rs.getDouble(2));
                typeIds.add(rs.getInt(1));
            }
        }
        if (typeIds.isEmpty()) {
            return;
        }
        int typeCount = typeIds.size();
        String insert = """
                INSERT INTO hf_house (building_id, house_type_id, unit_code, floor_no, room_no, direction,
                                      area, unit_price, total_price, sale_status)
                VALUES (?,?,?,?,?,'S',?,?,?,?)
                """;
        try (PreparedStatement ps = c.prepareStatement(insert)) {
            for (var e : buildings.entrySet()) {
                int b = e.getKey();
                int floors = (int) e.getValue()[0];
                int units = (int) e.getValue()[1];
                double avg = e.getValue()[2];
                for (int f = 1; f <= floors; f++) {
                    for (int r = 1; r <= units; r++) {
                        int ht = typeIds.get((b + r) % typeCount);
                        double area = gfa.getOrDefault(ht, 90d);
                        double total = avg * area * (1 + (f % 8) * 0.006 - 0.02);
                        int bucket = (b * 100 + f * 7 + r) % 100;
                        String status = bucket < 60 ? "AVAILABLE" : bucket < 68 ? "LOCKED" : bucket < 75 ? "RESERVED" : "SOLD";
                        ps.setInt(1, b);
                        ps.setInt(2, ht);
                        ps.setString(3, ((r - 1) % 2 + 1) + "单元");
                        ps.setInt(4, f);
                        ps.setString(5, String.format("%02d", r));
                        ps.setDouble(6, area);
                        ps.setDouble(7, Math.round(total / area * 100) / 100d);
                        ps.setDouble(8, Math.round(total * 100) / 100d);
                        ps.setString(9, status);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO hf_house (building_id, house_type_id, unit_code, floor_no, room_no, area, unit_price, total_price, sale_status)
                    SELECT 1, 2, '1单元', 12, '99', 98.00, 9600.00, 940800.00, 'AVAILABLE'
                    WHERE NOT EXISTS (SELECT 1 FROM hf_house WHERE floor_no = 12 AND room_no = '99')
                    """);
        }
    }
}
