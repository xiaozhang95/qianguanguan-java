package com.qianguanguan.model;

import lombok.Data;

/**
 * 用户列表查询请求参数
 */
@Data
public class UserListRequest {

    /** 用户名筛选（模糊匹配） */
    private String username;

    /** 角色筛选 */
    private String role;

    /** 状态筛选：1-启用 0-禁用 */
    private Integer status;

    /** 页码 */
    private Integer pageNum;

    /** 每页条数 */
    private Integer pageSize;
}
