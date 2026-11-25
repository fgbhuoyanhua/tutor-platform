-- ============================================================
-- 投诉防重复唯一索引修复（2026-11-25）
-- 背景：uk_complaint_pending 把可空的 order_id 放进唯一索引，
--       MySQL 对 NULL 不做唯一性比较，导致「同一人对同一老师、
--       不关联订单」的待处理投诉可以并发插入多条（实测 200/200）。
-- 做法：新增生成列 order_key = IFNULL(order_id, 0)，
--       用 (complainant_id, tutor_id, order_key, pending_flag) 建唯一索引。
-- 幂等：全部用 information_schema 判断后再执行，可重复运行。
-- 执行顺序：先跑 migration_20261201_feature_pack.sql，再跑本脚本。
-- ============================================================
SET @db := DATABASE();

-- 0) 历史脏数据清理：同一 (投诉人, 老师, order_key) 下多余待处理记录只保留最早的
SET @dup := (SELECT COUNT(*) FROM (
    SELECT complainant_id, tutor_id, IFNULL(order_id, 0) AS ok, COUNT(*) AS n
    FROM `complaint` WHERE `status` = 0
    GROUP BY complainant_id, tutor_id, IFNULL(order_id, 0)
    HAVING n > 1
) t);
SET @s := IF(@dup > 0,
  'DELETE c FROM `complaint` c JOIN (
       SELECT id, ROW_NUMBER() OVER (PARTITION BY complainant_id, tutor_id, IFNULL(order_id,0) ORDER BY id) AS rn
       FROM `complaint` WHERE `status` = 0
   ) d ON d.id = c.id AND d.rn > 1',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1) 生成列 order_key
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'complaint' AND COLUMN_NAME = 'order_key');
SET @s := IF(@has_col = 0,
  'ALTER TABLE `complaint` ADD COLUMN `order_key` BIGINT GENERATED ALWAYS AS (IFNULL(`order_id`, 0)) STORED COMMENT ''投诉去重键：order_id 为空按 0''',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 删旧唯一索引（含 NULL 列，形同虚设）
SET @has_old := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'complaint' AND INDEX_NAME = 'uk_complaint_pending');
SET @s := IF(@has_old > 0, 'ALTER TABLE `complaint` DROP INDEX `uk_complaint_pending`', 'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 新唯一索引
SET @has_new := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'complaint' AND INDEX_NAME = 'uk_complaint_pending_v2');
SET @s := IF(@has_new = 0,
  'ALTER TABLE `complaint` ADD UNIQUE KEY `uk_complaint_pending_v2` (`complainant_id`, `tutor_id`, `order_key`, `pending_flag`)',
  'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 结果确认
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'complaint' AND INDEX_NAME LIKE 'uk_complaint%'
GROUP BY INDEX_NAME;
