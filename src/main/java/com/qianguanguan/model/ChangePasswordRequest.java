package com.qianguanguan.model;

import lombok.Data;

/**
 * 修改密码请求参数
 */
@Data
public class ChangePasswordRequest {

    /** 原密码 */
    private String oldPassword;

    /** 新密码 */
    private String newPassword;
}
