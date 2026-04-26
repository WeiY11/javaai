package com.example.javaai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaai.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
