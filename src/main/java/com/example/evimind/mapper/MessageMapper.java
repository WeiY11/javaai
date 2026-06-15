package com.example.evimind.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.Message;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {}
