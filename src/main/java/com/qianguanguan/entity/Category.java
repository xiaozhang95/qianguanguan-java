package com.qianguanguan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 分类实体类
 * 对应数据库表 t_category，user_id 为 NULL 表示系统默认分类
 */
@Data
@TableName("t_category")
public class Category {

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 图标（emoji 或图片路径） */
    private String icon;

    /** 类型：INCOME-收入 / EXPENSE-支出 */
    private String type;

    /** 用户ID（NULL=系统默认分类，非NULL=用户自定义分类） */
    private Long userId;

    /** 排序序号 */
    private Integer sort;

    /** 创建时间 */
    private Date createtime;
}
