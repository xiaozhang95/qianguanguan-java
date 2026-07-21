-- ============================================
-- 钱罐罐 项目数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS `qianguanguan`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `qianguanguan`;

-- --------------------------------------------
-- 用户表
-- --------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id`         BIGINT       NOT NULL COMMENT '主键ID',
    `username`   VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`   VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `name`       VARCHAR(50)  DEFAULT NULL COMMENT '姓名/昵称',
    `role`       VARCHAR(50)  NOT NULL DEFAULT '用户' COMMENT '角色',
    `phone`      VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`      VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `createtime` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- --------------------------------------------
-- 默认管理员 admin / 123456
-- --------------------------------------------
INSERT INTO `t_user` (`id`, `username`, `password`, `name`, `role`, `phone`, `email`, `status`, `createtime`)
VALUES (1, 'admin', '$2b$10$DCutU/Klf3dOGH/qFgPekO4lcozfrHVyXKMONmhsny6NpDH7zbRw.', '系统管理员', 'SUPER_ADMIN', '13800000000', 'admin@qianguanguan.com', 1, NOW());

-- --------------------------------------------
-- 分类表
-- --------------------------------------------
DROP TABLE IF EXISTS `t_category`;
CREATE TABLE `t_category` (
    `id`         BIGINT       NOT NULL COMMENT '主键ID',
    `name`       VARCHAR(50)  NOT NULL COMMENT '分类名称',
    `icon`       VARCHAR(10)  NOT NULL COMMENT '图标（emoji）',
    `type`       VARCHAR(10)  NOT NULL COMMENT '类型：INCOME-收入 / EXPENSE-支出',
    `sort`       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `createtime` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分类表';

-- --------------------------------------------
-- 默认分类
-- --------------------------------------------
INSERT INTO `t_category` (`id`, `name`, `icon`, `type`, `sort`, `createtime`) VALUES
(1,  '工资',    '💰', 'INCOME',  1, NOW()),
(2,  '兼职',    '💼', 'INCOME',  2, NOW()),
(3,  '理财',    '📈', 'INCOME',  3, NOW()),
(4,  '红包',    '🧧', 'INCOME',  4, NOW()),
(5,  '其他收入','💵', 'INCOME', 99, NOW()),
(6,  '餐饮',    '🍜', 'EXPENSE', 1, NOW()),
(7,  '交通',    '🚗', 'EXPENSE', 2, NOW()),
(8,  '购物',    '🛒', 'EXPENSE', 3, NOW()),
(9,  '住房',    '🏠', 'EXPENSE', 4, NOW()),
(10, '娱乐',    '🎮', 'EXPENSE', 5, NOW()),
(11, '医疗',    '💊', 'EXPENSE', 6, NOW()),
(12, '教育',    '📚', 'EXPENSE', 7, NOW()),
(13, '通讯',    '📱', 'EXPENSE', 8, NOW()),
(14, '日用',    '🧴', 'EXPENSE', 9, NOW()),
(15, '其他支出','💸', 'EXPENSE',99, NOW());

-- --------------------------------------------
-- 账单表
-- --------------------------------------------
DROP TABLE IF EXISTS `t_bill`;
CREATE TABLE `t_bill` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `category_id` BIGINT       NOT NULL COMMENT '分类ID',
    `type`        VARCHAR(10)  NOT NULL COMMENT '类型：INCOME-收入 / EXPENSE-支出',
    `amount`      DECIMAL(10,2) NOT NULL COMMENT '金额',
    `bill_date`   DATE         NOT NULL COMMENT '账单日期',
    `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `createtime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_date` (`user_id`, `bill_date`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账单表';
