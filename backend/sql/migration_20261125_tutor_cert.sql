-- ============================================================
-- 二期功能：老师资格认证（一次执行，不可重复运行）
-- 日期：2026-11-25
-- ============================================================
USE tutor_platform;

ALTER TABLE `tutor`
  ADD COLUMN `school`      VARCHAR(100) DEFAULT NULL COMMENT '就读院校',
  ADD COLUMN `major`       VARCHAR(100) DEFAULT NULL COMMENT '专业',
  ADD COLUMN `grade_level` VARCHAR(20)  DEFAULT NULL COMMENT '年级',
  ADD COLUMN `cert_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '认证状态 0未提交 1待审 2已认证 3驳回',
  ADD COLUMN `cert_time`   DATETIME     DEFAULT NULL COMMENT '认证通过时间',
  ADD COLUMN `edu_email`   VARCHAR(100) DEFAULT NULL COMMENT '校园邮箱',
  ADD KEY `idx_cert_status` (`cert_status`);

CREATE TABLE IF NOT EXISTS `tutor_cert` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '认证记录ID',
  `tutor_id`    BIGINT       NOT NULL COMMENT '老师 user.id',
  `school`      VARCHAR(100) NOT NULL COMMENT '就读院校',
  `major`       VARCHAR(100) NOT NULL COMMENT '专业',
  `grade_level` VARCHAR(20)  NOT NULL COMMENT '年级',
  `cert_type`   TINYINT      NOT NULL COMMENT '证明类型 1学生证 2学信网报告 3校园卡 4校园邮箱 5其他',
  `cert_no`     VARCHAR(64)  DEFAULT NULL COMMENT '证明编号',
  `cert_url`    VARCHAR(255) DEFAULT NULL COMMENT '证明文件路径',
  `note`        VARCHAR(500) DEFAULT NULL COMMENT '补充说明',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  `reviewer_id` BIGINT       DEFAULT NULL COMMENT '审核管理员',
  `review_note` VARCHAR(255) DEFAULT NULL COMMENT '驳回理由',
  `review_time` DATETIME     DEFAULT NULL COMMENT '审核时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `pending_flag` TINYINT GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN 1 ELSE NULL END) STORED COMMENT '待审占位标记',
  PRIMARY KEY (`id`),
  KEY `idx_tutor` (`tutor_id`),
  KEY `idx_status` (`status`),
  UNIQUE KEY `uk_cert_pending` (`tutor_id`, `pending_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='老师资格认证记录表';
