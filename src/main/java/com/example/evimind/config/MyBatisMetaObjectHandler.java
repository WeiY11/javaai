package com.example.evimind.config;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

  @Override
  public void insertFill(MetaObject metaObject) {
    this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
    this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    this.strictInsertFill(metaObject, "joinedAt", LocalDateTime.class, LocalDateTime.now());
  }

  @Override
  public void updateFill(MetaObject metaObject) {
    this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
  }
}
