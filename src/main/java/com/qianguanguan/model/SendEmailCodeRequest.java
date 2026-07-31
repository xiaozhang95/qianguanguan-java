package com.qianguanguan.model;

import lombok.Data;

/**
 * 发送邮箱验证码请求参数
 */
@Data
public class SendEmailCodeRequest {

    /** 目标邮箱地址 */
    private String email;
}
