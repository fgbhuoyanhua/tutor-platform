package com.tutor.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tutor.platform.entity.FavoriteEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<FavoriteEntity> {
}
