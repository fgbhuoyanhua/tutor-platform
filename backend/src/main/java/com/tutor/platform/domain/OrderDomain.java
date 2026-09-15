package com.tutor.platform.domain;

import com.tutor.platform.common.BizException;

/**
 * 预约订单状态机（核心业务规则，独立于框架，可脱离 Spring 单元测试）
 *
 * 状态流转：
 * 待确认(0) --老师接单--> 已预约(1) --开始授课--> 授课中(2) --确认完成--> 已完成(3)
 * 待确认(0) --学生取消--> 已取消(4)
 * 待确认(0) --老师拒绝--> 已拒绝(5)
 */
public final class OrderDomain {

    public static final int PENDING = 0;
    public static final int CONFIRMED = 1;
    public static final int TEACHING = 2;
    public static final int FINISHED = 3;
    public static final int CANCELED = 4;
    public static final int REJECTED = 5;

    /** 订单双方角色：学生 / 老师 */
    public static final int OP_STUDENT = 1;
    public static final int OP_TUTOR = 2;

    private OrderDomain() {
    }

    /**
     * 校验并返回目标状态；非法流转直接抛业务异常。
     *
     * @param current   当前状态
     * @param operator  操作方（学生/老师）
     * @param action    动作：confirm / reject / cancel / start / finish
     */
    public static int next(int current, int operator, String action) {
        return switch (action) {
            case "confirm" -> {
                if (operator != OP_TUTOR) throw new BizException(403, "仅老师可接单");
                if (current != PENDING) throw new BizException("订单当前状态不可接单");
                yield CONFIRMED;
            }
            case "reject" -> {
                if (operator != OP_TUTOR) throw new BizException(403, "仅老师可拒绝预约");
                if (current != PENDING) throw new BizException("订单当前状态不可拒绝");
                yield REJECTED;
            }
            case "cancel" -> {
                if (operator != OP_STUDENT) throw new BizException(403, "仅学生可取消预约");
                if (current != PENDING) throw new BizException("仅待确认状态可取消");
                yield CANCELED;
            }
            case "start" -> {
                if (operator != OP_TUTOR) throw new BizException(403, "仅老师可开始授课");
                if (current != CONFIRMED) throw new BizException("仅已预约状态可开始授课");
                yield TEACHING;
            }
            case "finish" -> {
                if (operator != OP_STUDENT) throw new BizException(403, "仅学生可确认完成");
                if (current != TEACHING) throw new BizException("仅授课中状态可确认完成");
                yield FINISHED;
            }
            default -> throw new BizException("未知操作：" + action);
        };
    }

    public static String statusText(int status) {
        return switch (status) {
            case PENDING -> "待确认";
            case CONFIRMED -> "已预约";
            case TEACHING -> "授课中";
            case FINISHED -> "已完成";
            case CANCELED -> "已取消";
            case REJECTED -> "已拒绝";
            default -> "未知";
        };
    }
}
