package com.tutor.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tutor.platform.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
