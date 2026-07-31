package com.qianguanguan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qianguanguan.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper 接口
 * 继承 MyBatis-Plus BaseMapper，自动获得 CRUD 能力
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
