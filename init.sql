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
    `createtime` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- --------------------------------------------
-- 默认管理员 admin / 123456
-- --------------------------------------------
INSERT INTO `t_user` (`id`, `username`, `password`, `name`, `role`, `phone`, `email`, `createtime`)
VALUES (1, 'admin', '$2b$10$DCutU/Klf3dOGH/qFgPekO4lcozfrHVyXKMONmhsny6NpDH7zbRw.', '系统管理员', '超级管理员', '13800000000', 'admin@qianguanguan.com', NOW());
