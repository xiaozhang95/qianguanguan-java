package com.qianguanguan.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class BillSaveRequest {

    private Long categoryId;

    private String type;

    private BigDecimal amount;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date billDate;

    private String remark;
}
