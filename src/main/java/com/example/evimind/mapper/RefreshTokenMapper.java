package com.example.evimind.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.RefreshToken;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

  @Update(
      """
      UPDATE refresh_token
      SET revoked = TRUE
      WHERE id = #{id}
        AND revoked = FALSE
        AND expires_at > #{now}
      """)
  int consumeActiveToken(@Param("id") Long id, @Param("now") LocalDateTime now);
}
