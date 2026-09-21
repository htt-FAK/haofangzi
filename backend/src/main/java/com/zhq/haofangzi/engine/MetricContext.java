package com.zhq.haofangzi.engine;

import com.zhq.haofangzi.domain.entity.Building;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.HouseType;
import com.zhq.haofangzi.domain.entity.Project;
import com.zhq.haofangzi.domain.entity.Room;
import java.util.List;

/**
 * 指标上下文（不可变，一次评估装载一次；spec 004 plan §1 的"避免 21 次查库"）。
 *
 * <p>除 {@code house}/{@code profile} 外均必需；{@link #profile} 为空时 COST 类指标返回
 * {@code insufficient}（用户未填画像不影响其它维度评分，AC-31）。
 *
 * @param ht      户型主参数（评估主体）
 * @param rooms   房间构件（geometry 契约同源）
 * @param house   具体房源（楼层/朝向/单价修正，可为空 → 按"户型级"评估）
 * @param building 楼栋（梯户比、南向遮挡）
 * @param project 楼盘（均价基准，COST_unit_price_gap）
 * @param profile 画像（预算区间、必需居室、家庭结构）
 */
public record MetricContext(HouseType ht, List<Room> rooms, House house, Building building,
                            Project project, Profile profile) {

    /** 购房画像最小视图（001 FR-04） */
    public record Profile(Long budgetMin, Long budgetMax, String familyStructure, Integer mustRooms) {
        public boolean hasBudget() {
            return budgetMin != null && budgetMax != null && budgetMax >= budgetMin;
        }
    }

    /** 起居类房间（客厅/餐厅/主卧/次卧/书房），采光与动线的统计口径基础 */
    public List<Room> livingRooms() {
        return rooms == null ? List.of() : rooms.stream().filter(Room::livingSpace).toList();
    }

    public List<Room> roomsOf(String category) {
        return rooms == null ? List.of() : rooms.stream().filter(r -> category.equals(r.getCategory())).toList();
    }

    /** 有窗的南向 / 北向房间（VENT_cross 判定输入） */
    public List<Room> southWindowRooms() {
        return rooms == null ? List.of()
                : rooms.stream().filter(Room::hasWindow)
                       .filter(r -> "S".equals(r.getOrientation()) || "NS".equals(r.getOrientation())).toList();
    }

    public List<Room> northWindowRooms() {
        return rooms == null ? List.of()
                : rooms.stream().filter(Room::hasWindow)
                       .filter(r -> "N".equals(r.getOrientation()) || "NS".equals(r.getOrientation())).toList();
    }
}
