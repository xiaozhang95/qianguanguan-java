-- ============================================
-- 钱罐罐 数据库变更记录
-- ============================================

-- 2026-07-23: 分类图标字段扩容，支持图片路径
ALTER TABLE `t_category` MODIFY COLUMN `icon` VARCHAR(500) NOT NULL COMMENT '图标（emoji或图片路径）';

-- 2026-07-23: 分类增加 user_id 字段，区分系统默认和用户自定义
ALTER TABLE `t_category` ADD COLUMN `user_id` BIGINT NULL COMMENT '用户ID（NULL=系统默认）' AFTER `type`;

-- 2026-07-23: 用户表增加 wid 字段，支持微信小程序登录
ALTER TABLE `t_user` ADD COLUMN `wid` VARCHAR(100) NULL COMMENT '微信OpenID' AFTER `avatar`;
ALTER TABLE `t_user` ADD UNIQUE KEY `uk_wid` (`wid`);
