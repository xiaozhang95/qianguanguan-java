package com.qianguanguan.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BillSaveRequest {

    private Long categoryId;

    private String type;

    private BigDecimal amount;

    private LocalDate billDate;

    private String remark;
}
