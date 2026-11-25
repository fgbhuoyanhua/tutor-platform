package com.tutor.platform.config;

import com.tutor.platform.common.CreditRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 推荐排序权重配置：四个维度权重放 application.yml（app.recommend.*），
 * 改权重不用改代码，答辩演示时可直接对比"调整权重前后推荐列表变化"。
 */
@Configuration
public class RecommendConfig {

    @Bean
    public CreditRules creditRules(
            @Value("${app.recommend.weight-cert:0.35}") double wCert,
            @Value("${app.recommend.weight-rating:0.25}") double wRating,
            @Value("${app.recommend.weight-finish:0.20}") double wFinish,
            @Value("${app.recommend.weight-credit:0.20}") double wCredit) {
        return new CreditRules(wCert, wRating, wFinish, wCredit);
    }
}
