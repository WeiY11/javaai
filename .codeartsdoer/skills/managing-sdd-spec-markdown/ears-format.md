# EARS 格式规则

## 概述
EARS (Easy Approach to Requirements Syntax) 是一种简化的需求语法方法，用于编写清晰、可测试的需求。

## EARS 模式

### 1. 普遍性 (Ubiquitous)
- **格式**: The \<system\> shall \<action\>
- **用途**: 系统始终必须执行的行为
- **示例**: The 文件分析系统 shall 支持PDF文件内容提取

### 2. 事件驱动 (Event-Driven)
- **格式**: When \<trigger\>, the \<system\> shall \<action\>
- **用途**: 由特定事件触发的行为
- **示例**: When 用户选择多个文件并点击批量分析, the 文件分析系统 shall 对所有选中文件依次执行分析

### 3. 不期望状态 (Unwanted Behaviour)
- **格式**: If \<condition\>, then the \<system\> shall \<action\>
- **用途**: 处理异常或非预期情况
- **示例**: If 文件格式不受支持, then the 文件分析系统 shall 返回明确的错误提示信息

### 4. 状态驱动 (State-Driven)
- **格式**: While \<state\>, the \<system\> shall \<action\>
- **用途**: 在特定状态下持续执行的行为
- **示例**: While 批量分析正在进行, the 文件分析系统 shall 显示当前分析进度

### 5. 可选功能 (Optional Feature)
- **格式**: Where \<feature\> is enabled, the \<system\> shall \<action\>
- **用途**: 可选功能启用时的行为
- **示例**: Where PDF内容提取功能已启用, the 文件分析系统 shall 使用PDF解析库提取文本内容

## 验收标准编写规则
- 每个需求必须至少有一条验收标准
- 验收标准必须可测试、可验证
- 使用"应"或"shall"表示强制性要求
- 避免模糊词汇（如"适当"、"合理"）
- 验收标准应包含具体的输入和预期输出
