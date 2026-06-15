package com.example.evimind.config;

import java.sql.Connection;

import javax.sql.DataSource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@MapperScan("com.example.evimind.mapper")
public class MyBatisPlusConfig {

  private final DataSource dataSource;

  public MyBatisPlusConfig(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(detectDbType()));
    return interceptor;
  }

  private DbType detectDbType() {
    try (Connection conn = dataSource.getConnection()) {
      String url = conn.getMetaData().getURL();
      if (url.contains("h2")) return DbType.H2;
      if (url.contains("postgresql")) return DbType.POSTGRE_SQL;
      if (url.contains("mysql")) return DbType.MYSQL;
      return DbType.POSTGRE_SQL;
    } catch (Exception e) {
      log.warn("Failed to detect DB type, defaulting to PostgreSQL", e);
      return DbType.POSTGRE_SQL;
    }
  }
}
