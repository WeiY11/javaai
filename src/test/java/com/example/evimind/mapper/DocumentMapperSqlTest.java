package com.example.evimind.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.evimind.model.entity.Document;

class DocumentMapperSqlTest {

  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUpDatabase() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:document_permission_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    dataSource.setUser("sa");

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE document (
            id BIGINT PRIMARY KEY,
            knowledge_base_id BIGINT NOT NULL,
            file_name VARCHAR(256) NOT NULL,
            created_at TIMESTAMP NOT NULL
          )
          """);
      statement.execute(
          """
          CREATE TABLE document_permission (
            id BIGINT PRIMARY KEY,
            document_id BIGINT NOT NULL,
            user_id BIGINT NOT NULL,
            permission_type VARCHAR(16) NOT NULL
          )
          """);
      statement.execute(
          """
          INSERT INTO document (id, knowledge_base_id, file_name, created_at)
          VALUES
            (1, 7, 'unrestricted.pdf', TIMESTAMP '2026-01-01 00:00:00'),
            (2, 7, 'restricted-other-user.pdf', TIMESTAMP '2026-01-02 00:00:00'),
            (3, 7, 'readable.pdf', TIMESTAMP '2026-01-03 00:00:00'),
            (4, 7, 'admin-readable.pdf', TIMESTAMP '2026-01-04 00:00:00'),
            (5, 8, 'other-kb.pdf', TIMESTAMP '2026-01-05 00:00:00')
          """);
      statement.execute(
          """
          INSERT INTO document_permission (id, document_id, user_id, permission_type)
          VALUES
            (101, 2, 99, 'READ'),
            (102, 3, 11, 'READ'),
            (103, 4, 11, 'ADMIN')
          """);
    }

    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(
        new Environment("test", new JdbcTransactionFactory(), dataSource));
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
    configuration.addInterceptor(interceptor);
    configuration.addMapper(DocumentMapper.class);
    sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
  }

  @Test
  void shouldReturnOnlyDocumentsReadableByTheCurrentKnowledgeBaseMember() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      DocumentMapper mapper = session.getMapper(DocumentMapper.class);

      IPage<Document> page = mapper.selectAccessiblePage(new Page<>(1, 2), 7L, 11L);

      assertEquals(3L, page.getTotal());
      assertEquals(
          List.of(4L, 3L), page.getRecords().stream().map(Document::getId).toList());
    }
  }

  @Test
  void shouldFilterCandidateDocumentIdsByKnowledgeBaseAndReadPermission() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      DocumentMapper mapper = session.getMapper(DocumentMapper.class);

      List<Long> readableIds =
          mapper.findReadableDocumentIds(7L, List.of(1L, 2L, 3L, 4L, 5L), 11L);

      assertEquals(Set.of(1L, 3L, 4L), Set.copyOf(readableIds));
    }
  }
}
