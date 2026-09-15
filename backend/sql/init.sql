-- ============================================================
-- 大学生家教服务预约平台 - 建库建表脚本（MySQL 8.0）
-- 执行方式：mysql -uroot -p < sql/init.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS tutor_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tutor_platform;

-- 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`    VARCHAR(50)  NOT NULL COMMENT '登录用户名',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
  `role`        TINYINT      NOT NULL DEFAULT 1 COMMENT '角色：1学生 2老师 3管理员',
  `real_name`   VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
  `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB COMMENT='用户表';

-- 科目分类表
DROP TABLE IF EXISTS `subject`;
CREATE TABLE `subject` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name`      VARCHAR(50) NOT NULL COMMENT '科目名称',
  `parent_id` BIGINT      DEFAULT 0 COMMENT '父分类ID，0为顶级',
  `sort`      INT         DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB COMMENT='科目分类表';

-- 家教信息表
DROP TABLE IF EXISTS `tutor`;
CREATE TABLE `tutor` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '家教信息ID',
  `user_id`     BIGINT        NOT NULL COMMENT '所属老师 user.id',
  `subject_id`  BIGINT        NOT NULL COMMENT '授课科目 subject.id',
  `grade`       VARCHAR(50)   DEFAULT NULL COMMENT '辅导学段',
  `price`       DECIMAL(10,2) NOT NULL COMMENT '每小时资费（元）',
  `introduce`   TEXT          COMMENT '老师简介',
  `rating`      DECIMAL(3,2)  NOT NULL DEFAULT 5.00 COMMENT '综合评分',
  `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0待审核 1已上架 2已下架 3未通过',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_subject` (`status`, `subject_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='家教信息表';

-- 预约订单表
DROP TABLE IF EXISTS `appointment`;
CREATE TABLE `appointment` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no`     VARCHAR(32)   NOT NULL COMMENT '订单编号',
  `student_id`   BIGINT        NOT NULL COMMENT '学生 user.id',
  `tutor_id`     BIGINT        NOT NULL COMMENT '家教老师 user.id',
  `subject_id`   BIGINT        NOT NULL COMMENT '授课科目 subject.id',
  `appoint_date` DATE          NOT NULL COMMENT '预约日期',
  `time_slot`    VARCHAR(20)   NOT NULL COMMENT '时间段，如 09:00-11:00',
  `total_price`  DECIMAL(10,2) NOT NULL COMMENT '订单金额',
  `status`       TINYINT       NOT NULL COMMENT '0待确认 1已预约 2授课中 3已完成 4已取消 5已拒绝',
  `pay_status`   TINYINT       NOT NULL DEFAULT 0 COMMENT '支付状态：0未支付 1已支付',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_student` (`student_id`),
  KEY `idx_tutor` (`tutor_id`),
  -- 并发冲突兜底：同一老师同一日期同一时间段仅允许一个有效预约（有效状态 0/1/2）
  UNIQUE KEY `uk_slot` (`tutor_id`, `appoint_date`, `time_slot`, `status`)
) ENGINE=InnoDB COMMENT='预约订单表';

-- 评价表
DROP TABLE IF EXISTS `evaluation`;
CREATE TABLE `evaluation` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id`    BIGINT       NOT NULL COMMENT '所属订单 appointment.id',
  `student_id`  BIGINT       NOT NULL COMMENT '评价学生 user.id',
  `tutor_id`    BIGINT       NOT NULL COMMENT '被评老师 user.id',
  `score`       TINYINT      NOT NULL COMMENT '评分1-5',
  `content`     VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_student` (`order_id`, `student_id`)
) ENGINE=InnoDB COMMENT='评价表';

-- 消息表
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `from_id`     BIGINT       DEFAULT NULL COMMENT '发送用户 user.id，系统消息为空',
  `to_id`       BIGINT       NOT NULL COMMENT '接收用户 user.id',
  `type`        TINYINT      NOT NULL DEFAULT 3 COMMENT '消息类型：1预约 2订单 3系统',
  `content`     VARCHAR(500) NOT NULL COMMENT '消息内容',
  `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_to_read` (`to_id`, `is_read`)
) ENGINE=InnoDB COMMENT='消息表';

-- ============================================================
-- 种子数据说明：
-- 1. 科目分类直接在此初始化（无敏感信息）
-- 2. 演示账号（admin/tutor01/tutor02/student01，密码统一 123456）
--    由后端 DataInitializer 在应用首次启动时自动创建（BCrypt 加密），
--    无需手工维护密码哈希。如已存在则跳过。
-- ============================================================
INSERT INTO `subject` (`name`, `parent_id`, `sort`) VALUES
('语文', 0, 1), ('数学', 0, 2), ('英语', 0, 3), ('物理', 0, 4), ('化学', 0, 5), ('编程', 0, 6);
