# 3GPP 本地知识库

这套工具把 3GPP 官方规范归档完整镜像到本地，并建立 SQLite FTS5 全文索引。默认抓取 `Specs/Archive` 下所有系列、所有历史版本和 OpenAPI 文件。数据保存在 `data/3gpp/`，不会进入 Git。

## 直接运行

```powershell
# 全量发现、续传下载、解析和索引
python -m tools.threegpp_kb build

# 查看进度
python -m tools.threegpp_kb status

# 默认只查每份规范的最新版本
python -m tools.threegpp_kb search "NR sidelink resource allocation"

# 连历史版本一起查
python -m tools.threegpp_kb search "NR sidelink resource allocation" --all-versions
```

Windows 长期后台构建使用带单实例锁和失败重试的监督脚本：

```powershell
powershell -ExecutionPolicy Bypass -File tools/threegpp_kb/run_full.ps1
```

它持有 `data/3gpp/build.lock`，网络或 Office 临时失败后等待 60 秒续跑，直到全量下载和索引返回成功。

全量任务可以随时停止，再执行同一条命令会从 `.part` 文件继续下载，只重新解析内容哈希发生变化的归档。原始文件在 `data/3gpp/raw/`，清单和全文索引在 `data/3gpp/3gpp.db`。

## 受控运行

```powershell
# 只同步 23、38 系列
python -m tools.threegpp_kb sync --series 23 --series 38

# 官方源真实链路冒烟，只取前两份文件
python -m tools.threegpp_kb build --series 38 --max-files 2 --workers 1

# 强制重建已下载文件的正文索引
python -m tools.threegpp_kb index --force
```

下载器最多开 8 个并发，默认 4 个。旧 `.doc` 通过本机 Microsoft Word 后台提取；DOCX 直接解析 OOXML；PDF 调用本机 `pdftotext`。单个损坏文件会写入清单的失败状态，下次同步继续重试。

## 数据口径

- 来源固定为 3GPP 官方归档，不混入第三方转载。
- 搜索结果保留规范号、版本码、归档成员名和官方源 URL。
- `j00` 解码为 19.0.0，`k00` 解码为 20.0.0；检索默认按每个规范号的最高版本过滤。
- 原始规范版权归 3GPP 组织伙伴所有。本地镜像仅用于自己的检索和研究，不要把 `data/3gpp/` 对外发布。
