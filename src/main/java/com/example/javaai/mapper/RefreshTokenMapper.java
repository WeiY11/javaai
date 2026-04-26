package com.example.javaai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaai.model.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
