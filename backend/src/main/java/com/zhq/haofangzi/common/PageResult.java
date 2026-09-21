package com.zhq.haofangzi.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 统一分页结果（NFR-06：所有列表接口强制分页，size 默认 20 / 上限 100） */
@Data
@AllArgsConstructor
public class PageResult<T> {
    private List<T> records;
    private long total;
    private int page;
    private int size;

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }
}
