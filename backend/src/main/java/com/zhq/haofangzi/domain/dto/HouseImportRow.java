package com.zhq.haofangzi.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import java.math.BigDecimal;
import lombok.Data;

/** 销控导入行。表头与模板下载一致。 */
@Data
public class HouseImportRow {
    @ExcelProperty("楼栋ID")
    private Long buildingId;
    @ExcelProperty("户型ID")
    private Long houseTypeId;
    @ExcelProperty("楼层")
    private Integer floorNo;
    @ExcelProperty("房号")
    private String roomNo;
    @ExcelProperty("面积")
    private BigDecimal area;
    @ExcelProperty("单价")
    private BigDecimal unitPrice;
}
