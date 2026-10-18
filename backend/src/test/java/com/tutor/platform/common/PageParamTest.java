package com.tutor.platform.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页参数收敛单元测试：防止 size 传超大值拖垮数据库。
 */
class PageParamTest {

    @Test
    @DisplayName("页码：小于1纠正为1，正常值原样返回")
    void normalizePage() {
        assertEquals(1, PageParam.normalizePage(0));
        assertEquals(1, PageParam.normalizePage(-5));
        assertEquals(1, PageParam.normalizePage(1));
        assertEquals(7, PageParam.normalizePage(7));
    }

    @Test
    @DisplayName("页大小：小于1用默认值，超上限截断到100")
    void normalizeSize() {
        assertEquals(PageParam.DEFAULT_PAGE_SIZE, PageParam.normalizeSize(0));
        assertEquals(PageParam.DEFAULT_PAGE_SIZE, PageParam.normalizeSize(-10));
        assertEquals(1, PageParam.normalizeSize(1));
        assertEquals(50, PageParam.normalizeSize(50));
        assertEquals(PageParam.MAX_PAGE_SIZE, PageParam.normalizeSize(100));
    }

    @Test
    @DisplayName("页大小：恶意超大值被截断（防止一次查全表）")
    void normalizeSizePreventsDos() {
        assertEquals(100, PageParam.normalizeSize(999999));
        assertEquals(100, PageParam.normalizeSize(Long.MAX_VALUE));
    }
}
