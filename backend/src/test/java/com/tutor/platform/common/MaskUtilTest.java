package com.tutor.platform.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 敏感信息脱敏单元测试。
 */
class MaskUtilTest {

    @Test
    @DisplayName("手机号脱敏：保留前3后4")
    void maskPhone() {
        assertEquals("138****8000", MaskUtil.maskPhone("13800008000"));
        assertEquals("139****1234", MaskUtil.maskPhone("13912341234"));
    }

    @Test
    @DisplayName("手机号脱敏：空值与过短值原样返回，不抛异常")
    void maskPhoneEdgeCases() {
        assertNull(MaskUtil.maskPhone(null));
        assertEquals("", MaskUtil.maskPhone(""));
        assertEquals("12345", MaskUtil.maskPhone("12345"));
    }

    @Test
    @DisplayName("姓名脱敏：保留首字，其余星号")
    void maskName() {
        assertEquals("张*", MaskUtil.maskName("张三"));
        assertEquals("欧***", MaskUtil.maskName("欧阳娜娜"));
        assertEquals("李", MaskUtil.maskName("李"));
    }

    @Test
    @DisplayName("姓名脱敏：空值原样返回")
    void maskNameEdgeCases() {
        assertNull(MaskUtil.maskName(null));
        assertEquals("", MaskUtil.maskName(""));
    }
}
