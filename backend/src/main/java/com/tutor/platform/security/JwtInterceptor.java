package com.tutor.platform.security;

import com.tutor.platform.common.BizException;
import com.tutor.platform.common.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 拦截器：统一解析 Authorization 头，写入用户上下文；放行登录/注册/验证码等公开接口
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 公开查询接口：家教列表/详情、老师历史评价，无需登录
        // 注意：/api/tutors/income、/api/tutors/my-students 是老师私有接口，不能放行
        String uri = request.getRequestURI();
        boolean publicGet = HttpMethod.GET.matches(request.getMethod())
                && !uri.startsWith("/api/tutors/income")
                && !uri.startsWith("/api/tutors/my-students")
                && (uri.equals("/api/tutors") || uri.startsWith("/api/tutors/")
                || uri.equals("/api/evaluations/tutor/") || uri.startsWith("/api/evaluations/tutor/"));
        if (publicGet) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BizException(401, "未登录或登录已过期");
        }
        Claims claims = jwtUtil.parseToken(auth.substring(7));
        UserContext.set(claims.get("uid", Long.class), claims.get("role", Integer.class));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
