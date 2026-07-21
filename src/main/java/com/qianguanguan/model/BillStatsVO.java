package com.qianguanguan.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BillStatsVO {

    private BigDecimal totalIncome;

    private BigDecimal totalExpense;

    private BigDecimal balance;
}
