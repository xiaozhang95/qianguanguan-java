package com.qianguanguan.model;

import lombok.Data;

@Data
public class UserListRequest {

    private String username;

    private String role;

    private Integer status;

    private Integer pageNum;

    private Integer pageSize;
}
