package com.lhs.lawmind.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private List<T> list;
    private int page;
    private int pageSize;

    public PageResult(long total, List<T> list, int page, int pageSize) {
        this.total = total;
        this.list = list;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(long total, List<T> list, int page, int pageSize) {
        return new PageResult<>(total, list, page, pageSize);
    }
}