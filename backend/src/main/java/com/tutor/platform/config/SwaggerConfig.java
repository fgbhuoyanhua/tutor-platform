package com.tutor.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger 配置：契约驱动开发，前端以文档为契约并行开发
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI tutorOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("大学生家教服务预约平台 API")
                .description("移动端(uni-app x)与Web端共用同一套接口，角色字段区分权限")
                .version("1.0.0"));
    }
}
