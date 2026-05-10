# EviMind — RAG 增强的 AI 智能数据分析平台

基于 RAG（Retrieval-Augmented Generation）架构的文档分析与智能问答平台。支持文档自动入库、向量/关键词混合检索、多模型 AI 对话、引用追溯、知识库协作和科研辅助功能。

## 快速开始

### 当前本地启动（Windows 开发环境）

本项目没有内置默认账号。首次打开登录页后，先点击注册并创建自己的用户名和密码，之后再用该账号登录。

后端可用独立模式直接启动，使用 H2 本地数据库和本地文件存储，不需要 PostgreSQL、Elasticsearch 或 MinIO：

```powershell
cd D:\javaai
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
$env:DEEPSEEK_API_KEY = "your-key"   # 可选；不设置时部分 AI 对话能力不可用
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=standalone"
```

后端启动完成后，可用下面地址检查服务状态：

```text
http://localhost:8080/api/v1/health
```

如果已经构建出 `target\evimind-0.0.1-SNAPSHOT.jar`，也可以直接运行：

```powershell
cd D:\javaai
.\run-evimind.bat
```

启动日志会写入：

```text
evimind-backend.out.log
evimind-backend.err.log
```

如果要使用前端开发服务器，另开一个 PowerShell：

```powershell
cd D:\javaai\frontend
npm install
npm run dev -- --host 127.0.0.1
```

然后打开：

```text
http://127.0.0.1:5173
```

前端开发服务器会把 `/api` 请求代理到 `http://localhost:8080`，所以需要先启动后端。

如果使用打包后的静态页面，也可以打开：

```text
http://localhost:8080
```

### 方式一：独立模式（推荐，零依赖）

无需安装数据库、搜索引擎或对象存储，单个 JAR 即可运行。

```bash
# 1. 设置 AI API Key
set DEEPSEEK_API_KEY=your-key          # Windows
export DEEPSEEK_API_KEY=your-key       # Linux/Mac

# 2. 构建
build.bat                               # Windows
./build.sh                              # Linux/Mac

# 3. 启动
start.bat                               # Windows
./start.sh                              # Linux/Mac
```

浏览器打开 **http://localhost:8080**，注册账号即可使用。

### 方式二：Docker Compose（生产模式）

```bash
cp .env.example .env
# 编辑 .env 填入 API Key 和密钥
docker-compose up -d
```

服务端口：后端 `8080`，前端 `5173`。

## 功能概览

### 核心流程

```
上传文档 → 文本提取 → 清洗 → 切片 → 向量嵌入 + 关键词索引
                                                    ↓
用户提问 → 混合检索(pgvector + ES + RRF融合) → LLM 生成 → 附带引用
```

### 主要功能

| 模块 | 功能 |
|------|------|
| **知识库管理** | 创建/编辑/删除知识库，成员权限管理（OWNER/MEMBER），证据阈值、切片策略配置 |
| **文档管理** | 批量上传、拖拽上传，支持 PDF/Word/Excel/CSV/JSON/MD/TXT 等 25+ 格式 |
| **ETL 流水线** | 自动提取→清洗→切片→向量嵌入→ES 索引，失败可重试 |
| **混合检索** | pgvector 语义搜索 + Elasticsearch 关键词 + RRF 融合排序，任一不可用时自动降级 |
| **RAG 对话** | 流式 SSE 响应，答案附带引用来源（文档名、切片编号、相关度评分） |
| **多模型切换** | DeepSeek / GLM-4 / 通义千问 / OpenAI 运行时热切换，支持 Temperature/Top-P/MaxTokens 调节 |
| **文件分析** | 单文件或批量目录分析，生成 Markdown/PDF 报告 |
| **会话管理** | 对话历史、自动摘要、自动标题生成、双击重命名、搜索过滤 |
| **科研辅助** | 论文元数据提取（DOI/作者/年份）、BibTeX/APA 引用生成、科研笔记批注系统 |

### 科研功能

- **论文元数据自动提取**：上传学术 PDF 自动提取标题、作者、DOI、摘要、发表年份
- **引用管理**：支持 BibTeX 和 APA 格式导出
- **科研笔记**：对文档切片添加标注和高亮，支持标签分类
- **会话导出**：导出对话为 Markdown 或 JSON 格式

## API 接口

### 认证 `/api/v1/auth`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register` | 用户注册 |
| POST | `/auth/login` | 用户登录 |
| POST | `/auth/refresh` | 刷新 Token |
| GET | `/auth/me` | 获取当前用户 |

### 知识库 `/api/v1/knowledge-bases`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 创建知识库 |
| PUT | `/{id}` | 更新知识库 |
| DELETE | `/{id}` | 删除知识库 |
| GET | `/` | 列出可访问的知识库 |
| GET | `/{id}` | 获取知识库详情 |
| POST | `/{id}/members` | 添加成员 |
| DELETE | `/{id}/members/{userId}` | 移除成员 |
| GET | `/{id}/members` | 成员列表 |

### 文档 `/api/v1/documents`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/upload` | 上传文档 |
| GET | `/` | 文档列表（分页） |
| GET | `/{id}` | 文档详情 |
| GET | `/{id}/chunks` | 查看文档切片 |
| DELETE | `/{id}` | 删除文档 |
| POST | `/{id}/retry` | 重试入库 |
| POST | `/batch-delete` | 批量删除 |
| POST | `/batch-reingest` | 批量重新入库 |

### 会话 `/api/v1/conversations`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 创建会话 |
| GET | `/` | 会话列表 |
| GET | `/{id}/messages` | 消息历史 |
| POST | `/{id}/messages` | 发送消息 |
| POST | `/{id}/messages/stream` | 流式 RAG 对话 (SSE) |
| PUT | `/{id}/rename` | 重命名会话 |
| GET | `/{id}/export` | 导出会话 (markdown/json) |
| DELETE | `/{id}` | 删除会话 |

### 引用 `/api/v1/citations`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/export` | 导出引用 (bibtex/apa) |

### 科研笔记 `/api/v1/notes`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 创建笔记 |
| PUT | `/{id}` | 更新笔记 |
| DELETE | `/{id}` | 删除笔记 |
| GET | `/` | 查询笔记（按 chunkId 或 documentId） |

## 配置

### 环境变量

| 变量 | 说明 | 独立模式 | 生产模式 |
|------|------|----------|----------|
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | **必填** | 必填 |
| `ZHIPU_API_KEY` | 智谱 GLM-4 API 密钥 | 可选 | 可选 |
| `QIANWEN_API_KEY` | 通义千问 API 密钥 | 可选 | 可选 |
| `OPENAI_API_KEY` | OpenAI API 密钥 | 可选 | 可选 |
| `JWT_SECRET` | JWT 签名密钥 | 有默认值 | **必填** |
| `PORT` | 服务端口 | 8080 | 8080 |
| `EMBEDDING_ENABLED` | 启用向量嵌入 | false | true |
| `EMBEDDING_API_KEY` | 嵌入模型 API 密钥 | 可选 | 可选 |
| `POSTGRES_*` | PostgreSQL 连接 | 不需要 | **必填** |
| `MINIO_*` | MinIO 存储 | 不需要 | **必填** |

### 独立模式 vs 生产模式

| 组件 | 独立模式 | 生产模式 (Docker) |
|------|----------|-------------------|
| 数据库 | H2 (内嵌) | PostgreSQL 17 + pgvector |
| 文件存储 | 本地 `./data/documents/` | MinIO |
| 全文搜索 | 本地关键词匹配 | Elasticsearch 8 |
| 向量搜索 | 可选 (需 Embedding API) | pgvector |
| 前端 | Spring Boot 静态资源 | Nginx 独立容器 |
| 适用场景 | 开发/个人使用/演示 | 团队协作/生产部署 |

## 技术栈

**后端**: Spring Boot 3.5 · Spring AI 1.0 · MyBatis-Plus 3.5 · Spring Security · JWT · Flyway · PostgreSQL/pgvector · Elasticsearch 8 · MinIO · PDFBox · Apache POI · OpenPDF

**前端**: Vue 3 · TypeScript · Vite 8 · Element Plus · Pinia · Markdown-It · Axios · SSE Streaming

## 项目结构

```
evimind/
├── src/main/java/com/example/evimind/
│   ├── assistant/         # 会话管理 + 流式对话
│   ├── auth/              # JWT 认证
│   ├── config/            # AI/安全/存储/ES 配置
│   ├── controller/        # 分析/文件/引用/笔记 API
│   ├── document/          # 文档上传/管理
│   ├── extractor/         # PDF/Word/Excel 提取器 + 论文元数据
│   ├── ingestion/         # ETL 流水线（提取→清洗→切片→嵌入→索引）
│   ├── knowledgebase/     # 知识库 CRUD + 权限
│   ├── mapper/            # MyBatis-Plus 数据访问层
│   ├── model/             # 实体类 + DTO
│   ├── qa/                # RAG 问答引擎
│   ├── retrieval/         # 混合搜索 + RRF 融合 + 本地回退
│   ├── service/           # 业务服务
│   └── storage/           # MinIO + 本地文件存储
├── frontend/              # Vue 3 前端
│   ├── src/views/         # ChatView, KnowledgeBaseView, DocumentView
│   ├── src/stores/        # Pinia 状态管理
│   ├── src/api/           # API 调用封装
│   └── src/types/         # TypeScript 类型定义
├── docker-compose.yml     # 生产部署
├── Dockerfile             # 后端镜像
├── build.bat / build.sh   # 构建脚本
├── start.bat / start.sh   # 启动脚本
└── PROJECT_OVERVIEW.md    # 详细项目文档
```

## License

MIT
