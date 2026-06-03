# EviMind 项目深度解析

> 基于 RAG（检索增强生成）的企业级文档知识库问答与证据分析平台

---

## 1. 项目概述

### 1.1 项目定位

EviMind 是一个面向企业场景的 **RAG（Retrieval-Augmented Generation）智能问答平台**，核心目标是构建一个"可追溯证据"的知识库问答系统。它不是简单地将用户问题丢给大模型，而是通过完整的检索→评估→生成链路，让模型基于真实文档内容回答，有效抑制幻觉，并附带引用来源。

### 1.2 核心价值

| 价值维度 | 说明 |
|---------|------|
| **证据驱动** | 每个回答都有文档来源、切片编号、相关度评分，答案可追溯可验证 |
| **幻觉抑制** | 证据不足时直接拒答，不让模型编造 |
| **知识更新** | 上传新文档即刻入库，无需重新训练模型 |
| **权限隔离** | 知识库级成员权限控制，防止跨库访问 |
| **多模型支持** | DeepSeek / GLM-4 / 通义千问 / OpenAI 四种模型运行时热切换 |
| **学术辅助** | 论文元数据自动提取（DOI/作者/年份/期刊）、BibTeX/APA引用导出 |

### 1.3 适用场景

企业内部知识库问答、技术文档智能检索、学术论文分析、合同/报告内容分析、客服知识库等。

---

## 2. 技术架构

### 2.1 架构全景图

```
┌──────────────────────────────────────────────────────────────────────┐
│                    客户端层 (Client)                                    │
│  浏览器 → Nginx (:80) → Vue SPA 静态文件 + /api 反向代理               │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ HTTP / SSE
┌──────────────────────────────▼───────────────────────────────────────┐
│                 应用层 (Spring Boot 3.5 :8080)                         │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  拦截器层：JWT Filter → SecurityContext → GroupContext(ThreadLocal) │  │
│  │            CORS Filter → GlobalExceptionHandler                  │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────┐  ┌──────────────────────────────────────────┐   │
│  │  认证模块 (auth) │  │  业务 Controller 层 (8个)                  │   │
│  │  AuthController  │  │  ConversationController (SSE流式对话)     │   │
│  │  AuthService     │  │  KnowledgeBaseController (知识库CRUD)      │   │
│  │  TokenProvider   │  │  DocumentController (文档上传/管理)        │   │
│  │  JwtAuthFilter   │  │  AnalysisController (批量分析/导出)        │   │
│  └─────────────────┘  │  ChatController / FileController / ...     │   │
│                        └──────────────────────────────────────────┘   │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                    RAG 核心引擎                                    │  │
│  │                                                                   │  │
│  │  ┌───────────────┐  ┌───────────────┐  ┌──────────────────┐     │  │
│  │  │  ETL 流水线    │  │  混合检索引擎  │  │  RAG 问答管线     │     │  │
│  │  │  EtlPipeline  │  │ HybridSearch  │  │  RagPipeline     │     │  │
│  │  │  ├ TextCleaner│  │ ├ PgVector   │  │  ├ 权限校验       │     │  │
│  │  │  ├ DocChunker │──│ ├ ES Search  │──│  ├ 混合检索       │     │  │
│  │  │  ├ EmbedSvc   │  │ └ RRF Fusion │  │  ├ 证据评估       │     │  │
│  │  │  └ ES Index   │  │  并行+降级    │  │  ├ Portfolio选择  │     │  │
│  │  └───────────────┘  └───────────────┘  │  ├ Prompt组装     │     │  │
│  │                                         │  ├ LLM流式生成    │     │  │
│  │                                         │  └ 引用构建       │     │  │
│  │                                         └──────────────────┘     │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  数据访问层：MyBatis-Plus (12 Mapper) + JPA Repository           │  │
│  │            ElasticsearchClient + MinIO Client                     │  │
│  └─────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────────┐
│                 基础设施层 (Docker Compose)                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐               │
│  │ PostgreSQL 17│  │Elasticsearch │  │   MinIO      │               │
│  │ + pgvector   │  │  8.15.3      │  │  (S3兼容)    │               │
│  └──────────────┘  └──────────────┘  └──────────────┘               │
└──────────────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈清单

#### 后端技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 22 | Record、Switch表达式、虚拟线程等新特性 |
| 框架 | Spring Boot | 3.5.0 | IoC容器、自动配置、Web MVC |
| AI框架 | Spring AI | 1.0.0-M1 | ChatClient、EmbeddingModel、ChatMemory、PromptTemplate |
| ORM | MyBatis-Plus | 3.5.12 | Lambda类型安全查询、分页、自动填充 |
| ORM辅助 | Spring Data JPA | 内嵌 | analysis_result 简单CRUD |
| 安全 | Spring Security | 内嵌 | 过滤器链、BCrypt、CORS、CSP |
| JWT | jjwt | 0.12.6 | HMAC-SHA签名、双令牌机制 |
| 数据库 | PostgreSQL + pgvector | 17 | 业务数据 + 向量存储(1536维) + 余弦相似度搜索 |
| 搜索引擎 | Elasticsearch | 8.15.3 | BM25 关键词全文检索 |
| 对象存储 | MinIO | latest | S3兼容API，文档文件存储 |
| PDF解析 | Apache PDFBox | 3.0.1 | PDF文本+学术元数据提取 |
| Office解析 | Apache POI | 5.2.5 | Word/Excel内容提取 |
| 响应式 | Project Reactor | 内嵌 | Flux流式响应、SSE事件流 |
| 构建 | Maven | 3.9+ | 依赖管理、多阶段Docker构建 |
| 数据库迁移 | Flyway | 内嵌 | 7个版本化SQL迁移脚本 |

#### 前端技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Vue 3 (Composition API) | 3.5 | `<script setup>` 语法糖 |
| 语言 | TypeScript | 6.0 | 类型安全、9个接口类型定义模块 |
| 构建 | Vite | 8.0 | 极速HMR、ESBuild预构建 |
| 状态管理 | Pinia | 3.0 | auth/chat/knowledge-base 三模块组合式API |
| UI组件库 | Element Plus | 2.13 | 表单、表格、对话框、滑块等20+组件 |
| HTTP客户端 | Axios | 1.15 | 双实例(base+root)、拦截器、无感刷新 |
| Markdown | markdown-it | 14.1 | AI回复Markdown渲染(html:false, linkify:true) |
| XSS防护 | DOMPurify | 3.4 | 净化HTML输出防XSS |
| SSE解析 | fetch + ReadableStream | — | 手动解析SSE事件流(支持POST+Auth header) |

### 2.3 项目目录结构

```
d:\EviMind\
├── src/main/java/com/example/evimind/      # Java 后端源码
│   ├── EvimindApplication.java             # Spring Boot 启动类
│   ├── auth/                               # 认证授权 (4个文件)
│   │   ├── AuthController.java             # 注册/登录/刷新/获取用户 REST API
│   │   ├── AuthService.java (140行)        # BCrypt验证、双令牌生成、令牌轮换
│   │   ├── TokenProvider.java (72行)       # JWT生成/解析/验证，HMAC-SHA256
│   │   └── JwtAuthenticationFilter.java (63行) # OncePerRequestFilter，上下文注入
│   ├── assistant/                          # AI Agent工具 + 会话管理 (3个文件)
│   │   ├── AgentTools.java                 # Agent工具定义
│   │   ├── ConversationController.java (118行) # SSE流式对话 + 会话导出
│   │   └── ConversationService.java (301行) # 会话管理+流式拦截+摘要+标题生成
│   ├── config/                             # 配置类 (17个文件)
│   │   ├── AiConfig.java (158行)           # Map<String,ChatClient> + DeepSeek思考模式
│   │   ├── AiProperties.java               # 四家AI提供商配置映射
│   │   ├── SecurityConfig.java (80行)      # Security过滤链、CORS、CSP、BCrypt
│   │   ├── PromptTemplateManager.java (67行) # @PostConstruct加载+渲染.st模板
│   │   ├── AsyncConfig.java                # 异步线程池(10核心/50最大)
│   │   ├── ElasticsearchConfig.java        # ES Java Client配置
│   │   ├── MinioConfig.java                # MinIO Client Bean
│   │   ├── MyBatisPlusConfig.java          # 分页拦截器(PostgreSQL方言)
│   │   ├── MyBatisMetaObjectHandler.java   # 自动填充createdAt/updatedAt
│   │   └── JwtConfig.java                  # JWT密钥+过期时间配置
│   ├── controller/                         # REST Controller (5个文件)
│   ├── common/                             # 全局异常处理 + 健康检查
│   ├── document/                           # 文档管理 (2个文件)
│   │   ├── DocumentController.java         # 上传/列表/删除/重试 REST API
│   │   └── DocumentService.java (155行)    # 18格式校验+50MB限制+异步入库
│   ├── extractor/                          # 文件内容提取器 (策略模式, 6+文件)
│   │   ├── FileContentExtractor.java       # 抽象策略接口
│   │   ├── FileExtractorService.java       # Spring自动收集所有实现
│   │   ├── PdfFileExtractor.java           # PDFBox 3.0.1
│   │   ├── WordFileExtractor.java          # POI XWPFDocument
│   │   ├── ExcelFileExtractor.java         # POI XSSFWorkbook
│   │   ├── TextFileExtractor.java          # 直接读取文本
│   │   └── metadata/
│   │       └── AcademicPdfMetadataExtractor.java # DOI/作者/年份/期刊提取
│   ├── identity/                           # 用户上下文
│   │   ├── GroupContext.java (40行)        # 三个独立ThreadLocal + isAdmin()
│   │   └── GroupService.java               # 默认组织创建/查询
│   ├── ingestion/                          # ETL文档摄入流水线 (5个文件)
│   │   ├── EtlPipeline.java (203行)        # 全流程状态机+学术元数据+文档摘要
│   │   ├── TextCleaner.java (44行)         # 3正则清洗+短行移除
│   │   ├── DocumentChunker.java (164行)    # 3策略+智能Overlap边界
│   │   ├── EmbeddingService.java (103行)   # 批量100条+pgvector格式化
│   │   └── ElasticsearchIndexService.java (98行) # Bulk API批量索引
│   ├── knowledgebase/                      # 知识库管理 (2个文件)
│   │   ├── KnowledgeBaseController.java    # CRUD + 成员管理 REST API
│   │   └── KnowledgeBaseService.java (154行) # 权限模型(OWNER/MEMBER/ADMIN)
│   ├── mapper/                             # MyBatis-Plus Mapper (12个)
│   │   ├── DocumentChunkEmbeddingMapper.java # pgvector余弦相似度SQL
│   │   ├── DocumentMapper / DocumentChunkMapper / ...
│   │   └── ConversationMapper / MessageMapper / KbMemberMapper / ...
│   ├── model/                              # 数据模型
│   │   ├── dto/                            # StreamEvent, AuthResponse, ApiResponse...
│   │   └── entity/                         # User, Document, Conversation, Message...
│   ├── qa/                                 # RAG问答核心
│   │   ├── RagPipeline.java (296行)        # 完整RAG管线+证据评估+引用构建
│   │   ├── EvidencePortfolioSelector.java (174行) # 贪心选择+CJK bigram+Jaccard
│   │   └── RagResponse.java                # EvidenceStatus枚举+答案+引用
│   ├── repository/                         # JPA Repository
│   ├── retrieval/                          # 检索引擎 (6个文件)
│   │   ├── HybridSearchService.java (83行) # CompletableFuture并行+超时+降级
│   │   ├── PgVectorSearchService.java (68行) # 向量搜索+降级
│   │   ├── ElasticsearchSearchService.java (85行) # bool查询+volatile日志抑制
│   │   ├── SimpleKeywordSearchService.java # 本地关键词降级
│   │   ├── RrfFusionService.java (132行)   # RRF融合+Min-Max归一化
│   │   └── SearchResult.java               # 统一搜索结果模型
│   ├── service/                            # 业务服务 (10+文件)
│   └── storage/                            # 对象存储
│       ├── MinioStorageService.java        # MinIO S3兼容存储
│       └── LocalFileStorageService.java    # 本地文件系统(dev)
├── src/main/resources/
│   ├── application.yml                     # 生产配置(4家AI+RAG参数)
│   ├── application-standalone.yml          # 本地dev配置(H2+本地存储)
│   ├── db/migration/                       # Flyway SQL迁移 (7个版本化脚本)
│   └── prompts/                            # 6个Prompt模板 (.st文件)
├── frontend/                               # Vue 3 前端
│   └── src/
│       ├── api/                            # 7个API调用模块
│       ├── views/                          # 7个页面视图
│       ├── stores/                         # Pinia状态管理 (3个组合式API)
│       ├── types/                          # TypeScript类型定义 (9个模块)
│       ├── components/                     # AppShell布局 + HelloWorld
│       ├── router/                         # 路由配置(导航守卫)
│       └── utils/                          # Axios双实例封装 + token无感刷新
├── docker-compose.yml                      # 5服务编排+healthcheck
├── Dockerfile                              # 后端多阶段构建(JDK→JRE)
└── pom.xml                                 # Maven项目配置
```

---

## 3. 核心功能实现

### 3.1 文档自动 ETL 流水线

**核心类**：`EtlPipeline.java`（203行）

ETL（Extract-Transform-Load）流水线负责将上传文档转化为可检索的知识片段，是整个 RAG 系统的数据基石。

#### 3.1.1 完整流程

```
用户上传文件 → DocumentService.upload()
    → requireKbMember(kbId) 权限校验
    → 校验格式(18种白名单) + 大小(≤50MB)
    → 存储路径: {kbId}/{timestamp}_{originalName}
    → MinIO(生产) / LocalFileStorage(dev)
    → 插入document表(ingestion_status=PENDING, chunk_count=0)
    → @Async("analysisTaskExecutor") 异步触发

EtlPipeline.processDocument(documentId):
    → EXTRACTING: 从存储下载到临时文件，策略模式调度提取器
        ├─ .pdf  → PdfFileExtractor (PDFBox 3.0.1)
        ├─ .doc/.docx → WordFileExtractor (POI XWPFDocument)
        ├─ .xls/.xlsx → ExcelFileExtractor (POI XSSFWorkbook)
        └─ .csv/.json/.txt/.md/.py/.java/.sql/.xml/.yaml/.log/.tex/.markdown → TextFileExtractor
        ├─ PDF额外: AcademicPdfMetadataExtractor 提取DOI/作者/年份/期刊
    → CLEANING: TextCleaner 正则清洗
        ├─ 生成文档摘要（调用LLM，取前3000字符，200字以内简介）
    → CHUNKING: DocumentChunker 按知识库配置切片(3种策略)
    → EMBEDDING: EmbeddingService 批量调用Embedding API(每批100条)
        ├─ 格式化为pgvector格式 [0.1,0.2,...]
        └─ saveBatch(allEmbeddings, 100) + updateBatchById(chunks, 100)
    → INDEXING: ElasticsearchIndexService Bulk API批量写入ES索引
    → COMPLETED: 更新chunk_count

任一步骤异常 → FAILED（try-catch 全局捕获）
```

#### 3.1.2 文件提取器（策略模式详解）

```java
// 抽象策略接口
public interface FileContentExtractor {
    boolean supports(String fileName);     // 是否支持该文件格式
    ExtractionResult extract(Path filePath, int maxSize);  // 提取内容
}

// Spring自动收集所有实现到List<FileContentExtractor>
@Service
public class FileExtractorService {
    private final List<FileContentExtractor> extractors;  // Spring自动注入所有实现
    public ExtractionResult extractFile(Path file, int maxSize) {
        for (FileContentExtractor ext : extractors) {
            if (ext.supports(file.getFileName().toString())) {
                return ext.extract(file, maxSize);
            }
        }
        // 无匹配提取器 → 返回失败
    }
}
```

| 提取器 | 支持格式 | 底层库 | 特殊处理 |
|--------|---------|--------|---------|
| `PdfFileExtractor` | .pdf | Apache PDFBox 3.0.1 | 配合AcademicPdfMetadataExtractor提取学术元数据 |
| `WordFileExtractor` | .docx | Apache POI 5.2.5 | XWPFDocument解析 |
| `ExcelFileExtractor` | .xlsx | Apache POI 5.2.5 | XSSFWorkbook逐行读取 |
| `TextFileExtractor` | .csv/.json/.txt/.md/.py/.java/.sql/.xml/.yaml/.yml/.log/.tex/.markdown | JDK直接读取 | 零依赖 |

**允许的文件格式白名单（18种）**：
```java
private static final Set<String> ALLOWED_FORMATS = Set.of(
    "pdf", "xlsx", "xls", "docx", "doc", "csv", "json", "md", "txt",
    "py", "java", "sql", "xml", "yaml", "yml", "log", "tex", "markdown"
);
```

#### 3.1.3 文本清洗（TextCleaner 详解）

```java
// 三个预编译正则 Pattern
CONTROL_CHARS  = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");  // ASCII控制字符
MULTIPLE_SPACES = Pattern.compile(" {3,}");    // 3个及以上连续空格 → 替换为单空格
MULTIPLE_NEWLINES = Pattern.compile("\n{3,}"); // 3个及以上连续换行 → 替换为双换行

// 清洗流程:
cleaned = CONTROL_CHARS.matcher(text).replaceAll("");     // 移除控制字符
cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" "); // 压缩空格
cleaned = MULTIPLE_NEWLINES.matcher(cleaned).replaceAll("\n\n"); // 压缩换行
cleaned = cleaned.trim();                                  // 去除首尾空白
cleaned = removeShortLines(cleaned, 10);                   // 移除<10字符的短行（保留空行）
```

`removeShortLines()` 逻辑：逐行扫描，`trimmed.length() < minLineLength && !trimmed.isEmpty()` 的行被移除。这能有效过滤PDF提取产生的页眉页脚噪声。

#### 3.1.4 切片策略详解（DocumentChunker 164行）

使用 Java 22 switch expression 分派三种策略：

```java
return switch (config.getStrategy()) {
    case FIXED_LENGTH -> chunkFixedLength(text, config);
    case PARAGRAPH    -> chunkByParagraph(text, config);
    case SEMANTIC     -> chunkSemantic(text, config);
};
```

| 策略 | 切分方式 | 实现细节 | 适用场景 |
|------|---------|---------|---------|
| FIXED_LENGTH | 固定字符窗口 | `start = end - overlap` 滑动窗口 | 格式不规整文本 |
| PARAGRAPH | 双换行符分隔 | `text.split("\\n\\s*\\n")` 按段落聚合 | 结构化文档 |
| SEMANTIC | 句子聚合 | 正则分句 `[^。！？.!?\\n]+[。！？.!?\\n]*`，按句子累加到chunkSize | 高质量语义场景 |

**智能Overlap边界算法**（`getOverlapText()`，三种策略共用）：

```java
// 目标切割点 = text.length() - overlapSize
// 在目标点前后寻找最近的句子边界: . 。! ！? ？ \n
int breakBefore = -1;  // 向前搜索最近的句子结束符
for (int i = targetStart; i >= 0; i--) { ... }

int breakAfter = -1;   // 向后搜索最近的句子结束符  
for (int i = targetStart; i < text.length(); i++) { ... }

// 选距离更近的边界（避免在句子中间断开）
if ((targetStart - breakBefore) <= (breakAfter - targetStart))
    bestStart = breakBefore + 1;
else
    bestStart = breakAfter + 1;

// 安全阀：如果偏移导致内容太少（<max(10, overlap*0.2)），回退到精确切割点
if (text.length() - bestStart < Math.max(10, overlapSize * 0.2))
    bestStart = targetStart;
```

#### 3.1.5 向量化（EmbeddingService 103行）

```java
// 批量处理，每批100条
final int BATCH_SIZE = 100;
for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
    List<String> texts = batchChunks.stream().map(DocumentChunk::getContent).toList();
    List<List<Double>> vectors = embeddingModel.embed(texts);  // Spring AI EmbeddingModel
    
    // 格式化为pgvector格式: [0.123,0.456,...,0.789]
    String vectorStr = formatPgVector(vector);  // StringBuilder拼接
    emb.setEmbedding(vectorStr);
}

// 批量写入数据库
documentChunkEmbeddingService.saveBatch(allEmbeddings, 100);
// 更新chunk的vectorId引用
documentChunkService.updateBatchById(chunks, 100);
```

- **EmbeddingModel 为 null 时降级**：`@Autowired(required = false)`，无Embedding API时跳过，warn日志
- **维度配置**：`custom.ai.embedding.dimension=1536`，通过 `@ConditionalOnProperty` 条件创建

#### 3.1.6 Elasticsearch 索引写入（ElasticsearchIndexService 98行）

```java
// ES Bulk API 批量索引，一次请求写入所有切片
List<BulkOperation> operations = new ArrayList<>();
for (DocumentChunk chunk : chunks) {
    String chunkId = chunk.getId() != null
        ? "chunk_" + chunk.getId()                    // 有DB ID时用ID
        : "chunk_" + documentId + "_" + chunkIndex;   // 无ID时用组合键
    Map<String, Object> doc = Map.of(
        "content", chunk.getContent(),
        "knowledgeBaseId", knowledgeBaseId,
        "documentId", documentId,
        "chunkIndex", chunkIndex,
        "chunkId", chunkId
    );
    operations.add(BulkOperation.of(b -> b.index(idx -> idx.index("document_chunk").id(chunkId).document(doc))));
}
elasticsearchClient.bulk(BulkRequest.of(b -> b.operations(operations)));

// 删除操作：deleteByQuery
deleteByDocumentId(Long documentId)        // term(documentId, value)
deleteByKnowledgeBaseId(Long knowledgeBaseId) // term(knowledgeBaseId, value)
```

#### 3.1.7 文档摘要自动生成

ETL 在 CLEANING 阶段后，自动调用 LLM 生成文档简介：

```java
String contextText = cleanedText.substring(0, Math.min(3000, cleanedText.length()));
String summary = chatClient.prompt()
    .system("你是一个专业的文档分析助手。请根据提供的文档开头内容，提取并凝练出一份简洁的文档简介（控制在200字以内）。...")
    .user(contextText)
    .call()
    .content();
doc.setSummary(summary.trim());
```

#### 3.1.8 级联删除

```java
// EtlPipeline.deleteDocument() 级联删除全部关联数据
documentChunkMapper.delete(documentId)       // 删除所有切片
embeddingService.deleteByDocumentId(id)      // 删除向量(embedding表)
elasticsearchIndexService.deleteByDocumentId(id)  // 删除ES索引
minioStorageService.deleteFile(storagePath)  // 删除物理文件
documentMapper.deleteById(id)                // 删除文档记录
```

### 3.2 混合检索系统

**核心类**：`HybridSearchService.java`（83行）

混合检索是 RAG 检索质量的关键保障，通过语义检索和关键词检索的互补，最大化召回率。

#### 3.2.1 并行调度与超时控制

```java
// candidateK = max(topK, min(50, topK × 3))  给RRF更多候选
int candidateK = Math.max(requestedTopK, Math.min(50, requestedTopK * 3));

// CompletableFuture 并行发起两路检索
CompletableFuture<List<SearchResult>> semanticFuture = 
    CompletableFuture.supplyAsync(() -> pgVectorSearchService.search(query, kbId, candidateK));
CompletableFuture<List<SearchResult>> keywordFuture = 
    CompletableFuture.supplyAsync(() -> elasticsearchSearchService.search(query, kbId, candidateK));

// 等待结果，1500ms超时（可配置: custom.rag.search.backend-timeout-ms）
private List<SearchResult> awaitResults(String backendName, CompletableFuture future) {
    try {
        return future.get(backendTimeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        future.cancel(true);  // ★ 超时后取消任务，释放资源
        return List.of();
    }
}
```

#### 3.2.2 两路检索对比

| 搜索方式 | 实现类 | 原理 | 优势 | 劣势 |
|---------|--------|------|------|------|
| 语义检索 | `PgVectorSearchService` (68行) | query→EmbeddingModel转向量→pgvector `<=>` 余弦距离 | 理解同义词、语义意图 | 可能漏掉精确关键词 |
| 关键词检索 | `ElasticsearchSearchService` (85行) | ES bool查询: must(match content) + filter(term knowledgeBaseId) | 精确匹配专有名词、编号 | 不懂同义词和语境 |

**PgVector 语义检索SQL**：

```sql
SELECT dce.chunk_id, dce.knowledge_base_id, dc.document_id, dc.content, 
       dc.chunk_index, 1 - (dce.embedding <=> #{queryEmbedding}::vector) AS score
FROM document_chunk_embedding dce
JOIN document_chunk dc ON dc.id = dce.chunk_id
WHERE dce.knowledge_base_id = #{knowledgeBaseId}
ORDER BY dce.embedding <=> #{queryEmbedding}::vector
LIMIT #{limit}
```

- `<=>` 是 pgvector 余弦距离操作符，`1 - distance` 转为相似度分数
- PgVectorSearchService 在 EmbeddingModel 为 null 时返回空（降级）

**Elasticsearch BM25 查询**：

```java
elasticsearchClient.search(s -> s
    .index("document_chunk")
    .size(topK)
    .query(q -> q.bool(b -> b
        .must(m -> m.match(t -> t.field("content").query(query)))      // BM25 全文匹配
        .filter(f -> f.term(t -> t.field("knowledgeBaseId").value(kbId))) // 知识库隔离
    )), Map.class);
```

#### 3.2.3 ES volatile 日志抑制机制

```java
private volatile boolean esUnavailableLogged = false;

catch (Exception e) {
    if (!esUnavailableLogged) {
        log.warn("Elasticsearch unavailable... Subsequent failures will be suppressed.");
        esUnavailableLogged = true;   // 首次打warn
    } else {
        log.debug("Elasticsearch search failed: ...");  // 后续降为debug，避免日志洪泛
    }
    return List.of();
}
// 连接恢复时重置: esUnavailableLogged = false;
```

使用 `volatile` 保证多线程可见性，首次失败打 warn 级别日志，后续抑制为 debug，避免 ES 长期不可用时日志洪泛。

#### 3.2.4 三级降级策略

```
Level 0: PgVector ∥ ES 正常 → RRF融合两路结果
Level 1: PgVector失败/超时 → 仅用ES结果做RRF（单路）
Level 1: ES失败/超时 → 降级到SimpleKeywordSearch(本地DB LIKE查询) → 与PgVector结果RRF融合
Level 2: ES结果为空 → SimpleKeywordSearch降级 → 仍可用语义结果
Level 3: 两路都失败/空 → 返回空 → RagPipeline走证据不足分支
```

### 3.3 RRF（倒数排名融合）算法

**核心类**：`RrfFusionService.java`（132行）

RRF（Reciprocal Rank Fusion）是本项目检索融合的核心算法，解决了不同检索系统分数不可比的问题。

#### 3.3.1 算法原理

不同检索后端的分数尺度完全不同（PgVector 的余弦相似度 ∈ [0,1] vs ES 的 BM25 分数 ∈ [0,∞)），无法直接相加。RRF 的核心思想是：**排名是可比的——第一名就是第一名，不管分数是多少**。

#### 3.3.2 完整实现步骤

```
Step 1: Min-Max归一化（统一两路分数的值域到[0,1]）
  normalized = (score - min) / (max - min)
  特殊处理: 当 max == min 时（所有分数相同），全部归一化为 1.0

Step 2: 各自按归一化分数降序排名（rank从1开始）

Step 3: 用 fusionKey 去重
  fusionKey = documentId + "#" + chunkIndex  （同一切片在两路中可能都出现）
  使用 LinkedHashMap.computeIfAbsent() 保证同一切片只创建一个RrfEntry

Step 4: 累加RRF分数
  rrfScore += 1.0 / (k + rank)     k=60（经验最优值）
  同时记录: normalizedScoreSum += normalizedScore, sourceHits++

Step 5: 计算最终置信度（RrfEntry.calculateConfidence()）
  idealRrf = activeSources × (1.0 / (k + 1))   // 理想最大RRF分数
  rankConfidence = rrfScore / idealRrf          // 排名置信度 ∈ [0,1]
  sourceConfidence = normalizedScoreSum / sourceHits  // 原始分数置信度
  score = clamp(0.75 × rankConfidence + 0.25 × sourceConfidence)

Step 6: 按score降序取topN
```

#### 3.3.3 k=60 的效果

```
rank=1  → 1/61 ≈ 0.01639
rank=2  → 1/62 ≈ 0.01613  (差距: 0.00026)
rank=10 → 1/70 ≈ 0.01429
rank=50 → 1/110≈ 0.00909
rank=100→ 1/160≈ 0.00625
```

排名越靠前权重越大，但差距平滑——第1名和第2名差距极小（0.00026），避免单一排名主导结果。

#### 3.3.4 RrfEntry 内部类

```java
private static class RrfEntry {
    final SearchResult result;
    double rrfScore = 0.0;          // 累积RRF分数
    double normalizedScoreSum = 0.0; // 归一化原始分数和
    int sourceHits = 0;             // 被几个检索源命中
    double score = 0.0;             // 最终综合得分

    void addRank(int rank, int k, double normalizedScore) {
        rrfScore += 1.0 / (k + rank);
        normalizedScoreSum += clamp(normalizedScore);
        sourceHits++;
    }

    void calculateConfidence(double idealRrf) {
        double rankConfidence = idealRrf > 0.0 ? rrfScore / idealRrf : 0.0;
        double sourceConfidence = sourceHits > 0 ? normalizedScoreSum / sourceHits : 0.0;
        score = clamp(0.75 * rankConfidence + 0.25 * sourceConfidence);
    }
}
```

**双被命中的优势**：如果一个切片同时被语义和关键词检索命中（sourceHits=2），它的 rankConfidence 和 sourceConfidence 都会更高，自然排在只有单路命中的结果前面。

### 3.4 证据充分性判断与引用溯源

**核心类**：`RagPipeline.java`（296行）、`EvidencePortfolioSelector.java`（174行）

#### 3.4.1 证据充分性门控

RagPipeline 在检索后、生成前进行证据质量评估，只有达到阈值才允许模型回答：

```java
private boolean hasSufficientEvidence(List<SearchResult> results, BigDecimal threshold) {
    if (results.isEmpty()) return false;
    if (threshold == null) return true;  // 未配置阈值则默认通过
    return evidenceConfidence(results) >= threshold.doubleValue();
}

private double evidenceConfidence(List<SearchResult> results) {
    double topScore = ranked.get(0).getScore();                    // 最高分证据
    int supportCount = Math.min(3, ranked.size());                 // 最多取前3条
    double topSupportAverage = ranked.stream().limit(supportCount) // 前N条平均分
        .mapToDouble(SearchResult::getScore).average().orElse(0.0);
    return Math.max(0.0, Math.min(1.0, 
        0.70 * topScore + 0.30 * topSupportAverage));             // clamp到[0,1]
}
```

**为什么不只看top1**：只看最高分容易被偶然命中误导。加入前几条的平均支持度，能判断是否有多条证据共同支持，降低"检索到一条弱相关材料就强行回答"的风险。

**三种证据状态**：

| 状态 | 条件 | 行为 |
|------|------|------|
| `NO_RESULTS` | results为空 | 返回证据不足模板消息 |
| `INSUFFICIENT` | confidence < threshold | 返回证据不足模板消息 |
| `SUFFICIENT` | confidence >= threshold | 继续Portfolio选择→生成 |

#### 3.4.2 Evidence Portfolio 证据组合选择（174行）

不是简单把 topK 全塞进 prompt，而是在上下文预算内选择更有价值的一组证据：

**打分公式**：
```java
value = 0.62 × confidence          // 检索置信度（RRF融合后的分数）
      + 0.22 × coverageGain        // 对问题词的覆盖增益（新覆盖词数/总词数）
      + 0.12 × diversityBonus      // 文档多样性（新文档=1.0, 同文档=0.0）
      - 0.24 × redundancyPenalty   // 冗余惩罚（与已选的最大Jaccard相似度）
```

**选择算法**：

```
1. 去重: documentId#chunkIndex 相同取最高分（LinkedHashMap）
2. 预处理: 对每个候选 tokenize(content) + estimateBlockChars(content+96)
3. 贪心迭代:
   a. 遍历所有未选候选，计算scoreCandidate()
   b. 跳过超预算的候选
   c. 跳过 coverageGain==0 && diversityBonus==0 的候选（无新信息）
   d. 选value最高的候选加入selected
   e. 更新coveredTerms, selectedDocs, usedChars
4. 停止条件:
   - 边际价值 < 0.40（MIN_MARGINAL_VALUE_AFTER_FIRST）→ 避免凑数
   - usedChars >= budget → 超预算
   - 无更多候选
```

**CJK Bigram 分词**（支持中文查询的覆盖增益计算）：

```java
private void addCjkNgrams(String raw, Set<String> terms) {
    StringBuilder cjk = new StringBuilder();
    for (char ch : raw.toCharArray()) {
        if (isCjk(ch)) cjk.append(ch);  // HAN/HIRAGANA/KATAKANA/HANGUL
    }
    for (int i = 0; i + 1 < cjk.length(); i++) {
        terms.add(cjk.substring(i, i + 2));  // 连续二字组
    }
}
```

**Jaccard 冗余度计算**：

```java
jaccard(A, B) = |A ∩ B| / |A ∪ B|
```

取候选与所有已选候选的最大 Jaccard 相似度作为冗余惩罚。

**证据块格式化**：

```java
// 每条证据在prompt中的格式
"[来源1] 文档ID=5 切片#3 置信度=0.921 检索源=rrf_fused\n{content}\n\n"
// 字符预算: maxEvidenceContextChars 默认6000，最小400
// 超预算时截断最后一条，保证不超prompt token限制
```

#### 3.4.3 引用溯源

```java
// buildCitations(): 从最终选入portfolio的证据构建引用
Citation { documentId, fileName, chunkIndex, score }
// fileName通过批量查询document表获取（selectBatchIds）
// 引用来自portfolio而非所有候选，确保引用与实际证据一致
```

### 3.5 JWT 双令牌认证

**核心类**：`JwtAuthenticationFilter.java`（63行）、`TokenProvider.java`（72行）、`AuthService.java`（140行）

#### 3.5.1 双令牌设计

| 维度 | Access Token | Refresh Token |
|------|-------------|---------------|
| 生命周期 | 1小时 (`jwtConfig.getAccessTokenExpiration()`) | 7天 (`jwtConfig.getRefreshTokenExpiration()`) |
| 传输频率 | 每次请求 | 仅刷新时 |
| 验证方式 | 本地HMAC-SHA256验签（无状态） | 查数据库SHA-256哈希（有状态） |
| 存储方式 | 明文JWT格式 | 只存SHA-256哈希(token_hash VARCHAR(256) UNIQUE) |
| 泄露应对 | 短有效期限制危害 | 服务端可撤销(revoked字段) |
| 内容 | subject=userId, claims: username+systemRole | UUID.randomUUID() |

#### 3.5.2 TokenProvider 完整实现

```java
// JWT签名密钥: HMAC-SHA，密钥从jwtConfig.getSecret()读取
private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
}

// 生成Access Token
public String generateAccessToken(Long userId, String username, String systemRole) {
    return Jwts.builder()
        .subject(userId.toString())           // subject = userId
        .claim("username", username)          // 自定义claim
        .claim("systemRole", systemRole)      // 自定义claim: USER/ADMIN
        .issuedAt(now)                        // 签发时间
        .expiration(new Date(now.getTime() + jwtConfig.getAccessTokenExpiration()))
        .signWith(getSigningKey())            // HMAC-SHA256签名
        .compact();
}

// 生成Refresh Token: 纯随机UUID（不含JWT结构）
public String generateRefreshToken() {
    return java.util.UUID.randomUUID().toString();
}

// 解析+验证: 验签+检查过期，失败抛JwtException
public Claims parseToken(String token) {
    return Jwts.parser().verifyWith(getSigningKey()).build()
        .parseSignedClaims(token).getPayload();
}

// 安全验证: 捕获所有JwtException，返回boolean
public boolean validateToken(String token) {
    try { parseToken(token); return true; }
    catch (JwtException | IllegalArgumentException e) { return false; }
}
```

#### 3.5.3 AuthService 令牌轮换机制

```java
@Transactional
public AuthResponse refreshToken(RefreshTokenRequest request) {
    // 1. SHA-256哈希查找
    String tokenHash = hashToken(request.getRefreshToken());
    RefreshToken rt = refreshTokenMapper.selectOne(
        new LambdaQueryWrapper<RefreshToken>()
            .eq(RefreshToken::getTokenHash, tokenHash)
            .eq(RefreshToken::getRevoked, false)  // 必须未撤销
    );
    
    // 2. 检查过期+撤销状态
    if (rt == null || rt.getExpiresAt().isBefore(LocalDateTime.now()))
        throw new IllegalArgumentException("Invalid or expired refresh token");
    
    // 3. 旧token设为revoked（令牌轮换：每次刷新后旧token失效）
    rt.setRevoked(true);
    refreshTokenMapper.updateById(rt);
    
    // 4. 检查用户状态（DISABLED用户不允许刷新）
    User user = userMapper.selectById(rt.getUserId());
    if (user == null || "DISABLED".equals(user.getStatus()))
        throw new IllegalArgumentException("User not found or disabled");
    
    // 5. 生成全新双令牌（Access+Refresh都是新的）
    return generateAuthResponse(user);
}

// SHA-256哈希实现
private String hashToken(String token) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : hash) sb.append(String.format("%02x", b));
    return sb.toString();  // 64字符十六进制字符串
}
```

**令牌轮换安全特性**：每次刷新都会废弃旧 Refresh Token 并生成新的。如果攻击者窃取了旧的 Refresh Token，在使用一次后就会被标记为 revoked，后续使用会失败。

#### 3.5.4 GroupContext ThreadLocal 上下文

```java
public class GroupContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> GROUP_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SYSTEM_ROLE = new ThreadLocal<>();
    
    public static void set(Long userId, Long groupId, String systemRole) { ... }
    public static Long getUserId() { return USER_ID.get(); }
    public static boolean isAdmin() { return "ADMIN".equals(SYSTEM_ROLE.get()); }
    public static void clear() {
        USER_ID.remove();    // ★ 使用remove()而非set(null)，防止内存泄漏
        GROUP_ID.remove();
        SYSTEM_ROLE.remove();
    }
}
```

三个独立 ThreadLocal 而非一个包装对象，减少对象创建开销。`clear()` 在 `JwtAuthenticationFilter.doFilterInternal()` 的 `finally` 块中调用，确保线程池复用时不泄漏。

#### 3.5.5 JwtAuthenticationFilter 完整流程

```java
protected void doFilterInternal(...) {
    try {
        // 1. 提取Bearer token（去掉"Bearer "前缀）
        String token = extractToken(request);  // Authorization: Bearer <token>
        
        // 2. 验证+解析
        if (token != null && tokenProvider.validateToken(token)) {
            Long userId = tokenProvider.getUserIdFromToken(token);
            String username = tokenProvider.getUsernameFromToken(token);
            String systemRole = tokenProvider.getSystemRoleFromToken(token);
            
            // 3. 获取/创建默认组织
            Long groupId = groupService.getOrCreateDefaultGroupId(userId, username);
            
            // 4. 注入双上下文
            GroupContext.set(userId, groupId, systemRole);              // ThreadLocal
            SecurityContextHolder.getContext().setAuthentication(       // Spring Security
                new UsernamePasswordAuthenticationToken(username, null, 
                    List.of(new SimpleGrantedAuthority("ROLE_" + systemRole))));
        }
        
        // 5. 继续过滤链
        filterChain.doFilter(request, response);
    } finally {
        GroupContext.clear();  // ★ 必须清理，防止线程池复用时内存泄漏
    }
}
```

### 3.6 流式 SSE 对话与多模型热切换

#### 3.6.1 流式对话后端实现

**ConversationController**（118行）：

```java
@PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamMessage(@PathVariable Long id, @RequestBody MessageRequest request) {
    return conversationService.streamMessage(id, request.getContent(),
        request.getTemperature(), request.getTopP(), request.getMaxTokens(),
        request.getModelName(), request.getThinking(), request.getReasoningEffort());
}
```

**ConversationService.streamMessage()（核心，301行中的关键逻辑）**：

```java
public Flux<String> streamMessage(Long conversationId, String content, ...) {
    // ① 校验会话所有权
    Conversation conv = requireConversationOwner(conversationId);
    
    // ② 校验知识库成员权限
    if (!ragPipeline.isKbMember(conv.getKnowledgeBaseId()))
        return Flux.just(StreamEvent.error("Access denied..."));
    
    // ③ 保存用户消息
    Message userMsg = new Message();
    userMsg.setRole("user"); userMsg.setContent(content);
    messageMapper.insert(userMsg);
    
    // ④ 调用RAG管线获取Flux流
    StringBuilder fullContent = new StringBuilder();   // 拼接完整回答
    String[] citationsHolder = new String[1];           // 暂存引用JSON
    
    return ragPipeline.streamQuery(content, kbId, modelProvider, ...)
        .map(event -> {
            // ⑤ 拦截每个SSE事件，提取内容
            Map<String, Object> map = objectMapper.readValue(event, Map.class);
            if ("token".equals(type)) {
                fullContent.append(text);               // 逐token拼接
            } else if ("citations".equals(type)) {
                citationsHolder[0] = citations JSON;    // 暂存引用
            } else if ("done".equals(type)) {
                saveAssistantMessage(conversationId, fullContent, citations);  // 保存完整回答
            }
            return event;  // 原样转发给前端
        })
        .doOnComplete(() -> {
            // ⑥ 流完成时：确保保存 + 检查是否需要生成摘要
            if (fullContent.length() > 0) saveAssistantMessage(...);
            checkAndGenerateSummary(conversationId);
        })
        .doOnError(e -> log.error("Stream error..."));
}
```

**关键设计**：使用 `Flux.map()` 拦截流式事件而非 `doOnNext()`，因为需要在 map 中同时完成内容拼接和事件转发。`doOnComplete()` 作为兜底，防止 `done` 事件未触发时消息丢失。

#### 3.6.2 SSE 事件类型（StreamEvent 50行）

```java
// 4种事件工厂方法，使用Java Record序列化
record TokenEvent(String type, String text) {}       // {"type":"token","text":"根据"}
record CitationEvent(String type, List<Citation> citations) {}  // {"type":"citations","citations":[...]}
record ErrorEvent(String type, String message) {}    // {"type":"error","message":"..."}
// done事件直接拼JSON: {"type":"done","messageId":42}

// 安全降级: Jackson序列化失败时用手动escapeJson拼接
private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
}
```

#### 3.6.3 RagPipeline 流式生成

```java
return promptSpec
    .stream()                    // Spring AI 流式调用
    .content()                   // 获取Flux<String>
    .map(StreamEvent::token)     // 每个token包装为SSE事件
    .concatWithValues(           // 末尾追加引用和完成事件
        citationsJson,           // 所有引用一次发送
        StreamEvent.done(null)   // 完成标记
    );
```

**模型参数动态注入**：

```java
var optionsBuilder = OpenAiChatOptions.builder();
if (actualModelName != null) optionsBuilder.withModel(actualModelName);
if (temperature != null) optionsBuilder.withTemperature(temperature.floatValue());
if (topP != null) optionsBuilder.withTopP(topP.floatValue());
if (maxTokens != null) optionsBuilder.withMaxTokens(maxTokens);
```

#### 3.6.4 会话摘要自动生成

```java
private void checkAndGenerateSummary(Long conversationId) {
    Long count = messageMapper.selectCount(conversationId);
    if (count > SUMMARY_WINDOW * 2) {  // 消息数 > 20 时触发
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv.getSummary() == null || conv.getSummary().isBlank()) {
            // 取前10条消息（SUMMARY_WINDOW=10）
            List<Message> earlyMessages = ... LIMIT 10 ORDER BY createdAt ASC;
            
            // 渲染summary-prompt.st模板
            String summaryPrompt = promptTemplateManager.render("summary-prompt", vars);
            
            // 调用AI生成摘要（同步调用，非流式）
            String summary = chatClient.prompt().user(summaryPrompt).call().content();
            conv.setSummary(summary);
            conversationMapper.updateById(conv);
        }
    }
}
```

#### 3.6.5 会话标题自动生成

```java
public void generateAutoTitle(Long conversationId, String firstResponse) {
    String truncated = firstResponse.length() > 200 
        ? firstResponse.substring(0, 200) : firstResponse;
    String title = chatClient.prompt()
        .user("为以下AI助手的回答生成一个简短的标题（不超过20字，只返回标题文本，不要引号）:\n" + truncated)
        .call().content();
    // 仅在会话无标题时设置（避免覆盖用户手动修改的标题）
    if (conv.getTitle() == null) { conv.setTitle(title.trim()); }
}
```

#### 3.6.6 会话上下文构建

```java
public String buildContext(Long conversationId, String currentQuery) {
    StringBuilder context = new StringBuilder();
    // 1. 注入会话摘要（如果存在）
    if (conv.getSummary() != null) 
        context.append("[会话摘要]\n").append(conv.getSummary()).append("\n\n");
    
    // 2. 注入最近10条消息（SUMMARY_WINDOW=10）
    List<Message> recentMessages = ... ORDER BY createdAt DESC LIMIT 10;
    Collections.reverse(recentMessages);  // 恢复时间正序
    for (Message msg : recentMessages)
        context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
    
    // 3. 追加当前问题
    context.append("user: ").append(currentQuery);
    return context.toString();
}
```

#### 3.6.7 多模型热切换

**AiConfig.java**（158行）：

```java
// 启动时遍历配置，为每个provider创建ChatClient
Map<String, ChatClient> chatClients = new HashMap<>();
for (provider in [deepseek, zhipu, qianwen, openai]) {
    OpenAiApi api = new OpenAiApi(config.getBaseUrl(), config.getApiKey(), rcBuilder, wcBuilder);
    OpenAiChatModel model = new OpenAiChatModel(api, options);
    ChatClient client = ChatClient.builder(model).build();
    chatClients.put(providerName, client);
}
```

四种模型虽然 API 域名不同，但都兼容 OpenAI Chat Completion 格式，统一使用 `OpenAiApi` 作为底层客户端。运行时通过 `chatClients.get(provider)` 切换。

**DeepSeek 思考模式特殊处理**：AiConfig 中为 DeepSeek 注册了自定义 Jackson `BeanSerializerModifier`，拦截 `ChatCompletionRequest` 序列化过程。当模型名包含 `|thinking:enabled` 时：
- 自动在请求体中注入 `{"thinking": {"type": "enabled"}}` 节点
- 支持 `reasoning_effort` 参数（low/medium/high）

**模型名编码规则**（`buildModelName()`）：

```java
// 普通模式: "deepseek-chat"
// 思考模式: "deepseek-v4-pro|thinking:enabled"
// 思考+推理强度: "deepseek-v4-pro|thinking:enabled|effort:high"
```

### 3.7 前端流式接收与无感刷新

#### 3.7.1 fetch + ReadableStream 实现SSE

```typescript
// 使用fetch替代EventSource的原因:
// 1. EventSource只支持GET，我们需要POST发送消息体
// 2. EventSource不支持自定义header（需要Authorization: Bearer）
const response = await fetch(`/api/v1/conversations/${id}/messages/stream`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, temperature, topP, maxTokens, modelName, thinking })
})

const reader = response.body?.getReader()
const decoder = new TextDecoder()
// 逐块读取 → 按行拆分 → 识别data:前缀 → JSON.parse → 按type分发回调
// onToken: assistantMessage.content += text  (逐字追加)
// onCitations: assistantMessage.citations = [...]
// onDone: 标记完成
```

#### 3.7.2 Axios 无感令牌刷新

```typescript
// request.ts 中的401拦截器核心逻辑:
let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

// 响应拦截器:
if (error.response?.status === 401) {
    if (!isRefreshing) {
        isRefreshing = true
        // 用refreshToken请求新令牌
        const { accessToken, refreshToken } = await rootRequest.post('/auth/refresh', ...)
        localStorage.setItem('accessToken', accessToken)
        // 通知所有等待者
        refreshSubscribers.forEach(cb => cb(accessToken))
        refreshSubscribers = []
        isRefreshing = false
    }
    // 当前请求加入等待队列
    return new Promise(resolve => {
        refreshSubscribers.push(newToken => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(request(originalRequest))  // 用新token重试
        })
    })
}
```

**并发安全**：多个401同时发生时，`isRefreshing` 标志确保只发起一次刷新请求，其余请求排队等待新令牌后重试。

---

## 4. 代码架构分析

### 4.1 后端核心模块

#### 模块依赖关系

```
ConversationController → ConversationService → RagPipeline → HybridSearchService
                                                          → EvidencePortfolioSelector
                                                          → PromptTemplateManager
                                                          → chatClients Map

DocumentController → DocumentService → EtlPipeline → FileExtractorService
                                                  → TextCleaner
                                                  → DocumentChunker
                                                  → EmbeddingService
                                                  → ElasticsearchIndexService

KnowledgeBaseController → KnowledgeBaseService → kbMemberMapper

AuthController → AuthService → TokenProvider + UserMapper + RefreshTokenMapper
```

#### PromptTemplateManager 详解（67行）

```java
@PostConstruct
public void loadTemplates() {
    Resource[] resources = resolver.getResources("classpath:prompts/*.st");
    for (Resource resource : resources) {
        String name = filename.replace(".st", "");
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        templates.put(name, content);  // HashMap<String, String>
    }
}

public String render(String templateName, Map<String, Object> variables) {
    String templateContent = templates.get(templateName);
    PromptTemplate promptTemplate = new PromptTemplate(templateContent);  // Spring AI
    return promptTemplate.render(variables);  // 变量替换: {evidence} → 实际证据
}
```

- `@PostConstruct` 在Bean初始化后加载所有模板到内存HashMap
- 使用 Spring AI 的 `PromptTemplate` 渲染，支持 `{variable}` 占位符
- 模板文件与代码解耦，修改Prompt不需重新编译部署

### 4.2 前端核心模块

#### ChatView.vue 三栏布局（535行）

```
┌─────────────┬──────────────────────┬─────────────┐
│  左侧侧边栏  │      中间消息区        │  右侧证据面板 │
│  (300px)    │    (flex: 1fr)       │  (320px)    │
│             │                      │             │
│ 知识库选择   │  会话标题(双击重命名)  │ Evidence标题 │
│ 新建对话     │  消息列表(流式渲染)    │ 引用卡片列表 │
│ 搜索会话     │  输入区+发送按钮      │ (文件名+    │
│ 会话列表     │                      │  切片号+    │
│             │                      │  score)     │
│ 模型参数面板 │                      │             │
│ ├ 供应商选择 │                      │ 研究工作流   │
│ ├ 模型版本   │                      │ 说明        │
│ ├ 思维链开关 │                      │             │
│ ├ 推理强度   │                      │             │
│ ├ Temperature│                     │             │
│ ├ Top-P     │                      │             │
│ └ MaxTokens │                      │             │
│             │                      │             │
│ API接入信息  │                      │             │
└─────────────┴──────────────────────┴─────────────┘
```

**DeepSeek 专属UI**：当 `modelProvider === 'deepseek'` 时显示额外控件：
- 模型版本选择：deepseek-chat(V3) / deepseek-reasoner(R1) / deepseek-v4-pro / deepseek-v4-flash
- 思维链开关（Thinking）
- 推理强度选择（仅pro模型 + thinking开启时显示）：Low / Medium / High

**Markdown渲染**：
```typescript
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
function renderMarkdown(content: string): string {
    if (!content) return '<span class="streaming-cursor">生成中...</span>'
    return DOMPurify.sanitize(md.render(content))
}
```

**响应式布局**：
- `≥1280px`: 三栏布局 (300px + 1fr + 320px)
- `820px~1280px`: 两栏布局，证据面板折叠到底部
- `≤820px`: 单栏布局，全宽消息

#### Pinia 状态管理

```typescript
// chat.store.ts — 聊天核心状态（组合式API）
export const useChatStore = defineStore('chat', () => {
    const conversations = ref<Conversation[]>([])
    const currentConversation = ref<Conversation | null>(null)
    const messages = ref<ChatMessage[]>([])
    
    async function sendMessage(content, params) {
        // 1. push用户消息到messages数组
        // 2. push空assistant消息占位（content=''）
        // 3. 调用SSE streamMessage API
        //    onToken: assistantMessage.content += text  (逐字追加，Vue响应式自动更新)
        //    onCitations: assistantMessage.citations = [...]
        //    onDone: 标记完成 + loadConversations刷新列表（标题可能已自动生成）
    }
})
```

---

## 5. 数据库设计

### 5.1 实体关系模型

```
sys_user (用户)
  │
  ├──1:N── group_member ──N:1── sys_group (组织)
  │                                │
  │                                └──1:N── knowledge_base (知识库)
  │                                              │
  ├──1:N── kb_member ─────────────────────────────┤
  │                                                │
  ├──1:N── conversation (会话) ─────────────────────┤
  │            │                                    │
  │            └──1:N── message (消息)               │
  │                                                │
  ├──1:N── document (文档) ─────────────────────────┤
  │            │
  │            └──1:N── document_chunk (切片)
  │                          │
  │                          └──1:1── document_chunk_embedding (向量)
  │
  └──1:N── refresh_token (刷新令牌)
```

### 5.2 核心表设计

#### 用户与组织（3张）

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| `sys_user` | id, username(UNIQUE), password(BCrypt), system_role(ADMIN/USER), status(ACTIVE/DISABLED) | 用户账户 |
| `sys_group` | id, name, org_code(UNIQUE), creator_id(FK) | 组织/团队，多租户隔离 |
| `group_member` | group_id(FK), user_id(FK), role(MEMBER/OWNER), UNIQUE(group_id,user_id) | 组织成员关系 |

#### 知识库与权限（2张）

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| `knowledge_base` | id, name, group_id(FK), evidence_threshold(DECIMAL,默认0.50), chunk_strategy(默认PARAGRAPH), chunk_size(默认500), chunk_overlap(默认100), creator_id, status | 知识库配置 |
| `kb_member` | knowledge_base_id(FK), user_id(FK), role(MEMBER/OWNER), UNIQUE(kb_id,user_id) | 知识库级权限 |

#### 文档与索引（3张）

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| `document` | id, knowledge_base_id(FK), file_name, file_format, storage_path, ingestion_status(状态机7态), chunk_count, summary, doi, authors, publication_year, journal, uploader_id | 文档元数据+学术信息 |
| `document_chunk` | id, document_id(FK), knowledge_base_id(FK), content(TEXT), chunk_index, vector_id | 文本切片 |
| `document_chunk_embedding` | id, chunk_id(FK), knowledge_base_id(FK), embedding vector(1536) | pgvector向量表 |

#### 对话（2张）

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| `conversation` | id, user_id(FK), knowledge_base_id(FK), model_provider, title, summary, status(ACTIVE/DELETED) | 对话会话(软删除) |
| `message` | id, conversation_id(FK), role(user/assistant/system), content(TEXT), citations(JSONB), tool_calls(JSONB) | 消息+引用 |

### 5.3 向量索引设计

```sql
-- pgvector IVFFlat索引：将1536维空间划分为100个列表
CREATE INDEX idx_chunk_embedding_ivfflat ON document_chunk_embedding
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

- `vector_cosine_ops`：余弦相似度操作类，适合文本语义比较
- `lists = 100`：推荐值为 `sqrt(总行数)`，搜索时 `SET ivfflat.probes = 10` 控制探测数
- IVFFlat 将全表扫描降为近似最近邻搜索（ANN），牺牲少量精度换取数量级速度提升

### 5.4 JSONB 在引用存储中的应用

```json
// message.citations 示例
[{"documentId":5,"fileName":"合同.pdf","chunkIndex":3,"score":0.921}]
```

**JSONB优势**：无需为引用单独建关联表，减少JOIN；支持GIN索引可查询JSONB内部字段；灵活扩展字段不影响表结构。

### 5.5 document.ingestion_status 状态机

```
PENDING → EXTRACTING → CLEANING → CHUNKING → EMBEDDING → INDEXING → COMPLETED
   ↓         ↓           ↓          ↓           ↓           ↓
  FAILED   FAILED     FAILED     FAILED      FAILED      FAILED
```

前端可轮询此字段展示入库进度。每个状态转换由 `EtlPipeline.updateStatus()` 实时更新到数据库。

---

## 6. 安全机制

### 6.1 四层安全防护

```
┌──────────────────────────────────────────┐
│  第一层：Spring Security 过滤器链          │
│  • 公开端点白名单 (auth, swagger, health)  │
│  • 其余全部 require authentication         │
│  • /admin/** 额外 require ROLE_ADMIN      │
│  • CSRF禁用 (REST API无状态，用JWT)        │
│  • SessionCreationPolicy.STATELESS        │
│  • CSP策略限制脚本/样式/连接来源           │
└────────────────┬─────────────────────────┘
                 ▼
┌──────────────────────────────────────────┐
│  第二层：JWT 令牌校验                      │
│  • HMAC-SHA256 签名验证（防篡改）          │
│  • 过期时间校验                            │
│  • 从令牌提取 userId + systemRole          │
│  • 注入 SecurityContext + ThreadLocal      │
└────────────────┬─────────────────────────┘
                 ▼
┌──────────────────────────────────────────┐
│  第三层：业务权限隔离                       │
│  • RagPipeline.requireKbMember() 校验     │
│  • DocumentService.requireKbMember()      │
│  • ConversationService.requireConversationOwner() │
│  • KnowledgeBaseService.isOwner()/isMember()│
│  • OWNER才能管理成员/删除知识库             │
│  • ADMIN角色可绕过部分成员检查             │
└────────────────┬─────────────────────────┘
                 ▼
┌──────────────────────────────────────────┐
│  第四层：输入安全                           │
│  • 路径遍历防护：safePath() 禁止 ../       │
│  • 文件大小限制：50MB (multipart+代码校验) │
│  • 文件格式白名单：18种允许格式             │
│  • XSS防护：前端DOMPurify净化HTML          │
│  • SQL注入防护：MyBatis-Plus参数化查询     │
└──────────────────────────────────────────┘
```

### 6.2 Refresh Token 安全存储

```sql
CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id),
    token_hash  VARCHAR(256) NOT NULL UNIQUE,  -- SHA-256(原始UUID)
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE  -- 支持远程撤销+令牌轮换
);
```

数据库只存 SHA-256 哈希（64字符十六进制），泄露数据库也不会泄露令牌；`revoked` 字段支持管理员远程撤销和令牌轮换（每次刷新旧token设为revoked）。

---

## 7. 部署架构

### 7.1 Docker Compose 服务编排

```yaml
services:
  postgres:       # pgvector/pgvector:pg17 + healthcheck(pg_isready) + 数据卷
  elasticsearch:  # elasticsearch:8.15.3 单节点 + 禁用安全 + 512M堆
  minio:          # minio/minio:latest + API:9000 + Console:9001
  backend:        # 多阶段构建: Maven→JRE + 依赖3个服务健康
  frontend:       # 多阶段构建: Node→Nginx + 端口80
```

**服务依赖链**：

```
frontend → backend → postgres (healthcheck: pg_isready)
                   → elasticsearch (healthcheck: curl cluster/health)
                   → minio (healthcheck: curl minio/health/live)
```

`depends_on` 配合 `condition: service_healthy` 确保启动顺序，避免后端在数据库未就绪时启动失败。

### 7.2 多阶段 Docker 构建

**后端**（JDK→JRE 瘦身约60%）：

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder  # ~500MB
RUN mvn clean package -DskipTests
FROM eclipse-temurin:21-jre                    # ~200MB
COPY --from=builder target/*.jar /app/app.jar
```

**前端**（Node→Nginx 瘦身约80%）：

```dockerfile
FROM node:22 AS builder                        # ~1GB
RUN npm ci && npm run build
FROM nginx:alpine                              # ~25MB
COPY --from=builder dist /usr/share/nginx/html
```

### 7.3 Nginx 对 SSE 的关键配置

```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_buffering off;           # ★ 禁用响应缓冲，SSE必须
    proxy_cache off;
    proxy_http_version 1.1;
}
```

如果不禁用缓冲，Nginx 会囤积 SSE token 事件，用户看到的是"等几秒后突然冒出一大段文字"而非逐字输出。

### 7.4 双运行模式

| 模式 | 数据库 | 存储 | ES | 适用场景 |
|------|--------|------|-----|---------|
| standalone/dev | H2本地(embedded) | 本地文件系统 | 可选降级到SimpleKeywordSearch | 本地演示、开发 |
| Docker Compose | PostgreSQL+pgvector | MinIO | 完整Elasticsearch | 生产部署 |

---

## 8. 性能优化策略

### 8.1 向量索引优化

- **IVFFlat 索引**：将1536维空间划分为100个列表，搜索只查最近的几个列表，将全表扫描降为近似搜索
- **批量插入**：EmbeddingService 每批100条调用API和写入数据库，减少网络往返
- **pgvector 原生操作符**：`<=>` 余弦距离在数据库层面计算，避免数据传输

### 8.2 并行检索

```java
// CompletableFuture 并行执行两路检索
// 总延迟从 T1+T2 降到 max(T1,T2)
CompletableFuture.supplyAsync(() -> pgVectorSearchService.search(...));
CompletableFuture.supplyAsync(() -> elasticsearchSearchService.search(...));
```

使用默认 ForkJoinPool。`candidateK = max(topK, min(50, topK×3))` 给 RRF 融合提供更多候选。1500ms 超时防止慢查询阻塞请求。

### 8.3 异步处理

- **文档ETL异步**：`@Async("analysisTaskExecutor")` 注解，上传接口立即返回
- **线程池配置**：AsyncConfig 10核心/50最大线程，避免高并发下线程耗尽
- **ES Bulk API**：一次HTTP请求批量索引所有切片，减少网络往返

### 8.4 流式响应

- **Flux<String> SSE**：逐token推送，降低首字等待时间
- **Nginx proxy_buffering off**：确保流式事件实时传递
- **前端 fetch + ReadableStream**：增量解码，边收边渲染
- **ConversationService 流式拦截**：在 map() 中拼接内容并保存，不阻塞流

### 8.5 检索优化

- **Min-Max 归一化**：消除不同检索后端的分数尺度差异
- **fusion key 去重**：`documentId#chunkIndex` 避免同一切片重复计算
- **ES volatile 日志抑制**：首次 warn 后续 debug，避免日志洪泛影响性能
- **Evidence Portfolio 预算控制**：`maxEvidenceContextChars=6000` 限制 prompt 长度

### 8.6 前端优化

- **Axios并发刷新**：多个401同时发生时只刷新一次令牌（isRefreshing标志+队列）
- **Pinia响应式**：消息列表增量更新，不触发全量重渲染
- **Vite HMR**：开发环境极速热更新
- **DOMPurify + markdown-it**：html:false 禁止原始HTML，linkify:true 自动链接

---

## 9. 项目亮点与创新

### 9.1 核心技术亮点

| # | 亮点 | 实现细节 | 技术价值 |
|---|------|---------|---------|
| 1 | **证据驱动问答** | evidence threshold 门控 + 0.70×topScore + 0.30×topSupportAvg 公式 | 不是"什么都答"，有效抑制幻觉 |
| 2 | **混合检索+RRF融合** | CompletableFuture 并行 + k=60 + 0.75×rankConf + 0.25×sourceConf | NDCG通常比单路提升5-15% |
| 3 | **Evidence Portfolio** | 0.62×conf + 0.22×coverage + 0.12×diversity - 0.24×redundancy | 贪心选择+预算控制+CJK支持 |
| 4 | **三级优雅降级** | PgVector/ES/本地关键词三级降级，任一后端故障不影响可用性 | 生产环境高可用 |
| 5 | **多模型运行时切换** | `Map<String, ChatClient>` + OpenAI-compatible统一抽象 | 零代码切换AI提供商 |
| 6 | **DeepSeek思考模式** | Jackson BeanSerializerModifier注入thinking节点 | 支持V3/R1/V4多版本+推理强度 |
| 7 | **JWT双令牌+无感刷新** | AT短效+RT SHA-256存储+令牌轮换+前端并发401只刷新一次 | 安全性+用户体验 |
| 8 | **ThreadLocal+Filter** | 三个独立ThreadLocal + finally清理 + isAdmin()便捷方法 | 轻量级上下文传递 |
| 9 | **Prompt模板文件化** | @PostConstruct加载.st文件 + Spring AI PromptTemplate渲染 | 修改Prompt不需重编译 |
| 10 | **流式消息拦截保存** | Flux.map()中拼接完整内容 + doOnComplete兜底保存 | 确保消息持久化不丢失 |
| 11 | **会话摘要+标题自动生成** | 消息>20自动生成摘要 + 首次回答自动生成标题 | 智能会话管理 |
| 12 | **智能Overlap边界** | 在目标切割点前后寻找句子边界(. 。! ！? ？ \n)，选更近的 | 避免句子中间断开 |
| 13 | **Docker多阶段构建** | JDK→JRE/Node→Nginx，镜像瘦身60%+ | 生产部署优化 |
| 14 | **策略模式** | 文件提取器4实现 + 切片策略3种 + 存储2种 | 高度可扩展 |

### 9.2 设计模式应用

| 模式 | 应用位置 | 效果 |
|------|---------|------|
| **策略模式** | FileContentExtractor + 4个实现（Spring自动收集） | 新增文件格式零改动 |
| **策略模式** | DocumentChunker + 3种切片策略（switch expression分派） | 切片算法可替换 |
| **策略模式** | StorageService: MinIO / Local（@Autowired(required=false)） | 存储后端可切换 |
| **工厂模式** | AiConfig 根据配置创建 `Map<String, ChatClient>` | Provider动态创建 |
| **代理/装饰** | JwtAuthenticationFilter 包装请求链 + finally清理 | 透明认证 |
| **观察者** | Pinia store 响应式状态 + Vue 3 reactivity | UI自动更新 |
| **贪心算法** | EvidencePortfolioSelector 迭代选最优候选 | 近似最优证据组合 |

### 9.3 工程最佳实践

- **Flyway数据库版本管理**：7个版本化迁移脚本（V1~V7），数据库变更可审计可回滚
- **MyBatis-Plus Lambda查询**：编译时检查字段名，避免运行时SQL错误（`eq(User::getUsername, ...)`）
- **全局异常处理**：`@RestControllerAdvice` + `ApiResponse` 统一错误响应格式
- **Swagger API文档**：SpringDoc OpenAPI 自动生成 `/swagger-ui.html`
- **环境变量管理**：`.env.example` 模板 + `application.yml` 占位符，敏感信息不入代码
- **@Transactional 声明式事务**：切片批量保存、会话操作等关键路径
- **@Autowired(required=false)**：ES/MinIO/EmbeddingModel 可选依赖，优雅降级

---

## 10. 业务流程梳理

### 10.1 用户认证完整流程

```
用户注册:
  前端 → POST /api/v1/auth/register {username, password, email}
  → AuthService.register()
    → LambdaQueryWrapper检查username唯一性
    → passwordEncoder.encode(password)  BCrypt加密
    → 插入sys_user (systemRole=USER, status=ACTIVE)
    → generateAuthResponse():
      ├─ tokenProvider.generateAccessToken(userId, username, "USER")
      │   → Jwts.builder().subject(userId).claim("username",...).claim("systemRole",...)
      │     .signWith(HMAC-SHA256).compact()  → JWT字符串
      ├─ tokenProvider.generateRefreshToken() → UUID.randomUUID()
      ├─ hashToken(refreshToken) → SHA-256 → 64字符hex
      ├─ 插入refresh_token表 (token_hash, expires_at=now+7d, revoked=false)
      └─ 返回 {accessToken, refreshToken, userInfo}

用户登录:
  → AuthService.login()
    → passwordEncoder.matches(input, stored)  BCrypt验证
    → 检查status != "DISABLED"
    → generateAuthResponse() → 生成全新双令牌

API请求认证:
  → JwtAuthenticationFilter.doFilterInternal()
    → extractToken(request): "Authorization: Bearer <token>" → 提取token部分
    → tokenProvider.validateToken(token): Jwts.parser().verifyWith().parseSignedClaims()
    → 解析 userId/username/systemRole
    → groupService.getOrCreateDefaultGroupId(userId, username)
    → GroupContext.set(userId, groupId, systemRole)  [ThreadLocal]
    → SecurityContextHolder.setAuthentication()      [Spring Security]
    → filterChain.doFilter(request, response)
    → finally: GroupContext.clear()  [ThreadLocal清理]

Token刷新:
  前端Axios 401拦截 → POST /api/v1/auth/refresh {refreshToken}
  → AuthService.refreshToken()
    → hashToken(refreshToken) → SHA-256
    → 查找refresh_token: token_hash匹配 AND revoked=false
    → 检查expires_at未过期
    → 旧token设为revoked=true  [令牌轮换]
    → 检查用户status != DISABLED
    → generateAuthResponse() → 全新双令牌
  → 前端更新localStorage双token → 用新token重试原请求
  → 刷新失败 → 清空token → 跳转登录页
```

### 10.2 知识库创建与权限流程

```
创建知识库:
  → KnowledgeBaseService.create(kb)
    → 设置默认值: evidence_threshold=0.50, chunk_strategy=PARAGRAPH, chunk_size=500, overlap=100
    → kb.setCreatorId(GroupContext.getUserId()), kb.setGroupId(GroupContext.getGroupId())
    → 插入knowledge_base表
    → 插入kb_member表: (kbId, userId, role=OWNER)  创建者自动成为OWNER

访问知识库列表:
  → KnowledgeBaseService.listAccessible(page, size)
    → 先查kb_member: SELECT knowledge_base_id FROM kb_member WHERE user_id = ?
    → 再查knowledge_base: WHERE id IN (kbIds) AND status='ACTIVE' ORDER BY created_at DESC
    → 只返回当前用户参与的

知识库操作权限矩阵:
  ┌──────────────┬────────┬────────┬────────┐
  │ 操作          │ MEMBER │ OWNER  │ ADMIN  │
  ├──────────────┼────────┼────────┼────────┤
  │ 查看/检索     │   ✓    │   ✓    │   ✓    │
  │ 上传文档      │   ✓    │   ✓    │   ✓    │
  │ 更新配置      │   ✗    │   ✓    │   ✓    │
  │ 删除知识库    │   ✗    │   ✓    │   ✓    │
  │ 管理成员      │   ✗    │   ✓    │   ✓    │
  │ 查看成员列表  │   ✓    │   ✓    │   ✓    │
  └──────────────┴────────┴────────┴────────┘

权限检查实现:
  isOwner(kbId): kb_member WHERE kb_id=? AND user_id=? AND role='OWNER'
  isMember(kbId): kb_member WHERE kb_id=? AND user_id=?
  getById(kbId): if (!isMember && !GroupContext.isAdmin()) throw SecurityException
```

### 10.3 文档上传与入库完整流程

```
前端选择知识库 + 拖拽/选择文件
  → POST /api/v1/documents/upload  (multipart/form-data)
  → DocumentController.upload() → DocumentService.upload()
    → requireKbMember(kbId)  权限校验
    → getFormat(filename) → 检查ALLOWED_FORMATS(18种)
    → file.getSize() > MAX_FILE_SIZE(50MB) → 拒绝
    → objectName = "{kbId}/{timestamp}_{originalName}"
    → minioStorageService.uploadFile() / localFileStorageService.uploadFile()
    → 插入document表 (status=PENDING, chunk_count=0, uploader_id=userId)
    → @Async("analysisTaskExecutor") triggerIngestionAsync(docId)
    → 立即返回Document记录（前端可轮询状态）

异步ETL流水线（后台线程）:
  EtlPipeline.processDocument(documentId):
    PENDING
    → EXTRACTING: 下载文件到临时目录 → 策略模式选择提取器 → 提取纯文本
       PDF额外: AcademicPdfMetadataExtractor.extract(rawText, fileName)
       → PaperMetadata(doi, authors, year, journal) → 更新document表
    → CLEANING: TextCleaner.clean(text)
       → 移除控制字符 → 压缩空格/换行 → 移除短行
       → 调用LLM生成200字文档摘要(取前3000字符) → doc.setSummary()
    → CHUNKING: DocumentChunker.chunk(text, config)
       → config从knowledge_base表读取(chunkSize, overlap, strategy)
       → switch分派策略 → 生成List<String> chunks
    → EMBEDDING: EmbeddingService.embedAndStore(chunks)
       → 每批100条调用EmbeddingModel.embed(texts)
       → formatPgVector(vector) → "[0.1,0.2,...]"
       → saveBatch(embeddings, 100) + updateBatchById(chunks, 100)
    → INDEXING: ElasticsearchIndexService.indexChunks(chunks, kbId, docId)
       → Bulk API批量写入ES "document_chunk"索引
       → 文档ID: "chunk_{chunkId}" 或 "chunk_{docId}_{chunkIndex}"
    → COMPLETED: doc.setChunkCount(chunks.size()) → 更新document表
    
    异常捕获: catch(Exception) → doc.setIngestionStatus("FAILED")
```

### 10.4 RAG 问答核心链路（代码级调用链）

```
用户输入问题 → 前端 chat.store.sendMessage(content, params)
    → messages.push({role:'user', content})
    → messages.push({role:'assistant', content:''})  // 占位
    → fetch POST /conversations/{id}/messages/stream
       headers: Authorization: Bearer <token>
       body: {content, temperature, topP, maxTokens, modelName, thinking, reasoningEffort}

后端 ConversationController.streamMessage(id, request)
  → ConversationService.streamMessage(id, content, ...)
    → requireConversationOwner(id): 查conversation表, 验证userId一致
    → 检查kb_id非null
    → ragPipeline.isKbMember(kbId): 查kb_member表
    → 插入user消息: messageMapper.insert(userMsg)
    → 确定modelProvider: conv.modelProvider || "deepseek"
    
    → ragPipeline.streamQuery(query, kbId, modelProvider, ...):
      → requireKbMember(kbId): 查kb_member表验证权限
      → knowledgeBaseMapper.selectById(kbId): 获取阈值配置
      
      → hybridSearchService.search(query, kbId, 10):
        → candidateK = max(10, min(50, 10*3)) = 30
        → CompletableFuture.supplyAsync(pgVectorSearch)  // 并行
        → CompletableFuture.supplyAsync(esSearch)         // 并行
        → awaitResults(1500ms超时, cancel on timeout)
        → ES空 → simpleKeywordSearch降级
        → rrfFusionService.fuse(semantic, keyword, 10):
          → normalizeScores(Min-Max, max==min→1.0)
          → 排名 + fusionKey去重(documentId#chunkIndex)
          → RRF累加: 1/(60+rank)
          → calculateConfidence: 0.75*rankConf + 0.25*sourceConf
          → 按score降序取top10
      
      → hasSufficientEvidence(results, kb.evidenceThreshold):
        → evidenceConfidence = 0.70*topScore + 0.30*avg(top3)
        → < threshold → INSUFFICIENT → 渲染evidence-insufficient-prompt.st
        → ≥ threshold → SUFFICIENT → 继续
      
      → selectEvidencePortfolio(query, results):
        → EvidencePortfolioSelector.select(query, results, maxEvidenceContextChars=6000)
        → 去重(documentId#chunkIndex) → tokenize(query+CJK bigram)
        → 贪心迭代: scoreCandidate()
          0.62*confidence + 0.22*coverageGain + 0.12*diversityBonus - 0.24*redundancy
        → 边际价值<0.40停止 / 超预算停止
      
      → renderEvidencePrompt(query, portfolio):
        → buildBudgetedEvidenceContext(): "[来源1] 文档ID=5 切片#3 置信度=0.921..."
        → promptTemplateManager.render("evidence-sufficient-prompt", {evidence, query})
      
      → buildCitations(portfolio): 
        → 收集documentIds → selectBatchIds → 构建Citation列表
      
      → resolveChatClient(modelProvider): chatClients.get("deepseek")
      → buildModelName("deepseek-v4-pro", thinking=true, effort="medium")
        → "deepseek-v4-pro|thinking:enabled|effort:medium"
      → OpenAiChatOptions: model + temperature + topP + maxTokens
      
      → chatClient.prompt().user(prompt).options(opts).stream().content()
        → Flux<String> → map(StreamEvent::token)
        → concatWithValues(citationsJson, StreamEvent.done(null))
    
    → Flux.map(event → {  // ConversationService拦截
        if token → fullContent.append(text)
        if citations → citationsHolder[0] = json
        if done → saveAssistantMessage(convId, fullContent, citations)
        return event  // 原样转发
      })
    → doOnComplete → saveAssistantMessage(兜底) + checkAndGenerateSummary
    → doOnError → log.error

前端流式接收:
  → ReadableStream reader → TextDecoder → 按行拆分
  → JSON.parse → 按type分发:
    token: assistantMessage.content += text  (Vue响应式自动渲染)
    citations: assistantMessage.citations = [...]
    done: 标记完成 + loadConversations刷新
  → 消息区实时渲染: DOMPurify.sanitize(md.render(content))
```

---

## 附录

### A. Prompt 模板体系

| 模板文件 | 变量 | 用途 |
|---------|------|------|
| `system-prompt.st` | — | ChatService系统提示词，定义AI角色 |
| `evidence-sufficient-prompt.st` | {evidence}, {query} | 要求AI基于证据回答并标注来源引用 |
| `evidence-insufficient-prompt.st` | {query} | 证据不足时的礼貌拒绝模板 |
| `rag-context-prompt.st` | {context}, {query} | RAG上下文问答模板 |
| `query-rewrite-prompt.st` | {query} | 改写用户问题优化检索效果 |
| `summary-prompt.st` | {messages} | 对话总结模板（消息>20时触发） |

模板由 `PromptTemplateManager` 在 `@PostConstruct` 阶段加载到 HashMap，使用 Spring AI 的 `PromptTemplate.render(variables)` 渲染。

### B. API 接口清单

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 认证 | POST | `/api/v1/auth/register` | 用户注册 |
| 认证 | POST | `/api/v1/auth/login` | 登录 |
| 认证 | POST | `/api/v1/auth/refresh` | 刷新令牌(令牌轮换) |
| 认证 | GET | `/api/v1/auth/me` | 获取当前用户 |
| 知识库 | POST | `/api/v1/knowledge-bases` | 创建知识库(自动成为OWNER) |
| 知识库 | GET | `/api/v1/knowledge-bases` | 列表(只返回参与的) |
| 知识库 | PUT | `/api/v1/knowledge-bases/{id}` | 更新(仅OWNER) |
| 知识库 | DELETE | `/api/v1/knowledge-bases/{id}` | 删除(仅OWNER) |
| 知识库 | POST/DELETE | `/api/v1/knowledge-bases/{id}/members` | 成员管理(仅OWNER) |
| 知识库 | GET | `/api/v1/knowledge-bases/{id}/members` | 成员列表 |
| 文档 | POST | `/api/v1/documents/upload` | 上传(自动异步ETL) |
| 文档 | GET | `/api/v1/documents?kbId=&page=&size=` | 分页列表 |
| 文档 | DELETE | `/api/v1/documents/{id}` | 删除(级联) |
| 文档 | POST | `/api/v1/documents/{id}/retry` | 重试入库 |
| 会话 | POST | `/api/v1/conversations` | 创建会话 |
| 会话 | GET | `/api/v1/conversations` | 列表(按更新时间) |
| 会话 | DELETE | `/api/v1/conversations/{id}` | 软删除(status=DELETED) |
| 会话 | PUT | `/api/v1/conversations/{id}` | 重命名 |
| 消息 | POST | `/api/v1/conversations/{id}/messages/stream` | ★ SSE流式问答 |
| 消息 | GET | `/api/v1/conversations/{id}/messages` | 历史消息 |
| 模型 | GET | `/api/v1/models` | 获取可用模型列表 |
| 分析 | POST | `/api/analysis/batch` | 批量分析 |
| 导出 | GET | `/api/analysis/export/pdf` | PDF报告导出 |
| 公共 | GET | `/api/v1/health` | 健康检查 |

### C. 关键配置参数参考

```yaml
# application.yml 关键参数
custom:
  rag:
    max-evidence-context-chars: 6000   # 证据上下文字符预算
    search:
      backend-timeout-ms: 1500         # 检索后端超时
  ai:
    embedding:
      dimension: 1536                  # 向量维度
      enabled: true                    # @ConditionalOnProperty

# Spring配置
spring:
  servlet:
    multipart:
      max-file-size: 50MB              # 文件上传大小限制

# JWT配置
jwt:
  secret: ${JWT_SECRET}               # 环境变量注入
  access-token-expiration: 3600000     # 1小时 (ms)
  refresh-token-expiration: 604800000  # 7天 (ms)

# 知识库默认值
knowledge_base:
  evidence_threshold: 0.50             # 证据充分性阈值
  chunk_strategy: PARAGRAPH            # 默认切片策略
  chunk_size: 500                      # 切片大小(字符)
  chunk_overlap: 100                   # 重叠大小(字符)
```

---

> 本文档基于 EviMind 项目源码编写，涵盖架构设计、核心算法（含公式推导与代码级实现）、业务流程（含完整调用链）、安全机制等全方位技术细节，适用于技术评审、项目交接和学习参考。
