-- ============================================================
-- 二期功能：老师等级 + 推荐排序显式化 + 课时账单 + 投诉治理
-- 日期：2026-11-25
-- 说明：幂等脚本，可重复执行（按 information_schema 判断后再 ALTER）
-- 存量库：mysql -uroot -proot --ssl-mode=DISABLED tutor_platform < migration_20261201_feature_pack.sql
-- 注意：不要用 init.sql 升级存量库（含 DROP TABLE）
-- ============================================================
USE tutor_platform;

-- ---------- 1. tutor 表：信用账本 ----------
DROP PROCEDURE IF EXISTS sp_add_col_if_absent;
DELIMITER $$
CREATE PROCEDURE sp_add_col_if_absent(IN t VARCHAR(64), IN c VARCHAR(64), IN ddl VARCHAR(512))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
    SET @sql = CONCAT('ALTER TABLE `', t, '` ADD COLUMN ', ddl);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL sp_add_col_if_absent('tutor', 'credit_score', "`credit_score` INT NOT NULL DEFAULT 100 COMMENT '信用分，100 满分，违规扣分、完成订单加分'");
CALL sp_add_col_if_absent('tutor', 'violation_count', "`violation_count` INT NOT NULL DEFAULT 0 COMMENT '违规累计次数'");
DROP PROCEDURE IF EXISTS sp_add_col_if_absent;

-- 历史数据：等级按信用分计算，默认 100（无违规）

-- ---------- 2. 老师违规记录 ----------
CREATE TABLE IF NOT EXISTS `tutor_violation` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `tutor_id`    BIGINT       NOT NULL COMMENT '老师 user.id',
  `type`        TINYINT      NOT NULL COMMENT '1投诉成立 2超时未确认 3频繁拒绝',
  `ref_type`    VARCHAR(20)  DEFAULT NULL COMMENT '关联单据类型，如 complaint/appointment',
  `ref_id`      BIGINT       DEFAULT NULL COMMENT '关联单据 id',
  `deduct`      INT          NOT NULL DEFAULT 10 COMMENT '扣减信用分',
  `reason`      VARCHAR(255) DEFAULT NULL,
  `operator_id` BIGINT       DEFAULT NULL COMMENT '操作管理员 user.id（系统执行为 NULL）',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1有效 0已撤销',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tutor_time` (`tutor_id`, `create_time`),
  KEY `idx_ref` (`ref_type`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老师违规记录';

-- ---------- 3. 投诉举报 ----------
CREATE TABLE IF NOT EXISTS `complaint` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `complainant_id` BIGINT      NOT NULL COMMENT '投诉人 user.id',
  `tutor_id`      BIGINT       NOT NULL COMMENT '被投诉老师 user.id',
  `order_id`      BIGINT       DEFAULT NULL COMMENT '关联订单 appointment.id',
  `type`          TINYINT      NOT NULL COMMENT '1爽约 2私改价格 3骚扰辱骂 4虚假信息 5其他',
  `content`       VARCHAR(500) NOT NULL COMMENT '投诉描述',
  `evidence_url`  VARCHAR(255) DEFAULT NULL COMMENT '证据材料路径（复用证明材料接口）',
  `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1成立 2驳回',
  `handler_id`    BIGINT       DEFAULT NULL COMMENT '处理管理员',
  `handle_note`   VARCHAR(255) DEFAULT NULL COMMENT '处理说明',
  `handler_time`  DATETIME     DEFAULT NULL,
  `deduct_score`  INT          NOT NULL DEFAULT 0 COMMENT '成立时扣减信用分',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pending_flag`  TINYINT GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN 1 ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  -- 同一人对同一老师同一订单只能有一条待处理投诉，防重复刷投诉（业务层先查 409，唯一索引并发兜底）
  UNIQUE KEY `uk_complaint_pending` (`complainant_id`, `tutor_id`, `order_id`, `pending_flag`),
  KEY `idx_status` (`status`),
  KEY `idx_tutor` (`tutor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉举报';
