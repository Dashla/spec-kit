# Markdown 编程实战指南

> **从零开始创建你自己的 AI 命令**

## 目录

1. [核心概念](#核心概念)
2. [基础语法](#基础语法)
3. [实战示例](#实战示例)
4. [高级技巧](#高级技巧)
5. [调试与优化](#调试与优化)
6. [最佳实践](#最佳实践)

---

## 核心概念

### 什么是 "Markdown 编程"？

```mermaid
graph LR
    A[编写 Markdown 指令] --> B[AI 读取并理解]
    B --> C[AI 执行步骤]
    C --> D[产生实际结果]

    style A fill:#e1f5e1
    style B fill:#fff9c4
    style C fill:#fce4ec
    style D fill:#c8e6c9
```

**传统编程**：
```python
# Python 代码
def create_task(title, description):
    task = Task(title=title, desc=description)
    db.session.add(task)
    db.session.commit()
    return task
```

**Markdown 编程**：
```markdown
## Outline

1. 读取用户提供的任务标题和描述
2. 创建一个新的任务对象，包含这些信息
3. 将任务保存到数据库
4. 返回创建的任务详情
```

**关键区别**：
- 传统编程：写代码让计算机执行
- Markdown 编程：写自然语言让 AI 理解并执行

---

## 基础语法

### 1. 文件结构

每个命令文件都是一个 `.md` 文件，包含三个部分：

```markdown
---
# Part 1: YAML Front Matter (元数据)
description: 命令的简短描述
scripts:
  sh: scripts/bash/my-script.sh
  ps: scripts/powershell/my-script.ps1
handoffs:
  - label: 下一步操作
    agent: speckit.next-command
---

## User Input

# Part 2: 用户输入捕获
```text
$ARGUMENTS
```

你必须考虑用户的输入（如果不为空）。

## Outline

# Part 3: 执行步骤（最重要！）
1. 第一步做什么
2. 第二步做什么
3. ...
```

### 2. YAML Front Matter 详解

#### 必需字段

```yaml
---
description: 命令在命令列表中显示的描述
---
```

#### 可选字段

```yaml
---
description: 生成项目摘要文档

# 调用外部脚本
scripts:
  sh: scripts/bash/analyze-project.sh --json
  ps: scripts/powershell/analyze-project.ps1 -Json

# 下一步操作建议
handoffs:
  - label: 创建文档
    agent: speckit.document
    prompt: 基于摘要创建详细文档
    send: true  # 自动发送，不需要用户确认

# MCP 工具（高级功能）
tools: ['github/github-mcp-server/issue_write']
---
```

### 3. 特殊占位符

| 占位符 | 用途 | 示例 |
|-------|------|------|
| `$ARGUMENTS` | 用户输入的参数 | 用户输入 `/cmd hello` → `$ARGUMENTS = "hello"` |
| `{SCRIPT}` | 脚本执行结果 | AI 会根据 OS 选择 sh 或 ps 执行 |
| `{ARGS}` | 传递给脚本的参数 | `{SCRIPT} --json "{ARGS}"` |

### 4. Outline 编写规则

#### 使用明确的动词

```markdown
## Outline

✅ 好的示例 - 使用明确动词：
1. **读取** spec.md 文件
2. **提取** 所有功能需求
3. **生成** 摘要报告
4. **写入** summary.md 文件

❌ 不好的示例 - 模糊描述：
1. 处理 spec 文件
2. 看一下需求
3. 做一个报告
```

#### 使用结构化格式

```markdown
## Outline

1. **初始化阶段**：
   - 运行 `{SCRIPT}` 获取项目路径
   - 解析返回的 JSON：`PROJECT_DIR`, `BRANCH_NAME`

2. **分析阶段**：
   a. 读取以下文件：
      - README.md
      - package.json
      - spec.md
   b. 提取关键信息：
      - 项目名称
      - 技术栈
      - 依赖列表

3. **生成阶段**：
   - 创建 `PROJECT_DIR/summary.md`
   - 使用以下格式：
     ```markdown
     # Project Summary
     - Name: [项目名称]
     - Tech: [技术栈]
     ```

4. **报告阶段**：
   - 输出："✅ 摘要已生成: summary.md"
```

#### 条件逻辑

```markdown
## Outline

1. 读取用户输入 `$ARGUMENTS`

2. **条件分支**：
   - 如果 `$ARGUMENTS` 包含 "quick"：
     - 只生成简短摘要
     - 跳过详细分析
   - 如果 `$ARGUMENTS` 包含 "detailed"：
     - 生成完整报告
     - 包含依赖关系图
   - 如果 `$ARGUMENTS` 为空：
     - 使用默认模式（中等详细度）

3. 根据选择的模式执行相应步骤
```

#### 错误处理

```markdown
## Outline

1. 尝试读取 spec.md 文件

2. **错误处理**：
   - 如果文件不存在：
     - ERROR: "规格文件缺失，请先运行 /speckit.specify"
     - 停止执行
   - 如果文件为空：
     - WARNING: "规格文件为空，继续执行吗？"
     - 等待用户确认

3. 如果一切正常，继续处理...
```

---

## 实战示例

### 示例 1：简单命令 - 项目统计

**需求**：创建一个 `/speckit.stats` 命令，统计项目中的文件数量。

**步骤 1**：创建文件 `.claude/commands/stats.md`

```markdown
---
description: 统计项目中的文件和代码行数
---

## User Input

```text
$ARGUMENTS
```

你必须考虑用户的输入（如果不为空）。

## Outline

1. **扫描项目文件**：
   - 使用 Glob 工具查找所有文件
   - 排除 node_modules/, .git/, dist/

2. **分类统计**：
   - JavaScript/TypeScript: `**/*.{js,ts,jsx,tsx}`
   - Python: `**/*.py`
   - Markdown: `**/*.md`
   - 其他

3. **统计代码行数**：
   - 使用 Read 工具读取每个文件
   - 计算非空行数

4. **生成报告**：
   ```
   📊 项目统计

   总文件数: [数量]
   总代码行数: [数量]

   按类型分类:
   - JavaScript: [数量] 文件, [行数] 行
   - Python: [数量] 文件, [行数] 行
   - Markdown: [数量] 文件, [行数] 行
   ```

5. **用户选项**（通过 `$ARGUMENTS`）：
   - 如果用户输入包含 "save"：
     - 将报告保存到 stats.md
   - 如果用户输入包含文件类型（如 "python"）：
     - 只统计该类型文件
```

**使用**：
```
/speckit.stats
/speckit.stats python
/speckit.stats save
```

---

### 示例 2：中级命令 - 依赖检查

**需求**：创建一个 `/speckit.deps` 命令，检查项目依赖的安全漏洞。

**步骤 1**：创建脚本 `scripts/bash/check-deps.sh`

```bash
#!/bin/bash
# 检查 npm 依赖的安全漏洞

if [ ! -f "package.json" ]; then
    echo '{"error": "No package.json found"}' >&2
    exit 1
fi

# 运行 npm audit
OUTPUT=$(npm audit --json 2>&1)

echo "$OUTPUT"
```

**步骤 2**：创建命令文件 `.claude/commands/deps.md`

```markdown
---
description: 检查项目依赖的安全漏洞
scripts:
  sh: scripts/bash/check-deps.sh
  ps: scripts/powershell/check-deps.ps1
handoffs:
  - label: 修复漏洞
    agent: speckit.implement
    prompt: 修复检测到的安全漏洞
---

## User Input

```text
$ARGUMENTS
```

你必须考虑用户的输入（如果不为空）。

## Outline

1. **验证环境**：
   - 检查是否存在 package.json
   - 如果不存在：ERROR "这不是一个 Node.js 项目"

2. **执行安全检查**：
   - 运行 `{SCRIPT}`
   - 解析返回的 JSON 结果

3. **分析结果**：
   - 提取漏洞信息：
     - 严重级别（critical, high, moderate, low）
     - 受影响的包
     - 修复建议

4. **生成报告**：
   ```
   🔒 依赖安全检查

   ❌ 发现 [数量] 个漏洞

   严重级别分布:
   - Critical: [数量]
   - High: [数量]
   - Moderate: [数量]
   - Low: [数量]

   受影响的包:
   1. [包名] ([版本]) - [严重级别]
      修复: npm install [包名]@[安全版本]
   ```

5. **提供修复选项**：
   - 如果有严重漏洞：
     - 询问："发现严重漏洞，是否立即修复？"
     - 提供 handoff 到 /speckit.implement

6. **保存报告**（如果 `$ARGUMENTS` 包含 "save"）：
   - 创建 security-report.md
   - 包含详细的漏洞信息和修复步骤
```

**使用**：
```
/speckit.deps
/speckit.deps save
```

---

### 示例 3：高级命令 - 代码审查

**需求**：创建一个 `/speckit.review` 命令，自动审查代码质量。

**步骤 1**：创建命令文件 `.claude/commands/review.md`

```markdown
---
description: 自动审查代码质量和最佳实践
scripts:
  sh: scripts/bash/check-prerequisites.sh --json
  ps: scripts/powershell/check-prerequisites.ps1 -Json
handoffs:
  - label: 修复问题
    agent: speckit.implement
    prompt: 修复代码审查中发现的问题
---

## User Input

```text
$ARGUMENTS
```

你必须考虑用户的输入（如果不为空）。

## Outline

1. **初始化**：
   - 运行 `{SCRIPT}` 获取 `FEATURE_DIR`, `BRANCH_NAME`
   - 如果失败：提示用户先运行 /speckit.specify

2. **确定审查范围**：
   - 如果 `$ARGUMENTS` 指定了文件路径：
     - 只审查指定文件
   - 如果 `$ARGUMENTS` 包含 "current-branch"：
     - 审查当前分支的所有修改
   - 否则：
     - 审查 `FEATURE_DIR` 下的所有代码文件

3. **多维度审查**（并行执行）：

   **A. 安全检查**（优先级：CRITICAL）：
   - 扫描硬编码的密钥、token、密码
   - 检查 SQL 注入风险（查找字符串拼接的 SQL）
   - 检查 XSS 漏洞（未转义的用户输入）
   - 检查不安全的依赖

   **B. 代码质量**（优先级：HIGH）：
   - 检查过长的函数（>50 行）
   - 检查重复代码（相似度 >80%）
   - 检查复杂度过高的函数（圈复杂度 >10）
   - 检查命名规范违规

   **C. 最佳实践**（优先级：MEDIUM）：
   - 检查错误处理是否完善
   - 检查是否有 TODO/FIXME/HACK 注释
   - 检查是否缺少必要的注释
   - 检查是否有死代码（未使用的变量/函数）

   **D. 性能问题**（优先级：MEDIUM）：
   - 检查 N+1 查询问题
   - 检查不必要的循环嵌套
   - 检查内存泄漏风险

4. **问题分类**：
   - 按严重级别分类：
     - 🔴 CRITICAL: 必须立即修复
     - 🟠 HIGH: 应该尽快修复
     - 🟡 MEDIUM: 建议修复
     - 🟢 LOW: 可选改进

5. **生成详细报告**：
   - 创建 `FEATURE_DIR/review-report.md`
   - 使用以下格式：
     ```markdown
     # 代码审查报告

     **分支**: [BRANCH_NAME]
     **日期**: [当前日期]
     **审查文件**: [数量] 个

     ## 🔴 严重问题 (CRITICAL)

     ### [问题类别]
     - **文件**: `path/to/file.js:123`
     - **问题**: 硬编码的 API 密钥
     - **代码**:
       ```javascript
       const API_KEY = "sk-1234567890abcdef";
       ```
     - **建议**: 使用环境变量 `process.env.API_KEY`
     - **影响**: 安全风险

     ## 🟠 高优先级问题 (HIGH)

     ### [问题类别]
     ...

     ## ✅ 符合规范

     - 错误处理完善
     - 命名规范统一
     - 注释清晰
     ```

6. **交互式修复**：
   - 统计问题数量：
     ```
     发现 15 个问题:
     - CRITICAL: 2 个
     - HIGH: 5 个
     - MEDIUM: 6 个
     - LOW: 2 个
     ```

   - 如果有 CRITICAL 问题：
     - 显示问题详情
     - 询问："是否立即修复这些严重问题？(yes/no)"
     - 如果 yes：触发 handoff 到 /speckit.implement

   - 如果只有 HIGH/MEDIUM/LOW：
     - 输出："建议修复 HIGH 优先级问题"
     - 提供 handoff 选项

7. **生成改进建议**：
   - 基于发现的问题，提供：
     - 代码重构建议
     - 性能优化建议
     - 安全加固建议

8. **总结**：
   ```
   ✅ 代码审查完成

   📊 统计:
   - 审查文件: 23 个
   - 代码行数: 1,547 行
   - 发现问题: 15 个
   - 符合规范: 8 项

   📄 详细报告: specs/001-feature/review-report.md

   💡 建议: 优先修复 2 个严重问题
   ```
```

**使用**：
```
/speckit.review
/speckit.review src/auth.js
/speckit.review current-branch
```

---

## 高级技巧

### 1. 并行任务执行

**场景**：需要同时进行多个独立的分析任务。

```markdown
## Outline

1. **并行启动分析任务**：

   使用 Task tool 同时执行以下任务：

   a. 任务 1: 分析代码复杂度
      - 使用 explore agent
      - 扫描所有 .js 文件
      - 计算圈复杂度

   b. 任务 2: 分析依赖关系
      - 使用 explore agent
      - 读取 package.json
      - 绘制依赖树

   c. 任务 3: 检查安全漏洞
      - 使用 general-purpose agent
      - 运行 npm audit
      - 解析结果

2. **等待所有任务完成**：
   - 收集所有任务的返回结果
   - 如果任何任务失败，记录错误但继续

3. **整合结果**：
   - 合并所有分析报告
   - 生成综合评分
```

**关键点**：
- 明确告诉 AI "并行"执行
- 使用 Task tool 启动多个 agents
- AI 会自动管理并行任务

### 2. 增量更新策略

**场景**：长时间运行的任务需要持续更新状态。

```markdown
## Outline

1. **初始化进度跟踪**：
   - 创建 `progress.json` 文件
   - 初始状态：
     ```json
     {
       "total_tasks": 10,
       "completed": 0,
       "current": "初始化中..."
     }
     ```

2. **执行任务循环**：

   对于每个任务：

   a. 更新当前状态：
      - 修改 progress.json: `"current": "执行任务 3/10"`

   b. 执行任务

   c. **立即保存结果**（增量更新）：
      - 追加到 results.md
      - 更新 progress.json: `"completed": 3`

   d. 报告进度：
      - 输出："✅ 任务 3/10 完成 (30%)"

3. **优点**：
   - 任何时候中断，已完成的工作不会丢失
   - 用户可以实时看到进度
```

### 3. 上下文管理

**场景**：处理大文件时避免超出 token 限制。

```markdown
## Outline

1. **渐进式加载**：

   不要一次性读取整个文件，而是：

   a. 先读取文件头部 100 行
   b. 分析是否需要完整内容
   c. 如果需要，再分批读取：
      - 读取 100-200 行
      - 处理这批内容
      - 提取关键信息到摘要
      - 丢弃原始内容
      - 继续下一批

2. **智能摘要**：

   对于超过 500 行的文件：

   a. 提取结构：
      - 函数签名
      - 类定义
      - 导入语句
      - 导出语句

   b. 创建摘要：
      ```
      文件: src/auth.js (847 行)

      导出:
      - class AuthService
      - function login()
      - function logout()

      依赖:
      - express
      - bcrypt
      - jsonwebtoken
      ```

   c. 仅在需要时读取完整内容

3. **Token 预算管理**：
   - 估算每步操作的 token 使用
   - 如果接近限制，切换到摘要模式
```

### 4. 条件工作流

**场景**：根据项目类型执行不同的逻辑。

```markdown
## Outline

1. **检测项目类型**：

   a. 检查特征文件：
      - 如果存在 package.json → Node.js 项目
      - 如果存在 requirements.txt → Python 项目
      - 如果存在 Cargo.toml → Rust 项目

   b. 读取配置文件确认技术栈

2. **分支执行**：

   **如果是 Node.js 项目**：
   - 检查 package.json 中的脚本
   - 运行 npm audit
   - 检查 ESLint 配置
   - 验证 .gitignore 包含 node_modules/

   **如果是 Python 项目**：
   - 检查虚拟环境
   - 运行 pip check
   - 检查 pylint 配置
   - 验证 .gitignore 包含 __pycache__/

   **如果是 Rust 项目**：
   - 运行 cargo check
   - 运行 clippy
   - 检查 Cargo.lock

3. **通用检查**（所有项目类型）：
   - Git 配置
   - README 完整性
   - License 文件
```

### 5. 用户交互模式

**场景**：需要用户做出选择或确认。

```markdown
## Outline

1. **收集信息**：
   - 分析当前项目状态
   - 识别需要用户决策的点

2. **设计选择题**：

   对于每个需要决策的点：

   a. 提供清晰的选项：
      ```
      Q1: 测试框架选择

      **Recommended:** Option A - Jest (最流行，文档完善)

      | Option | Framework | Pros | Cons |
      |--------|-----------|------|------|
      | A | Jest | 功能完整，社区大 | 配置稍复杂 |
      | B | Vitest | 快速，与 Vite 集成 | 较新，生态小 |
      | C | Mocha | 灵活，轻量 | 需要额外配置 |

      Reply with option letter (A/B/C) or 'yes' for recommendation
      ```

   b. 等待用户回答

   c. 验证回答：
      - 如果用户输入 "yes" 或 "recommended" → 使用推荐选项
      - 如果用户输入字母 → 使用对应选项
      - 如果用户输入无效 → 要求重新输入

3. **应用决策**：
   - 根据用户选择更新配置
   - 记录决策到 decisions.md

4. **确认重要操作**：

   对于破坏性操作（删除、覆盖）：

   a. 显示将要执行的操作
   b. 询问确认：
      ```
      ⚠️  警告：即将删除以下文件:
      - old-config.json
      - legacy-code/

      是否继续? (yes/no)
      ```
   c. 等待明确的 "yes" 才继续
```

---

## 调试与优化

### 1. 调试技巧

#### 添加调试输出

```markdown
## Outline

1. **执行步骤 1**：
   - 读取 spec.md
   - **调试输出**："已读取 spec.md，共 [行数] 行"

2. **执行步骤 2**：
   - 提取需求
   - **调试输出**："提取到 [数量] 个需求："
   - **调试输出**：列出前 3 个需求

3. **执行步骤 3**：
   - 生成报告
   - **调试输出**："报告长度: [字数] 字"
```

#### 错误追踪

```markdown
## Outline

1. 尝试执行操作

2. **如果出错**：
   - 捕获错误信息
   - 输出详细上下文：
     ```
     ❌ 错误发生在步骤 2.3

     正在执行: 读取 data-model.md
     文件路径: specs/001-feature/data-model.md
     错误: FileNotFoundError

     可能原因:
     1. 文件尚未创建（需要先运行 /speckit.plan）
     2. 路径错误

     建议: 运行 /speckit.plan 生成数据模型
     ```
   - 停止执行，等待用户修复
```

### 2. 性能优化

#### 避免重复读取

```markdown
## Outline

1. **一次性加载所有需要的文件**：

   在开始时：
   - 读取 spec.md → 存储到变量 SPEC_CONTENT
   - 读取 plan.md → 存储到变量 PLAN_CONTENT
   - 读取 tasks.md → 存储到变量 TASKS_CONTENT

2. **在后续步骤中使用已加载的内容**：

   ✅ 好的做法:
   - 从 SPEC_CONTENT 提取需求
   - 从 PLAN_CONTENT 提取技术栈

   ❌ 不好的做法:
   - 再次读取 spec.md（浪费）
   - 再次读取 plan.md（浪费）
```

#### 智能缓存

```markdown
## Outline

1. **检查缓存**：

   如果存在 .cache/analysis-result.json：
   - 读取缓存
   - 检查缓存时间戳
   - 如果 < 1 小时：
     - 使用缓存结果
     - 输出："使用缓存的分析结果"
     - 跳过分析步骤

2. **如果缓存不存在或过期**：
   - 执行完整分析
   - 保存结果到 .cache/analysis-result.json
   - 记录时间戳
```

---

## 最佳实践

### 1. 命令设计原则

#### 单一职责

```markdown
✅ 好的命令设计：
- /speckit.stats → 只统计项目数据
- /speckit.deps → 只检查依赖
- /speckit.review → 只审查代码

❌ 不好的命令设计：
- /speckit.do-everything → 统计、检查、审查、修复...全做
```

#### 可组合性

```markdown
命令应该可以串联使用：

/speckit.review       → 审查代码，生成报告
/speckit.implement    → 根据报告修复问题
/speckit.review       → 再次审查，确认修复

通过 handoffs 实现：
---
handoffs:
  - label: 修复问题
    agent: speckit.implement
---
```

### 2. 文档编写规范

#### 清晰的描述

```yaml
✅ 好的描述：
description: 分析代码质量并生成详细的审查报告

❌ 不好的描述：
description: 做一些代码相关的事情
```

#### 完整的 Outline

```markdown
✅ 好的 Outline:
## Outline

1. 初始化（做什么，为什么）
2. 执行分析（具体步骤）
3. 生成报告（格式要求）
4. 用户交互（如何处理）
5. 错误处理（异常情况）

❌ 不好的 Outline:
## Outline

1. 分析代码
2. 生成报告
```

### 3. 错误处理

```markdown
## Outline

1. **验证前置条件**：

   必须检查:
   - 文件是否存在
   - 依赖是否安装
   - 配置是否正确

   如果任何检查失败:
   - 输出清晰的错误信息
   - 提供解决方案
   - 停止执行

2. **优雅降级**：

   如果非关键操作失败:
   - 记录警告
   - 继续执行
   - 在最后报告警告

3. **用户友好的错误信息**：

   ✅ 好的错误信息:
   ```
   ❌ 错误: 无法读取 spec.md

   原因: 文件不存在

   解决方案:
   1. 运行 /speckit.specify 创建规格文件
   2. 或者检查文件路径是否正确

   当前路径: specs/001-feature/spec.md
   ```

   ❌ 不好的错误信息:
   ```
   Error: File not found
   ```
```

### 4. 用户体验

#### 进度反馈

```markdown
## Outline

1. 对于长时间操作：

   ✅ 提供进度更新:
   - "正在分析文件 1/25..."
   - "已完成 40%"
   - "预计剩余时间: 30 秒"

2. 使用视觉元素：

   ✅ 使用 emoji 和格式:
   - ✅ 成功
   - ❌ 失败
   - ⚠️  警告
   - 📊 统计
   - 💡 建议

3. 结构化输出：

   ✅ 使用表格和列表:
   ```
   | 文件类型 | 数量 | 行数 |
   |---------|------|------|
   | JS      | 45   | 2341 |
   | TS      | 23   | 1567 |
   ```
```

#### 交互设计

```markdown
## Outline

1. **提供默认选项**：

   ✅ 好的交互:
   ```
   **Recommended:** Option A - 快速模式

   | Option | Description |
   |--------|-------------|
   | A | 快速模式 (推荐) |
   | B | 详细模式 |

   Reply with 'yes' for recommendation or option letter
   ```

   用户只需输入 "yes" 即可

2. **减少交互次数**：

   ✅ 合并相关问题:
   ```
   Q1: 测试框架和代码检查工具

   A. Jest + ESLint (推荐)
   B. Vitest + Biome
   C. 自定义
   ```

   ❌ 分开问:
   ```
   Q1: 测试框架?
   Q2: 代码检查工具?
   ```
```

---

## 完整示例：创建 `/speckit.document` 命令

让我们从零开始创建一个完整的命令，生成项目文档。

### 需求分析

**功能**：
1. 自动生成项目文档
2. 包括 API 文档、架构说明、部署指南
3. 可选择详细程度（quick/standard/detailed）
4. 支持多种格式（markdown/pdf）

### 实现步骤

**步骤 1**：创建脚本（如果需要）

```bash
# scripts/bash/analyze-structure.sh
#!/bin/bash

# 分析项目结构
echo '{'
echo '  "directories": ['
find . -type d -not -path '*/node_modules/*' -not -path '*/.git/*' |
  while read dir; do
    echo "    \"$dir\","
  done | sed '$ s/,$//'
echo '  ],'
echo '  "files": ['
find . -type f -not -path '*/node_modules/*' -not -path '*/.git/*' |
  while read file; do
    echo "    \"$file\","
  done | sed '$ s/,$//'
echo '  ]'
echo '}'
```

**步骤 2**：创建命令文件

```markdown
---
description: 自动生成项目文档（API、架构、部署指南）
scripts:
  sh: scripts/bash/analyze-structure.sh
  ps: scripts/powershell/analyze-structure.ps1
handoffs:
  - label: 更新文档
    agent: speckit.document
    prompt: 更新已有文档
---

## User Input

```text
$ARGUMENTS
```

你必须考虑用户的输入（如果不为空）。

## Outline

### 1. 初始化与配置

1. **解析用户选项**（从 `$ARGUMENTS`）：

   - 详细程度：
     - "quick" → 简短文档
     - "standard" → 标准文档（默认）
     - "detailed" → 详细文档

   - 输出格式：
     - "markdown" → .md 文件（默认）
     - "pdf" → PDF 文件（需要 pandoc）

   - 示例: `$ARGUMENTS = "detailed pdf"` → 详细 + PDF

2. **运行结构分析脚本**：
   - 执行 `{SCRIPT}`
   - 解析返回的 JSON：`DIRECTORIES`, `FILES`

3. **检测项目类型**：
   - 检查特征文件：
     - package.json → Node.js
     - requirements.txt → Python
     - Cargo.toml → Rust
   - 读取配置文件识别框架

### 2. 收集文档内容

4. **收集基础信息**：
   - 项目名称（从 package.json 或 README）
   - 版本号
   - 描述
   - 作者/维护者
   - License

5. **分析架构**（如果选择 standard 或 detailed）：

   a. 读取以下文件（如果存在）：
      - specs/*/plan.md → 技术栈
      - specs/*/data-model.md → 数据模型
      - specs/*/contracts/ → API 契约

   b. 分析项目结构：
      - 识别分层（controllers, services, models）
      - 识别配置文件
      - 识别测试目录

6. **生成 API 文档**（如果有 API）：

   从以下来源提取 API 信息：
   - contracts/api-spec.json（如果存在）
   - 代码注释（JSDoc, Python docstrings）
   - Express/FastAPI 路由定义

   对于每个 API 端点，记录：
   - HTTP 方法
   - 路径
   - 请求参数
   - 响应格式
   - 示例

7. **生成架构图**（如果选择 detailed）：

   使用 Mermaid 语法：

   ```mermaid
   graph TD
       Client[客户端] --> API[API 层]
       API --> Service[业务逻辑层]
       Service --> DB[(数据库)]
   ```

### 3. 生成文档文件

8. **创建文档目录**：
   - 创建 `docs/` 目录（如果不存在）
   - 创建子目录：
     - docs/api/
     - docs/architecture/
     - docs/deployment/

9. **生成主文档** `docs/README.md`：

   ```markdown
   # [项目名称] 文档

   版本: [版本号]
   最后更新: [日期]

   ## 目录

   - [项目概述](#项目概述)
   - [快速开始](#快速开始)
   - [架构设计](./architecture/README.md)
   - [API 文档](./api/README.md)
   - [部署指南](./deployment/README.md)

   ## 项目概述

   [项目描述]

   **技术栈**:
   - [技术1]
   - [技术2]

   **主要功能**:
   - [功能1]
   - [功能2]

   ## 快速开始

   ### 环境要求

   - [要求1]
   - [要求2]

   ### 安装

   \`\`\`bash
   # 克隆仓库
   git clone ...

   # 安装依赖
   [安装命令]

   # 启动
   [启动命令]
   \`\`\`
   ```

10. **生成 API 文档** `docs/api/README.md`：

    对于每个 API 端点：

    ```markdown
    ## POST /api/tasks

    创建新任务

    ### 请求

    **Headers**:
    ```json
    {
      "Content-Type": "application/json",
      "Authorization": "Bearer {token}"
    }
    ```

    **Body**:
    ```json
    {
      "title": "string",
      "description": "string",
      "due_date": "2024-01-01"
    }
    ```

    ### 响应

    **成功 (200)**:
    ```json
    {
      "id": "uuid",
      "title": "string",
      "created_at": "timestamp"
    }
    ```

    **错误 (400)**:
    ```json
    {
      "error": "Invalid input"
    }
    ```

    ### 示例

    \`\`\`bash
    curl -X POST http://localhost:3000/api/tasks \
      -H "Content-Type: application/json" \
      -d '{"title": "Test task"}'
    \`\`\`
    ```

11. **生成架构文档** `docs/architecture/README.md`：

    ```markdown
    # 架构设计

    ## 系统架构

    \`\`\`mermaid
    [生成的架构图]
    \`\`\`

    ## 分层设计

    ### API 层 (routes/)
    - 处理 HTTP 请求
    - 参数验证
    - 返回响应

    ### 业务逻辑层 (services/)
    - 实现业务规则
    - 调用数据层

    ### 数据层 (models/)
    - 数据库交互
    - 数据验证

    ## 数据模型

    [从 data-model.md 复制内容]

    ## 技术选型

    [从 plan.md 和 research.md 提取]

    ### 为什么选择 [技术]?

    - 原因1
    - 原因2
    ```

12. **生成部署指南** `docs/deployment/README.md`：

    ```markdown
    # 部署指南

    ## 环境配置

    ### 环境变量

    \`\`\`bash
    # .env 文件
    DATABASE_URL=postgresql://...
    API_KEY=...
    PORT=3000
    \`\`\`

    ## Docker 部署

    \`\`\`bash
    # 构建镜像
    docker build -t my-app .

    # 运行容器
    docker run -p 3000:3000 my-app
    \`\`\`

    ## 生产环境部署

    ### 步骤

    1. 准备服务器
    2. 安装依赖
    3. 配置环境变量
    4. 启动服务
    5. 配置反向代理（Nginx）

    ### 监控

    - 日志: /var/log/app/
    - 指标: Prometheus + Grafana
    ```

### 4. 格式转换（如果需要）

13. **如果用户选择 PDF 格式**：

    a. 检查 pandoc 是否安装：
       - 运行: `pandoc --version`
       - 如果未安装: 提示用户安装

    b. 转换 Markdown 到 PDF：
       ```bash
       pandoc docs/README.md \
         -o docs/project-documentation.pdf \
         --toc \
         --number-sections
       ```

    c. 报告: "✅ PDF 已生成: docs/project-documentation.pdf"

### 5. 验证与报告

14. **验证生成的文档**：

    检查:
    - [ ] 所有链接有效
    - [ ] Markdown 格式正确
    - [ ] 代码块语法高亮正确
    - [ ] Mermaid 图表可渲染

15. **生成目录索引**：

    在 `docs/` 根目录创建索引文件列出所有文档

16. **生成完成报告**：

    ```
    ✅ 项目文档已生成

    📊 统计:
    - 总页面: 5 个
    - API 端点: 12 个
    - 架构图: 3 个

    📁 生成的文件:
    - docs/README.md (主文档)
    - docs/api/README.md (API 文档)
    - docs/architecture/README.md (架构文档)
    - docs/deployment/README.md (部署指南)
    [如果生成 PDF: - docs/project-documentation.pdf]

    🔗 快速访问:
    - 在浏览器中打开: docs/README.md
    - 或运行文档服务器: npx serve docs/

    💡 提示:
    - 文档会随项目更新，建议定期重新生成
    - 可以手动编辑文档并提交到版本控制
    ```

### 6. 后续操作

17. **提供更新选项**：

    询问用户:
    ```
    文档已生成。后续操作:

    A. 将文档添加到 Git
    B. 更新现有文档（增量）
    C. 完成

    选择 (A/B/C):
    ```

18. **如果用户选择 A（添加到 Git）**：

    ```bash
    git add docs/
    git commit -m "docs: 生成项目文档"
    ```

    报告: "✅ 文档已添加到 Git"

### 7. 错误处理

**可能的错误**：

- 无法检测项目类型：
  - 提示: "未识别的项目类型，将生成通用文档"
  - 继续执行

- API 契约文件不存在：
  - 提示: "跳过 API 文档生成（未找到契约文件）"
  - 继续生成其他部分

- pandoc 未安装（PDF 格式）：
  - 错误: "PDF 生成失败: 未安装 pandoc"
  - 建议: "安装 pandoc: npm install -g pandoc"
  - 提供 Markdown 版本

**关键错误**（停止执行）：

- 无法创建 docs/ 目录
- 无法写入文件（权限问题）

## Notes

- 这个命令可以独立运行，也可以在其他命令之后运行
- 生成的文档应该是可维护的，不要过度自动化
- 提供清晰的视觉反馈和进度更新
```

### 使用示例

```bash
# 生成标准文档
/speckit.document

# 生成详细文档
/speckit.document detailed

# 生成 PDF 格式
/speckit.document pdf

# 生成详细的 PDF 文档
/speckit.document detailed pdf

# 快速文档
/speckit.document quick
```

---

## 总结

### 核心要点

1. **Markdown 编程 = 自然语言指令 + AI 执行**
2. **文件结构 = YAML 元数据 + 用户输入 + 执行步骤**
3. **关键在于清晰、详细的 Outline**
4. **充分利用 AI 的理解能力**

### 创建命令的步骤

```mermaid
flowchart LR
    A[定义需求] --> B[设计交互]
    B --> C[编写 YAML]
    C --> D[编写 Outline]
    D --> E[测试验证]
    E --> F[优化完善]

    style A fill:#e1f5e1
    style D fill:#fff9c4
    style F fill:#c8e6c9
```

### 从这里开始

1. **选择一个简单的场景**（如统计、分析）
2. **创建基础命令文件**（只包含 description 和简单 Outline）
3. **测试并迭代**（逐步添加功能）
4. **参考现有命令**（学习最佳实践）

### 推荐学习路径

```
第 1 天: 创建简单的只读命令（stats, info）
第 2 天: 添加文件写入（生成报告）
第 3 天: 集成脚本调用
第 4 天: 添加用户交互
第 5 天: 实现复杂工作流
```

---

**下一步行动**：

1. 阅读 Spec Kit 中的现有命令文件
2. 尝试创建你的第一个自定义命令
3. 分享你的命令到社区！

**记住**：Markdown 编程的威力在于 AI 能理解你的意图，而不是机械地执行代码。写清楚你想要什么，AI 会帮你实现！
