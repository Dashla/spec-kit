# Code Review Tool 技术实现架构

## 如何将这个设计整合到 spec-kit 中

---

## 一、核心架构

### 1.1 整体设计

```
spec-kit (现有)
    ↓
spec-kit-code-review (新插件)
    ↓
使用spec-kit的执行引擎 + 扩展代码分析能力
```

### 1.2 关键组件

```
┌─────────────────────────────────────────────────┐
│           Code Review CLI                        │
│  $ codereview analyze --spec review.spec.yaml   │
└──────────────────┬──────────────────────────────┘
                   │
        ┌──────────▼─────────────┐
        │   Spec Parser          │
        │  (解析 spec.yaml)       │
        └──────────┬─────────────┘
                   │
        ┌──────────▼─────────────┐
        │  Layer Orchestrator    │
        │  (分层执行调度器)        │
        └──────────┬─────────────┘
                   │
     ┌─────────────┼─────────────┐
     │             │             │
     ▼             ▼             ▼
┌─────────┐  ┌─────────┐  ┌──────────┐
│Context  │  │ File    │  │ Language │
│Manager  │  │ Scanner │  │ Analyzer │
└─────────┘  └─────────┘  └──────────┘
     │             │             │
     └─────────────┼─────────────┘
                   │
        ┌──────────▼─────────────┐
        │   AI Agent (Claude)    │
        │  (执行分析任务)          │
        └──────────┬─────────────┘
                   │
        ┌──────────▼─────────────┐
        │  Output Generator      │
        │  (生成markdown文档)     │
        └────────────────────────┘
```

---

## 二、关键模块实现

### 2.1 Context Manager - 解决上下文爆炸

这是整个系统最关键的模块。

#### 问题
- 大型项目有几万个文件、几百万行代码
- Claude的上下文窗口有限（即使200K tokens也不够）
- 需要智能的上下文管理策略

#### 解决方案

```typescript
// context-manager.ts

interface ContextWindow {
  maxTokens: number;      // 最大token数
  currentUsage: number;   // 当前使用量
  reserved: number;       // 保留给输出的token
}

interface ContextItem {
  id: string;
  content: string;
  tokens: number;
  priority: 'high' | 'medium' | 'low';
  lastAccessed: Date;
  type: 'spec' | 'previous_output' | 'source_code' | 'summary';
}

class SmartContextManager {
  private window: ContextWindow;
  private items: Map<string, ContextItem>;
  private strategy: ContextStrategy;

  constructor(maxTokens: number = 150000) {
    this.window = {
      maxTokens,
      currentUsage: 0,
      reserved: 30000  // 预留30k给AI输出
    };
    this.items = new Map();
  }

  /**
   * 核心方法：智能加载上下文
   */
  async loadContextForStep(step: AnalysisStep): Promise<string[]> {
    const available = this.window.maxTokens - this.window.reserved;
    const contextItems: ContextItem[] = [];

    // 1. 必须加载：当前步骤的spec定义（最高优先级）
    const specItem = this.createSpecItem(step);
    contextItems.push(specItem);

    // 2. 必须加载：依赖的前置步骤输出
    for (const dep of step.dependencies) {
      const depItem = await this.loadPreviousOutput(dep);
      if (depItem) {
        // 如果文件太大，使用摘要
        if (depItem.tokens > 10000) {
          depItem = await this.summarize(depItem);
        }
        contextItems.push(depItem);
      }
    }

    // 3. 选择性加载：源代码文件
    const sourceFiles = await this.selectSourceFiles(step, available);
    contextItems.push(...sourceFiles);

    // 4. 智能剪裁：如果超出限制
    const finalContext = this.fitToWindow(contextItems, available);

    return finalContext.map(item => item.content);
  }

  /**
   * 智能选择源代码文件
   */
  private async selectSourceFiles(
    step: AnalysisStep,
    availableTokens: number
  ): Promise<ContextItem[]> {
    const files = await this.scanFiles(step.tasks);

    // 按优先级排序
    const prioritized = this.prioritizeFiles(files, step);

    // 智能采样
    const selected: ContextItem[] = [];
    let usedTokens = 0;

    for (const file of prioritized) {
      const item = await this.processFile(file);

      if (usedTokens + item.tokens > availableTokens * 0.6) {
        // 只使用60%的空间给源代码，剩余给其他
        break;
      }

      selected.push(item);
      usedTokens += item.tokens;
    }

    return selected;
  }

  /**
   * 文件优先级评分
   */
  private prioritizeFiles(files: File[], step: AnalysisStep): File[] {
    return files.sort((a, b) => {
      let scoreA = 0, scoreB = 0;

      // 根据文件类型打分
      if (step.name === 'api-scan') {
        if (a.name.includes('Controller')) scoreA += 100;
        if (b.name.includes('Controller')) scoreB += 100;
      }

      if (step.name === 'database-scan') {
        if (a.name.includes('Entity')) scoreA += 100;
        if (b.name.includes('Entity')) scoreB += 100;
      }

      // 根据文件大小打分（小文件优先）
      scoreA -= Math.log(a.size);
      scoreB -= Math.log(b.size);

      // 根据修改时间打分（新文件优先）
      scoreA += (Date.now() - a.mtime.getTime()) / 1000000;
      scoreB += (Date.now() - b.mtime.getTime()) / 1000000;

      return scoreB - scoreA;
    });
  }

  /**
   * 智能摘要：将大文件压缩成摘要
   */
  private async summarize(item: ContextItem): Promise<ContextItem> {
    // 如果是前置步骤的输出文档，提取关键信息
    if (item.type === 'previous_output') {
      // 使用轻量级模型（Haiku）来做摘要
      const summary = await this.callAI({
        model: 'haiku',
        prompt: `
          这是前一步分析的输出文档，请提取最关键的信息：

          ${item.content}

          请只保留：
          1. 关键发现（主要问题、重要特征）
          2. 数据摘要（有多少API、多少表等）
          3. 下游步骤可能需要的上下文

          输出要简洁，控制在2000 tokens以内。
        `
      });

      return {
        ...item,
        content: summary,
        tokens: this.countTokens(summary),
        type: 'summary'
      };
    }

    // 如果是源代码，提取签名和关键部分
    if (item.type === 'source_code') {
      const ast = await this.parseAST(item.content);
      const summary = this.extractSignatures(ast);
      return {
        ...item,
        content: summary,
        tokens: this.countTokens(summary)
      };
    }

    return item;
  }

  /**
   * AST提取：只提取签名，不要函数体
   */
  private extractSignatures(ast: any): string {
    // 示例：对于Java类
    let output = '';

    // 类签名
    output += `class ${ast.className} {\n`;

    // 字段（只要类型和名称）
    for (const field of ast.fields) {
      output += `  ${field.type} ${field.name};\n`;
    }

    // 方法签名（不要方法体）
    for (const method of ast.methods) {
      const params = method.params.map(p => `${p.type} ${p.name}`).join(', ');
      output += `  ${method.returnType} ${method.name}(${params});\n`;

      // 如果是关键方法，保留简化的实现
      if (this.isKeyMethod(method)) {
        output += `    // ${method.summary}\n`;
      }
    }

    output += '}\n';

    return output;
  }

  /**
   * 判断是否是关键方法
   */
  private isKeyMethod(method: any): boolean {
    // 有@Transactional注解
    if (method.annotations.includes('@Transactional')) return true;

    // 有复杂的业务逻辑（根据圈复杂度）
    if (method.complexity > 5) return true;

    // 名称包含关键词
    const keywords = ['create', 'update', 'delete', 'pay', 'refund', 'order'];
    return keywords.some(kw => method.name.toLowerCase().includes(kw));
  }
}
```

---

### 2.2 Layer Orchestrator - 分层执行引擎

```typescript
// layer-orchestrator.ts

interface AnalysisState {
  currentStep: number;
  completedSteps: string[];
  outputs: Map<string, string>;  // step_name -> output_file_path
  context: Record<string, any>;  // 全局上下文（关键发现）
}

class LayerOrchestrator {
  private spec: CodeReviewSpec;
  private state: AnalysisState;
  private contextManager: SmartContextManager;

  async execute() {
    // 恢复状态（如果是resume）
    this.state = await this.loadState();

    for (let i = this.state.currentStep; i < this.spec.analysis_plan.length; i++) {
      const step = this.spec.analysis_plan[i];

      console.log(`\n[Step ${i+1}/${this.spec.analysis_plan.length}] ${step.description}`);

      try {
        // 1. 准备上下文
        const context = await this.contextManager.loadContextForStep(step);

        // 2. 执行分析
        const output = await this.executeStep(step, context);

        // 3. 保存输出
        await this.saveOutput(step, output);

        // 4. 更新状态
        this.state.completedSteps.push(step.name);
        this.state.outputs.set(step.name, step.output.file);

        // 5. 提取关键上下文（用于后续步骤）
        const keyContext = await this.extractKeyContext(output);
        this.state.context = { ...this.state.context, ...keyContext };

        // 6. 保存checkpoint
        await this.saveState();

        // 7. 显示进度
        console.log(`✓ 完成，输出: ${step.output.file}`);
        console.log(`  Token使用: ${context.totalTokens}`);

      } catch (error) {
        console.error(`✗ 步骤失败: ${error.message}`);

        if (this.spec.execution.error_handling.on_step_failure === 'pause') {
          console.log('保存checkpoint，等待用户介入...');
          await this.saveState();
          break;
        }
      }
    }

    console.log('\n✅ 分析完成！');
    this.printSummary();
  }

  /**
   * 执行单个步骤
   */
  private async executeStep(step: AnalysisStep, context: string[]): Promise<string> {
    // 构造AI prompt
    const prompt = this.buildPrompt(step, context);

    // 调用AI（使用spec-kit的agent系统）
    const response = await this.callAI({
      model: step.model || 'sonnet',  // 默认用Sonnet，简单任务可用Haiku
      prompt,
      maxTokens: 30000
    });

    return response;
  }

  /**
   * 构造AI prompt
   */
  private buildPrompt(step: AnalysisStep, context: string[]): string {
    return `
你是一个代码分析专家。当前任务：${step.description}

# 上下文

${context.join('\n\n---\n\n')}

# 任务要求

${step.ai_instructions}

# 输出格式

请严格按照以下模板输出：

${step.output.template}

# 重要提示

1. 所有代码引用必须包含文件路径和行号（例如：UserController.java:45）
2. 不要臆测，只基于实际扫描到的代码
3. 如果某些信息缺失，明确说明"未找到"而不是猜测
4. 优先级：准确性 > 完整性

开始分析：
    `.trim();
  }

  /**
   * 提取关键上下文
   * 这一步很关键：从输出中提取关键信息，供后续步骤使用
   */
  private async extractKeyContext(output: string): Promise<Record<string, any>> {
    // 使用轻量级模型提取关键信息
    const extraction = await this.callAI({
      model: 'haiku',
      prompt: `
        从下面的分析输出中，提取最关键的信息（JSON格式）：

        ${output}

        请提取：
        1. 关键数字（有多少API、多少表等）
        2. 重要发现（主要问题、风险）
        3. 后续步骤可能需要的上下文

        输出格式：
        {
          "key_metrics": {...},
          "key_findings": [...],
          "context_for_next_steps": {...}
        }
      `
    });

    return JSON.parse(extraction);
  }

  /**
   * 保存状态（用于恢复）
   */
  private async saveState() {
    const stateFile = this.spec.state.checkpoint_file || '.code-review-state.json';
    await fs.writeFile(stateFile, JSON.stringify(this.state, null, 2));
  }

  /**
   * 加载状态
   */
  private async loadState(): Promise<AnalysisState> {
    const stateFile = this.spec.state.checkpoint_file || '.code-review-state.json';

    if (await fs.exists(stateFile)) {
      return JSON.parse(await fs.readFile(stateFile, 'utf-8'));
    }

    return {
      currentStep: 0,
      completedSteps: [],
      outputs: new Map(),
      context: {}
    };
  }
}
```

---

### 2.3 Language Analyzer - 语言特定分析器

```typescript
// analyzers/java-spring-analyzer.ts

interface JavaSpringAnalyzer {
  /**
   * 提取Spring Boot的所有REST API
   */
  extractAPIs(files: File[]): APIEndpoint[];

  /**
   * 提取JPA Entity
   */
  extractEntities(files: File[]): DatabaseEntity[];

  /**
   * 分析Spring Bean依赖关系
   */
  analyzeDependencies(files: File[]): DependencyGraph;
}

class JavaSpringAnalyzerImpl implements JavaSpringAnalyzer {
  extractAPIs(files: File[]): APIEndpoint[] {
    const endpoints: APIEndpoint[] = [];

    for (const file of files) {
      // 解析Java AST
      const ast = this.parseJavaFile(file);

      // 查找@RestController类
      for (const cls of ast.classes) {
        if (!this.hasAnnotation(cls, 'RestController')) continue;

        const basePath = this.getRequestMapping(cls);

        // 查找所有@RequestMapping方法
        for (const method of cls.methods) {
          const mapping = this.getMethodMapping(method);
          if (!mapping) continue;

          endpoints.push({
            method: mapping.httpMethod,  // GET, POST, etc.
            path: basePath + mapping.path,
            handler: `${cls.name}.${method.name}`,
            file: file.path,
            line: method.lineNumber,
            params: this.extractParameters(method),
            returnType: method.returnType,
            annotations: method.annotations
          });
        }
      }
    }

    return endpoints;
  }

  extractEntities(files: File[]): DatabaseEntity[] {
    const entities: DatabaseEntity[] = [];

    for (const file of files) {
      const ast = this.parseJavaFile(file);

      for (const cls of ast.classes) {
        if (!this.hasAnnotation(cls, 'Entity')) continue;

        const tableName = this.getTableName(cls);

        const fields = cls.fields.map(field => ({
          name: this.getColumnName(field),
          type: this.mapJavaTypeToSQL(field.type),
          nullable: !this.hasAnnotation(field, 'Column', { nullable: false }),
          isPrimaryKey: this.hasAnnotation(field, 'Id'),
          isUnique: this.hasAnnotation(field, 'Column', { unique: true })
        }));

        entities.push({
          tableName,
          entityClass: cls.name,
          file: file.path,
          line: cls.lineNumber,
          fields,
          relationships: this.extractRelationships(cls)
        });
      }
    }

    return entities;
  }

  private extractRelationships(cls: JavaClass): Relationship[] {
    const relationships: Relationship[] = [];

    for (const field of cls.fields) {
      // @OneToMany
      if (this.hasAnnotation(field, 'OneToMany')) {
        relationships.push({
          type: 'one-to-many',
          targetEntity: field.genericType,  // List<Order> -> Order
          mappedBy: this.getAnnotationValue(field, 'OneToMany', 'mappedBy')
        });
      }

      // @ManyToOne
      if (this.hasAnnotation(field, 'ManyToOne')) {
        relationships.push({
          type: 'many-to-one',
          targetEntity: field.type,
          joinColumn: this.getAnnotationValue(field, 'JoinColumn', 'name')
        });
      }

      // @ManyToMany
      if (this.hasAnnotation(field, 'ManyToMany')) {
        relationships.push({
          type: 'many-to-many',
          targetEntity: field.genericType,
          joinTable: this.getAnnotationValue(field, 'JoinTable', 'name')
        });
      }
    }

    return relationships;
  }
}
```

---

## 三、与spec-kit的集成

### 3.1 作为spec-kit的插件

```typescript
// spec-kit-code-review/index.ts

import { SpecKit, Plugin } from 'spec-kit';

export class CodeReviewPlugin implements Plugin {
  name = 'code-review';

  async register(specKit: SpecKit) {
    // 注册新的spec类型
    specKit.registerSpecType('code-review', {
      parser: CodeReviewSpecParser,
      executor: CodeReviewExecutor
    });

    // 注册新的命令
    specKit.registerCommand('analyze', async (options) => {
      const spec = await this.loadSpec(options.spec);
      const orchestrator = new LayerOrchestrator(spec);
      await orchestrator.execute();
    });

    // 注册新的文件扫描器
    specKit.registerScanner('java-spring', new JavaSpringAnalyzer());
    specKit.registerScanner('node-express', new NodeExpressAnalyzer());
    specKit.registerScanner('python-django', new PythonDjangoAnalyzer());
  }
}

// 使用
const specKit = new SpecKit();
specKit.use(new CodeReviewPlugin());
await specKit.run();
```

### 3.2 复用spec-kit的能力

```typescript
// 复用spec-kit的以下能力：

// 1. Markdown解析器（用于解析输出模板）
import { MarkdownParser } from 'spec-kit/core';

// 2. Agent系统（用于调用Claude AI）
import { Agent } from 'spec-kit/agent';

// 3. 文件系统工具（用于扫描文件）
import { FileSystem } from 'spec-kit/utils';

// 4. 状态管理（用于checkpoint）
import { StateManager } from 'spec-kit/state';
```

---

## 四、实际使用流程

### 4.1 初始化项目分析

```bash
$ cd /path/to/legacy-project

$ codereview init

# 交互式问答
? 项目类型？ (Use arrow keys)
  ❯ Java / Spring Boot
    Node.js / Express
    Python / Django
    Go / Gin

? 分析深度？
    Quick (只分析结构和API)
  ❯ Standard (包含业务逻辑分析)
    Deep (包含代码质量审查)

? 重点关注？ (Space to select)
  ◉ 安全问题
  ◉ 性能问题
  ◯ 代码规范
  ◉ 架构设计

# 生成 code-review.spec.yaml
✓ 配置已生成: ./code-review.spec.yaml
```

### 4.2 执行分析

```bash
$ codereview analyze

[Step 1/6] 项目发现...
  扫描文件: 23个配置文件
  识别技术栈: Spring Boot 2.5.6 + MySQL + Redis + Kafka
  ✓ 完成 (45s, 5,000 tokens)
  输出: 00-project-overview.md

[Step 2/6] 基础设施扫描...
  分析数据库配置...
  分析Redis配置...
  分析Kafka配置...
  ✓ 完成 (1m 20s, 8,000 tokens)
  输出: 01-infrastructure-map.md

[Step 3/6] API接口提取...
  扫描Controller文件: 45个
  发现API端点: 127个
  ✓ 完成 (2m 10s, 15,000 tokens)
  输出: 02-api-endpoints.md

[Step 4/6] 数据库模型提取...
  扫描Entity文件: 32个
  发现数据表: 28个
  生成ER图...
  ✓ 完成 (1m 45s, 12,000 tokens)
  输出: 03-database-schema.md

[Step 5/6] 业务逻辑分析...
  分析核心服务: OrderService, PaymentService, InventoryService
  追踪调用链...
  生成流程图...
  ✓ 完成 (3m 30s, 25,000 tokens)
  输出: 04-business-logic.md

[Step 6/6] 代码质量审查...
  安全扫描...
    发现: 3个SQL注入风险
    发现: 2个硬编码密钥
  性能扫描...
    发现: 5个N+1查询问题
  ✓ 完成 (2m 40s, 18,000 tokens)
  输出: 05-issues-report.md

[Final] 生成总结报告...
  汇总所有分析结果...
  生成架构图...
  ✓ 完成 (1m 10s, 10,000 tokens)
  输出: ANALYSIS-REPORT.md

✅ 分析完成！

总耗时: 12分钟
Token消耗: 93,000
输出文件: 7个

查看报告: cat ANALYSIS-REPORT.md
```

### 4.3 交互式问答

```bash
$ codereview chat

基于分析结果的交互式问答已启动...

> 这个项目用了哪些数据库？

根据 01-infrastructure-map.md，项目使用了3个MySQL数据库：
1. user_db - 用户数据
2. order_db - 订单数据
3. inventory_db - 库存数据

此外还使用了Redis作为缓存。

> 订单创建的流程是什么？

根据 04-business-logic.md，订单创建流程如下：

1. 用户提交订单 (OrderController.createOrder:45)
2. 校验商品库存 (InventoryService.checkStock:78)
3. 扣减库存 (InventoryService.deductStock:92)
4. 创建订单记录 (OrderService.create:156)
5. 发送MQ消息 'order-created' (OrderService.create:178)
6. 返回订单ID

⚠️ 发现的问题：
- 库存扣减和订单创建不在同一个事务中，存在数据一致性风险
- 详见 05-issues-report.md 第12条

> 有哪些高危安全问题？

根据 05-issues-report.md，发现3个高危安全问题：

1. SQL注入风险 (OrderService.java:234)
   - 直接拼接SQL，未使用参数化查询
   - 建议修改为PreparedStatement

2. 硬编码API密钥 (PaymentService.java:89)
   - 支付密钥直接写在代码中
   - 建议迁移到配置文件或密钥管理服务

3. 缺少权限校验 (UserController.java:123)
   - deleteUser接口缺少权限校验
   - 任何登录用户都能删除其他用户

建议优先修复这些问题。
```

---

## 五、关键优势总结

### 5.1 解决的核心问题

| 问题 | 传统方案 | 我们的方案 |
|------|---------|-----------|
| 上下文爆炸 | 全部喂给AI，导致超限 | 分层扫描 + 智能采样 + AST提取 |
| AI遗忘 | 重复提醒AI之前说过的 | 每步输出持久化 + 上下文明确声明依赖 |
| AI幻觉 | AI臆测不存在的代码 | 所有输出包含文件:行号，可验证 |
| 不够优雅 | 各种临时方案拼凑 | 声明式spec + 模板化输出 |
| 无法恢复 | 中断后从头开始 | Checkpoint机制 + 状态管理 |

### 5.2 与spec-kit的协同

**spec-kit的核心价值**：
- 将"测试规范"变成可执行文档
- AI按照人类定义的步骤执行
- 每步有明确的输入、输出、验证

**code-review-tool的核心价值**：
- 将"分析流程"变成可执行文档
- AI按照人类定义的层次分析
- 每层有明确的扫描策略、输出格式、上下文依赖

两者都是"用文档驱动AI工作"，只是应用场景不同。

---

## 六、下一步

1. **立即可做**：
   - 基于spec-kit框架，实现LayerOrchestrator
   - 实现SmartContextManager的核心逻辑
   - 实现JavaSpringAnalyzer（最小可用版本）

2. **短期目标**：
   - 完成Java/Spring Boot项目的完整支持
   - 验证在真实大型项目上的效果
   - 优化token使用和执行速度

3. **长期愿景**：
   - 支持更多语言和框架
   - 建立分析模板市场
   - 实现增量分析（只分析变更部分）
   - 与CI/CD集成（自动代码审查）

这就是一个完整的、可落地的解决方案。
