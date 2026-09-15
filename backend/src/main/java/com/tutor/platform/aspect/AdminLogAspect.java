package com.tutor.platform.aspect;

import com.tutor.platform.common.UserContext;
import com.tutor.platform.entity.AdminLogEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.AdminLogMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.security.AdminLog;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 管理员操作日志切面：业务执行成功后自动落库，日志记录失败不影响主流程。
 * SpEL 从方法参数解析 targetId / detail（依赖 -parameters 编译参数）。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AdminLogAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final AdminLogMapper adminLogMapper;
    private final UserMapper userMapper;

    @Around("@annotation(adminLog)")
    public Object record(ProceedingJoinPoint pjp, AdminLog adminLog) throws Throwable {
        Object result = pjp.proceed();
        try {
            save(pjp, adminLog);
        } catch (Exception e) {
            // 日志写入失败不阻断业务
        }
        return result;
    }

    private void save(ProceedingJoinPoint pjp, AdminLog adminLog) {
        Long uid = UserContext.getUid();
        if (uid == null) {
            return;
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Object[] args = pjp.getArgs();
        UserEntity admin = userMapper.selectById(uid);

        AdminLogEntity log = new AdminLogEntity();
        log.setAdminId(uid);
        log.setAdminName(admin == null ? null : admin.getUsername());
        log.setAction(adminLog.action());
        log.setTargetType(blankToNull(adminLog.targetType()));
        Object tid = eval(sig, args, adminLog.target());
        log.setTargetId(tid instanceof Number n ? n.longValue() : null);
        Object detail = eval(sig, args, adminLog.detail());
        log.setDetail(detail == null ? null : String.valueOf(detail));
        log.setCreateTime(LocalDateTime.now());
        adminLogMapper.insert(log);
    }

    private Object eval(MethodSignature sig, Object[] args, String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            String[] names = sig.getParameterNames();
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    ctx.setVariable(names[i], args[i]);
                }
            }
            return PARSER.parseExpression(expr).getValue(ctx);
        } catch (Exception e) {
            return null;
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
