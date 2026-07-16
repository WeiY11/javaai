package com.example.evimind.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.example.evimind.model.entity.DocumentChunk;

class DocumentChunkMapperSqlTest {

  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUpDatabase() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:keyword_search_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    dataSource.setUser("sa");

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE document (
            id BIGINT PRIMARY KEY,
            active_ingestion_version INT
          )
          """);
      statement.execute(
          """
          CREATE TABLE document_chunk (
            id BIGINT PRIMARY KEY,
            document_id BIGINT NOT NULL,
            knowledge_base_id BIGINT NOT NULL,
            content CLOB NOT NULL,
            chunk_index INT,
            ingestion_version INT NOT NULL,
            vector_id VARCHAR(128),
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
          )
          """);

      statement.execute("INSERT INTO document VALUES (1, 2), (2, 1), (3, 1)");
      statement.execute(
          """
          INSERT INTO document_chunk
            (id, document_id, knowledge_base_id, content, chunk_index, ingestion_version)
          VALUES
            (101, 1, 7, 'Alpha and beta are active', 0, 2),
            (102, 1, 7, 'alpha from an inactive version', 0, 1),
            (103, 2, 8, 'beta in another knowledge base', 0, 1),
            (104, 3, 7, 'gamma only', 0, 1),
            (105, 3, 7, 'contains literal a_% token', 1, 1)
          """);
    }

    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(
        new Environment("test", new JdbcTransactionFactory(), dataSource));
    configuration.addMapper(DocumentChunkMapper.class);
    sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
  }

  @Test
  void shouldReturnOnlyActiveMatchingChunksFromRequestedKnowledgeBase() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      DocumentChunkMapper mapper = session.getMapper(DocumentChunkMapper.class);

      List<DocumentChunk> results =
          mapper.findActiveContainingAnyTerm(7L, List.of("alpha", "beta"));

      assertEquals(List.of(101L), results.stream().map(DocumentChunk::getId).toList());
    }
  }

  @Test
  void shouldTreatLikeWildcardsInTermsAsLiteralCharacters() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      DocumentChunkMapper mapper = session.getMapper(DocumentChunkMapper.class);

      List<DocumentChunk> results = mapper.findActiveContainingAnyTerm(7L, List.of("a_%"));

      assertEquals(List.of(105L), results.stream().map(DocumentChunk::getId).toList());
    }
  }
}
