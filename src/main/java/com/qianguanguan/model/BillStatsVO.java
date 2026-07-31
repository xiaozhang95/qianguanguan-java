package com.qianguanguan.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账单统计响应 VO
 */
@Data
@Builder
public class BillStatsVO {

    /** 总收入 */
    private BigDecimal totalIncome;

    /** 总支出 */
    private BigDecimal totalExpense;

    /** 结余（收入 - 支出） */
    private BigDecimal balance;
}
