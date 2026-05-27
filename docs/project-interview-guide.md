# EviMind 项目面试解读文档

本文档用于面试、答辩和项目复盘。它不是泛泛的项目介绍，而是围绕“我做了什么、为什么这样做、核心链路怎么跑、遇到问题怎么解释”来组织。

## 1. 项目一句话介绍

EviMind 是一个面向文档知识库的 RAG 智能问答与证据分析平台。用户可以创建知识库、上传文档，系统自动完成文本提取、清洗、切片、向量化、关键词索引，然后在提问时通过语义检索和关键词检索召回证据，使用 RRF 融合排序和证据充分性判断，最后调用大模型生成带引用来源的流式回答。

面试中可以这样说：

> 这个项目的目标不是做一个普通聊天机器人，而是做一个“可追溯证据”的知识库问答系统。核心价值是把上传文档变成可检索证据，并且让模型回答受证据约束，减少幻觉，同时给出文档、切片和相关度引用。

## 2. 技术栈总览

### 后端

- Java 21
- Spring Boot 3.5.0
- Spring Web、Validation、Security
- Spring AI 1.0.0-M1
- Reactor Flux，用于流式响应
- MyBatis-Plus 3.5.12
- Spring Data JPA
- Flyway 数据库迁移
- JJWT 0.12.6
- PDFBox、Apache POI、OpenPDF
- Springdoc OpenAPI / Swagger

### 前端

- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia
- Vue Router
- Axios
- Markdown-It
- highlight.js
- DOMPurify
- fetch streaming，用于解析后端 SSE 风格流式数据

### 数据与中间件

- PostgreSQL 17
- pgvector
- Elasticsearch 8.15.3
- MinIO
- H2，本地 standalone/dev 模式使用
- Nginx，前端容器部署使用
- Docker Compose，一键编排 PostgreSQL、Elasticsearch、MinIO、后端、前端

### AI 与 RAG

- Spring AI OpenAI 兼容接口
- DeepSeek、智谱 GLM、通义千问、OpenAI 多供应商配置
- EmbeddingModel 生成向量
- pgvector 语义检索
- Elasticsearch 关键词检索
- SimpleKeywordSearch 本地关键词降级检索
- RRF 融合排序
- Evidence Portfolio 证据组合选择
- evidence threshold 证据充分性阈值
- Prompt Template 约束模型只基于证据回答

## 3. 项目模块结构

后端主包路径是 `src/main/java/com/example/evimind`。

| 模块 | 作用 |
|---|---|
| `auth` | 注册、登录、JWT、刷新令牌 |
| `config` | 安全配置、AI 客户端、MinIO、ES、MyBatis、Prompt 模板 |
| `knowledgebase` | 知识库 CRUD、成员权限、切片策略参数 |
| `document` | 文档上传、格式限制、异步入库触发 |
| `ingestion` | ETL 入库流水线：提取、清洗、切片、向量化、索引 |
| `retrieval` | pgvector、ES、本地关键词检索、RRF 融合 |
| `qa` | RAG 主链路、证据充分性判断、证据组合选择、引用构建 |
| `assistant` | 会话、消息、流式问答入口 |
| `extractor` | PDF、Word、Excel、文本文件提取 |
| `storage` | MinIO 和本地文件存储 |
| `service` | 引用导出、批量分析、报告导出、科研笔记等业务服务 |
| `controller` | 分析、文件、引用、笔记等 API |

前端主路径是 `frontend/src`。

| 模块 | 作用 |
|---|---|
| `views` | 登录、聊天、知识库、文档、分析、引用、笔记页面 |
| `stores` | Pinia 状态管理，包括认证、知识库、聊天 |
| `api` | 前端 API 封装 |
| `types` | TypeScript 类型定义 |
| `components/AppShell.vue` | 工作台主框架、导航和主题 |
| `utils/request.ts` | 请求封装和 token 处理 |

## 4. 核心业务流程

### 4.1 用户认证流程

核心类：

- `AuthController`
- `AuthService`
- `TokenProvider`
- `JwtAuthenticationFilter`
- `SecurityConfig`

流程：

1. 用户注册时提交用户名、密码、邮箱。
2. `AuthService.register()` 检查用户名是否已存在。
3. 密码通过 BCrypt 加密后存入 `sys_user`。
4. 登录成功后生成 access token 和 refresh token。
5. refresh token 不明文保存，只保存 SHA-256 hash。
6. 每个 API 请求经过 `JwtAuthenticationFilter`，解析 Bearer token，将用户身份写入上下文。
7. `SecurityConfig` 设置无状态会话，并开放登录、注册、健康检查、Swagger 和前端静态资源。

面试可讲亮点：

- 使用 JWT 实现无状态认证，适合前后端分离和容器化部署。
- access token 和 refresh token 分离，提高会话续期能力。
- refresh token 只存 hash，即使数据库泄露也不会直接暴露可用 token。
- 业务层再做知识库成员权限校验，不只依赖接口层认证。

### 4.2 知识库创建与权限流程

核心类：

- `KnowledgeBaseController`
- `KnowledgeBaseService`
- `KbMemberMapper`
- `GroupContext`

流程：

1. 用户创建知识库。
2. 系统写入知识库基本信息：名称、描述、证据阈值、切片策略、切片大小、overlap。
3. 创建者自动成为该知识库的 `OWNER`。
4. 访问知识库列表时，只返回当前用户参与的知识库。
5. 更新、删除、添加成员等操作要求用户是 `OWNER`。
6. 普通访问要求用户是知识库成员，管理员可绕过部分成员检查。

关键设计：

- 知识库不仅是文档集合，也是权限边界。
- `evidence_threshold` 是 RAG 回答可信度控制的重要参数。
- `chunk_strategy/chunk_size/chunk_overlap` 允许不同知识库配置不同切片策略。

### 4.3 文档上传与入库流程

核心类：

- `DocumentController`
- `DocumentService`
- `EtlPipeline`
- `MinioStorageService`
- `LocalFileStorageService`

流程：

1. 前端选择知识库并上传文件。
2. 后端检查用户是否是知识库成员。
3. 检查文件格式和大小，目前限制为 50MB。
4. 支持格式包括 PDF、Word、Excel、CSV、JSON、Markdown、TXT、代码文件、日志、LaTeX 等。
5. 生产模式下文件写入 MinIO；standalone 模式下写入本地文件系统。
6. 数据库 `document` 表记录文件名、格式、大小、存储路径、入库状态。
7. `DocumentService.triggerIngestionAsync()` 异步触发 ETL 入库，避免上传接口长时间阻塞。

文档状态变化：

```text
PENDING -> EXTRACTING -> CLEANING -> CHUNKING -> EMBEDDING -> INDEXING -> COMPLETED
```

如果中间失败：

```text
FAILED
```

面试可讲亮点：

- 上传和入库解耦，上传接口只负责落库和触发任务。
- 入库状态可被前端展示，用户能知道文档是否处理完成。
- 支持 MinIO 和本地存储两种模式，兼顾生产部署和本地演示。

### 4.4 ETL 入库流水线

核心类：

- `EtlPipeline`
- `FileExtractorService`
- `TextCleaner`
- `DocumentChunker`
- `EmbeddingService`
- `ElasticsearchIndexService`
- `AcademicPdfMetadataExtractor`

完整流程：

1. 根据文档存储路径下载文件。
2. 通过 `FileExtractorService` 按文件类型提取文本。
3. 如果是 PDF，尝试提取论文元数据，例如 DOI、作者、年份、期刊。
4. 使用 `TextCleaner` 清洗文本。
5. 调用模型为文档生成摘要，写回 `document.summary`。
6. 根据知识库配置执行切片。
7. 保存切片到 `document_chunk` 表。
8. 调用 `EmbeddingService` 生成向量。
9. 保存向量到 `document_chunk_embedding` 表。
10. 将切片同步到 Elasticsearch，建立关键词检索索引。
11. 更新文档状态为 `COMPLETED`，记录切片数量。

### 4.5 文档切片策略

核心类：`DocumentChunker`

支持三种策略：

| 策略 | 说明 |
|---|---|
| `FIXED_LENGTH` | 按固定长度切分，简单稳定 |
| `PARAGRAPH` | 按段落聚合到目标长度，更适合普通文档 |
| `SEMANTIC` | 按句子聚合，尽量保持语义完整 |

关键参数：

- `chunkSize`：目标切片长度，默认 500。
- `overlap`：相邻切片重叠长度，默认 100。

为什么需要 overlap：

- 避免答案所需上下文被切在两个 chunk 中间。
- 提高召回率，尤其适合跨句、跨段的问题。

面试可讲：

> 切片不是越小越好。太小会丢上下文，太大会降低检索精度和增加 prompt 成本。所以项目把 chunk size 和 overlap 做成知识库级配置，允许按文档类型调优。

## 5. RAG 问答核心链路

### 5.1 前端到后端的流式链路

核心文件：

- `frontend/src/stores/chat.store.ts`
- `frontend/src/api/chat.ts`
- `ConversationController`
- `ConversationService`
- `RagPipeline`

流程：

1. 用户在 `ChatView` 输入问题。
2. Pinia 的 `chat.store.ts` 先把用户消息插入本地消息列表。
3. 前端创建一个空的 assistant 消息，用于边收边展示。
4. `chatApi.streamMessage()` 使用 `fetch` 请求：

```text
POST /api/v1/conversations/{id}/messages/stream
```

5. 请求头携带 `Authorization: Bearer <token>`。
6. 后端 `ConversationController.streamMessage()` 返回 `Flux<String>`。
7. 前端不断读取 response body，并解析 `token`、`citations`、`done`、`error` 等事件。
8. token 事件实时追加到 assistant 消息内容。
9. citations 事件写入引用列表。

为什么不用普通 HTTP 一次性返回：

- 大模型生成可能耗时较长。
- 流式返回能降低首字等待时间。
- 用户可以看到答案逐步生成，交互体验更好。

### 5.2 后端 RAG 主流程

核心类：`RagPipeline`

流程：

1. `requireKbMember()` 校验当前用户是否有权限访问知识库。
2. 查询知识库配置，获取 evidence threshold。
3. 调用 `HybridSearchService.search()`，从知识库中召回候选证据。
4. 如果没有结果，返回证据不足模板。
5. 如果证据置信度低于阈值，返回证据不足模板。
6. 如果证据充分，调用 `EvidencePortfolioSelector` 选择最终证据组合。
7. 使用 `PromptTemplateManager` 渲染 evidence-sufficient prompt。
8. 根据模型供应商选择对应 `ChatClient`。
9. 调用大模型生成回答。
10. 流式模式下持续输出 token。
11. 最后追加 citations 和 done 事件。

关键点：

- 先检索，再判断证据是否足够。
- 不足时不让模型自由发挥，而是返回“当前知识库没有足够证据”的提示。
- 足够时把证据拼进 prompt，要求模型基于证据回答。
- 引用信息来自最终证据组合，而不是所有候选结果。

### 5.3 混合检索设计

核心类：

- `HybridSearchService`
- `PgVectorSearchService`
- `ElasticsearchSearchService`
- `SimpleKeywordSearchService`

检索流程：

1. 根据用户问题并行发起语义检索和关键词检索。
2. 语义检索使用 EmbeddingModel 生成 query vector。
3. pgvector 根据向量相似度在 `document_chunk_embedding` 中查找相近切片。
4. Elasticsearch 在 `document_chunk` 内容索引中做关键词 match。
5. 两路检索都按 `knowledgeBaseId` 过滤，防止跨知识库泄露。
6. 每个后端设置超时时间，默认 1500ms。
7. 如果 ES 不可用，尝试 `SimpleKeywordSearchService` 本地关键词降级。
8. 如果某一路失败，另一条链路仍然可以返回结果。
9. 最后交给 RRF 融合排序。

为什么要混合检索：

- 语义检索擅长处理同义表达、概念相近的问题。
- 关键词检索擅长精确术语、编号、公式、专有名词。
- RAG 场景中两者互补，单独使用一种检索容易漏召回。

### 5.4 RRF 融合排序

核心类：`RrfFusionService`

RRF 是 Reciprocal Rank Fusion，即倒数排名融合。它不强依赖不同检索系统的原始分数可比性，而是根据每个结果在各检索列表中的排名来加权。

项目中的处理：

1. 先对语义检索和关键词检索各自做分数归一化。
2. 使用 `documentId#chunkIndex` 作为融合 key，避免同一切片重复出现。
3. 对每个结果累加不同来源的排名贡献。
4. 根据 active source 数量计算理想 RRF 分数。
5. 最终置信分数由排名置信度和来源分数共同决定：

```text
score = 0.75 * rankConfidence + 0.25 * sourceConfidence
```

面试可以这样解释：

> 不同检索后端的分数尺度不一样，pgvector 的相似度和 ES 的 BM25 分数不能直接相加。RRF 更看重结果在各自列表里的相对排名，能稳定融合语义和关键词召回。项目里还把同一个文档切片用 documentId#chunkIndex 去重，避免重复证据污染 prompt。

### 5.5 证据充分性判断

核心类：`RagPipeline.hasSufficientEvidence()`

逻辑：

1. 如果没有检索结果，证据不足。
2. 如果知识库没有配置阈值，默认认为证据可用。
3. 如果配置了 threshold，计算 evidence confidence。
4. confidence 低于 threshold 时，不进入大模型生成。

confidence 计算方式：

```text
evidenceConfidence = 0.70 * topScore + 0.30 * topSupportAverage
```

其中：

- `topScore` 是最高分证据。
- `topSupportAverage` 是前 3 个结果的平均分。

为什么不只看 top1：

- 只看 top1 容易被偶然命中误导。
- 加入前几个结果的平均支持度，可以判断是否有多条证据共同支持。
- 这样能降低“检索到一条弱相关材料就强行回答”的风险。

### 5.6 Evidence Portfolio 证据组合选择

核心类：`EvidencePortfolioSelector`

作用：

不是简单把 topK 全塞进 prompt，而是在上下文预算内选择更有价值的一组证据。

选择因素：

- 置信度：检索分数越高越好。
- 覆盖增益：是否覆盖用户问题中的关键词/概念。
- 多样性：优先覆盖不同文档。
- 冗余惩罚：避免选入内容高度重复的切片。
- 上下文预算：默认最大证据上下文长度由配置控制。

打分思想：

```text
value = 0.62 * confidence
      + 0.22 * coverageGain
      + 0.12 * diversityBonus
      - 0.24 * redundancyPenalty
```

面试可讲亮点：

> RAG 不只是检索 topK，还要控制 prompt 预算和证据冗余。这个项目在最终 prompt 前增加 evidence portfolio 选择，用置信度、覆盖、文档多样性和冗余惩罚来挑选证据，避免把重复或边缘相关内容塞给模型。

### 5.7 Prompt 约束

核心资源：

- `evidence-sufficient-prompt.st`
- `evidence-insufficient-prompt.st`
- `query-rewrite-prompt.st`
- `summary-prompt.st`
- `system-prompt.st`

核心策略：

- 证据充分时：要求模型只能基于检索证据回答，并对关键结论标注来源。
- 证据不足时：直接返回无法确认，不让模型编造。
- 文档摘要生成：只基于文档开头内容生成简要摘要。

面试可讲：

> 项目把“证据充分”和“证据不足”分成两个 prompt 分支。这样不是把所有问题都交给大模型，而是先用检索结果做门控，只有证据达到阈值才生成答案。

## 6. 数据库设计解读

核心表：

| 表 | 作用 |
|---|---|
| `sys_user` | 用户账号 |
| `sys_group` | 组织/团队 |
| `group_member` | 团队成员关系 |
| `knowledge_base` | 知识库配置 |
| `kb_member` | 知识库成员与角色 |
| `document` | 文档元数据和入库状态 |
| `document_chunk` | 文档切片 |
| `document_chunk_embedding` | 切片向量 |
| `conversation` | 会话 |
| `message` | 消息和引用 |
| `analysis_result` | 批量分析结果 |
| `refresh_token` | 刷新令牌 hash |
| `research_note` | 科研笔记 |

关键关系：

- 一个用户可以创建多个知识库。
- 一个知识库可以有多个成员。
- 一个知识库下有多个文档。
- 一个文档切成多个 chunk。
- chunk 对应 embedding，用于向量检索。
- conversation 绑定用户和知识库。
- message 保存对话内容、引用和工具调用信息。

面试讲法：

> 数据模型的核心是 knowledge_base -> document -> document_chunk -> document_chunk_embedding。知识库是权限和配置边界，document 是原始文件元数据，chunk 是 RAG 检索最小单位，embedding 是语义检索索引。会话和消息再引用这些证据，形成可追溯回答。

## 7. 前端设计解读

### 7.1 页面

| 页面 | 说明 |
|---|---|
| `LoginView.vue` | 登录/注册 |
| `ChatView.vue` | RAG 问答主界面 |
| `KnowledgeBaseView.vue` | 知识库管理 |
| `DocumentView.vue` | 文档上传、状态查看 |
| `AnalysisView.vue` | 文件批量分析 |
| `CitationView.vue` | BibTeX/APA 引用导出 |
| `NotesView.vue` | 文档切片批注和科研笔记 |

### 7.2 状态管理

| Store | 说明 |
|---|---|
| `auth.store.ts` | token、用户信息、登录状态 |
| `knowledge-base.store.ts` | 知识库列表、当前知识库 |
| `chat.store.ts` | 会话列表、当前会话、消息、流式生成状态 |

### 7.3 流式消息解析

前端没有使用浏览器 `EventSource`，而是用 `fetch` + `ReadableStream`：

1. 发送 POST 请求，便于携带 JSON body。
2. 读取 `response.body.getReader()`。
3. 使用 `TextDecoder` 增量解码。
4. 按行拆分，识别 `data:` 前缀。
5. 将 JSON event 分为 token、citations、done、error。

这种方式比 EventSource 更灵活，因为 EventSource 主要适合 GET，而聊天请求需要 POST body 和模型参数。

## 8. 部署与运行模式

### 8.1 standalone/dev 模式

特点：

- 使用 H2 本地数据库。
- 使用本地文件存储。
- MinIO 关闭。
- Flyway 关闭，使用 H2 schema。
- Elasticsearch 可选，不可用时降级本地关键词检索。
- 适合本地演示和开发。

### 8.2 Docker Compose 生产式模式

服务：

- `postgres`：pgvector/pgvector:pg17
- `elasticsearch`：elasticsearch:8.15.3
- `minio`：对象存储
- `backend`：Spring Boot 应用
- `frontend`：Nginx 托管 Vue 构建产物

端口：

- 后端：8080
- 前端：5173 映射到容器 80
- PostgreSQL：5432
- Elasticsearch：9200/9300
- MinIO：9000/9001

面试讲法：

> 项目支持轻量本地运行和完整中间件部署两种模式。standalone 模式降低演示门槛，Docker Compose 模式更接近真实生产环境，能使用 PostgreSQL/pgvector、Elasticsearch 和 MinIO 完整能力。

## 9. 项目亮点

### 9.1 不是普通 AI 聊天，而是证据驱动问答

普通聊天系统只把用户问题发给模型，而 EviMind 在回答前执行：

- 知识库权限检查
- 混合检索
- 融合排序
- 证据充分性判断
- 证据组合选择
- Prompt 证据约束
- 引用来源返回

这更符合企业知识库、论文分析、研发资料问答等场景。

### 9.2 检索链路具备降级能力

如果 embedding 不可用，pgvector 返回空结果。

如果 Elasticsearch 不可用，系统尝试本地关键词检索。

如果某一路检索超时或失败，另一条链路仍然可以返回结果。

这比单一检索后端更稳。

### 9.3 证据预算与去冗余

项目通过 `max-evidence-context-chars` 控制 prompt 中证据长度，避免长文档检索导致 prompt 过大。

`EvidencePortfolioSelector` 通过覆盖增益、多样性和冗余惩罚挑选证据，避免简单 topK 带来的重复上下文。

### 9.4 权限边界明确

知识库成员关系贯穿：

- 知识库访问
- 文档上传
- 文档列表
- 文档删除
- RAG 查询

这能防止用户跨知识库访问不属于自己的资料。

### 9.5 前后端流式体验完整

后端使用 `Flux<String>` 输出事件，前端用 fetch stream 增量解析，用户能看到答案持续生成，并在结束时看到引用。

### 9.6 面向科研工作流扩展

除了 RAG 问答，项目还支持：

- 学术 PDF 元数据提取
- BibTeX/APA 引用导出
- 文档切片笔记
- 批量文件分析
- Markdown/PDF 报告导出

## 10. 面试高频 Q&A

### Q1：这个项目解决了什么问题？

答：

它解决的是用户面对大量文档时难以快速定位信息、难以判断回答依据的问题。项目把文档上传后自动入库，切成可检索片段，并建立向量索引和关键词索引。用户提问时，系统先检索知识库证据，再基于证据生成回答，并返回引用来源。相比普通大模型问答，它更强调可追溯性和证据约束。

### Q2：项目整体架构是什么？

答：

整体是前后端分离加 RAG 检索服务。前端用 Vue 3、TypeScript、Element Plus 构建工作台；后端用 Spring Boot 提供认证、知识库、文档、问答、引用和分析 API；数据层使用 PostgreSQL/pgvector 存结构化数据和向量，Elasticsearch 做关键词检索，MinIO 存原始文件；AI 层通过 Spring AI 对接 DeepSeek、GLM、通义和 OpenAI。部署上用 Docker Compose 编排完整环境。

### Q3：RAG 流程具体怎么跑？

答：

用户问题进入 `RagPipeline` 后，先校验知识库权限，然后调用 `HybridSearchService` 同时执行 pgvector 语义检索和 Elasticsearch 关键词检索。检索结果由 `RrfFusionService` 融合排序。随后系统根据 top score 和前 3 条结果平均分计算 evidence confidence，如果低于知识库配置的 evidence threshold，就返回证据不足提示；如果证据充分，就通过 `EvidencePortfolioSelector` 在上下文预算内选择最终证据，渲染 prompt，调用 ChatClient 流式生成答案，最后返回引用。

### Q4：为什么同时使用 pgvector 和 Elasticsearch？

答：

因为两类检索解决的问题不同。pgvector 基于向量相似度，适合语义相关、同义表达、概念匹配；Elasticsearch 基于关键词/BM25，更适合精确术语、编号、文件中特定字段等。RAG 场景里只用向量检索可能漏掉精确关键词，只用关键词检索又难处理语义改写，所以项目采用混合检索，并通过 RRF 融合结果。

### Q5：为什么用 RRF 融合？

答：

pgvector 和 Elasticsearch 的原始分数不可直接比较。RRF 更关注每个结果在各自检索列表里的排名，通过倒数排名贡献进行融合，不需要强行统一两种检索分数尺度。项目中还用 `documentId#chunkIndex` 做融合 key，避免同一个 chunk 被两路检索重复放入 prompt。

### Q6：证据充分性怎么判断？

答：

项目不是检索到结果就让模型回答，而是计算 evidence confidence。它由最高分结果和前 3 条结果平均支持度组成：

```text
0.70 * topScore + 0.30 * topSupportAverage
```

如果低于知识库的 `evidence_threshold`，系统返回证据不足提示。这样可以避免弱相关内容触发模型编造。

### Q7：Evidence Portfolio 是什么？

答：

它是最终进入 prompt 的证据组合选择器。简单 topK 可能带来大量重复内容或只来自同一篇文档，浪费 prompt 预算。Evidence Portfolio 会综合检索置信度、对问题词的覆盖、文档多样性、内容冗余和最大上下文长度，选择一组更适合回答问题的证据。

### Q8：文档上传后怎么变成可问答知识？

答：

上传后先保存原始文件，记录 document 元数据，然后异步执行 ETL。ETL 会提取文本、清洗文本、按知识库配置切片，把切片保存到数据库；如果 embedding 可用，就生成向量写入 pgvector 表；同时把切片内容写入 Elasticsearch。这样每个文档切片既能被语义检索，也能被关键词检索。

### Q9：为什么入库要异步？

答：

文档入库可能包括文本提取、调用 embedding、写 ES 索引等耗时步骤。如果在上传接口里同步完成，用户会长时间等待，甚至请求超时。所以项目上传成功后先返回 document 记录，后台异步处理，前端通过 ingestion status 展示处理进度。

### Q10：如何保证用户不能访问别人的知识库？

答：

首先接口层通过 JWT 认证识别当前用户。然后业务层使用 `kb_member` 表做知识库成员校验。文档上传、文档列表、文档详情、删除、RAG 查询都会调用类似 `requireKbMember()` 的逻辑，确认当前用户属于该知识库。知识库更新、删除和成员管理则要求 OWNER 权限。

### Q11：流式回答怎么实现？

答：

后端 `ConversationController` 暴露 `POST /api/v1/conversations/{id}/messages/stream`，返回 `MediaType.TEXT_EVENT_STREAM_VALUE` 和 `Flux<String>`。`RagPipeline` 调用 Spring AI 的 streaming API，把模型 token 映射成 `StreamEvent.token`。前端用 fetch 发送 POST 请求，通过 `ReadableStream` 逐块读取响应，解析 JSON event，并实时追加到 assistant 消息内容中。

### Q12：为什么用 fetch stream，而不是 EventSource？

答：

EventSource 原生更适合 GET 请求，而聊天接口需要 POST body，里面有用户问题和模型参数，也要带 Authorization header。fetch stream 更灵活，可以用 POST、JSON body 和自定义 header，同时仍然实现流式读取。

### Q13：项目里如何处理模型供应商切换？

答：

后端配置文件中定义了 `custom.ai.providers`，包括 deepseek、zhipu、qianwen、openai。`AiConfig` 会遍历这些配置，构造 `Map<String, ChatClient>`。会话或请求中传入 modelProvider 后，`RagPipeline` 根据 provider 从 map 中取对应 ChatClient。这样新增供应商主要是增加配置和兼容 OpenAI 风格接口。

### Q14：Embedding 不可用时系统会怎样？

答：

如果 `EmbeddingModel` 没有配置，`EmbeddingService` 会跳过向量生成，`PgVectorSearchService` 也会返回空结果。系统仍然可以依赖 Elasticsearch 或本地关键词检索降级运行。standalone 模式下 embedding 默认关闭，适合没有 embedding key 的本地演示。

### Q15：Elasticsearch 不可用时系统会怎样？

答：

`ElasticsearchSearchService` 捕获异常并返回空结果，同时避免重复刷 warn 日志。`HybridSearchService` 发现关键词结果为空时，如果本地关键词检索服务可用，会调用 `SimpleKeywordSearchService` 作为 fallback。如果语义检索仍然可用，也可以只基于语义结果做 RRF 输出。

### Q16：如何控制 prompt 过大？

答：

配置项 `custom.rag.max-evidence-context-chars` 控制证据上下文最大字符数，默认 6000。`EvidencePortfolioSelector` 在选择证据时估算每个证据块长度，超过预算就停止加入。`RagPipeline.buildBudgetedEvidenceContext()` 也会再次按预算截断，防止 prompt 失控。

### Q17：数据库迁移怎么管理？

答：

生产模式使用 Flyway，迁移文件位于 `src/main/resources/db/migration`。包括初始化用户、知识库、文档、切片、会话、消息表，启用 pgvector 扩展，创建向量表和索引，增加论文元数据和科研笔记等。standalone/dev 模式使用 H2 schema，Flyway 关闭。

### Q18：项目里的 citation 是怎么来的？

答：

RAG 回答的 citation 来自最终选入 prompt 的 evidence portfolio。每条 citation 包含 documentId、fileName、chunkIndex 和 score。后端会根据 documentId 批量查询文档名，避免前端只看到 ID。除此之外，项目还有 CitationService，可以根据文档元数据导出 BibTeX 和 APA。

### Q19：科研笔记功能和 RAG 有什么关系？

答：

科研笔记是围绕文档和 chunk 的人工知识管理能力。用户可以按文档或切片创建笔记、高亮和标签。它不是 RAG 主链路必须步骤，但增强了科研阅读和证据整理场景，可以和引用导出、批量分析一起构成研究工作台。

### Q20：批量分析功能是什么？

答：

批量分析功能允许用户选择多个文件或目录，让系统提取文件内容，构造分析 prompt，调用模型生成分析结果，并保存到 `analysis_result`。用户可以查看进度、结果，并导出 Markdown 或 PDF 报告。它更像一个文档/实验结果分析工具，和知识库 RAG 是并列能力。

### Q21：你在项目中最核心的技术难点是什么？

答：

核心难点是把“检索到证据”和“模型可信回答”之间的链路打通。只做向量检索或只调用大模型都不难，难点在于如何保证回答来自当前知识库、如何在多个检索后端之间融合排序、如何判断证据是否足够、如何控制 prompt 预算、如何把引用和最终证据保持一致。项目通过知识库权限过滤、RRF 融合、evidence threshold、Evidence Portfolio 和引用构建解决这些问题。

### Q22：如果让你继续优化这个项目，你会怎么做？

答：

我会从四个方向优化：

1. 检索质量：增加 query rewrite、reranker、分领域切片策略评估。
2. 可观测性：记录每次 RAG 的召回结果、融合分数、证据阈值、prompt 长度和模型耗时。
3. 权限与安全：细化 group 和 kb 权限模型，限制 CORS，完善文件内容安全扫描。
4. 工程质量：增加端到端测试、引入任务队列替代内存异步任务、把批量分析进度持久化。

### Q23：这个项目有哪些不足或风险？

答：

主要风险有：

- 如果 embedding 配置不完整，语义检索不可用，只能靠关键词检索。
- RRF 和 evidence confidence 是启发式策略，需要用真实数据集持续调参。
- 批量分析当前主要依赖内存任务状态，服务重启后进度会丢失。
- CORS 当前比较宽松，生产环境应收紧 allowed origins。
- 文档编码和复杂 PDF 解析质量会影响后续切片和检索。
- standalone 模式是演示友好，但不适合多人生产使用。

面试中不要回避这些问题。可以强调已经有降级策略，后续会通过评测集、日志观测、任务持久化和安全配置收敛风险。

### Q24：如何证明这个项目不是简单套壳？

答：

可以从链路复杂度说明：

- 上传文档后有完整 ETL，不是直接把文件发给模型。
- 检索使用 pgvector + ES 混合召回，不是只靠一个向量库。
- 有 RRF 融合和知识库过滤。
- 有证据充分性判断，不足时拒答。
- 有 evidence portfolio 控制 prompt 预算和冗余。
- 回答带 citation，并和最终证据一致。
- 前端实现了真实流式输出和引用展示。
- 还有知识库权限、文档状态、引用导出、科研笔记和批量分析能力。

### Q25：如果面试官让你现场画架构图，你怎么画？

可以按这个结构画：

```text
             Vue 3 Frontend
    Chat / KB / Documents / Notes / Analysis
                    |
                    | REST + streaming fetch
                    v
             Spring Boot Backend
                    |
    +---------------+----------------+
    |               |                |
 Auth & KB      Document ETL      RAG Pipeline
    |               |                |
 JWT + ACL      Extract/Clean       Hybrid Search
                Chunk/Embed         Evidence Gate
                Index               Prompt + LLM
    |               |                |
    v               v                v
 PostgreSQL     MinIO/Local      pgvector + ES
 Users/KB       Raw Files        Chunk Retrieval
 Docs/Chunks
                    |
                    v
           DeepSeek / GLM / Qwen / OpenAI
```

讲图时重点强调：

- 前端是工作台入口。
- 后端是业务和 RAG 编排层。
- PostgreSQL 保存业务数据和向量。
- Elasticsearch 提供关键词检索。
- MinIO 保存原始文件。
- 大模型只在证据准备完成后参与生成。

## 11. 面试中的 2 分钟项目介绍模板

可以直接背这个版本：

> EviMind 是我做的一个基于 RAG 的文档知识库问答和证据分析平台。它的核心目标是解决大模型回答不可追溯、容易幻觉的问题。用户可以创建知识库并上传 PDF、Word、Excel、Markdown 等文档，后端会异步执行 ETL，包括文本提取、清洗、切片、embedding 生成和 Elasticsearch 索引。用户提问时，系统不会直接把问题丢给模型，而是先做知识库权限校验，然后同时执行 pgvector 语义检索和 Elasticsearch 关键词检索，再用 RRF 做融合排序。融合后还会根据最高分和前几条证据平均支持度计算证据置信度，如果低于知识库阈值就拒答；如果证据充分，再通过 Evidence Portfolio 选择一组低冗余、覆盖度更高的证据放入 prompt，最后调用 DeepSeek、GLM、通义或 OpenAI 生成流式回答，并返回引用来源。前端用 Vue 3、TypeScript、Element Plus 和 Pinia，后端用 Spring Boot、Spring AI、MyBatis-Plus、PostgreSQL/pgvector、Elasticsearch 和 MinIO，部署上支持 standalone 本地模式和 Docker Compose 完整模式。

## 12. 面试中的 5 分钟项目介绍模板

如果面试官让你详细讲，可以按这个顺序：

1. 项目定位：证据驱动的 RAG 知识库，不是普通聊天。
2. 技术栈：Spring Boot + Vue + PostgreSQL/pgvector + Elasticsearch + MinIO + Spring AI。
3. 文档入库：上传、存储、异步 ETL、提取、清洗、切片、向量化、ES 索引。
4. 查询链路：权限校验、混合检索、RRF 融合、证据阈值、证据组合、prompt、流式生成。
5. 前端体验：工作台、流式输出、引用展示、知识库/文档/笔记/分析页面。
6. 安全和权限：JWT、refresh token hash、知识库成员校验、OWNER/MEMBER。
7. 亮点：证据充分性门控、prompt 预算控制、降级检索、多供应商模型。
8. 不足和优化：reranker、可观测性、任务持久化、生产 CORS、安全扫描。

## 13. 可以重点展示的代码入口

面试前可以熟悉这些文件：

| 文件 | 重点 |
|---|---|
| `RagPipeline.java` | RAG 主链路、证据阈值、prompt、引用 |
| `HybridSearchService.java` | 双路检索、超时、降级 |
| `RrfFusionService.java` | RRF 融合和分数归一 |
| `EvidencePortfolioSelector.java` | 证据组合选择 |
| `EtlPipeline.java` | 文档入库流水线 |
| `DocumentChunker.java` | 切片策略 |
| `EmbeddingService.java` | embedding 批处理入库 |
| `PgVectorSearchService.java` | 向量检索 |
| `ElasticsearchSearchService.java` | 关键词检索 |
| `DocumentService.java` | 上传、格式限制、异步入库 |
| `KnowledgeBaseService.java` | 权限和知识库配置 |
| `AuthService.java` | 注册登录和 token |
| `SecurityConfig.java` | Spring Security |
| `frontend/src/api/chat.ts` | 前端流式解析 |
| `frontend/src/stores/chat.store.ts` | 聊天状态管理 |

## 14. 简历写法建议

可以写成：

- 设计并实现基于 Spring Boot + Vue 3 的 RAG 文档知识库平台，支持文档上传、自动入库、混合检索、流式问答、引用追溯和科研笔记。
- 构建文档 ETL 流水线，完成 PDF/Word/Excel/Markdown 等文件的文本提取、清洗、可配置切片、embedding 生成和 Elasticsearch 索引。
- 实现 pgvector 语义检索与 Elasticsearch 关键词检索的混合召回，并使用 RRF 进行结果融合，支持检索超时和本地关键词降级。
- 设计证据充分性判断与 Evidence Portfolio 选择机制，在 prompt 预算内平衡置信度、覆盖度、多样性和冗余，降低无依据回答风险。
- 实现基于 JWT 的无状态认证和知识库成员权限控制，支持 OWNER/MEMBER 权限模型。
- 使用 Vue 3 + TypeScript + Pinia + Element Plus 实现知识库工作台，支持 fetch streaming 实时展示模型回答和引用来源。

## 15. 回答项目不足时的稳妥说法

可以这样答：

> 当前版本已经完成了从文档入库、混合检索到证据约束生成的闭环，但它还有工程和算法上可以继续优化的地方。比如 evidence threshold 和 Evidence Portfolio 的权重目前是启发式的，后续应该引入标注问答集做离线评测；批量分析任务现在更偏本地内存任务，生产环境应该引入队列和持久化进度；安全上 CORS 和文件扫描可以进一步加强。我的理解是，RAG 系统不是接上向量库就结束，真正难的是持续评估检索质量、回答忠实度、延迟和成本。

## 16. 面试追问速查

| 面试官追问 | 回答关键词 |
|---|---|
| 为什么不用单纯向量检索？ | 精确术语、编号、专有名词需要关键词检索 |
| 为什么不直接 topK 进 prompt？ | 冗余、预算浪费、来源单一、覆盖不足 |
| 怎么减少幻觉？ | evidence threshold、证据不足拒答、prompt 约束、citation |
| 怎么保证知识库隔离？ | JWT + kb_member + knowledgeBaseId filter |
| ES 挂了怎么办？ | 捕获异常、返回空、local keyword fallback |
| embedding 挂了怎么办？ | pgvector 空结果、关键词链路仍可用 |
| 为什么用 Flux？ | 流式输出，降低首字延迟 |
| 怎么扩展模型？ | OpenAI 兼容配置 + ChatClient map |
| 怎么扩展文件类型？ | 新增 FileExtractor 实现和格式白名单 |
| 怎么做生产优化？ | 队列化 ETL、RAG 观测、reranker、权限细化、安全配置 |

## 17. 最后总结

EviMind 的核心竞争力可以概括为：

```text
文档入库自动化 + 混合检索 + 融合排序 + 证据门控 + 引用追溯 + 流式交互
```

面试时不要只说“用了 Spring Boot 和 Vue”，要重点讲清楚 RAG 的工程闭环：

```text
上传文档 -> 提取清洗 -> 切片入库 -> 向量/关键词索引
-> 用户提问 -> 混合检索 -> RRF 融合 -> 证据判断
-> 证据组合 -> Prompt 约束 -> 大模型流式回答 -> 引用返回
```

这条链路讲清楚，基本就能证明你理解了项目的核心技术价值。
