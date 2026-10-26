-- ============================================================
-- 增量迁移脚本：修复 DEFECT-2 / DEFECT-3
-- 适用于【已经存在】的 tutor_platform 数据库（不重建、不丢数据）
-- 执行方式：mysql -uroot -p < sql/migrate-2026-10-26.sql
-- 幂等：重复执行不会报错
-- ============================================================
USE tutor_platform;

-- ------------------------------------------------------------
-- DEFECT-2：uk_slot 把 status 算进唯一键
--   旧: UNIQUE KEY uk_slot (tutor_id, appoint_date, time_slot, status)
--   症状: 同一老师/同日/同时段只能各存在一条 已完成(3)/已取消(4)/已拒绝(5)，
--         复购场景下「确认完成」抛 Duplicate entry '...-3' for key 'uk_slot'
--   修复: 用生成列 active_flag（有效状态=1，其余=NULL）+ 条件唯一索引，
--         因为 MySQL 唯一索引允许多个 NULL，非有效订单不再互相冲突
-- ------------------------------------------------------------

-- 1) 先删旧唯一键（若不存在会报 1091，用过程包裹保证幂等）
DROP PROCEDURE IF EXISTS sp_migrate_uk_slot;
DELIMITER $$
CREATE PROCEDURE sp_migrate_uk_slot()
BEGIN
    -- 删除旧索引 uk_slot
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE()
                 AND table_name = 'appointment'
                 AND index_name = 'uk_slot') THEN
        ALTER TABLE `appointment` DROP INDEX `uk_slot`;
    END IF;

    -- 删除可能残留的新索引（方便重复执行）
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE()
                 AND table_name = 'appointment'
                 AND index_name = 'uk_slot_active') THEN
        ALTER TABLE `appointment` DROP INDEX `uk_slot_active`;
    END IF;

    -- 新增生成列 active_flag
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'appointment'
                     AND column_name = 'active_flag') THEN
        ALTER TABLE `appointment`
            ADD COLUMN `active_flag` TINYINT
            GENERATED ALWAYS AS (
                CASE WHEN `status` IN (0, 1, 2) THEN 1 ELSE NULL END
            ) STORED COMMENT '有效占位标记（生成列）';
    END IF;

    -- 新增条件唯一索引
    ALTER TABLE `appointment`
        ADD UNIQUE KEY `uk_slot_active`
            (`tutor_id`, `appoint_date`, `time_slot`, `active_flag`);
END$$
DELIMITER ;

CALL sp_migrate_uk_slot();
DROP PROCEDURE IF EXISTS sp_migrate_uk_slot;

-- ------------------------------------------------------------
-- DEFECT-3：init.sql 缺失 4 张表（favorite / reschedule / study_report / admin_log）
--   本条只补建【不存在】的表，已有表及其数据不受影响
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `student_id`  BIGINT   NOT NULL COMMENT '学生 user.id',
  `tutor_id`    BIGINT   NOT NULL COMMENT '家教信息 tutor.id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_tutor` (`student_id`, `tutor_id`)
) ENGINE=InnoDB COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS `reschedule` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '调课申请ID',
  `order_id`    BIGINT      NOT NULL COMMENT '订单 appointment.id',
  `student_id`  BIGINT      NOT NULL COMMENT '发起申请的 student user.id',
  `old_date`    DATE        DEFAULT NULL COMMENT '原预约日期',
  `old_slot`    VARCHAR(20) DEFAULT NULL COMMENT '原时间段',
  `new_date`    DATE        DEFAULT NULL COMMENT '申请的新日期',
  `new_slot`    VARCHAR(20) DEFAULT NULL COMMENT '申请的新时间段',
  `status`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0待同意 1已同意 2已拒绝',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='调课申请表';

CREATE TABLE IF NOT EXISTS `study_report` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '报告ID',
  `order_id`    BIGINT   NOT NULL COMMENT '订单 appointment.id',
  `teacher_id`  BIGINT   NOT NULL COMMENT '填写老师 user.id',
  `student_id`  BIGINT   NOT NULL COMMENT '学生 user.id',
  `content`     TEXT     NOT NULL COMMENT '报告内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`)
) ENGINE=InnoDB COMMENT='学习报告表';

CREATE TABLE IF NOT EXISTS `admin_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `admin_id`    BIGINT       NOT NULL COMMENT '操作管理员 user.id',
  `admin_name`  VARCHAR(50)  DEFAULT NULL COMMENT '管理员用户名',
  `action`      VARCHAR(50)  NOT NULL COMMENT '操作类型',
  `target_type` VARCHAR(20)  DEFAULT NULL COMMENT '操作对象类型',
  `target_id`   BIGINT       DEFAULT NULL COMMENT '操作对象ID',
  `detail`      VARCHAR(255) DEFAULT NULL COMMENT '操作详情',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB COMMENT='管理员操作日志表';

-- ------------------------------------------------------------
-- 校验
-- ------------------------------------------------------------
SELECT 'appointment 索引' AS check_item, GROUP_CONCAT(DISTINCT index_name) AS result
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'appointment';

SELECT '缺失表检查' AS check_item,
       GROUP_CONCAT(table_name) AS tables_present
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('favorite', 'reschedule', 'study_report', 'admin_log');
