package com.tutor.platform.common;

import lombok.Getter;

/**
 * 业务异常：由全局异常处理器统一转为友好提示
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        // 业务校验失败统一按 400（客户端可纠正）返回；真正的服务端故障走 500 兜底
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
