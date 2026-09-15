package com.tutor.platform.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理员操作日志注解：标注在管理员接口方法上，由 {@code AdminLogAspect} 切面自动记录。
 * target/detail 支持 SpEL 表达式，可引用方法参数（如 #id、#pass、#reason）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminLog {

    /** 操作类型描述，如"审核家教" */
    String action();

    /** 操作对象类型：tutor / user */
    String targetType() default "";

    /** 操作对象ID的 SpEL 表达式，如 "#id" */
    String target() default "";

    /** 操作详情的 SpEL 表达式，如 "#pass ? '审核通过' : '审核驳回'" */
    String detail() default "";
}
