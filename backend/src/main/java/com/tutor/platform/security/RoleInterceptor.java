package com.tutor.platform.security;

import com.tutor.platform.common.BizException;
import com.tutor.platform.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色校验拦截器：方法标注 @RequireRole 时校验当前用户角色
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole require = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequireRole.class);
        if (require == null) {
            return true;
        }
        Integer role = UserContext.getRole();
        for (int r : require.value()) {
            if (r == role) {
                return true;
            }
        }
        throw new BizException(403, "无权限访问");
    }
}
