package com.tutor.platform.common;

/**
 * 当前登录用户上下文（由 JWT 拦截器写入 ThreadLocal）
 */
public class UserContext {

    private static final ThreadLocal<Long> UID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> ROLE = new ThreadLocal<>();

    public static void set(Long uid, Integer role) {
        UID.set(uid);
        ROLE.set(role);
    }

    public static Long getUid() {
        return UID.get();
    }

    public static Integer getRole() {
        return ROLE.get();
    }

    public static void clear() {
        UID.remove();
        ROLE.remove();
    }

    public static final int ROLE_STUDENT = 1;
    public static final int ROLE_TUTOR = 2;
    public static final int ROLE_ADMIN = 3;
}
