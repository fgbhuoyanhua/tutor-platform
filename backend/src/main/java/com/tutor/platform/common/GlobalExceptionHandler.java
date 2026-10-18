package com.tutor.platform.common;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：业务异常转友好提示，参数/类型/格式/重复键/JWT 等异常统一转为可读提示，不再暴露 500。
 *
 * <p>HTTP 状态码与业务 code 保持一致（400/401/403/404/500），便于前端拦截器、网关、监控和日志
 * 按标准语义识别错误；响应体仍保持 {@code {code, message, data}} 结构不变，前端兼容。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 把业务 code 同步到 HTTP 状态码，非法/非常用 code 归一到 500 */
    private static void applyStatus(HttpServletResponse response, int code) {
        if (code >= 400 && code <= 599) {
            response.setStatus(code);
        } else {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e, HttpServletResponse response) {
        applyStatus(response, e.getCode());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e, HttpServletResponse response) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数错误" : fe.getDefaultMessage();
        applyStatus(response, 400);
        return ApiResponse.fail(400, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException e, HttpServletResponse response) {
        applyStatus(response, 400);
        return ApiResponse.fail(400, e.getMessage() == null ? "参数不合法" : e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e,
                                                HttpServletResponse response) {
        applyStatus(response, 400);
        return ApiResponse.fail(400, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                HttpServletResponse response) {
        applyStatus(response, 400);
        return ApiResponse.fail(400, "参数类型不正确：" + e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e,
                                               HttpServletResponse response) {
        applyStatus(response, 400);
        return ApiResponse.fail(400, "请求体格式错误，请检查 JSON 或日期格式");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Void> handleDuplicateKey(DuplicateKeyException e, HttpServletResponse response) {
        log.warn("唯一键冲突：{}", e.getMessage());
        applyStatus(response, 400);
        return ApiResponse.fail(400, "数据重复，请勿重复提交");
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ApiResponse<Void> handleJwtExpired(ExpiredJwtException e, HttpServletResponse response) {
        applyStatus(response, 401);
        return ApiResponse.fail(401, "登录已过期，请重新登录");
    }

    @ExceptionHandler(JwtException.class)
    public ApiResponse<Void> handleJwt(JwtException e, HttpServletResponse response) {
        applyStatus(response, 401);
        return ApiResponse.fail(401, "登录状态无效，请重新登录");
    }

    /** 静态资源/未知路径 404，不打 error 日志 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ApiResponse<Void> handleNotFound(
            org.springframework.web.servlet.resource.NoResourceFoundException e,
            HttpServletResponse response) {
        log.debug("接口不存在：{}", e.getMessage());
        applyStatus(response, 404);
        return ApiResponse.fail(404, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e, HttpServletResponse response) {
        log.error("系统异常", e);
        applyStatus(response, 500);
        return ApiResponse.fail(500, "系统繁忙，请稍后再试");
    }
}
