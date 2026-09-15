package com.tutor.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 大学生家教服务预约平台 - 启动类
 */
@SpringBootApplication
@MapperScan("com.tutor.platform.mapper")
public class TutorPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutorPlatformApplication.class, args);
    }
}
