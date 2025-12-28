# 代码审查工具设计方案
## 基于 Spec-Kit 理念的渐进式代码分析系统

### 一、核心理念

**Analysis as Code**: 将代码分析过程定义为可执行的规范文档

关键特性：
1. **分层扫描** - 避免上下文爆炸
2. **导航文档** - AI的"记忆"和"地图"
3. **增量分析** - 每步产生可复用的中间产物
4. **可恢复性** - 随时可以从中断处继续

---

## 二、分层扫描策略（Layered Analysis）

### Layer 0: 项目发现层（Project Discovery）
**目标**：快速建立项目全局认知
**扫描内容**：
- 技术栈识别（package.json, pom.xml, requirements.txt, Gemfile等）
- 项目类型（Web应用、微服务、库、CLI工具等）
- 构建工具（Maven, Gradle, npm, webpack等）
- 目录结构骨架

**输出**：`00-project-overview.md`
```markdown
# 项目概览
- **项目名称**: xxx
- **技术栈**: Spring Boot 2.5 + MySQL + Redis
- **项目类型**: 微服务应用
- **主要模块**: [user-service, order-service, payment-service]
```

### Layer 1: 基础设施层（Infrastructure Layer）
**目标**：识别项目使用的基础技术
**扫描内容**：
- 数据库连接配置 → 数据库类型和数量
- 缓存配置 → Redis/Memcached
- 消息队列配置 → Kafka/RabbitMQ/RocketMQ
- 定时任务 → Quartz/Cron/ScheduledTask
- 分布式框架 → Dubbo/Spring Cloud

**输出**：`01-infrastructure-map.md`

### Layer 2: API接口层（API Layer）
**目标**：提取所有对外接口
**扫描策略**：
- Spring: 扫描 @RestController, @RequestMapping
- Express: 扫描 app.get/post/put/delete
- Django: 扫描 urls.py + views
- FastAPI: 扫描 @app.get/post 装饰器

**输出**：`02-api-endpoints.md`
```markdown
# API接口文档
## 用户服务
### POST /api/users
- 描述: 创建用户
- 参数: {name, email, password}
- 返回: User对象
- 所在文件: src/controllers/UserController.java:45
```

### Layer 3: 数据模型层（Data Model Layer）
**目标**：提取数据库设计
**扫描策略**：
- JPA Entity / MyBatis Mapper
- Django Models
- Sequelize Models
- SQL Migration 文件

**输出**：`03-database-schema.md`

### Layer 4: 业务逻辑层（Business Logic Layer）
**目标**：理解核心业务流程
**扫描策略**：
- 识别Service层
- 提取关键业务方法
- 分析调用链路

**输出**：`04-business-logic.md`

### Layer 5: 代码质量层（Code Quality Layer）
**目标**：发现代码问题
**检查内容**：
- 安全漏洞（SQL注入、XSS、硬编码密码）
- 性能问题（N+1查询、大循环、内存泄漏）
- 代码异味（过长方法、重复代码、复杂度过高）
- 最佳实践违反

**输出**：`05-issues-report.md`

---

## 三、导航文档设计（Navigator Spec）

这是整个分析过程的"指挥官"，格式类似spec-kit的spec文件：

```yaml
# code-review.spec.yaml

version: 1.0
project:
  name: legacy-ecommerce
  path: /path/to/project
  type: java-spring-boot

strategy:
  # 渐进式扫描策略
  scan_mode: progressive  # progressive | full | selective
  max_file_size: 5000     # 单文件最大行数
  max_context_per_step: 50000  # 每步最大token数

analysis_plan:
  # 第0步：项目发现
  - step: project-discovery
    description: "快速扫描项目结构和技术栈"
    targets:
      - pattern: "**/package.json"
      - pattern: "**/pom.xml"
      - pattern: "**/build.gradle"
      - pattern: "**/*.properties"
      - pattern: "**/*.yml"
    max_files: 20
    output: "00-project-overview.md"
    next_step_depends_on: "00-project-overview.md"

  # 第1步：基础设施扫描
  - step: infrastructure-scan
    description: "识别数据库、缓存、消息队列等基础设施"
    context:
      - "00-project-overview.md"  # 读取上一步结果
    targets:
      - pattern: "**/application*.yml"
      - pattern: "**/database.properties"
      - pattern: "**/*Config.java"
      - keywords: ["DataSource", "RedisTemplate", "KafkaTemplate"]
    output: "01-infrastructure-map.md"

  # 第2步：API接口扫描
  - step: api-scan
    description: "提取所有REST API接口"
    context:
      - "00-project-overview.md"
      - "01-infrastructure-map.md"
    targets:
      - pattern: "**/*Controller.java"
      - pattern: "**/routes/**/*.js"
      - annotations: ["@RestController", "@RequestMapping", "@GetMapping"]
    output: "02-api-endpoints.md"
    analysis_depth: signature  # signature | full-implementation

  # 第3步：数据模型扫描
  - step: database-scan
    description: "提取数据库表结构和关系"
    context:
      - "01-infrastructure-map.md"
    targets:
      - pattern: "**/*Entity.java"
      - pattern: "**/models/**/*.py"
      - pattern: "**/migrations/**/*.sql"
      - annotations: ["@Entity", "@Table"]
    output: "03-database-schema.md"
    generate_erd: true  # 生成ER图

  # 第4步：业务逻辑分析
  - step: business-logic-analysis
    description: "理解核心业务流程"
    context:
      - "00-project-overview.md"
      - "02-api-endpoints.md"
      - "03-database-schema.md"
    targets:
      - pattern: "**/*Service.java"
      - pattern: "**/service/**/*.js"
      - focus_on:
          - "OrderService"     # 重点分析订单服务
          - "PaymentService"   # 重点分析支付服务
    output: "04-business-logic.md"
    generate_flowchart: true

  # 第5步：代码质量审查
  - step: code-quality-review
    description: "发现代码问题和潜在bug"
    context:
      - "04-business-logic.md"  # 基于业务逻辑上下文来审查
    checks:
      - type: security
        rules:
          - sql-injection
          - xss
          - hardcoded-secrets
          - insecure-random
      - type: performance
        rules:
          - n-plus-one-query
          - large-loop
          - memory-leak
      - type: maintainability
        rules:
          - long-method        # 方法超过50行
          - high-complexity    # 圈复杂度>10
          - code-duplication   # 重复代码
    output: "05-issues-report.md"

  # 第6步：生成总结报告
  - step: final-report
    description: "汇总所有分析结果"
    context:
      - "00-project-overview.md"
      - "01-infrastructure-map.md"
      - "02-api-endpoints.md"
      - "03-database-schema.md"
      - "04-business-logic.md"
      - "05-issues-report.md"
    output: "ANALYSIS-REPORT.md"
    generate_mindmap: true
    generate_architecture_diagram: true

# 状态管理
state:
  current_step: 0
  completed_steps: []
  failed_steps: []
  checkpoint: "00-project-overview.md"  # 最后一个成功的输出

# 上下文管理策略
context_management:
  # 智能上下文加载
  strategy: smart-loading
  rules:
    - "每步只加载必要的context文件"
    - "使用摘要而非全文（对于大文件）"
    - "优先加载最近3步的输出"
    - "长期记忆放在navigator spec本身"
```

---

## 四、关键技术实现

### 4.1 上下文爆炸的解决方案

#### 方案A: 分层摘要（Hierarchical Summarization）
```
扫描文件 → 生成文件摘要 → 生成模块摘要 → 生成项目摘要
                                              ↓
                                  AI只需要读取项目摘要
```

#### 方案B: 智能文件过滤
```javascript
// 只扫描关键文件，而非全部文件
function smartFilter(files) {
  const priorities = {
    high: ['*Controller.java', '*Service.java', 'application.yml'],
    medium: ['*Repository.java', '*Config.java'],
    low: ['*Test.java', '*DTO.java']
  };

  // 优先扫描high，如果token充足再扫描medium
  return filterByPriority(files, priorities, maxTokens);
}
```

#### 方案C: AST而非全文本
```python
# 不要读取整个文件，只提取关键信息
def extract_api_signatures(file):
    ast = parse_java_file(file)
    return {
        "endpoints": extract_endpoints(ast),
        "methods": extract_method_signatures(ast),
        "dependencies": extract_imports(ast)
    }
```

### 4.2 领航员文档的状态管理

```yaml
# 每次执行后更新状态
state:
  session_id: "analysis-20250101-001"
  current_step: 3
  completed_steps:
    - step: project-discovery
      output: 00-project-overview.md
      token_used: 5000
      completed_at: 2025-01-01T10:00:00Z
    - step: infrastructure-scan
      output: 01-infrastructure-map.md
      token_used: 8000
      completed_at: 2025-01-01T10:05:00Z

  # AI的"短期记忆" - 最近的关键发现
  recent_findings:
    - "项目使用Spring Boot 2.5，存在版本过旧风险"
    - "发现3个数据库：MySQL(主), Redis(缓存), MongoDB(日志)"
    - "使用Kafka作为消息队列，有10个topic"

  # AI的"长期记忆" - 关键上下文
  key_context:
    project_type: "电商微服务系统"
    main_modules: ["user", "order", "payment", "inventory"]
    tech_stack: ["Spring Boot", "MySQL", "Redis", "Kafka"]
```

### 4.3 可恢复性设计

```bash
# 执行分析
$ codereview analyze --spec code-review.spec.yaml

# 中途中断后，可以继续
$ codereview resume --session analysis-20250101-001

# 或者从特定步骤重新开始
$ codereview analyze --spec code-review.spec.yaml --from-step api-scan
```

---

## 五、与Spec-Kit的对比

| 特性 | Spec-Kit | Code-Review Tool |
|------|----------|------------------|
| 核心理念 | Specification as Code | Analysis as Code |
| 输入 | spec.md (测试规范) | code-review.spec.yaml (分析规范) |
| 执行单元 | Step | Layer/Step |
| 输出 | 验证结果 | 分析文档 |
| AI角色 | 执行者+验证者 | 分析者+总结者 |
| 状态管理 | 每个step的状态 | 分层分析的状态 |
| 可恢复性 | ✓ | ✓ |

---

## 六、实际使用示例

### 场景1: 接手遗留项目

```bash
# 1. 初始化分析
$ codereview init --path /path/to/legacy-project

# 自动生成 code-review.spec.yaml

# 2. 开始分析
$ codereview analyze

# 输出:
# ✓ [Step 0/6] 项目发现完成 → 00-project-overview.md
# ✓ [Step 1/6] 基础设施扫描完成 → 01-infrastructure-map.md
# ✓ [Step 2/6] API接口扫描完成 → 02-api-endpoints.md (发现127个接口)
# ⏳ [Step 3/6] 数据模型扫描中...

# 3. 查看结果
$ ls analysis-output/
00-project-overview.md
01-infrastructure-map.md
02-api-endpoints.md
03-database-schema.md
04-business-logic.md
05-issues-report.md
ANALYSIS-REPORT.md
architecture-diagram.svg
business-flow.mmd

# 4. 交互式问答
$ codereview chat
> 这个项目用了哪些消息队列？
AI: 根据 01-infrastructure-map.md，项目使用了Kafka，配置在KafkaConfig.java:23

> 订单服务的核心流程是什么？
AI: 根据 04-business-logic.md，订单流程为：
   创建订单 → 库存扣减 → 发起支付 → 支付回调 → 订单完成
   详见 OrderService.java:156 的 createOrder() 方法
```

### 场景2: 生成架构文档

```bash
$ codereview generate-docs --type architecture

# 输出:
# - architecture.md: 架构说明文档
# - api-reference.md: API参考文档
# - database-design.md: 数据库设计文档
# - deployment.md: 部署文档
```

---

## 七、核心优势

### 7.1 解决上下文爆炸
- ✓ 分层扫描，每层只加载必要信息
- ✓ 智能过滤，跳过不重要的文件
- ✓ 摘要机制，用摘要代替全文

### 7.2 解决遗忘问题
- ✓ 每步输出持久化
- ✓ context字段显式声明依赖
- ✓ state管理保存关键发现

### 7.3 解决幻觉问题
- ✓ 所有分析基于实际代码
- ✓ 输出包含文件路径和行号
- ✓ 可验证性（用户可以点击查看源码）

### 7.4 优雅性
- ✓ 声明式配置（类似spec-kit）
- ✓ 渐进式分析（符合人类理解项目的方式）
- ✓ 可定制化（不同项目可调整spec）

---

## 八、技术实现建议

### 8.1 工具链
```
spec-kit 作为基础框架
  ↓
扩展: code-review-kit
  ↓
插件系统:
  - java-analyzer (Spring Boot分析器)
  - node-analyzer (Express/Nest.js分析器)
  - python-analyzer (Django/FastAPI分析器)
  - go-analyzer (Gin/Echo分析器)
```

### 8.2 关键模块

```
code-review-kit/
├── core/
│   ├── spec-parser.ts        # 解析 code-review.spec.yaml
│   ├── layer-executor.ts     # 执行分层扫描
│   ├── context-manager.ts    # 上下文管理
│   └── state-manager.ts      # 状态管理
├── analyzers/
│   ├── java/
│   │   ├── spring-analyzer.ts
│   │   └── mybatis-analyzer.ts
│   ├── node/
│   │   └── express-analyzer.ts
│   └── python/
│       └── django-analyzer.ts
├── generators/
│   ├── markdown-generator.ts  # 生成分析文档
│   ├── diagram-generator.ts   # 生成架构图
│   └── mindmap-generator.ts   # 生成思维导图
└── cli/
    └── index.ts              # 命令行接口
```

---

## 九、下一步行动

### 第一阶段：MVP（最小可行产品）
1. 实现核心的spec解析器
2. 实现分层扫描引擎
3. 支持Java/Spring Boot项目
4. 实现基础的5层分析

### 第二阶段：增强
1. 支持更多语言和框架
2. 添加可视化（架构图、ER图、流程图）
3. 实现智能问答（基于分析结果）
4. 添加增量分析（只分析变更部分）

### 第三阶段：生态
1. 插件市场
2. 分析模板库
3. 最佳实践库
4. 社区贡献

---

## 十、总结

这个方案的核心创新点：

1. **借鉴Spec-Kit的"文档即代码"思想** → "分析即代码"
2. **分层渐进式** → 避免上下文爆炸
3. **导航文档** → AI的记忆和指南针
4. **可恢复可验证** → 不怕中断，不怕幻觉

**最关键的洞察**：
- Spec-Kit用spec文件驱动AI执行测试
- Code-Review-Kit用analysis-spec驱动AI分析代码
- 两者都是"让AI按照人类定义的流程工作"
- 区别在于：一个是执行验证，一个是理解分析

这就像给AI一个"代码考古学家的工作手册"，而不是让它盲目地阅读所有代码。
