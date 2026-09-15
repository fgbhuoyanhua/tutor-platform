package com.tutor.platform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 演示数据初始化：首次启动自动创建演示账号（密码统一 123456）与示例家教信息。
 * 已存在则跳过，可重复启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final TutorMapper tutorMapper;

    @Override
    public void run(String... args) {
        ensureUser("admin", 3, "平台管理员", "13800000001");
        ensureUser("tutor01", 2, "王老师", "13800000002");
        ensureUser("tutor02", 2, "李老师", "13800000003");
        ensureUser("student01", 1, "张同学", "13800000004");

        // 幂等兜底：同一位老师若因历史测试/异常产生多条家教记录，保留最早一条，避免 selectOne 抛 TooManyResults 导致启动失败
        dedupeTutorByUserId(2L);
        dedupeTutorByUserId(3L);

        TutorEntity math = tutorMapper.selectOne(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, 2L));
        if (math == null) {
            insertTutor(2L, 2L, "初中/高中", "120.00", "数学专业研究生，三年家教经验，擅长提分。", "4.80");
            log.info("[数据初始化] 已创建示例家教：王老师-数学");
        }
        TutorEntity cs = tutorMapper.selectOne(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, 3L));
        if (cs == null) {
            insertTutor(3L, 6L, "大学/高中", "150.00", "计算机系在读，Python/Java 入门辅导。", "4.90");
            log.info("[数据初始化] 已创建示例家教：李老师-编程");
        }
    }

    private void dedupeTutorByUserId(Long userId) {
        java.util.List<TutorEntity> list = tutorMapper.selectList(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, userId)
                .orderByAsc(TutorEntity::getId));
        for (int i = 1; i < list.size(); i++) {
            tutorMapper.deleteById(list.get(i).getId());
            log.warn("[数据初始化] 已清理重复家教记录 id={}（userId={}）", list.get(i).getId(), userId);
        }
    }

    private void ensureUser(String username, int role, String realName, String phone) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        if (count == null || count == 0) {
            UserEntity u = new UserEntity();
            u.setUsername(username);
            u.setPhone(phone);
            u.setPassword(ENCODER.encode("123456"));
            u.setRole(role);
            u.setRealName(realName);
            u.setStatus(1);
            u.setCreateTime(LocalDateTime.now());
            userMapper.insert(u);
            log.info("[数据初始化] 已创建演示账号：{} / 密码 123456", username);
        }
    }

    private void insertTutor(Long userId, Long subjectId, String grade, String price, String intro, String rating) {
        TutorEntity t = new TutorEntity();
        t.setUserId(userId);
        t.setSubjectId(subjectId);
        t.setGrade(grade);
        t.setPrice(new BigDecimal(price));
        t.setIntroduce(intro);
        t.setRating(new BigDecimal(rating));
        t.setStatus(1); // 已上架
        t.setCreateTime(LocalDateTime.now());
        tutorMapper.insert(t);
    }
}
