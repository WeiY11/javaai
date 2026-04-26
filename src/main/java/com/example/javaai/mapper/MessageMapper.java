package com.example.javaai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javaai.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
