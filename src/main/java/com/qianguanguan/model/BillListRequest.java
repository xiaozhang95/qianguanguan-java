package com.qianguanguan.model;

import lombok.Data;

/**
 * 账单列表查询请求参数
 */
@Data
public class BillListRequest {

    /** 账单类型：INCOME-收入 / EXPENSE-支出 */
    private String type;

    /** 分类ID筛选 */
    private Long categoryId;

    /** 开始日期（yyyy-MM-dd） */
    private String startDate;

    /** 结束日期（yyyy-MM-dd） */
    private String endDate;

    /** 页码 */
    private Integer pageNum;

    /** 每页条数 */
    private Integer pageSize;
}
