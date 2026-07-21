package com.qianguanguan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String icon;

    private String type;

    private Integer sort;

    private Date createtime;
}
