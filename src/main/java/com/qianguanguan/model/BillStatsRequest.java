package com.qianguanguan.model;

import lombok.Data;

/**
 * 账单统计请求参数
 */
@Data
public class BillStatsRequest {

    /** 年份 */
    private String year;

    /** 月份 */
    private String month;

    /** 日期（可选，传入则按日统计） */
    private String day;

    /** 账单类型筛选 */
    private String type;

    /** 分类ID筛选 */
    private Long categoryId;
}
