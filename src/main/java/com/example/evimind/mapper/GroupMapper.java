package com.example.evimind.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.Group;

@Mapper
public interface GroupMapper extends BaseMapper<Group> {}
