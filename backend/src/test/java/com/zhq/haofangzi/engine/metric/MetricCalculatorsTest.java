package com.zhq.haofangzi.engine.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhq.haofangzi.domain.entity.Building;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.HouseType;
import com.zhq.haofangzi.domain.entity.Project;
import com.zhq.haofangzi.domain.entity.Room;
import com.zhq.haofangzi.engine.MetricCalculator;
import com.zhq.haofangzi.engine.MetricContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetricCalculatorsTest {

    private static final List<MetricCalculator> ALL = List.of(
            new LightWfaCalculator(), new LightOrientationCalculator(), new LightBayDepthCalculator(),
            new LightFloorOcclusionCalculator(), new LightDarkBathCalculator(),
            new VentCrossCalculator(), new VentOpenWindowRatioCalculator(), new VentKitchBathExhaustCalculator(),
            new CircCorridorRatioCalculator(), new CircWetSeparationCalculator(), new CircPublicPrivateCalculator(),
            new CircMealKitchenCalculator(),
            new UtilUsableRatioCalculator(), new UtilIrregularCalculator(), new UtilRoomMinSizeCalculator(),
            new UtilStorageCalculator(),
            new QuietElevatorRatioCalculator(), new QuietRoadCalculator(), new QuietShaftCalculator(),
            new GreenCeilingCalculator(), new GreenBalconyCalculator(), new GreenProjectRateCalculator(),
            new CostTotalFitCalculator(), new CostUnitPriceGapCalculator(), new CostAreaWasteCalculator());

    @Test
    @DisplayName("空上下文不抛异常，全部返回数据不足")
    void emptyContextNeverThrows() {
        MetricContext empty = new MetricContext(null, null, null, null, null, null);
        assertThat(ALL).hasSize(25);
        for (MetricCalculator c : ALL) {
            var r = c.calc(empty);
            assertThat(r.ok()).isFalse();
            assertThat(r.value()).isNull();
        }
    }

    @Test
    void bayDepth() {
        HouseType ht = base();
        ht.setBay(new BigDecimal("12"));
        ht.setDepth(new BigDecimal("8"));
        assertThat(new LightBayDepthCalculator().calc(ctx(ht)).value()).isEqualTo(1.5);
        ht.setDepth(BigDecimal.ZERO);
        assertThat(new LightBayDepthCalculator().calc(ctx(ht)).ok()).isFalse();
    }

    @Test
    void floorOcclusionAndMissingFloor() {
        HouseType ht = base();
        House house = new House();
        house.setFloorNo(2);
        Building b = new Building();
        b.setSouthOcclusion(new BigDecimal("0.95"));
        var ok = new LightFloorOcclusionCalculator().calc(new MetricContext(ht, List.of(), house, b, null, null));
        assertThat(ok.ok()).isTrue();
        assertThat(ok.value()).isGreaterThan(0.9);
        assertThat(new LightFloorOcclusionCalculator().calc(ctx(ht)).ok()).isFalse();
    }

    @Test
    void darkBath() {
        Room bath = room("BATH", 0, 0, 2, 2);
        bath.setWindowArea(BigDecimal.ZERO);
        assertThat(new LightDarkBathCalculator().calc(new MetricContext(base(), List.of(bath), null, null, null, null)).value())
                .isEqualTo(1d);
        assertThat(new LightDarkBathCalculator().calc(ctx(base())).ok()).isFalse();
    }

    @Test
    void openWindowAndExhaust() {
        Room kitchen = room("KITCHEN", 0, 0, 3, 2);
        kitchen.setWindowArea(new BigDecimal("2"));
        kitchen.setWindowOpenableRatio(new BigDecimal("0.5"));
        Room bath = room("BATH", 4, 0, 2, 2);
        bath.setWindowArea(new BigDecimal("1"));
        bath.setWindowOpenableRatio(new BigDecimal("0.5"));
        var ctx = new MetricContext(base(), List.of(kitchen, bath), null, null, null, null);
        assertThat(new VentOpenWindowRatioCalculator().calc(ctx).value()).isEqualTo(0.5);
        assertThat(new VentKitchBathExhaustCalculator().calc(ctx).value()).isEqualTo(1d);
        assertThat(new VentOpenWindowRatioCalculator().calc(ctx(base())).ok()).isFalse();
        assertThat(new VentKitchBathExhaustCalculator().calc(ctx(base())).ok()).isFalse();
    }

    @Test
    void circulation() {
        HouseType ht = base();
        ht.setBathArea(new BigDecimal("6"));
        Room b1 = room("BATH", 0, 0, 2, 2);
        Room b2 = room("BATH", 3, 0, 1.2, 1.5);
        assertThat(new CircWetSeparationCalculator().calc(new MetricContext(ht, List.of(b1, b2), null, null, null, null)).value())
                .isEqualTo(1d);
        assertThat(new CircWetSeparationCalculator().calc(ctx(base())).ok()).isFalse();

        Room master = room("MASTER", 0, 0, 4, 3);
        Room living = room("LIVING", 4, 0, 5, 4);
        var overlap = new CircPublicPrivateCalculator().calc(new MetricContext(ht, List.of(master, living), null, null, null, null));
        assertThat(overlap.ok()).isTrue();
        assertThat(new CircPublicPrivateCalculator().calc(ctx(ht)).ok()).isFalse();

        Room dining = room("DINING", 0, 0, 3, 3);
        Room kitchen = room("KITCHEN", 3, 0, 2, 2);
        var dist = new CircMealKitchenCalculator().calc(new MetricContext(ht, List.of(dining, kitchen), null, null, null, null));
        assertThat(dist.value()).isLessThan(3d);
        assertThat(new CircMealKitchenCalculator().calc(ctx(ht)).ok()).isFalse();
    }

    @Test
    void utility() {
        HouseType ht = base();
        ht.setIrrRatio(new BigDecimal("0.04"));
        ht.setStorageWallLen(new BigDecimal("8"));
        Room living = room("LIVING", 0, 0, 4, 5);
        var ctx = new MetricContext(ht, List.of(living), null, null, null, null);
        assertThat(new UtilIrregularCalculator().calc(ctx).value()).isEqualTo(0.04);
        assertThat(new UtilRoomMinSizeCalculator().calc(ctx).value()).isEqualTo(4d);
        assertThat(new UtilStorageCalculator().calc(ctx).value()).isGreaterThan(0);
        HouseType blank = new HouseType();
        assertThat(new UtilIrregularCalculator().calc(ctx(blank)).ok()).isFalse();
        assertThat(new UtilRoomMinSizeCalculator().calc(ctx(blank)).ok()).isFalse();
        assertThat(new UtilStorageCalculator().calc(ctx(blank)).ok()).isFalse();
    }

    @Test
    void quietAndGreen() {
        HouseType ht = base();
        ht.setNearRoad(1);
        ht.setAdjacentElevator(0);
        ht.setBalconyCount(2);
        Building b = new Building();
        b.setGreenRate(new BigDecimal("35"));
        b.setElevatorCount(2);
        b.setUnitsPerFloor(4);
        var ctx = new MetricContext(ht, List.of(), null, b, null, null);
        assertThat(new QuietRoadCalculator().calc(ctx).value()).isEqualTo(1d);
        assertThat(new QuietShaftCalculator().calc(ctx).value()).isEqualTo(0d);
        assertThat(new GreenBalconyCalculator().calc(ctx).value()).isEqualTo(2d);
        assertThat(new GreenProjectRateCalculator().calc(ctx).value()).isEqualTo(0.35);
        assertThat(new QuietRoadCalculator().calc(ctx(new HouseType())).ok()).isFalse();
        assertThat(new QuietShaftCalculator().calc(ctx(new HouseType())).ok()).isFalse();
        assertThat(new GreenBalconyCalculator().calc(new MetricContext(null, null, null, null, null, null)).ok()).isFalse();
        assertThat(new GreenProjectRateCalculator().calc(ctx(ht)).ok()).isFalse();
    }

    @Test
    void costGapAndWaste() {
        HouseType ht = base();
        ht.setPriceRef(new BigDecimal("900000"));
        ht.setCorridorRatio(new BigDecimal("0.04"));
        ht.setIrrRatio(new BigDecimal("0.02"));
        Project p = new Project();
        p.setAvgPrice(new BigDecimal("10000"));
        var ctx = new MetricContext(ht, List.of(), null, null, p, null);
        assertThat(new CostUnitPriceGapCalculator().calc(ctx).ok()).isTrue();
        assertThat(new CostAreaWasteCalculator().calc(ctx).value()).isEqualTo(0.06);
        assertThat(new CostUnitPriceGapCalculator().calc(ctx(ht)).ok()).isFalse();
        assertThat(new CostAreaWasteCalculator().calc(ctx(new HouseType())).ok()).isFalse();
    }

    private static MetricContext ctx(HouseType ht) {
        return new MetricContext(ht, List.of(), null, null, null, null);
    }

    private static HouseType base() {
        HouseType ht = new HouseType();
        ht.setGfa(new BigDecimal("100"));
        ht.setPrivateArea(new BigDecimal("80"));
        ht.setOrientation("S");
        return ht;
    }

    private static Room room(String category, double x, double y, double w, double h) {
        Room r = new Room();
        r.setCategory(category);
        r.setX(BigDecimal.valueOf(x));
        r.setY(BigDecimal.valueOf(y));
        r.setW(BigDecimal.valueOf(w));
        r.setH(BigDecimal.valueOf(h));
        r.setArea(BigDecimal.valueOf(w * h));
        r.setWindowArea(new BigDecimal("1"));
        return r;
    }
}
