package com.qianguanguan.model;

import lombok.Data;

/**
 * 用户注册请求参数
 */
@Data
public class RegisterRequest {

    /** 登录密码 */
    private String password;

    /** 邮箱（同时作为登录用户名） */
    private String email;

    /** 邮箱验证码 */
    private String emailCode;

    /** 图形验证码 key */
    private String captchaKey;

    /** 图形验证码 */
    private String captchaCode;
}
