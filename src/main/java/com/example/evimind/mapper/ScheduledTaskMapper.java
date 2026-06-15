package com.example.evimind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.evimind.model.entity.ScheduledTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTask> {

    @Select("SELECT * FROM scheduled_task WHERE status = 'ACTIVE' AND next_run_at <= #{now} ORDER BY next_run_at")
    List<ScheduledTask> findDueTasks(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM scheduled_task WHERE status = 'ACTIVE' ORDER BY next_run_at")
    List<ScheduledTask> findAllActive();
}
