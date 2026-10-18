package com.tutor.platform.common;

/**
 * 分页参数收敛：统一处理页码/页大小的合法范围，防止超大批量查询拖垮数据库。
 *
 * <p>所有分页接口（家教列表、订单列表、评价列表、管理端列表）在 Service 入口调用
 * {@link #normalizePage(long)} / {@link #normalizeSize(long)}，避免逐个 Controller 写校验。
 */
public final class PageParam {

    /**
     * 单页最大条数上限。
     * 取 200：移动端课程表（student/calendar、tutor/calendar）一次拉全量订单用于本地按周分组，
     * 请求 size=200。上限低于 200 会让课程表静默丢数据。
     */
    public static final int MAX_PAGE_SIZE = 200;

    /** 默认页大小 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private PageParam() {
    }

    /** 页码至少为 1 */
    public static long normalizePage(long page) {
        return page < 1 ? 1 : page;
    }

    /**
     * 页大小收敛到 [1, MAX_PAGE_SIZE]。
     * 小于 1 用默认值，超过上限直接截断到上限（而非报错，保持前端兼容）。
     */
    public static long normalizeSize(long size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
