package com.qianguanguan.model;

import lombok.Data;

@Data
public class BillListRequest {

    private String type;

    private Long categoryId;

    private String startDate;

    private String endDate;

    private Integer pageNum;

    private Integer pageSize;
}
