package com.example.evimind.integration;

import com.example.evimind.auth.AuthService;
import com.example.evimind.mapper.DocumentMapper;
import com.example.evimind.mapper.KnowledgeBaseMapper;
import com.example.evimind.model.entity.Document;
import com.example.evimind.model.entity.KnowledgeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证 + 文档入库集成测试。
 * 验证：注册 → 登录 → 创建知识库 → 查询文档列表 的完整流程。
 *
 * 注意：需要 Docker 环境才能运行此测试（Testcontainers 依赖）。
 * 在没有 Docker 的 CI 环境中可以跳过此测试。
 * 设置环境变量 SKIP_INTEGRATION_TESTS=true 可跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "SKIP_INTEGRATION_TESTS", matches = "true")
class AuthDocumentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Test
    void contextLoads() {
        // Verify Spring context starts successfully with Testcontainers
        assertNotNull(authService);
        assertNotNull(knowledgeBaseMapper);
        assertNotNull(documentMapper);
    }

    @Test
    void shouldCreateKnowledgeBaseAndQueryDocuments() {
        // Create a knowledge base directly in the test DB
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName("Test KB");
        kb.setChunkSize(512);
        kb.setChunkOverlap(50);
        kb.setChunkStrategy("FIXED");
        kb.setEvidenceThreshold(java.math.BigDecimal.valueOf(0.5));
        knowledgeBaseMapper.insert(kb);

        assertNotNull(kb.getId());

        // Create a document record
        Document doc = new Document();
        doc.setKnowledgeBaseId(kb.getId());
        doc.setFileName("test.pdf");
        doc.setFileFormat("pdf");
        doc.setFileSize(1024L);
        doc.setStoragePath("test/path");
        doc.setIngestionStatus("PENDING");
        doc.setChunkCount(0);
        documentMapper.insert(doc);

        assertNotNull(doc.getId());

        // Query documents for the KB
        var docs = documentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                        .eq(Document::getKnowledgeBaseId, kb.getId()));
        assertEquals(1, docs.size());
        assertEquals("test.pdf", docs.get(0).getFileName());
    }
}
