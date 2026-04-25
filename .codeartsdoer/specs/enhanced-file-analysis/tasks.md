# 编码任务规划 - 强化文件分析能力

## 文档信息
| 项目 | 内容 |
|------|------|
| 特性名称 | enhanced-file-analysis |
| 版本 | 1.0 |
| 创建日期 | 2025-04-25 |
| 状态 | 草稿 |

---

## 任务 1: 项目基础设施搭建

**描述**: 添加 Maven 依赖和配置类，为后续所有功能开发提供基础支撑。

**输入**: 现有 pom.xml、application.yml
**输出**: 更新后的 pom.xml（含 PDFBox、POI、OpenPDF 依赖）、新增 AnalysisProperties 配置类、更新后的 application.yml

**验收标准**:
- pom.xml 中包含 pdfbox 3.0.1、poi 5.2.5、poi-ooxml 5.2.5、openpdf 2.0.3 依赖
- AnalysisProperties 类包含 maxContentSize、maxPromptSize、batchMaxFiles、resultDir、reportDir、pdfFontPath 配置项
- application.yml 中新增 custom.analysis 配置段，含所有默认值
- 项目可正常编译启动

**代码生成提示**: 在 Spring Boot 3.3.2 + Java 22 项目中，向 pom.xml 添加 Apache PDFBox 3.0.1、Apache POI 5.2.5 + poi-ooxml 5.2.5、OpenPDF 2.0.3 的 Maven 依赖。创建 `com.example.javaai.config.AnalysisProperties` 配置类，使用 `@ConfigurationProperties(prefix = "custom.analysis")` 绑定配置项：maxContentSize(默认512000)、maxPromptSize(默认100000)、batchMaxFiles(默认20)、resultDir(默认".analysis-results")、reportDir(默认".analysis-reports")、pdfFontPath(默认空字符串)。在 application.yml 中添加对应的 custom.analysis 配置段。

---

## 任务 2: 文件内容提取器接口与数据模型

**描述**: 创建 FileContentExtractor 接口和 ExtractionResult 数据类，定义提取器的统一契约。

**输入**: design.md 中的接口定义
**输出**: FileContentExtractor 接口、ExtractionResult 类

**验收标准**:
- FileContentExtractor 接口包含 `boolean supports(String fileName)` 和 `ExtractionResult extract(Path filePath, int maxSize) throws IOException` 方法
- ExtractionResult 包含 success、content、contentType、errorMessage、metadata 字段
- ExtractionResult 提供静态工厂方法 success() 和 failure() 便于构造

**代码生成提示**: 在 `com.example.javaai.extractor` 包下创建 `FileContentExtractor` 接口，定义 `supports(String fileName)` 和 `extract(Path filePath, int maxSize)` 方法。创建 `ExtractionResult` record 或不可变类，包含 boolean success、String content、String contentType、String errorMessage、Map<String,Object> metadata 字段，提供 `ExtractionResult.success(content, contentType, metadata)` 和 `ExtractionResult.failure(errorMessage)` 静态工厂方法。

### 任务 2.1: PDF 文件提取器实现

**描述**: 实现 PdfFileExtractor，使用 Apache PDFBox 从 PDF 文件提取文本内容。

**输入**: FileContentExtractor 接口、PDFBox 依赖
**输出**: PdfFileExtractor 类

**验收标准**:
- supports() 对 .pdf 文件名返回 true
- 使用 PDDocument 加载 PDF，PDFTextStripper 提取所有页面文本
- 提取文本为空时返回 content="[扫描件PDF，无法提取文本内容]"
- 内容超过 maxSize 时截断并附加截断提示
- metadata 中包含 "pageCount" 键
- 捕获 IOException 返回 failure 结果
- 使用 try-with-resources 确保 PDDocument 关闭

**代码生成提示**: 在 `com.example.javaai.extractor` 包下创建 `PdfFileExtractor` 类实现 `FileContentExtractor` 接口，添加 `@Component` 注解。supports() 检查文件名以 .pdf 结尾（不区分大小写）。extract() 方法使用 `PDDocument.load()` 加载文件，`PDFTextStripper.getText()` 提取文本，遍历所有页面。若文本为空或纯空白，返回 success 结果但 content 为"[扫描件PDF，无法提取文本内容]"。超过 maxSize 时截断。metadata 放入 pageCount。捕获 IOException 返回 failure。

### 任务 2.2: Excel 文件提取器实现

**描述**: 实现 ExcelFileExtractor，使用 Apache POI 解析 Excel 文件，提取工作表结构与数据。

**输入**: FileContentExtractor 接口、POI 依赖
**输出**: ExcelFileExtractor 类

**验收标准**:
- supports() 对 .xlsx 和 .xls 文件名返回 true
- 使用 WorkbookFactory.create() 自动识别格式
- 遍历所有 Sheet，提取工作表名称、列头（首行）、数据行（最多100行预览）
- 对数值列计算 min/max/avg/count 统计信息
- 输出为结构化文本格式（工作表名 → 列头 → 数据 → 统计）
- 内容超过 maxSize 时截断
- 捕获 IOException 和 EncryptedDocumentException 返回友好错误
- 使用 try-with-resources 确保 Workbook 关闭

**代码生成提示**: 在 `com.example.javaai.extractor` 包下创建 `ExcelFileExtractor` 类实现 `FileContentExtractor` 接口，添加 `@Component` 注解。supports() 检查 .xlsx/.xls 后缀。extract() 使用 `WorkbookFactory.create(new FileInputStream(filePath.toFile()), true)` 加载。遍历每个 Sheet，提取 Sheet 名称、首行作为列头、后续行作为数据（限制100行）。对数值列用 DoubleStream 计算 min/max/average/count。将所有信息格式化为结构化文本（如 "## Sheet: xxx\n### 列头: ...\n### 数据: ...\n### 统计: ..."）。捕获 EncryptedDocumentException 返回"文件受密码保护，无法解析"。contentType 为 "structured-text"。

### 任务 2.3: Word 文件提取器实现

**描述**: 实现 WordFileExtractor，使用 Apache POI 解析 Word 文档，提取文本、标题和表格。

**输入**: FileContentExtractor 接口、POI 依赖
**输出**: WordFileExtractor 类

**验收标准**:
- supports() 对 .docx 文件名返回 true
- 使用 XWPFDocument 加载 docx 文件
- 遍历段落，根据样式保留标题层级（# / ## / ###）
- 遍历表格，转换为 Markdown 表格格式
- 内容超过 maxSize 时截断
- 捕获 IOException 返回友好错误
- metadata 中包含 "paragraphCount" 和 "tableCount"
- 使用 try-with-resources 确保 XWPFDocument 关闭

**代码生成提示**: 在 `com.example.javaai.extractor` 包下创建 `WordFileExtractor` 类实现 `FileContentExtractor` 接口，添加 `@Component` 注解。supports() 检查 .docx 后缀。extract() 使用 `new XWPFDocument(new FileInputStream(filePath.toFile()))` 加载。遍历 `document.getParagraphs()`，根据 `paragraph.getStyle()` 判断标题层级（Heading1→#，Heading2→## 等），普通段落直接追加。遍历 `document.getTables()`，将每个表格转为 Markdown 表格格式（首行作表头，后续作数据行，用 | 分隔）。contentType 为 "text"。metadata 放入 paragraphCount 和 tableCount。

### 任务 2.4: 文本文件提取器实现（兜底）

**描述**: 实现 TextFileExtractor，处理现有文本格式文件（JSON/CSV/MD/PY/TEX 等），与现有逻辑一致。

**输入**: FileContentExtractor 接口
**输出**: TextFileExtractor 类

**验收标准**:
- supports() 对所有非二进制文本文件后缀返回 true（.json/.csv/.md/.py/.tex/.txt/.log 等）
- 使用 Files.readString() 读取文件内容
- 内容超过 maxSize 时截断并附加截断提示
- contentType 为 "text"

**代码生成提示**: 在 `com.example.javaai.extractor` 包下创建 `TextFileExtractor` 类实现 `FileContentExtractor` 接口，添加 `@Component` 注解。supports() 对 .json/.csv/.md/.py/.tex/.txt/.log/.yaml/.yml/.xml/.html/.css/.js/.java/.c/.cpp/.h/.sh/.r/.sql 后缀返回 true。extract() 使用 `Files.readString(filePath)` 读取，超过 maxSize 截断。contentType 为 "text"。

---

## 任务 3: 文件提取调度服务

**描述**: 创建 FileExtractorService，根据文件扩展名分派到对应的 FileContentExtractor，统一管理提取器注册和调用。

**输入**: 所有 FileContentExtractor 实现
**输出**: FileExtractorService 类

**验收标准**:
- 通过 Spring 自动注入所有 FileContentExtractor 实现
- extractFile() 方法根据文件名找到 supports() 为 true 的提取器并调用
- 无匹配提取器时返回 failure("不支持的文件格式")
- isExtractable() 方法判断文件是否有对应提取器（排除 .pt/.pth 等纯二进制）
- 图片文件（.png/.jpg）特殊处理：返回 base64 编码内容

**代码生成提示**: 在 `com.example.javaai.service` 包下创建 `FileExtractorService` 类，添加 `@Service` 注解。通过构造函数注入 `List<FileContentExtractor> extractors`。extractFile() 遍历 extractors 找到第一个 supports() 为 true 的提取器并调用其 extract()。对 .png/.jpg 图片文件特殊处理：读取字节数组，Base64 编码，返回 ExtractionResult(success=true, content="data:image/xxx;base64,...", contentType="image-base64")。isExtractable() 判断是否存在 supports() 为 true 的提取器。无匹配时返回 ExtractionResult.failure("不支持的文件格式")。

---

## 任务 4: 数据模型类创建

**描述**: 创建 AnalysisResult、BatchProgress、BatchAnalysisRequest、BatchAnalysisItemResult 数据模型类。

**输入**: design.md 中的数据模型定义
**输出**: model 包下的四个数据类

**验收标准**:
- AnalysisResult 包含 id、filePath、fileName、provider、sessionId、analyzedAt、content、fileSize、fileCategory 字段，提供全参构造和默认构造
- BatchProgress 包含 taskId、totalCount、completedCount、currentFile、status、errorMessage、startTime、endTime 字段
- BatchAnalysisRequest 包含 paths、provider、sessionId 字段
- BatchAnalysisItemResult 包含 filePath、fileName、success、content、error、resultId 字段
- 所有类使用 Jackson 注解确保 JSON 序列化/反序列化正确

**代码生成提示**: 在 `com.example.javaai.model` 包下创建四个 Java 类。AnalysisResult：字段 id(String)、filePath(String)、fileName(String)、provider(String)、sessionId(String)、analyzedAt(LocalDateTime)、content(String)、fileSize(long)、fileCategory(String)，使用 @JsonFormat 注解格式化 analyzedAt。BatchProgress：字段 taskId(String)、totalCount(int)、completedCount(int)、currentFile(String)、status(String)、errorMessage(String)、startTime(LocalDateTime)、endTime(LocalDateTime)。BatchAnalysisRequest：字段 paths(List<String>)、provider(String)、sessionId(String)。BatchAnalysisItemResult：字段 filePath(String)、fileName(String)、success(boolean)、content(String)、error(String)、resultId(String)。所有类提供全参构造、无参构造、getter/setter。

---

## 任务 5: 分析结果存储服务

**描述**: 创建 AnalysisResultService，将 AI 分析结果以 JSON 格式持久化到本地文件系统，提供查询接口。

**输入**: AnalysisResult 模型、AnalysisProperties 配置
**输出**: AnalysisResultService 类

**验收标准**:
- saveResult() 将 AnalysisResult 序列化为 JSON，保存到 {base-dir}/{result-dir}/{file-path-hash}/{uuid}.json
- 更新全局索引文件 index.json
- getResultsByFile() 根据文件路径查询该文件的所有分析结果（按时间倒序）
- getAllResults() 查询所有分析结果，支持分页（page/size 参数）
- getResultsByIds() 根据结果 ID 列表批量获取
- 存储目录不存在时自动创建
- 使用 ObjectMapper 进行 JSON 序列化/反序列化

**代码生成提示**: 在 `com.example.javaai.service` 包下创建 `AnalysisResultService` 类，添加 `@Service` 注解。注入 AnalysisProperties 和 @Value("${custom.data.base-dir}") baseDir。使用 Jackson ObjectMapper 序列化/反序列化。saveResult()：计算 filePath 的 hashCode 作为目录名，创建 {base-dir}/{result-dir}/{hash}/ 目录，将结果写入 {uuid}.json，同时更新 index.json（Map<filePath, List<resultId>>）。getResultsByFile()：从 index.json 读取对应 resultId 列表，逐个读取 JSON 文件反序列化。getAllResults()：遍历所有结果目录，收集所有 AnalysisResult，按 analyzedAt 倒序，手动分页。getResultsByIds()：根据 ID 列表直接定位文件读取。使用 Files.createDirectories() 确保目录存在。

---

## 任务 6: 批量分析进度服务

**描述**: 创建 BatchProgressService，基于 ConcurrentHashMap 管理批量分析任务的进度状态。

**输入**: BatchProgress 模型
**输出**: BatchProgressService 类

**验收标准**:
- createProgress() 生成 UUID 作为 taskId，初始化 BatchProgress（status=RUNNING），存入 ConcurrentHashMap
- updateProgress() 更新 completedCount 和 currentFile
- completeProgress() 设置 status=COMPLETED 和 endTime
- failProgress() 设置 status=FAILED、errorMessage 和 endTime
- getProgress() 返回指定 taskId 的进度信息
- 线程安全（使用 ConcurrentHashMap）

**代码生成提示**: 在 `com.example.javaai.service` 包下创建 `BatchProgressService` 类，添加 `@Service` 注解。内部使用 `ConcurrentHashMap<String, BatchProgress> progressMap` 存储。createProgress(int totalCount)：生成 UUID 作为 taskId，创建 BatchProgress(status="RUNNING", startTime=LocalDateTime.now())，放入 map，返回 taskId。updateProgress()：从 map 取出 BatchProgress，更新 completedCount 和 currentFile。completeProgress()：设置 status="COMPLETED"，endTime=now。failProgress()：设置 status="FAILED"，errorMessage，endTime=now。getProgress()：从 map 获取并返回。

---

## 任务 7: 报告导出服务

**描述**: 创建 ReportExportService，支持将分析结果导出为 Markdown 和 PDF 格式报告。

**输入**: AnalysisResult 模型、AnalysisProperties 配置、OpenPDF 依赖
**输出**: ReportExportService 类

**验收标准**:
- exportMarkdown() 生成包含报告标题、生成时间、各文件分析内容的 Markdown 文件
- 多文件导出时每个文件分析结果作为独立二级标题章节
- Markdown 文件使用 UTF-8 编码
- exportPdf() 使用 OpenPDF 生成 PDF 报告，支持中文字体
- PDF 报告包含标题页和各文件分析内容章节
- 导出文件保存到 {base-dir}/{report-dir}/ 目录
- 返回导出文件的相对路径
- PDF 生成失败时抛出明确异常

**验收标准（子任务）**:

### 任务 7.1: Markdown 报告导出

**描述**: 实现 Markdown 格式报告导出功能。

**输入**: List<AnalysisResult>、reportTitle
**输出**: Markdown 文件路径

**验收标准**:
- 报告以 "# {title}" 开头，第二行为生成时间
- 每个 AnalysisResult 生成 "## {fileName}" 章节，包含元信息（分析时间、AI 模型）和分析内容
- 文件保存到 {base-dir}/{report-dir}/{timestamp}-{title}.md
- 使用 StandardCharsets.UTF_8 写入

**代码生成提示**: 在 ReportExportService 中实现 exportMarkdown() 方法。使用 StringBuilder 构建 Markdown 内容：首行 "# {reportTitle}"，次行 "生成时间: {LocalDateTime.now()}"，空行。遍历 results，每个生成 "## {fileName}" + "- 分析时间: {analyzedAt}" + "- AI模型: {provider}" + 空行 + content + 空行。使用 Files.writeString() 写入 {base-dir}/{report-dir}/{timestamp}-{sanitizedTitle}.md，UTF-8 编码。返回相对路径。

### 任务 7.2: PDF 报告导出

**描述**: 实现 PDF 格式报告导出功能，使用 OpenPDF 并支持中文字体。

**输入**: List<AnalysisResult>、reportTitle
**输出**: PDF 文件路径

**验收标准**:
- 使用 OpenPDF 的 Document、PdfWriter 生成 PDF
- 注册中文字体（从配置的 fontPath 加载，或尝试系统默认中文字体）
- 报告包含标题页（标题 + 生成时间）
- 每个 AnalysisResult 生成一个章节（文件名作标题 + 分析内容）
- PDF 报告支持中文字符正确渲染
- 文件保存到 {base-dir}/{report-dir}/{timestamp}-{title}.pdf
- 捕获 DocumentException 并抛出运行时异常

**代码生成提示**: 在 ReportExportService 中实现 exportPdf() 方法。使用 `com.lowagie.text.Document` 和 `PdfWriter.getInstance()`。创建中文字体：若 fontPath 非空则从文件加载，否则尝试从系统字体目录加载 SimHei/SimSun，若均失败则使用 BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.EMBEDDED)。使用 Font chineseFont = new Font(baseFont, 12)。添加标题页：Paragraph(reportTitle, titleFont) + Paragraph(生成时间)。遍历 results，每个添加 Chapter(Section)：文件名作 Section 标题，分析内容作 Paragraph。使用 document.open()/close() 确保资源释放。捕获 DocumentException 包装为 RuntimeException。

---

## 任务 8: FileController 改造

**描述**: 改造现有 FileController，使用 FileExtractorService 替代硬编码的文件读取逻辑，扩展文件格式分类，并集成 AnalysisResultService 保存分析结果。

**输入**: 现有 FileController、FileExtractorService、AnalysisResultService
**输出**: 改造后的 FileController

**验收标准**:
- listFiles() 新增 .xlsx/.xls → "excel"、.docx → "word" 分类
- getFileContent() 对 PDF 文件使用 PdfFileExtractor 提取文本而非返回"二进制文件"占位
- getFileContent() 对 Excel/Word 文件使用对应提取器返回内容
- analyzeFile() 对 PDF/Excel/Word 文件使用 FileExtractorService 提取内容后分析
- analyzeFile() 分析完成后异步调用 AnalysisResultService.saveResult() 保存结果
- 现有 API 路径和响应结构保持兼容
- 图片文件（.png/.jpg）的 base64 返回逻辑保持不变
- .pt/.pth 文件仍返回"二进制文件"占位信息

**代码生成提示**: 改造 `com.example.javaai.controller.FileController`。注入 FileExtractorService 和 AnalysisResultService。listFiles()：在文件类型判断中新增 .xlsx/.xls → "excel"、.docx → "word" 分支。getFileContent()：将现有的 if-else 链替换为调用 fileExtractorService.extractFile()，对图片文件保留原有 base64 逻辑，对 .pt/.pth 保留"二进制文件"逻辑，其余格式使用提取器。analyzeFile()：将现有的 Files.readString() + 二进制判断替换为 fileExtractorService.extractFile()，提取失败时返回 Flux.just(errorMessage)，成功时构建 prompt 并调用 chatService.streamChat()。在流式返回完成后使用 .doOnComplete() 异步调用 analysisResultService.saveResult() 保存结果。

---

## 任务 9: AnalysisController 新增

**描述**: 创建 AnalysisController，承载批量分析、结果查询、报告导出的 API 端点。

**输入**: FileExtractorService、ChatService、AnalysisResultService、BatchProgressService、ReportExportService
**输出**: AnalysisController 类

**验收标准**:
- POST /api/analysis/batch：接收 BatchAnalysisRequest，校验文件数不超过20，创建进度，异步执行批量分析，返回 taskId
- GET /api/analysis/batch/progress：根据 taskId 返回 BatchProgress
- GET /api/analysis/batch/result：根据 taskId 返回批量分析结果列表
- GET /api/analysis/results：分页查询所有分析结果
- GET /api/analysis/results/file：查询指定文件的分析结果
- GET /api/analysis/export/markdown：导出 Markdown 报告，返回文件下载流
- GET /api/analysis/export/pdf：导出 PDF 报告，返回文件下载流
- 批量分析在独立线程中异步执行，不阻塞 API 响应
- 单文件分析失败不影响其余文件

**验收标准（子任务）**:

### 任务 9.1: 批量分析端点

**描述**: 实现批量分析的提交、进度查询和结果获取端点。

**输入**: BatchAnalysisRequest、各服务依赖
**输出**: batch 相关三个 API 端点

**验收标准**:
- POST /api/analysis/batch 校验 paths 不为空且不超过 batchMaxFiles，对每个路径执行 safePath 安全校验
- 批量分析在 @Async 或 CompletableFuture.runAsync() 中异步执行
- 异步执行中：逐文件调用 FileExtractorService 提取内容 → ChatService 分析 → AnalysisResultService 保存 → BatchProgressService 更新进度
- 单文件失败时记录错误，继续下一个
- 使用 ConcurrentHashMap 存储批量任务结果（taskId → List<BatchAnalysisItemResult>）

**代码生成提示**: 在 AnalysisController 中创建批量分析端点。POST /api/analysis/batch：接收 @RequestBody BatchAnalysisRequest，校验 paths.size() <= maxFiles，对每个 path 调用 safePath() 校验。调用 batchProgressService.createProgress() 获取 taskId。使用 CompletableFuture.runAsync() 异步执行：遍历 paths，对每个文件调用 fileExtractorService.extractFile()，若成功则构建 prompt 调用 chatService.streamChat() 并用 .collectList().block() 收集完整结果，然后调用 analysisResultService.saveResult()；若失败则记录错误。每完成一个文件调用 batchProgressService.updateProgress()。全部完成后调用 completeProgress()。立即返回 {taskId: taskId}。GET /api/analysis/batch/progress：返回 batchProgressService.getProgress(taskId)。GET /api/analysis/batch/result：从内存 map 中获取结果列表返回。

### 任务 9.2: 结果查询端点

**描述**: 实现分析结果的查询端点。

**输入**: AnalysisResultService
**输出**: results 相关两个 API 端点

**验收标准**:
- GET /api/analysis/results 支持 page（默认0）和 size（默认20）分页参数
- GET /api/analysis/results/file 接受 path 参数，返回该文件的所有分析结果（按时间倒序）

**代码生成提示**: 在 AnalysisController 中添加 GET /api/analysis/results 端点，调用 analysisResultService.getAllResults(page, size) 返回分页结果。添加 GET /api/analysis/results/file 端点，接受 @RequestParam("path") String filePath，调用 analysisResultService.getResultsByFile(filePath) 返回结果列表。

### 任务 9.3: 报告导出端点

**描述**: 实现分析结果的 Markdown 和 PDF 导出端点。

**输入**: AnalysisResultService、ReportExportService
**输出**: export 相关两个 API 端点

**验收标准**:
- GET /api/analysis/export/markdown 接受 resultIds（逗号分隔）和 title 参数
- GET /api/analysis/export/pdf 接受 resultIds（逗号分隔）和 title 参数
- 导出后返回文件下载流（Content-Disposition: attachment）
- resultIds 为空时返回 400 错误

**代码生成提示**: 在 AnalysisController 中添加导出端点。GET /api/analysis/export/markdown：接受 @RequestParam("resultIds") String resultIds 和 @RequestParam(value="title", defaultValue="分析报告") String title。将 resultIds 按逗号分隔为列表，调用 analysisResultService.getResultsByIds()，再调用 reportExportService.exportMarkdown()，最后读取导出文件返回 ResponseEntity.with InputStreamResource，设置 Content-Type 为 "text/markdown;charset=UTF-8" 和 Content-Disposition 为 "attachment; filename=xxx.md"。PDF 导出类似，Content-Type 为 "application/pdf"。

---

## 任务 10: 集成测试与验证

**描述**: 对所有新增功能进行端到端集成测试，验证功能正确性和 API 兼容性。

**输入**: 所有已实现的组件
**输出**: 测试用例、验证报告

**验收标准**:
- PDF 文件内容提取：准备测试 PDF 文件，验证 /api/files/content 返回文本内容
- Excel 文件内容解析：准备测试 xlsx 文件，验证内容返回包含工作表信息
- Word 文件内容解析：准备测试 docx 文件，验证内容返回包含段落和表格
- 文件列表分类：验证 .xlsx/.xls/.docx 文件的 category 正确
- 单文件分析：验证 PDF/Excel/Word 文件可通过 /api/files/analyze 进行 AI 分析
- 批量分析：验证 POST /api/analysis/batch 返回 taskId，进度可查询，结果可获取
- 结果存储：验证分析结果被持久化，可通过查询接口获取
- Markdown 导出：验证导出文件格式正确、内容完整
- PDF 导出：验证导出文件可打开、中文正确渲染
- API 兼容性：验证原有 /api/files、/api/files/content、/api/files/analyze 对现有格式文件的行为不变
- 错误处理：验证损坏文件、加密文件、不支持的格式返回友好错误信息

**代码生成提示**: 创建集成测试类 `EnhancedFileAnalysisIntegrationTest`，使用 Spring Boot 的 @SpringBootTest 和 @AutoConfigureMockMvc。准备测试文件（PDF、xlsx、docx）放在 src/test/resources/ 目录。测试用例包括：testPdfContentExtraction()、testExcelContentExtraction()、testWordContentExtraction()、testFileListCategory()、testPdfAnalysis()、testBatchAnalysis()、testResultStorage()、testMarkdownExport()、testPdfExport()、testApiCompatibility()、testErrorHandling()。每个测试使用 MockMvc 调用对应 API 端点并验证响应。
