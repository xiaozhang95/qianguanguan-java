package com.qianguanguan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应数据库表 t_user
 */
@Data
@TableName("t_user")
public class User {

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 姓名/昵称 */
    private String name;

    /** 角色：SUPER_ADMIN / ADMIN / USER */
    private String role;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    /** 头像路径 */
    private String avatar;

    /** 微信 OpenID */
    private String wid;

    /** 创建时间 */
    private LocalDateTime createtime;
}
