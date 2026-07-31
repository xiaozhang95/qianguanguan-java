package com.qianguanguan.model;

import lombok.Data;

/**
 * 个人信息更新请求参数
 */
@Data
public class ProfileUpdateRequest {

    /** 姓名/昵称 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 邮箱（修改时需验证码） */
    private String email;

    /** 邮箱验证码 */
    private String emailCode;

    /** 头像路径 */
    private String avatar;
}
