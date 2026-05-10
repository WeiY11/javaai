# EviMind — RAG 增强的 AI 智能数据分析平台

> 一个完整的企业级 RAG（Retrieval-Augmented Generation）应用，涵盖文档摄入、向量检索、混合搜索、AI 对话、权限管理等全链路能力。

---

## 目录

1. [项目定位](#1-项目定位)
2. [技术架构全景图](#2-技术架构全景图)
3. [技术栈清单](#3-技术栈清单)
4. [项目目录结构](#4-项目目录结构)
5. [数据库设计](#5-数据库设计)
6. [后端架构详解](#6-后端架构详解)
7. [前端架构详解](#7-前端架构详解)
8. [核心业务流程](#8-核心业务流程)
9. [API 接口清单](#9-api-接口清单)
10. [基础设施与部署](#10-基础设施与部署)
11. [安全设计](#11-安全设计)
12. [技术决策与选型理由](#12-技术决策与选型理由)
13. [面试技术点深度解析](#13-面试技术点深度解析)
14. [项目亮点与改进方向](#14-项目亮点与改进方向)

---

## 1. 项目定位

**EviMind** 是一个基于 **RAG（检索增强生成）** 架构的 AI 文档分析与智能问答平台。核心能力：

- 用户上传 PDF、Word、Excel、CSV、JSON、TXT 等格式文档
- 系统自动完成**文本提取 → 清洗 → 切片 → 向量嵌入 → 双路索引**的全自动 ETL 流水线
- 构建可检索的**知识库**，用户可创建多个知识库并管理成员权限
- 在知识库范围内与 AI 大模型对话，模型基于真实文档内容回答，**有效抑制幻觉**
- 每个回答附带**引用来源**（来源文档、切片编号、相关度评分），答案可追溯可验证
- 支持 **DeepSeek / GLM-4 / 通义千问 / OpenAI** 四种大模型运行时热切换

**适用场景**：企业内部知识库问答、技术文档智能检索、合同/报告内容分析、客服知识库等。

---

## 2. 技术架构全景图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         客户端层 (Client)                                  │
│  浏览器 → Nginx (端口 80) → 静态文件 (Vue SPA) + /api 反向代理             │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │ HTTP/SSE
┌──────────────────────────────▼───────────────────────────────────────────┐
│                      应用层 (Spring Boot 3.5 :8080)                        │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │                         拦截器层                                   │     │
│  │  JwtAuthenticationFilter → SecurityContext → GroupContext(ThreadLocal)│   │
│  │  CORS Filter → GlobalExceptionHandler                             │     │
│  └──────────────────────────────────────────────────────────────────┘     │
│                                                                           │
│  ┌──────────────────────┐  ┌──────────────────────────────────────┐      │
│  │   认证模块 (auth/)    │  │       业务 Controller 层               │      │
│  │  AuthController      │  │  ChatController       (SSE 流式对话)   │      │
│  │  AuthService         │  │  ConversationController (会话 CRUD)    │      │
│  │  TokenProvider(JWT)  │  │  KnowledgeBaseController (知识库管理)  │      │
│  │  JwtAuthFilter       │  │  DocumentController  (文档上传/管理)   │      │
│  └──────────────────────┘  │  AnalysisController  (文件分析/导出)   │      │
│                              │  FileController      (目录浏览/读取)   │      │
│  ┌──────────────────────┐  │  HealthController    (健康检查)        │      │
│  │   AI 配置 (config/)   │  └──────────────────────────────────────┘      │
│  │  AiConfig            │                                                │
│  │  - ChatClient ×4     │  ┌──────────────────────────────────────┐      │
│  │  - EmbeddingModel    │  │        Service 业务层                  │      │
│  │  - ChatMemory        │  │  ChatService          (对话+记忆)     │      │
│  │  AiDataTools         │  │  ConversationService  (会话管理)      │      │
│  │  PromptTemplateMgr   │  │  DocumentService      (文档管理)      │      │
│  │  SecurityConfig      │  │  KnowledgeBaseService (知识库管理)    │      │
│  │  AsyncConfig         │  │  FileExtractorService (文件提取调度)   │      │
│  └──────────────────────┘  │  MinioStorageService  (对象存储)      │      │
│                              │  ReportExportService  (报告导出)      │      │
│                              └──────────────────────────────────────┘      │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │                      RAG 核心引擎                                 │     │
│  │                                                                    │     │
│  │  ┌─────────────────┐   ┌─────────────────┐   ┌────────────────┐  │     │
│  │  │   ETL 流水线     │   │   混合检索引擎    │   │  RAG 问答管线   │  │     │
│  │  │                 │   │                 │   │                │  │     │
│  │  │ EtlPipeline     │   │ HybridSearchSvc │   │ RagPipeline    │  │     │
│  │  │  ├ TextCleaner  │   │  ├ PgVectorSrch │   │  ├ 权限校验     │  │     │
│  │  │  ├ DocChunker   │──▶│  ├ ES Search    │──▶│  ├ 混合检索     │  │     │
│  │  │  ├ EmbeddingSvc │   │  └ RrfFusion    │   │  ├ 证据评估     │  │     │
│  │  │  └ ES IndexSvc  │   │                 │   │  ├ Prompt组装  │  │     │
│  │  │                 │   │  并行搜索+降级    │   │  ├ LLM生成     │  │     │
│  │  │  文件→文本→切片  │   │  RRF无监督融合   │   │  └ 引用构建     │  │     │
│  │  │  →向量→双路索引  │   │                 │   │                │  │     │
│  │  └─────────────────┘   └─────────────────┘   └────────────────┘  │     │
│  └──────────────────────────────────────────────────────────────────┘     │
│                                                                           │
│  ┌──────────────────────────────────────────────────────────────────┐     │
│  │                       数据访问层                                   │     │
│  │  MyBatis-Plus Mapper (9个)  +  JPA Repository (1个)               │     │
│  │  Spring Data Elasticsearch → ElasticsearchClient                  │     │
│  │  MinIO Client → 对象存储操作                                       │     │
│  └──────────────────────────────────────────────────────────────────┘     │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────────────────┐
│                      基础设施层 (Docker Compose)                           │
│                                                                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐           │
│  │ PostgreSQL 17   │  │ Elasticsearch   │  │ MinIO           │           │
│  │ + pgvector      │  │ 8.15.3          │  │ (S3 兼容存储)    │           │
│  │                 │  │                 │  │                 │           │
│  │ • 业务数据       │  │ • 关键词全文索引  │  │ • 文档文件存储    │           │
│  │ • 向量存储       │  │ • BM25 评分      │  │ • S3 API 兼容    │           │
│  │ • 向量相似搜索   │  │ • 倒排索引       │  │ • 私有化部署      │           │
│  │ • IVFFlat 索引   │  │                 │  │                 │           │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘           │
└──────────────────────────────────────────────────────────────────────────┘
```

### 架构分层说明

| 层 | 职责 | 关键组件 |
|----|------|---------|
| **客户端层** | 静态资源服务 + API 网关 | Nginx 反向代理 |
| **拦截器层** | 认证、授权、上下文传递、异常处理 | JWT Filter、CORS、GlobalExceptionHandler |
| **Controller 层** | HTTP 请求路由、参数校验、SSE 流式输出 | 8 个 Controller，Swagger 文档 |
| **Service 层** | 业务逻辑编排、事务管理 | 10+ Service 类 |
| **RAG 引擎层** | 文档摄入、混合检索、问答生成 | ETL Pipeline、HybridSearch、RagPipeline |
| **数据访问层** | ORM、向量查询、ES 查询、对象存储 | MyBatis-Plus、JPA、ElasticsearchClient、MinIO Client |
| **基础设施层** | 数据库、搜索引擎、对象存储 | PostgreSQL+pgvector、Elasticsearch、MinIO |

---

## 3. 技术栈清单

### 3.1 后端技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **语言** | Java | 22 | 主开发语言，使用虚拟线程、Record 类、Switch 表达式等新特性 |
| **框架** | Spring Boot | 3.5.0 | IoC 容器、自动配置、Web MVC |
| **AI 框架** | Spring AI | 1.0.0-M1 | 统一 LLM 调用抽象，ChatClient、EmbeddingModel、ChatMemory |
| **ORM-主** | MyBatis-Plus | 3.5.12 | Lambda 类型安全查询、分页、自动填充、10 个 Mapper |
| **ORM-辅** | Spring Data JPA | 内嵌于 Boot | 仅用于 analysis_result 的简单 CRUD |
| **安全** | Spring Security | 内嵌于 Boot | 过滤器链、BCrypt 密码编码、CORS 配置 |
| **JWT** | jjwt (io.jsonwebtoken) | 0.12.6 | HMAC-SHA 签名、Access Token + Refresh Token |
| **数据库** | PostgreSQL + pgvector | 17 | pgvector 扩展提供向量存储和余弦相似度搜索 |
| **搜索引擎** | Elasticsearch | 8.15.3 | 基于 BM25 的关键词全文检索 |
| **对象存储** | MinIO | latest | S3 兼容协议，私有化部署的文档存储 |
| **数据库迁移** | Flyway | 内嵌于 Boot | 版本化 SQL 迁移脚本，4 个版本 |
| **PDF 解析** | Apache PDFBox | 3.0.1 | PDF 文本提取 |
| **Office 解析** | Apache POI | 5.2.5 | Word(.docx) 和 Excel(.xlsx) 内容提取 |
| **PDF 生成** | OpenPDF | 2.0.3 | 分析报告导出为 PDF |
| **API 文档** | SpringDoc OpenAPI | 2.6.0 | Swagger UI (/swagger-ui.html) 自动生成 |
| **工具** | Lombok | 最新 | 消除 Java 样板代码 (@Data, @Slf4j, @RequiredArgsConstructor 等) |
| **模板引擎** | StringTemplate (Spring AI) | 内嵌 | Prompt 模板管理，`.st` 文件热加载 |
| **响应式** | Project Reactor | 内嵌于 Boot | Flux 流式响应，SSE |
| **JSON** | Jackson | 内嵌于 Boot | JSON 序列化，SSE 事件格式 |
| **构建工具** | Maven | 3.9+ | 依赖管理、多阶段构建 |

### 3.2 前端技术栈

| 类别 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **框架** | Vue 3 (Composition API) | 3.5 | `<script setup>` 语法糖，响应式 UI |
| **语言** | TypeScript | 6.0 | 类型安全、接口定义、编译时检查 |
| **构建工具** | Vite | 8.0 | 极速 HMR、ESBuild 预构建、开发代理 |
| **状态管理** | Pinia | 3.0 | 组合式 Store，auth / chat / knowledge-base 三个模块 |
| **路由** | Vue Router | 4.6 | 登录页、聊天页、知识库管理页，路由守卫 |
| **UI 组件库** | Element Plus | 2.13 | 表单、表格、对话框、标签、选择器等 20+ 组件 |
| **HTTP 客户端** | Axios | 1.15 | 请求/响应拦截器、JWT 自动附加、令牌无感刷新 |
| **Markdown 渲染** | markdown-it | 14.1 | AI 回复的 Markdown 转 HTML |
| **代码高亮** | highlight.js | 11.11 | Markdown 中代码块的语法着色 |
| **XSS 防护** | DOMPurify | 3.4 | 净化 Markdown HTML 输出，防 XSS 注入 |
| **SSE 解析** | 原生 fetch + ReadableStream | - | 手动解析 SSE 事件流（更灵活控制） |

### 3.3 基础设施 / DevOps

| 类别 | 技术 | 用途 |
|------|------|------|
| **容器化** | Docker | 多阶段构建（JDK/JRE 分离、Node/Nginx 分离） |
| **编排** | Docker Compose | 5 个服务一键启动：postgres、elasticsearch、minio、backend、frontend |
| **Web 服务器** | Nginx (Alpine) | 前端静态文件服务 + `/api` 反向代理 |
| **数据库镜像** | pgvector/pgvector:pg17 | 预装 pgvector 扩展的 PostgreSQL |
| **ES 镜像** | elasticsearch:8.15.3 | 单节点模式，关闭安全认证（内网环境） |
| **MinIO 镜像** | minio/minio:latest | API 端口 9000，控制台端口 9001 |

---

## 4. 项目目录结构

```
d:\evimind\
│
├── .codeartsdoer/                       # 设计规格文档（需求→设计→任务）
│   ├── AGENTS.md                        # 工程上下文元信息
│   └── specs/
│       ├── enhanced-file-analysis/      # 文件分析增强功能规格
│       │   ├── spec.md                  # 200+ 行需求规格
│       │   ├── design.md                # 设计文档
│       │   └── tasks.md                 # 10 个编码任务拆解
│       └── rag-enhanced-platform/       # RAG 平台功能规格
│           ├── spec.md                  # 1200+ 行需求规格
│           ├── design.md                # 1250+ 行详细设计
│           └── tasks.md                 # 任务拆解
│
├── src/main/java/com/example/evimind/    # ★ Java 后端源码 (53 个文件)
│   ├── EvimindApplication.java          # Spring Boot 启动类
│   │
│   ├── auth/                            # 认证授权模块
│   │   ├── AuthController.java          # 注册/登录/刷新/用户信息
│   │   ├── AuthService.java             # 注册/登录/刷新令牌业务逻辑
│   │   ├── JwtAuthenticationFilter.java # OncePerRequestFilter，解析JWT设SecurityContext
│   │   └── TokenProvider.java           # JWT 生成/解析/验证，HMAC-SHA签名
│   │
│   ├── assistant/                       # AI Agent 工具
│   │   ├── AgentTools.java              # @Bean 注册 Function Tool: kbSearch
│   │   ├── ConversationController.java  # 会话 CRUD REST API
│   │   └── ConversationService.java     # 会话创建/消息管理/历史记录
│   │
│   ├── config/                          # 配置类 (11 个)
│   │   ├── AiConfig.java                # 创建 ChatClient Map + EmbeddingModel
│   │   ├── AiProperties.java            # 映射四家 AI 提供商配置
│   │   ├── AiDataTools.java             # 注册 Spring AI 工具函数
│   │   ├── AnalysisProperties.java      # 分析相关配置
│   │   ├── AsyncConfig.java             # 异步线程池 (10核心/50最大)
│   │   ├── ElasticsearchConfig.java     # ES Java Client 配置
│   │   ├── EmbeddingProperties.java     # Embedding 模型配置
│   │   ├── JwtConfig.java              # JWT Secret/过期时间配置
│   │   ├── MinioConfig.java            # MinIO Client Bean
│   │   ├── MyBatisMetaObjectHandler.java # 自动填充 createAt/updateAt
│   │   ├── MyBatisPlusConfig.java       # 分页拦截器 (PostgreSQL)
│   │   ├── PromptTemplateManager.java   # 加载/渲染 .st 提示词模板
│   │   └── SecurityConfig.java          # Spring Security 过滤器链配置
│   │
│   ├── controller/                      # REST Controller
│   │   ├── AnalysisController.java      # 批量分析/进度/结果/导出
│   │   ├── ChatController.java          # SSE 流式对话
│   │   └── FileController.java          # 目录浏览/文件读取
│   │
│   ├── common/                          # 公共组件
│   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice 全局异常处理
│   │   └── HealthController.java        # GET /api/v1/health → "UP"
│   │
│   ├── document/                        # 文档管理
│   │   ├── DocumentController.java      # 上传/列表/详情/删除/重试
│   │   └── DocumentService.java         # 文档生命周期管理
│   │
│   ├── extractor/                       # 文件内容提取器（策略模式）
│   │   ├── FileContentExtractor.java    # 提取器接口
│   │   ├── ExtractionResult.java        # 提取结果模型
│   │   ├── PdfFileExtractor.java        # PDFBox PDF 文本提取
│   │   ├── WordFileExtractor.java       # POI Word(.docx) 文本提取
│   │   ├── ExcelFileExtractor.java      # POI Excel(.xlsx) 表格文本提取
│   │   └── TextFileExtractor.java       # 纯文本/CSV/JSON/Markdown 提取
│   │
│   ├── identity/                        # 用户上下文
│   │   └── GroupContext.java            # ThreadLocal<userId, groupId, systemRole>
│   │
│   ├── ingestion/                       # ETL 文档摄入流水线
│   │   ├── EtlPipeline.java             # 主编排：Extract→Clean→Chunk→Embed→Index
│   │   ├── TextCleaner.java             # 文本清洗（空白/乱码/特殊字符）
│   │   ├── DocumentChunker.java         # 文本切片（固定长度/段落/语义）
│   │   ├── EmbeddingService.java        # 调用 Embedding API 向量化
│   │   └── ElasticsearchIndexService.java # ES 索引写入/删除
│   │
│   ├── knowledgebase/                   # 知识库管理
│   │   ├── KnowledgeBaseController.java # CRUD + 成员管理 REST API
│   │   └── KnowledgeBaseService.java    # 知识库创建/权限校验业务
│   │
│   ├── mapper/                          # MyBatis-Plus Mapper (9 个)
│   │   ├── SysUserMapper.java
│   │   ├── SysGroupMapper.java
│   │   ├── GroupMemberMapper.java
│   │   ├── KnowledgeBaseMapper.java
│   │   ├── KbMemberMapper.java
│   │   ├── DocumentMapper.java
│   │   ├── DocumentChunkMapper.java
│   │   ├── DocumentChunkEmbeddingMapper.java  # 向量相似度搜索 SQL
│   │   └── RefreshTokenMapper.java
│   │
│   ├── model/                           # 数据模型
│   │   ├── dto/                         # 7 个 DTO：ApiResponse, AuthRequest, StreamEvent 等
│   │   └── entity/                      # 10 个实体：User, Group, KnowledgeBase, Document 等
│   │
│   ├── qa/                              # RAG 问答核心
│   │   ├── RagPipeline.java             # 完整 RAG 流程：检索→评估→生成→引用
│   │   └── RagResponse.java             # 响应模型：证据状态+答案+引用列表
│   │
│   ├── repository/                      # JPA Repository
│   │   └── AnalysisResultRepository.java
│   │
│   ├── retrieval/                       # 检索引擎
│   │   ├── HybridSearchService.java     # 并行调度：PgVector ∥ ES，降级策略
│   │   ├── PgVectorSearchService.java   # pgvector 余弦相似度搜索
│   │   ├── ElasticsearchSearchService.java # ES BM25 关键词搜索
│   │   ├── RrfFusionService.java        # RRF 倒数排名融合算法
│   │   └── SearchResult.java            # 统一搜索结果模型
│   │
│   ├── service/                         # 业务服务
│   │   ├── ChatService.java             # ChatClient 管理 + 对话记忆
│   │   ├── FileExtractorService.java    # 提取器调度（策略模式）
│   │   ├── AnalysisResultService.java   # 分析结果存储/查询
│   │   ├── BatchProgressService.java    # 批量任务进度追踪
│   │   └── ReportExportService.java     # Markdown/PDF 报告导出
│   │
│   ├── storage/                         # 对象存储
│   │   └── MinioStorageService.java     # MinIO 上传/下载/删除
│   │
│   └── test/                            # 单元测试
│       ├── EtlPipelineTest.java
│       ├── RagPipelineTest.java
│       └── RrfFusionServiceTest.java
│
├── src/main/resources/                  # ★ 配置文件
│   ├── application.yml                  # 生产配置：PG、ES、MinIO、JWT、AI、Flyway
│   ├── application-dev.yml              # 开发配置：H2 数据库、H2 Console
│   ├── db/migration/                    # Flyway SQL 迁移
│   │   ├── V1__init_postgresql_schema.sql  # 初始化 10 张表 + 向量索引
│   │   ├── V2__add_citations_column.sql
│   │   ├── V3__add_summary_column.sql
│   │   └── V4__add_system_role_column.sql
│   ├── db/schema-h2.sql                 # H2 开发环境备用 DDL
│   └── prompts/                         # Prompt 模板文件
│       ├── system-prompt.st             # 系统提示词
│       ├── rag-context-prompt.st        # RAG 上下文提示词
│       ├── evidence-sufficient-prompt.st # 证据充足时的回答模板
│       ├── evidence-insufficient-prompt.st # 证据不足时的拒绝模板
│       ├── query-rewrite-prompt.st      # 查询改写模板
│       └── summary-prompt.st            # 对话总结模板
│
├── frontend/                            # ★ Vue 3 前端
│   ├── public/
│   │   ├── favicon.svg
│   │   └── icons.svg
│   ├── src/
│   │   ├── api/                         # API 调用模块
│   │   │   ├── auth.ts                  # 登录、注册、刷新、获取用户
│   │   │   ├── chat.ts                  # 会话 CRUD、SSE 流式消息（fetch + ReadableStream）
│   │   │   ├── document.ts              # 文档上传、列表、删除
│   │   │   └── knowledge-base.ts        # 知识库 CRUD、成员管理
│   │   ├── components/
│   │   │   └── HelloWorld.vue           # 默认模板组件
│   │   ├── router/
│   │   │   └── index.ts                 # 路由：/login, / (Chat), /knowledge-bases
│   │   ├── stores/                      # Pinia 状态管理
│   │   │   ├── auth.store.ts            # 用户登录状态、token 管理
│   │   │   ├── chat.store.ts            # 会话列表、消息列表、流式消息处理
│   │   │   └── knowledge-base.store.ts  # 知识库列表、选中状态
│   │   ├── types/                       # TypeScript 类型定义
│   │   │   ├── api.types.ts             # ApiResponse<T> 通用响应类型
│   │   │   ├── auth.types.ts            # 登录/注册请求与响应
│   │   │   ├── chat.types.ts            # 消息、会话、引用、SSE 事件
│   │   │   ├── document.types.ts        # 文档、分析报告、分页
│   │   │   └── knowledge-base.types.ts  # 知识库、成员
│   │   ├── utils/
│   │   │   └── request.ts               # Axios 封装：拦截器、JWT 无感刷新
│   │   ├── views/
│   │   │   ├── LoginView.vue            # 登录/注册页面
│   │   │   ├── ChatView.vue             # ★ 核心聊天页：侧边栏+消息区+输入区
│   │   │   ├── KnowledgeBaseView.vue    # 知识库管理表格+创建对话框
│   │   │   └── DocumentView.vue         # 文档上传+列表+摄入状态
│   │   ├── App.vue                      # 根组件：导航栏+路由视图+认证守卫
│   │   ├── main.ts                      # 入口：createApp→Pinia→Router→ElementPlus
│   │   └── style.css                    # 全局样式
│   ├── dist/                            # 生产构建输出
│   ├── node_modules/                    # NPM 依赖
│   ├── Dockerfile                       # 多阶段构建：Node 22 → Nginx Alpine
│   ├── nginx.conf                       # Nginx 配置：静态文件 + API 代理
│   ├── index.html                       # Vite 入口 HTML
│   ├── vite.config.ts                   # Vite 配置：插件、端口、代理
│   ├── tsconfig.json                    # TypeScript 配置
│   ├── tsconfig.app.json               # 应用 TS 配置
│   ├── tsconfig.node.json              # Node 端 TS 配置
│   └── package.json                     # 依赖与脚本
│
├── docker/
│   └── elasticsearch/plugins/           # ES 插件挂载目录
│
├── data/                                # H2 开发数据库文件
├── .env.example                         # 环境变量模板
├── docker-compose.yml                   # ★ 5 服务编排：pg+es+minio+backend+frontend
├── Dockerfile                           # 后端多阶段构建：Maven → JRE
├── pom.xml                              # Maven 项目配置 (120+ 行)
└── README.md                            # 项目简介
```

---

## 5. 数据库设计

### 5.1 ER 图（逻辑关系）

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

### 5.2 10 张表详细说明

#### 用户与组织 (3 张)

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| **sys_user** | `id`, `username`(UNIQUE), `password`(BCrypt), `email`, `system_role`(ADMIN/USER), `status`(ACTIVE/DISABLED) | 用户账户，密码 BCrypt 加密存储 |
| **sys_group** | `id`, `name`, `org_code`(UNIQUE), `creator_id`(FK), `status` | 组织/团队，用于多租户隔离 |
| **group_member** | `id`, `group_id`(FK), `user_id`(FK), `role`(MEMBER/OWNER), UNIQUE(group_id,user_id) | 组织成员关系，含角色 |

#### 知识库与权限 (2 张)

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| **knowledge_base** | `id`, `name`, `group_id`(FK), `evidence_threshold`(DECIMAL, default 0.50), `chunk_strategy`(PARAGRAPH/FIXED_LENGTH/SEMANTIC), `chunk_size`(default 500), `chunk_overlap`(default 100), `creator_id`(FK) | 知识库配置，可配置切片策略和证据阈值 |
| **kb_member** | `id`, `knowledge_base_id`(FK), `user_id`(FK), `role`(MEMBER/OWNER), UNIQUE(kb_id,user_id) | 知识库级权限隔离，访问前必须校验 |

#### 文档与索引 (3 张)

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| **document** | `id`, `knowledge_base_id`(FK), `file_name`, `file_format`, `file_size`, `storage_path`(MinIO 路径), `ingestion_status`(PENDING/EXTRACTING/CLEANING/CHUNKING/EMBEDDING/INDEXING/COMPLETED/FAILED), `chunk_count` | 文档元数据 + 摄入状态机 |
| **document_chunk** | `id`, `document_id`(FK), `knowledge_base_id`(FK), `content`(TEXT), `chunk_index`, `vector_id` | 文本切片，每条对应一个向量 |
| **document_chunk_embedding** | `id`, `chunk_id`(FK), `knowledge_base_id`(FK), `embedding vector(1536)`, `created_at` | ★ pgvector 向量表，1536 维，IVFFlat 余弦索引 |

#### 对话 (2 张)

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| **conversation** | `id`, `user_id`(FK), `knowledge_base_id`(FK), `model_provider`, `title`, `summary`, `status` | 一次对话会话，记录使用的模型和关联知识库 |
| **message** | `id`, `conversation_id`(FK), `role`(user/assistant/system), `content`(TEXT), `citations`(JSONB), `tool_calls`(JSONB) | 对话消息，JSONB 存引用和工具调用记录 |

#### 分析报告与令牌 (2 张)

| 表名 | 核心字段 | 说明 |
|------|---------|------|
| **analysis_report** | `id`, `file_path`, `file_name`, `provider`, `content`(TEXT) | AI 分析结果持久化存储 |
| **refresh_token** | `id`, `user_id`(FK), `token_hash`(SHA-256), `expires_at`, `revoked` | Refresh Token 哈希存储，支持远程撤销 |

### 5.3 向量索引设计

```sql
-- pgvector IVFFlat 索引：将 1536 维空间划分为 100 个列表
-- 搜索时只查最近的几个列表，牺牲少量精度换数量级速度提升
CREATE INDEX idx_chunk_embedding_ivfflat ON document_chunk_embedding
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

**关键参数解释**：
- `vector_cosine_ops`：使用余弦相似度（Cosine Similarity）作为距离度量，适合文本语义比较
- `lists = 100`：IVFFlat 的列表数，推荐值为 `sqrt(总行数)`，百万级数据量时可用 1000+
- 搜索时 `SET ivfflat.probes = 10` 可控制探测列表数，probes 越大多度越高但也越慢

### 5.4 Elasticsearch 索引设计

```json
{
  "index": "document_chunk",
  "mappings": {
    "properties": {
      "content":       { "type": "text", "analyzer": "standard" },
      "knowledgeBaseId": { "type": "long" },
      "documentId":    { "type": "long" },
      "chunkIndex":    { "type": "integer" }
    }
  }
}
```

ES 原生 BM25 评分算法，不需要额外调参。查询时用 `bool` 组合：
- `must.match`：在 `content` 字段上进行全文匹配
- `filter.term`：按 `knowledgeBaseId` 精确过滤，不走评分

### 5.5 为什么用 JSONB

`message` 表的 `citations` 和 `tool_calls` 字段使用 PostgreSQL 的 **JSONB** 类型：

```json
// citations 示例
[{"documentId": 5, "fileName": "合同.pdf", "chunkIndex": 3, "score": 0.921}]

// tool_calls 示例
[{"name": "kbSearch", "arguments": {"query": "...", "knowledgeBaseId": 1, "topK": 5}}]
```

**JSONB 优势**：
- 无需为引用单独建关联表，减少 JOIN
- 支持 GIN 索引，可以查询 JSONB 内部字段
- 灵活扩展字段，不影响表结构
- PostgreSQL 的 JSONB 是二进制格式，查询效率高于 JSON 类型

---

## 6. 后端架构详解

### 6.1 认证流程（JWT 双令牌）

```
┌─────────────┐     POST /api/v1/auth/login     ┌──────────────┐
│   前端       │ ─────────────────────────────▶  │ AuthService  │
│   Vue 3     │                                  │              │
│             │  { accessToken, refreshToken,    │ 1. BCrypt 验证密码
│             │    userInfo }                    │ 2. 生成 Access Token (1h)
│             │ ◀─────────────────────────────  │     ─ payload: userId, username, systemRole
│             │                                  │     ─ 签名: HMAC-SHA256
│             │                                  │ 3. 生成 Refresh Token (UUID)
│             │  每次 API 请求                    │ 4. Refresh Token Hash 存库
│             │  Authorization: Bearer <token>   │
│             │ ──────────────────────────────▶  │
│             │                                  │
│             │  401 时自动刷新                    │
│             │  POST /api/v1/auth/refresh       │
│             │ ──────────────────────────────▶  │
└─────────────┘                                  └──────────────┘
```

**JWT 过滤器实现要点**（[JwtAuthenticationFilter.java](src/main/java/com/example/evimind/auth/JwtAuthenticationFilter.java)）：

1. 继承 `OncePerRequestFilter`，保证每个请求只过滤一次
2. 从 `Authorization: Bearer <token>` 头提取令牌
3. 用 HMAC-SHA256 验证签名，解析 `userId`、`username`、`systemRole`
4. 构建 `UsernamePasswordAuthenticationToken` 注入 `SecurityContextHolder`
5. 同时写入 `GroupContext`（ThreadLocal），供业务层随时获取用户信息
6. `finally` 块中清理 ThreadLocal，防止内存泄漏

### 6.2 AI 多模型管理

**设计思路**（[AiConfig.java](src/main/java/com/example/evimind/config/AiConfig.java)）：

四种模型提供商（DeepSeek / Zhipu / Qwen / OpenAI）虽然 API 域名不同，但都兼容 OpenAI 的 Chat Completion 格式，所以统一使用 Spring AI 的 `OpenAiApi` 作为底层 HTTP 客户端。

```java
// 启动时为每个 provider 创建一个 ChatClient
Map<String, ChatClient> chatClients = new HashMap<>();
for (provider in [deepseek, zhipu, qianwen, openai]) {
    OpenAiApi api = new OpenAiApi(provider.baseUrl, provider.apiKey);
    OpenAiChatModel model = new OpenAiChatModel(api, options);
    ChatClient client = ChatClient.builder(model).build();
    chatClients.put(provider.name, client);
}
```

**关键设计**：
- `Map<String, ChatClient>` 作为 Bean 注册，RagPipeline 通过 `chatClients.get(provider)` 选择模型
- 如果某个 provider 配置了空的 API Key，跳过（warn 日志），不影响其他 provider
- Embedding 模型也使用 `OpenAiEmbeddingModel`，默认 1536 维（兼容 text-embedding-ada-002）

### 6.3 Prompt 模板系统

**为什么需要模板系统**（[PromptTemplateManager.java](src/main/java/com/example/evimind/config/PromptTemplateManager.java)）：

- Prompt 工程是 AI 应用的核心，需要频繁调整
- 将模板与 Java 代码解耦，修改模板不需要重新编译
- 支持模板变量替换（StringTemplate 语法）

**6 个 Prompt 模板**：

| 模板文件 | 变量 | 用途 |
|---------|------|------|
| `system-prompt.st` | 无 | ChatService 使用的系统提示词，定义 AI 角色 |
| `rag-context-prompt.st` | `context`, `query` | RagPipeline 在证据充足时使用的问答模板 |
| `evidence-sufficient-prompt.st` | `evidence`, `query` | 要求 AI 基于证据回答并标注引用来源 |
| `evidence-insufficient-prompt.st` | `query` | 证据不足时的礼貌拒绝模板 |
| `query-rewrite-prompt.st` | `query` | 改写用户问题以优化检索效果 |
| `summary-prompt.st` | `messages` | 对话总结模板 |

**加载机制**：
```java
@PostConstruct
public void loadTemplates() {
    Resource[] resources = resolver.getResources("classpath:prompts/*.st");
    for (Resource resource : resources) {
        String name = resource.getFilename().replace(".st", "");
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        templates.put(name, content);
    }
}
```

应用启动时自动扫描 `classpath:prompts/` 目录下所有 `.st` 文件并加载到 HashMap。

### 6.4 ORM 双轨策略

| 场景 | 框架 | 原因 |
|------|------|------|
| 大部分业务表 (9 张表) | MyBatis-Plus | 复杂 SQL 可控、Lambda 类型安全查询、分页、自动填充 |
| analysis_report (1 张表) | Spring Data JPA | 简单 CRUD，不需要手写 SQL |
| Elasticsearch | ES Java Client | 原生 API，灵活构建 bool/must/filter 查询 |

**MyBatis-Plus 的典型用法**：
```java
// Lambda 类型安全查询（编译时检查字段名）
kbMemberMapper.selectCount(
    new LambdaQueryWrapper<KbMember>()
        .eq(KbMember::getKnowledgeBaseId, knowledgeBaseId)
        .eq(KbMember::getUserId, userId)
);
```

**自动填充**（MyBatisMetaObjectHandler）：
```java
// 插入时自动设置 createdAt 和 updatedAt
this.setFieldValByName("createdAt", new Date(), metaObject);
this.setFieldValByName("updatedAt", new Date(), metaObject);
```

### 6.5 SSR 流式事件格式

**后端 SSE 事件格式**（[StreamEvent.java](src/main/java/com/example/evimind/model/dto/StreamEvent.java)）：

```json
// 逐 token 事件
{"type":"token","text":"根据"}

// 引用来源事件（在 done 之前发送）
{"type":"citations","citations":[
  {"documentId":5,"fileName":"合同.pdf","chunkIndex":3,"score":0.921}
]}

// 完成事件
{"type":"done","messageId":42}

// 错误事件
{"type":"error","message":"搜索失败: 连接超时"}
```

**为什么自定义 SSE 而不是用 Spring 的 SseEmitter**：
- `Flux<String>` 更灵活，可以精确控制每个事件的内容和顺序
- 与 `ChatClient.stream().content()` 直接对接，无需额外转换
- 引用信息可以在生成过程中发送（如提前检索完成的引用结果）

**前端解析**（原生 fetch + ReadableStream）：
```typescript
const reader = response.body?.getReader()
const decoder = new TextDecoder()
while (true) {
    const { done, value } = await reader.read()
    // 手动按行分割 SSE 格式，提取 JSON payload
    // 按 event.type 分发到 onToken / onCitations / onDone / onError
}
```

选择原生 `fetch` 而非 `EventSource` 的原因：
- `EventSource` 只支持 GET 请求，而发送消息需要 POST
- `EventSource` 不支持自定义请求头（如 Authorization）
- `EventSource` 自动重连机制在对话场景中不适用

---

## 7. 前端架构详解

### 7.1 路由设计

```typescript
// frontend/src/router/index.ts
const routes = [
  { path: '/login',           component: LoginView,       meta: { requiresAuth: false } },
  { path: '/',                component: ChatView,        meta: { requiresAuth: true }  },
  { path: '/knowledge-bases', component: KnowledgeBaseView, meta: { requiresAuth: true }  },
]
```

- 登录页面不要求认证
- 其他页面通过 `beforeEach` 路由守卫检查 `localStorage.accessToken`

### 7.2 组件树

```
App.vue
├── 导航栏 (ElMenu)
│   ├── 首页(Chat)    → /
│   ├── 知识库管理     → /knowledge-bases
│   └── 登录/登出      → /login
│
├── ChatView.vue ★ 核心页面
│   ├── 侧边栏 (.sidebar)
│   │   ├── ElSelect (选择知识库)
│   │   ├── ElButton (新建会话)
│   │   ├── 会话列表 (.conv-item × N) — 点击切换，显示 × 删除
│   │   └── ElSelect (选择模型: DeepSeek/GLM-4/Qwen/OpenAI)
│   │
│   ├── 空状态 (ElEmpty) — 无会话时显示
│   │
│   ├── 消息区 (.messages)
│   │   ├── 用户消息 (.message.user)     — 蓝色气泡，右对齐
│   │   ├── AI 消息 (.message.assistant) — 灰色气泡, Markdown 渲染
│   │   │   └── 引用面板 (ElCollapse)    — 折叠显示引用来源
│   │   └── 打字指示器 (.typing-indicator) — 三个跳动的圆点
│   │
│   └── 输入区 (.input-area)
│       ├── ElInput (消息输入, Enter 发送)
│       └── ElButton (发送按钮, loading 状态)
│
├── KnowledgeBaseView.vue
│   ├── ElTable (知识库列表: 名称/描述/切片策略/状态/操作)
│   ├── ElDialog (创建/编辑知识库表单)
│   └── DocumentView.vue (内嵌文档管理子组件)
│       ├── ElUpload (文档上传, multipart/form-data)
│       ├── ElTable (文档列表: 文件名/大小/摄入状态/时间)
│       └── ElTag (状态标签: 彩色编码)
│
└── LoginView.vue
    └── ElForm (用户名 + 密码 + 登录/注册按钮)
```

### 7.3 状态管理 (Pinia)

**三个 Store 模块**：

#### auth.store.ts

```typescript
export const useAuthStore = defineStore('auth', () => {
  // 状态
  const user = ref<UserInfo | null>(null)
  const isLoggedIn = ref(!!localStorage.getItem('accessToken'))

  // 操作
  async function login(username, password)    { /* 调用 API → 存 token → 设状态 */ }
  async function register(username, password, email) { /* 同上 */ }
  function logout()                           { /* 清 localStorage + 清状态 */ }
  async function fetchUser()                  { /* 获取当前用户，失败则登出 */ }
})
```

#### chat.store.ts

```typescript
export const useChatStore = defineStore('chat', () => {
  // 状态
  const conversations = ref<Conversation[]>([])
  const currentConversation = ref<Conversation | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const selectedKbId = ref<number | undefined>(undefined)

  // 关键操作
  async function sendMessage(content) {
    // 1. 先 push 用户消息到列表
    // 2. push 空的 assistant 消息占位
    // 3. 调用 SSE stream API，通过回调逐步填充 assistantMessage.content
    // 4. 流结束后 onCitations 设置引用，onDone 标记完成
  }
})
```

#### knowledge-base.store.ts

```typescript
export const useKnowledgeBaseStore = defineStore('knowledgeBase', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const selectedKbId = ref<number | undefined>(undefined)
  // CRUD 操作...
})
```

### 7.4 Axios 封装与无感刷新

**请求拦截器**（自动附加 JWT）：
```typescript
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

**响应拦截器**（401 自动无感刷新）：
```typescript
request.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // 1. 用 refreshToken 请求 /auth/refresh
      // 2. 成功后更新 localStorage 中的两个 token
      // 3. 用新 token 重试原请求（error.config）
      // 4. 刷新失败则清空 token 跳转登录
    }
    return Promise.reject(error)
  }
)
```

**关键设计细节**：
- 多个并发请求同时 401 时，只刷新一次令牌（可用闭包锁控制）
- `error.config` 保留了原请求的所有参数，可直接 `request(error.config)` 重试
- 重试仍失败不进入死循环（不会带已失效的 refreshToken）

---

## 8. 核心业务流程

### 8.1 完整 ETL 摄入流水线

```
                          ┌──────────────┐
                          │  用户上传文件  │
                          └──────┬───────┘
                                 │ multipart/form-data
                                 ▼
                    ┌────────────────────────┐
                    │  DocumentController     │
                    │  POST /documents/upload  │
                    └────────────┬───────────┘
                                 │ 保存到 MinIO
                                 │ 插入 document 表 (status=PENDING)
                                 │ 异步触发 EtlPipeline.processDocument()
                                 ▼
              ┌──────────────────────────────────────┐
              │         EtlPipeline                   │
              │                                      │
              │  ┌──────────────────────────────┐    │
              │  │ 1. EXTRACTING                │    │
              │  │   从 MinIO 下载到临时文件      │    │
              │  │   FileExtractorService 调度   │    │
              │  │   ├─ .pdf → PdfFileExtractor  │    │
              │  │   ├─ .docx → WordFileExtractor│    │
              │  │   ├─ .xlsx → ExcelFileExt    │    │
              │  │   ├─ .csv/.json/.txt → Text   │    │
              │  │   └─ .png/.jpg → Base64       │    │
              │  └──────────────────────────────┘    │
              │              │                       │
              │              ▼                       │
              │  ┌──────────────────────────────┐    │
              │  │ 2. CLEANING                  │    │
              │  │   TextCleaner:                │    │
              │  │   ├─ 合并多余空白行             │    │
              │  │   ├─ 移除乱码字符               │    │
              │  │   ├─ 标准化 Unicode            │    │
              │  │   └─ 去除不可打印字符           │    │
              │  └──────────────────────────────┘    │
              │              │                       │
              │              ▼                       │
              │  ┌──────────────────────────────┐    │
              │  │ 3. CHUNKING                  │    │
              │  │   DocumentChunker:            │    │
              │  │   ├─ FIXED_LENGTH: 定长500+100│    │
              │  │   ├─ PARAGRAPH:   按双换行切  │    │
              │  │   └─ SEMANTIC:    段落切(预留) │    │
              │  └──────────────────────────────┘    │
              │              │                       │
              │              ▼                       │
              │  ┌──────────────────────────────┐    │
              │  │ 4. EMBEDDING                 │    │
              │  │   EmbeddingService:           │    │
              │  │   ├─ 批量调用 Embedding API   │    │
              │  │   ├─ 格式化为 pgvector 格式    │    │
              │  │   └─ 写入 chunk_embedding 表  │    │
              │  └──────────────────────────────┘    │
              │              │                       │
              │              ▼                       │
              │  ┌──────────────────────────────┐    │
              │  │ 5. INDEXING                  │    │
              │  │   ElasticsearchIndexService:  │    │
              │  │   ├─ 批量索引到 ES            │    │
              │  │   └─ knowledgeBaseId 过滤字段 │    │
              │  └──────────────────────────────┘    │
              │              │                       │
              │              ▼                       │
              │  status = COMPLETED                  │
              │  chunk_count = N                     │
              └──────────────────────────────────────┘
```

**每个步骤的数据库状态更新**：实时更新 `document.ingestion_status`，前端可轮询显示进度。

### 8.2 RAG 问答流程（流式）

```
用户输入问题: "这份合同的违约责任是什么？"
      │
      ▼
┌─────────────────────────────────────────────┐
│ RagPipeline.streamQuery()                   │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ STEP 1: 权限校验                    │    │
│  │   requireKbMember(knowledgeBaseId)  │    │
│  │   查询 kb_member 表确认当前用户      │    │
│  │   是否有权访问该知识库               │    │
│  └──────────────┬──────────────────────┘    │
│                 ▼                           │
│  ┌─────────────────────────────────────┐    │
│  │ STEP 2: 混合检索                    │    │
│  │   HybridSearchService.search()       │    │
│  │                                      │    │
│  │   CompletableFuture.supplyAsync      │    │
│  │   ┌──────────┐  ┌──────────┐        │    │
│  │   │ PgVector │  │   ES     │        │    │
│  │   │ (语义)   │  │ (关键词)  │        │    │
│  │   └────┬─────┘  └────┬─────┘        │    │
│  │        │ 并行执行      │              │    │
│  │        └──────┬───────┘              │    │
│  │               ▼                      │    │
│  │        RRF 融合排序                   │    │
│  │        返回 Top-10 结果               │    │
│  └──────────────┬──────────────────────┘    │
│                 ▼                           │
│  ┌─────────────────────────────────────┐    │
│  │ STEP 3: 证据评估                    │    │
│  │   计算 avgScore = Σscore / count    │    │
│  │   读取 kb.evidenceThreshold         │    │
│  │                                      │    │
│  │   if results.isEmpty():             │    │
│  │     → evidenceStatus = NO_RESULTS   │    │
│  │     → 渲染 insufficient-prompt      │    │
│  │     → 返回拒绝回答                    │    │
│  │                                      │    │
│  │   if avgScore < threshold:          │    │
│  │     → evidenceStatus = INSUFFICIENT │    │
│  │     → 返回"无法找到可靠证据"          │    │
│  │                                      │    │
│  │   else:                             │    │
│  │     → evidenceStatus = SUFFICIENT   │    │
│  │     → 继续下一步                      │    │
│  └──────────────┬──────────────────────┘    │
│                 ▼                           │
│  ┌─────────────────────────────────────┐    │
│  │ STEP 4: 组装 Prompt                 │    │
│  │   buildContext(results):            │    │
│  │     "[来源1] 文档ID=5 切片#3 (0.92) │    │
│  │      第X条：违约方应支付..."        │    │
│  │     ..."                            │    │
│  │                                      │    │
│  │   模板渲染:                          │    │
│  │     evidence-sufficient-prompt.st    │    │
│  │     + {evidence: context}           │    │
│  │     + {query: userQuery}            │    │
│  └──────────────┬──────────────────────┘    │
│                 ▼                           │
│  ┌─────────────────────────────────────┐    │
│  │ STEP 5: LLM 流式生成               │    │
│  │   chatClient.prompt()               │    │
│  │     .user(prompt)                   │    │
│  │     .stream()                       │    │
│  │     .content()    // Flux<String>   │    │
│  │                                      │    │
│  │   Flux 事件流:                       │    │
│  │    token("根据") →                   │    │
│  │    token("合同") →                   │    │
│  │    token("第")   → ...              │    │
│  │    → citations(json) →              │    │
│  │    → done(messageId)                │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
      │
      ▼  SSE → 前端逐字渲染
```

### 8.3 混合检索 + RRF 融合算法详解

**为什么需要混合检索**：

| 搜索方式 | 原理 | 优势 | 劣势 |
|---------|------|------|------|
| **语义搜索** (PgVector) | 将查询转为向量，与所有文档向量计算余弦相似度 | 理解同义词、理解语义意图 | 可能漏掉精确的关键词匹配 |
| **关键词搜索** (ES) | BM25 算法评估词频和逆文档频率 | 精确匹配专有名词、编号、日期 | 不懂同义词和语境 |

**RRF（Reciprocal Rank Fusion）详细步骤**：

```
输入: semanticResults = [S1(0.9), S2(0.7), S3(0.5)]
      keywordResults  = [K1(2.1), K3(1.8), K2(1.2)]

Step 1: Min-Max 归一化 (两个结果集的分数分布不同)
  S1: (0.9-0.5)/(0.9-0.5) = 1.0
  S2: (0.7-0.5)/(0.9-0.5) = 0.5
  S3: (0.5-0.5)/(0.9-0.5) = 0.0

Step 2: 各自按分数降序排名
  语义通道排名: S1(rank=1), S2(rank=2), S3(rank=3)
  关键词通道排名: K1(rank=1), K3(rank=2), K2(rank=3)

Step 3: 计算 RRF 分数
  RRF(chunk) = 1/(60+rank_semantic) + 1/(60+rank_keyword)

  chunk1 (S1=K1): 1/61 + 1/61 = 0.0328
  chunk2 (S2=K2): 1/62 + 1/63 = 0.0320
  chunk3 (S3=K3): 1/63 + 1/62 = 0.0320

Step 4: 按 RRF 分数降序 → [chunk1, chunk2, chunk3]
```

**k=60 的作用**：
- 平滑极端排名差异（排名 1 vs 排名 2 的差异不会过大）
- k 越小，排名靠前的文档权重越大
- 60 是学术界和工业界的经验最优值

**优雅降级策略**：
```java
try { semanticResults = semanticFuture.get(); }
catch (Exception e) {
    semanticResults = List.of();  // PgVector 挂了，降级为纯关键词
}

try { keywordResults = keywordFuture.get(); }
catch (Exception e) {
    keywordResults = List.of();   // ES 挂了，降级为纯语义
}
```

任何一路失败都不影响整体服务，只是搜索质量暂时下降。

---

## 9. API 接口清单

### 9.1 认证接口 (公开)

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|------|------|--------|------|------|
| POST | `/api/v1/auth/register` | `{username, password, email?}` | `{accessToken, refreshToken, userInfo}` | 用户注册，自动返回令牌 |
| POST | `/api/v1/auth/login` | `{username, password}` | `{accessToken, refreshToken, userInfo}` | BCrypt 验证 + 双令牌 |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` | `{accessToken, refreshToken}` | 用刷新令牌换新访问令牌 |
| GET | `/api/v1/auth/me` | — | `UserInfo` | 获取当前登录用户信息 (需认证) |

### 9.2 知识库接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/knowledge-bases` | 创建知识库（name/description/chunkStrategy/chunkSize/threshold） |
| GET | `/api/v1/knowledge-bases` | 列出当前用户有权限访问的知识库 |
| GET | `/api/v1/knowledge-bases/{id}` | 获取知识库详情 |
| PUT | `/api/v1/knowledge-bases/{id}` | 更新知识库配置 |
| DELETE | `/api/v1/knowledge-bases/{id}` | 删除知识库（级联删除文档/切片/向量） |
| POST | `/api/v1/knowledge-bases/{id}/members` | 添加知识库成员 |
| DELETE | `/api/v1/knowledge-bases/{id}/members/{userId}` | 移除成员 |
| GET | `/api/v1/knowledge-bases/{id}/members` | 列出成员列表 |

### 9.3 文档接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/documents/upload` | 上传文档 (multipart: file + knowledgeBaseId)，自动触发 ETL |
| GET | `/api/v1/documents?knowledgeBaseId=&page=&size=` | 分页列出知识库中的文档 |
| GET | `/api/v1/documents/{id}` | 获取文档详情 |
| DELETE | `/api/v1/documents/{id}` | 删除文档并清理关联数据（MinIO+ES+向量） |
| POST | `/api/v1/documents/{id}/retry` | 重试失败的文档摄入 |

### 9.4 会话与消息接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/conversations?knowledgeBaseId=&modelProvider=` | 创建新会话 |
| GET | `/api/v1/conversations` | 列出用户的所有会话 |
| GET | `/api/v1/conversations/{id}/messages` | 获取会话的所有消息（含引用） |
| POST | `/api/v1/conversations/{id}/messages?role=&content=` | 添加一条消息（非流式） |
| POST | `/api/v1/conversations/{id}/messages/stream` | ★ SSE 流式 RAG 问答 |
| DELETE | `/api/v1/conversations/{id}` | 软删除会话（status=DELETED） |

### 9.5 文件操作与 AI 对话接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/files?dir=` | 列出服务器指定目录的文件 |
| GET | `/api/files/content?path=` | 读取文件内容（带路径遍历保护） |
| GET | `/api/files/analyze?path=&provider=&sessionId=` | SSE 流式 AI 文件分析 |
| GET | `/api/chat/stream?message=&provider=&sessionId=` | SSE 流式 AI 对话（带历史记忆） |

### 9.6 分析与导出接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/analysis/batch` | 启动批量文件分析 |
| POST | `/api/analysis/batch-dir` | 分析目录下所有文件 |
| GET | `/api/analysis/batch/progress?taskId=` | 批量任务进度 |
| GET | `/api/analysis/batch/result?taskId=` | 批量任务结果 |
| GET | `/api/analysis/results?page=&size=` | 所有分析结果分页 |
| GET | `/api/analysis/results/file?path=` | 某文件的分析历史 |
| GET | `/api/analysis/export/markdown?resultIds=&title=` | 导出 Markdown 报告 |
| GET | `/api/analysis/export/pdf?resultIds=&title=` | 导出 PDF 报告 |

### 9.7 公共接口 (无需认证)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/health` | 健康检查，返回 `"UP"` |
| GET | `/swagger-ui.html` | Swagger UI 交互式 API 文档 |
| GET | `/v3/api-docs` | OpenAPI 3.0 JSON 规范 |

---

## 10. 基础设施与部署

### 10.1 Docker Compose 服务编排

```yaml
# docker-compose.yml 定义了 5 个服务

services:
  postgres:       # pgvector/pgvector:pg17 + 健康检查 + 数据卷持久化
  elasticsearch:  # elasticsearch:8.15.3 单节点 + 禁用安全 + 512M堆内存
  minio:          # minio/minio:latest + API:9000 + Console:9001
  backend:        # 多阶段构建: Maven → JRE + 环境变量注入 + 依赖3个服务健康
  frontend:       # 多阶段构建: Node → Nginx + 端口80
```

**服务依赖链**：
```
frontend → backend → postgres (healthcheck)
                   → elasticsearch (healthcheck)
                   → minio (healthcheck)
```

`depends_on` 配合 `condition: service_healthy` 确保启动顺序正确。

### 10.2 后端多阶段 Docker 构建

```dockerfile
# 阶段 1: 构建 (JDK 21 + Maven)
FROM maven:3.9-eclipse-temurin-21 AS builder
COPY . /build
RUN mvn -f /build/pom.xml clean package -DskipTests

# 阶段 2: 运行 (JRE 21)
FROM eclipse-temurin:21-jre
COPY --from=builder /build/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**为什么多阶段**：
- 构建阶段需要 JDK + Maven（约 500MB 镜像）
- 运行阶段只需要 JRE（约 200MB 镜像）
- 最终镜像不包含源代码和构建工具，缩小镜像体积，提高安全

### 10.3 前端多阶段 Docker 构建 + Nginx

```dockerfile
# 阶段 1: Node 构建
FROM node:22 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# 阶段 2: Nginx 运行
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

**Nginx 配置核心**：
```nginx
server {
    listen 80;
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;  # SPA 路由回退
    }
    location /api/ {
        proxy_pass http://backend:8080;     # API 反向代理
        proxy_buffering off;                # SSE 流式不能缓冲
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

`proxy_buffering off` 是 SSE 流式传输的**关键配置**——如果 Nginx 缓冲响应，前端将收不到逐 token 的流式事件，而是等全部生成完才一次性返回。

### 10.4 环境变量管理

```
.env.example                      # 模板文件，提交到 Git
├── POSTGRES_DB=evimind
├── POSTGRES_USER=evimind
├── POSTGRES_PASSWORD=evimind123
├── POSTGRES_PORT=5432
├── ES_URIS=http://localhost:9200
├── MINIO_ACCESS_KEY=minioadmin
├── MINIO_SECRET_KEY=minioadmin
├── JWT_SECRET=xxxxx
├── DEEPSEEK_API_KEY=sk-xxxx
├── ZHIPU_API_KEY=xxxx
├── QIANWEN_API_KEY=sk-xxxx
├── OPENAI_API_KEY=sk-xxxx
├── EMBEDDING_BASE_URL=           # 可选：独立部署的 Embedding 服务
├── EMBEDDING_API_KEY=
├── EMBEDDING_MODEL=
└── DATA_BASE_DIR=/data
```

`application.yml` 中通过 `${VAR_NAME:defaultValue}` 占位符引用环境变量，提供合理的开发默认值。

---

## 11. 安全设计

### 11.1 多层安全防护

```
┌──────────────────────────────────────────┐
│  第一层：Spring Security 过滤器链         │
│  • 公开端点白名单 (auth, swagger, health)  │
│  • 其余全部 require authentication       │
│  • /admin/** 额外 require ROLE_ADMIN      │
│  • CSRF 禁用 (REST API 无状态，用 JWT)     │
│  • SessionCreationPolicy.STATELESS        │
└────────────────┬─────────────────────────┘
                 ▼
┌──────────────────────────────────────────┐
│  第二层：JWT 令牌校验                     │
│  • HMAC-SHA256 签名验证                   │
│  • 过期时间校验                            │
│  • 从令牌中提取 userId + systemRole        │
│  • 注入 SecurityContext + ThreadLocal      │
└────────────────┬─────────────────────────┘
                 ▼
┌──────────────────────────────────────────┐
│  第三层：业务权限隔离                      │
│  • RagPipeline.requireKbMember()          │
│    查询 kb_member 表确认用户有权访问知识库  │
│  • 文档操作必须属于用户的知识库            │
│  • 会话只能查看自己的                      │
└────────────────┬─────────────────────────┘
                 ▼
┌──────────────────────────────────────────┐
│  第四层：输入安全                         │
│  • 路径遍历防护：safePath() 禁止 ../       │
│  • 文件大小限制：50MB (multipart)          │
│  • XSS 防护：前端 DOMPurify 净化 HTML      │
│  • SQL 注入防护：MyBatis-Plus 参数化查询   │
└──────────────────────────────────────────┘
```

### 11.2 Refresh Token 安全存储

```sql
CREATE TABLE refresh_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES sys_user(id),
    token_hash  VARCHAR(256) NOT NULL UNIQUE,  -- SHA-256(原始UUID)
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE  -- 支持远程撤销
);
```

**设计要点**：
- 数据库只存 SHA-256 哈希，不存明文，泄露数据库也不会泄露令牌
- `revoked` 字段支持管理员远程撤销某用户的 Refresh Token
- `expires_at` 到期后即使忘记撤销也会自动失效

---

## 12. 技术决策与选型理由

### 12.1 为什么选 PostgreSQL + pgvector 而不是 Milvus/Weaviate/Pinecone？

| 考量维度 | PgVector | Milvus | Weaviate | Pinecone |
|---------|----------|--------|----------|----------|
| 运维复杂度 | 同一数据库，零运维增量 | 独立集群，运维成本高 | 独立部署 | SaaS 托管 |
| 事务支持 | ACID，向量和元数据原子写入 | 不支持事务 | 不支持事务 | 不支持事务 |
| 规模 | 百万级向量（本项目够用） | 十亿级 | 亿级 | 无限 |
| 成本 | 免费 | 自托管免费 | 自托管免费 | 按量付费 |
| 技术栈统一 | SQL 查询统一 | 需额外 API | 额外 API | 额外 API |

**核心思路**：对于企业内部应用（万-百万级文档），PgVector 完全够用，且能利用 PostgreSQL 的成熟生态（事务、备份、监控、ORM 等）。当数据量增长到千万级以上再考虑迁移到专用向量数据库。

### 12.2 为什么需要 ES + PgVector 双路检索？

| 场景 | PgVector 优势 | ES 优势 |
|------|--------------|--------|
| "合同违约责任" | 理解语义，找到相关条款 | 精确匹配词语 |
| "2024-Q3-001号合同" | 可能误匹配 | 精确倒排索引 |
| "如果对方不付款怎么办" | 理解意图和同义表达 | 难以匹配具体条款 |

在实践中，**两者互补**，RRF 融合后的 NDCG（检索质量指标）通常比单路提升 5-15%。

### 12.3 为什么用 Spring AI 而不是直接调 HTTP API？

| 方案 | 优势 | 劣势 |
|------|------|------|
| 直接 HTTP/RestTemplate | 灵活，无框架依赖 | 需要自己处理重试、错误、流式解析 |
| LangChain4j | 功能全，社区活跃 | 与 Spring 生态集成不够原生 |
| Spring AI | Spring 原生，自动配置，与 Boot 深度集成 | 还是 Milestone 版本，API 可能变动 |

选择 Spring AI 的核心原因是它是 Spring 官方项目，提供了 `ChatClient`、`EmbeddingModel`、`ChatMemory` 等开箱即用的组件，且与 Spring Boot 3.x 无缝集成。

### 12.4 为什么用 MinIO 而不是直接存文件系统？

- **S3 API 兼容**：未来可无缝切换到 AWS S3、阿里云 OSS、腾讯云 COS
- **自带管理界面**：端口 9001 的 Web Console，可直接浏览/上传/删除文件
- **高可用**：支持分布式多节点部署
- **Docker 友好**：一个命令即可部署

### 12.5 为什么前后端分离 + Docker Compose 部署？

```
开发模式：Vite dev server (5173) → proxy → Spring Boot (8080)
生产模式：Nginx (80) → 静态文件 + proxy → Spring Boot (8080)
```

- 前后端独立开发、独立构建、独立部署
- Docker Compose 一键启动全部基础设施
- 通过 Nginx 统一入口，避免跨域问题

---

## 13. 面试技术点深度解析

### 13.1 RAG 核心原理

**RAG (Retrieval-Augmented Generation)** = 检索 + 生成，在 LLM 回答问题之前，先从外部知识库中检索相关信息，将检索结果注入 Prompt，让模型基于真实数据生成答案。

**为什么需要 RAG**：
1. **解决幻觉**：LLM 的知识来自训练数据，可能编造不存在的事实。RAG 强制它基于"证据"回答
2. **知识更新**：LLM 训练数据有截止日期，RAG 通过检索最新文档实现实时知识更新
3. **私有数据**：企业内部的文档 LLM 从未见过，RAG 让 LLM 能"读"到这些文档
4. **可追溯性**：每个回答都有引用来源，用户可验证答案的可靠性

**RAG 的三种范式演变**：
| 范式 | 描述 | 本项目的实现 |
|------|------|-------------|
| Naive RAG | 检索→生成，无质量判断 | 跳过证据评估 |
| Advanced RAG | 检索前查询优化 + 检索后重排序 | 查询改写 + RRF 融合重排序 |
| Modular RAG | 可插拔的模块化设计 | 可切换检索策略、评估阈值、Prompt 模板 |

### 13.2 为什么需要 Embedding？

**Embedding（嵌入）** 是将文本转化为固定长度的浮点数向量的技术。

```
"苹果很好吃" → [0.23, -0.15, 0.87, ..., -0.42]  (1536维)
"这个手机很好用" → [0.21, -0.18, 0.91, ..., -0.37]  (1536维)
```

- 语义相近的文本，向量在空间中距离近
- 通过计算向量之间的余弦相似度来判断文本相关性
- 1536 是 OpenAI text-embedding-ada-002 的维度，本项目的默认配置

**余弦相似度公式**：
```
cos_sim(A, B) = A·B / (|A| × |B|)
```
值域 [-1, 1]，越接近 1 表示越相似。数据库查询用 `1 - cos_sim` 作为距离。

### 13.3 文本切片策略详解

**为什么需要切片**：
1. LLM 的上下文窗口有限（虽然现在越来越长），单次 Prompt 不能超过限制
2. 向量模型通常有输入长度上限（如 8191 tokens）
3. 更短的切片粒度更高，检索更精准
4. 只把最相关的片段放入 Prompt，减少干扰信息

**三种策略对比**：

| 策略 | 方法 | 优点 | 缺点 | 适用场景 |
|------|------|------|------|---------|
| FIXED_LENGTH | 固定字符数窗口滑动，带 overlap | 实现简单，切片大小可控 | 可能在句子中间切断 | 格式不规整的文本 |
| PARAGRAPH | 按双换行符切分，超过上限再回溯 | 保持段落完整性 | 段落长短不一 | 结构化文档、技术文档 |
| SEMANTIC | 按语义边界切（基于相似度） | 语义单元完整 | 需要额外的分割模型 | 高质量要求场景 |

**Overlap 的数学原理**：
```
无 overlap:  [A B C | D E F] [G H I | J K L]  ← "D E F" 和 "G H I" 可能语义连贯但被切断
有 overlap:  [A B C | D E F] [D E F | G H I]  ← 重叠部分保证语义连续性
```

### 13.4 BM25 算法原理

BM25（Best Match 25）是 Elasticsearch 默认的评分算法，是 TF-IDF 的改进版。

```
BM25(q, d) = Σ IDF(qi) × [f(qi,d) × (k1+1)] / [f(qi,d) + k1 × (1-b + b×|d|/avgDL)]

其中：
  IDF(qi)     = 逆文档频率，衡量词的稀有程度
  f(qi,d)     = 词频，词在文档中出现的次数
  |d|         = 当前文档长度
  avgDL       = 平均文档长度
  k1 (≈1.5)   = 词频饱和度参数，控制词频的影响上限
  b (≈0.75)   = 长度归一化参数，控制文档长度的影响
```

**BM25 vs TF-IDF 的改进**：
- **词频饱和度**：词出现 100 次不比 10 次重要 10 倍，BM25 用非线性函数限制词频的边际收益
- **长度归一化**：长文档不会仅仅因为长就获得更高分数

### 13.5 RRF 为什么有效？

RRF 的核心思想来自信息检索的**排名聚合**理论：

1. 不同检索系统的**绝对分数不可比**（PgVector 的余弦分数 vs ES 的 BM25 分数完全不同的量纲）
2. 但**排名（rank）是可比的**（排第一就是第一，不管分数是多少）
3. 用 1/(k+rank) 将排名转为分数，k=60 做平滑处理
4. 同时在两个系统中排名都靠前的文档获得最高综合分

**数学直觉**：
```
rank=1  → 1/61 ≈ 0.0164
rank=2  → 1/62 ≈ 0.0161
rank=10 → 1/70 ≈ 0.0143
rank=50 → 1/110≈ 0.0091
```

排名越靠前，权重越大，但差距平滑（第 1 名和第 2 名差距很小）。

### 13.6 JWT 双令牌机制设计原理

**Access Token（AT）vs Refresh Token（RT）分工**：

| 考量 | Access Token | Refresh Token |
|------|-------------|---------------|
| **生命周期** | 短（1小时） | 长（7天） |
| **传输频率** | 每次请求 | 仅在刷新时 |
| **泄露风险** | 高（频繁传输） | 低（偶尔传输） |
| **应对泄露** | 短有效期限制危害 | 服务端可撤销 |
| **验证方式** | 本地验签（无状态） | 查数据库（有状态） |

**为什么不用 Session**：
- Session 需要服务端存储，不方便水平扩展
- JWT 是无状态的，任何服务实例都能独立验证
- 适合微服务架构

**本项目实现的安全措施**：
1. 密码用 BCrypt（自适应哈希）存储，即使数据库泄露也无法还原
2. Refresh Token 数据库只存 SHA-256 哈希，不存明文
3. 支持 `revoked` 字段远程撤销
4. JWT 签名使用 HMAC-SHA256，密钥通过环境变量注入

### 13.7 SSE vs WebSocket vs 轮询

| 方案 | 方向 | 协议 | 复杂度 | 适用场景 |
|------|------|------|--------|---------|
| SSE | 服务端→客户端 | HTTP | 低 | LLM 流式输出、日志推送、通知 |
| WebSocket | 双向 | WS | 中 | 即时通讯、协作编辑、游戏 |
| 轮询 | 客户端→服务端 | HTTP | 低 | 低频状态查询、进度监控 |

**本项目选 SSE 的原因**：
- AI 对话本质是"请求→流式响应"，只需服务端推送
- SSE 基于 HTTP，不需要额外协议升级
- 浏览器原生支持 `EventSource`（或 fetch + ReadableStream）
- 自动重连机制（虽然本项目用了手动 fetch 更灵活）
- Nginx 代理配置简单（只需关掉缓冲）

### 13.8 ThreadLocal 上下文传递

```java
// GroupContext.java
public class GroupContext {
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public static void set(Long userId, Long groupId, String systemRole) {
        CONTEXT.set(new Context(userId, groupId, systemRole));
    }

    public static Long getUserId() { return CONTEXT.get().userId; }
    public static void clear() { CONTEXT.remove(); }  // ★ 必须清理
}
```

**使用 ThreadLocal 的原因**：
- 在 Controller → Service → Mapper 整个调用链中任意位置都能获取当前用户信息
- 不需要在每个方法签名里加 `Long userId` 参数
- 比 Spring Security 的 `SecurityContextHolder` 更轻量

**内存泄漏风险**：
- 线程池复用时，上一次请求的 ThreadLocal 值可能残留
- **必须在 finally 块中调用 `clear()`**
- 本项目在 `JwtAuthenticationFilter.doFilterInternal()` 的 finally 中清理

### 13.9 CompletableFuture 并行搜索

```java
CompletableFuture<List<SearchResult>> semanticFuture =
    CompletableFuture.supplyAsync(() -> pgVectorSearchService.search(...));
CompletableFuture<List<SearchResult>> keywordFuture =
    CompletableFuture.supplyAsync(() -> elasticsearchSearchService.search(...));

List<SearchResult> semanticResults = semanticFuture.get();
List<SearchResult> keywordResults = keywordFuture.get();
```

**为什么用 CompletableFuture**：
- PgVector 和 ES 的搜索完全独立，并行执行可将延迟从 `T1+T2` 降到 `max(T1,T2)`
- 使用默认 ForkJoinPool（commonPool），不需要额外线程池管理
- `get()` 是阻塞调用，但此时两个 Future 已经并行运行，总等待时间约等于最慢的那个

**为什么不直接用 `parallelStream`**：
- parallelStream 的线程数不可控
- CompletableFuture 可以精细处理异常（try-catch 每路单独降级）

### 13.10 策略模式在文件提取中的应用

```java
// 抽象策略接口
public interface FileContentExtractor {
    boolean supports(String fileName);  // 判断能否处理该文件
    ExtractionResult extract(Path filePath, int maxSize);  // 执行提取
}

// 具体策略
public class PdfFileExtractor implements FileContentExtractor {
    public boolean supports(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf");
    }
    public ExtractionResult extract(Path filePath, int maxSize) {
        // PDFBox 提取逻辑
    }
}

// 调度器（自动注入所有实现类）
@Service
public class FileExtractorService {
    private final List<FileContentExtractor> extractors;
    // Spring 会自动收集所有 FileContentExtractor 的实现注入到 List

    public ExtractionResult extractFile(Path filePath, int maxSize) {
        for (FileContentExtractor extractor : extractors) {
            if (extractor.supports(fileName)) {
                return extractor.extract(filePath, maxSize);
            }
        }
        return ExtractionResult.failure("不支持的文件格式");
    }
}
```

**策略模式的优点**：
- 新增文件格式支持只需新增一个实现类，不需修改调度器
- 每个提取器职责单一，易于测试和维护
- Spring 的依赖注入让策略收集自动化

### 13.11 Flyway 数据库版本管理

```
db/migration/
├── V1__init_postgresql_schema.sql   # 基准版本
├── V2__add_citations_column.sql     # 增量：加字段
├── V3__add_summary_column.sql       # 增量：加字段
└── V4__add_system_role_column.sql   # 增量：加字段
```

**Flyway 工作原理**：
1. 启动时连接数据库，检查 `flyway_schema_history` 表
2. 扫描 `classpath:db/migration` 中未执行的 SQL 文件
3. 按版本号排序依次执行
4. 每执行一个，记录到 `flyway_schema_history` 表
5. 已执行的脚本的 checksum 不能变，否则启动失败

**为什么不用 Hibernate ddl-auto**：
- `ddl-auto: update` 在生产环境极其危险（可能误删列）
- Flyway 是显式的、版本化的、可审计的、可回滚的
- 团队协作时，数据库变更有明确的顺序和所有权

### 13.12 JSONB 在消息引用中的应用

```sql
-- 存储格式
citations JSONB = '[{"documentId":5,"chunkIndex":3,"score":0.921}]'

-- 查询示例：找到引用了某个文档的所有消息
SELECT * FROM message WHERE citations @> '[{"documentId":5}]';

-- 查询示例：引用评分高于 0.9 的消息
SELECT * FROM message WHERE citations @> '[{"score":0.9}]';
```

**JSONB vs JSON**：
| 特性 | JSON | JSONB |
|------|------|-------|
| 存储格式 | 原始文本 | 二进制解析后 |
| 写入速度 | 快（直接存） | 慢（需解析） |
| 查询速度 | 慢（每次解析） | 快（已解析，可索引） |
| 索引支持 | 不支持 | GIN 索引 |
| 本项目选择 | — | JSONB（读多写少） |

### 13.13 Docker 健康检查和服务依赖

```yaml
postgres:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U evimind"]
    interval: 10s
    timeout: 5s
    retries: 5

backend:
  depends_on:
    postgres:
      condition: service_healthy  # ★ 不是 service_started
```

**`service_healthy` vs `service_started`**：
- `service_started`：容器启动了但 PostgreSQL 可能还没准备好接受连接
- `service_healthy`：等到 healthcheck 通过，保证数据库已就绪
- 避免后端启动时数据库未就绪导致的连接失败

### 13.14 Nginx 对 SSE 的特殊处理

```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_buffering off;           # ★ 关键：禁用响应缓冲
    proxy_cache off;               # 禁用缓存
    proxy_set_header Connection '';
    proxy_http_version 1.1;        # HTTP/1.1 支持长连接
    chunked_transfer_encoding on;  # 分块传输
}
```

**如果不禁用缓冲会怎样**：
- Nginx 默认会缓冲后端响应，凑满一定大小再发给客户端
- SSE 的 token 事件会被囤积，用户看到的是"等几秒后突然冒出一大段文字"
- 与"逐字输出"的产品体验相悖

---

## 14. 项目亮点与改进方向

### 14.1 当前架构亮点

1. **完整的 RAG 工程闭环**：从文档摄入到 AI 回答，每个环节都有生产级实现
2. **混合检索 + RRF 融合**：语义 + 关键词双路并行，无监督融合，业界实践方案
3. **优雅降级设计**：任何一路检索失败都不影响整体服务可用性
4. **多模型运行时切换**：基于 OpenAI-compatible API 的 Provider 抽象，四家模型热切换
5. **JWT 双令牌**：无感刷新 + 远程撤销，用户体验和安全性兼顾
6. **ThreadLocal + Filter**：轻量级上下文传递范式
7. **Prompt 模板文件化**：模板与代码分离，AI 行为可独立迭代
8. **Docker 多阶段构建**：镜像瘦身（JDK→JRE, Node→Nginx）
9. **Flyway 数据库版本管理**：数据库变更有审计可追溯
10. **策略模式**：文件提取器、切片策略均可扩展

### 14.2 可改进方向

| 方向 | 现状 | 改进方案 |
|------|------|---------|
| **向量索引** | IVFFlat (近似搜索) | 升级为 HNSW 索引（PgVector 支持），查询更快 |
| **检索质量** | 单轮检索 | 增加查询改写（query-rewrite-prompt 已预留）、HyDE（假设文档嵌入） |
| **重排序** | 仅 RRF 融合 | 增加 Cross-Encoder 重排序模型，提升精排效果 |
| **记忆管理** | Spring AI 内置 InMemoryChatMemory | 持久化到 Redis，支持分布式部署 |
| **并发刷新** | 多个 401 同时刷新 | 加互斥锁，多个并发请求共享一次刷新结果 |
| **文件格式** | PDF/Word/Excel/Text | 增加图片 OCR、PPT、EPUB 等格式支持 |
| **监控** | 无 | 集成 Micrometer + Prometheus + Grafana |
| **流式解析** | 前端手动 fetch + ReadableStream | 使用 `@microsoft/fetch-event-source` 库简化 SSE 解析 |
| **知识图谱** | 无 | 增量构建实体关系图谱，支持多跳推理 |
| **权限模型** | 知识库级 | 增加文档级权限控制 |
| **异步任务** | 无持久化 | 集成消息队列（RabbitMQ/Kafka）保证 ETL 任务可靠性 |
| **测试覆盖** | 3 个单元测试 | 增加集成测试、API 测试、前端组件测试 |

---

> 项目持续迭代中。如需了解更多实现细节，请查阅 `.codeartsdoer/specs/` 下的规格说明文档。
