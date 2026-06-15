package com.example.evimind.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.KbMember;

@Mapper
public interface KbMemberMapper extends BaseMapper<KbMember> {}
