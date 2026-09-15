package com.tutor.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 生产环境配置自检：启动时校验关键安全配置，防止演示/测试配置泄漏到生产。
 * - prod 下禁止开启短信验证码 mock（app.sms-mock=true 直接拒绝启动）
 * - JWT 密钥使用默认值时给出强告警（必须通过环境变量 JWT_SECRET 覆盖）
 */
@Slf4j
@Component
public class StartupSecurityCheck implements ApplicationRunner {

    /** application.yml 中的默认 JWT 密钥（与配置一致，用于识别"未覆盖"） */
    private static final String DEFAULT_JWT_SECRET =
            "tutor-platform-dev-secret-key-please-change-in-prod-0123456789";

    private final Environment environment;

    @Value("${app.sms-mock:false}")
    private boolean smsMock;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    public StartupSecurityCheck(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            // 非生产环境：仅提示默认密钥风险
            if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
                log.warn("[配置自检] 当前使用默认 JWT 密钥，仅限开发/演示环境；生产环境必须通过 JWT_SECRET 环境变量覆盖");
            }
            log.info("[配置自检] 当前环境 profile={}，sms-mock={}", Arrays.toString(environment.getActiveProfiles()), smsMock);
            return;
        }
        // 生产环境强制校验
        if (smsMock) {
            log.error("[配置自检] 生产环境禁止开启短信验证码 mock（app.sms-mock=true），请设置为 false 后重启");
            throw new IllegalStateException("生产环境禁止开启短信验证码 mock（app.sms-mock）");
        }
        if (DEFAULT_JWT_SECRET.equals(jwtSecret) || jwtSecret.length() < 32) {
            log.error("[配置自检] 生产环境 JWT 密钥强度不足或使用默认值，必须通过 JWT_SECRET 环境变量设置至少 32 位随机密钥");
            throw new IllegalStateException("生产环境 JWT 密钥强度不足，请通过 JWT_SECRET 环境变量配置");
        }
        log.info("[配置自检] 生产环境安全配置检查通过");
    }
}
