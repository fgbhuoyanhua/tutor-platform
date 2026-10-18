package com.tutor.platform.common;

/**
 * 敏感信息脱敏工具：手机号、姓名等对外展示字段统一在此处理。
 *
 * <p>使用场景：订单、评价、消息等 VO 中涉及对方用户的字段，避免把完整手机号/全名下发给不相关角色。
 * 管理员后台如需查看完整信息，可不脱敏（由调用方决定）。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /**
     * 手机号脱敏：138****8000，长度不足时原样返回。
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        int keep = 3;
        int tail = 4;
        return phone.substring(0, keep) + "****" + phone.substring(phone.length() - tail);
    }

    /**
     * 姓名脱敏：张三 → 张*，欧阳娜娜 → 欧***，单字不变。
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }
}
