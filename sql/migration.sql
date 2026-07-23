-- ============================================
-- 钱罐罐 数据库变更记录
-- ============================================

-- 2026-07-23: 分类图标字段扩容，支持图片路径
ALTER TABLE `t_category` MODIFY COLUMN `icon` VARCHAR(500) NOT NULL COMMENT '图标（emoji或图片路径）';

-- 2026-07-23: 分类增加 user_id 字段，区分系统默认和用户自定义
ALTER TABLE `t_category` ADD COLUMN `user_id` BIGINT NULL COMMENT '用户ID（NULL=系统默认）' AFTER `type`;
