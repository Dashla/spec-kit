# 自我验证的代码审查系统
## Analysis with Verification Loop

---

## 核心问题

**AI分析结果可能是错误的，怎么办？**

传统方案的问题：
```
AI分析代码 → 生成大量文档 → 用户人肉验证 → 容易出错 ❌
```

用户的困境：
- 😰 AI给了100页分析报告，我怎么知道哪些是对的？
- 😰 AI说"订单创建会扣库存"，真的吗？还是AI幻觉？
- 😰 AI说"这里有SQL注入"，如何确认不是误报？
- 😰 我不可能逐行验证所有分析结果

---

## 解决方案：Verification Loop（借鉴spec-kit）

### spec-kit的验证机制

```yaml
# spec-kit的工作流程
steps:
  - name: "实现用户登录"
    verification:
      - run: "npm test -- login.test.js"
      - expect: "所有测试通过"
    action_on_failure: "重新实现"
```

**关键**：不是写完代码就结束，而是：
```
写代码 → 运行测试 → 验证 → 不通过重试 → 直到验证通过 ✓
```

### 代码审查的验证机制

```yaml
# 我们的新设计
analysis_steps:
  - name: "business-logic-analysis"
    description: "分析订单创建流程"

    # AI的分析过程
    analysis:
      - 读取: "OrderService.java"
      - 生成假设: "订单创建时会扣减库存，并发送Kafka消息"

    # 关键：自动验证假设
    verification:
      - type: "behavioral-test"
        generate_test: true
        run_test: true
        expect: "假设被验证"

    # 只有验证通过的分析才输出
    output_on_verified: "04-business-logic.md"
```

---

## 完整的验证驱动分析流程

### 阶段1: 代码理解 + 假设生成

```
AI阅读代码 → 生成假设
```

例如AI读了这段代码：
```java
// OrderService.java:156
@Transactional
public Order createOrder(String itemId, int quantity) {
    // 1. 扣减库存
    inventoryService.deductStock(itemId, quantity);

    // 2. 创建订单
    Order order = new Order(itemId, quantity);
    orderRepository.save(order);

    // 3. 发送消息
    kafkaTemplate.send("order-created", new OrderCreatedEvent(order));

    return order;
}
```

AI生成的假设：
```json
{
  "hypotheses": [
    {
      "id": "H1",
      "claim": "订单创建时会先扣减库存",
      "evidence": "OrderService.java:157调用了inventoryService.deductStock",
      "confidence": 0.95,
      "verification_needed": true
    },
    {
      "id": "H2",
      "claim": "库存扣减和订单创建在同一事务中",
      "evidence": "@Transactional注解在方法上",
      "confidence": 0.7,
      "verification_needed": true  // 需要验证！
    },
    {
      "id": "H3",
      "claim": "订单创建成功后会发送Kafka消息到order-created topic",
      "evidence": "OrderService.java:164",
      "confidence": 0.95,
      "verification_needed": true
    }
  ]
}
```

### 阶段2: 自动生成验证测试

对于每个假设，AI自动生成验证测试：

#### 验证H1: 订单创建会扣减库存

```java
// 自动生成的验证测试
@Test
@DisplayName("验证假设H1: 订单创建会扣减库存")
void verifyH1_OrderCreationDeductsInventory() {
    // Arrange
    String itemId = "test-item-123";
    int initialStock = inventoryService.getStock(itemId);

    // Act
    orderService.createOrder(itemId, 1);

    // Assert
    int finalStock = inventoryService.getStock(itemId);
    assertEquals(initialStock - 1, finalStock,
        "假设H1验证失败：库存未被扣减");
}
```

#### 验证H2: 事务一致性（关键！）

```java
@Test
@DisplayName("验证假设H2: 库存和订单在同一事务中")
void verifyH2_TransactionalConsistency() {
    // Arrange
    String itemId = "test-item-456";
    int initialStock = inventoryService.getStock(itemId);

    // 模拟Kafka发送失败（看是否会回滚）
    doThrow(new RuntimeException("Kafka error"))
        .when(kafkaTemplate).send(anyString(), any());

    // Act - 预期整个事务回滚
    assertThrows(RuntimeException.class, () -> {
        orderService.createOrder(itemId, 1);
    });

    // Assert - 验证库存是否回滚
    int finalStock = inventoryService.getStock(itemId);

    // ❌ 验证失败！
    assertEquals(initialStock, finalStock,
        "假设H2验证失败：库存被扣减但订单未创建，事务不一致！");

    // 实际结果：finalStock = initialStock - 1
    // 说明AI的假设是错误的！inventoryService.deductStock是独立事务
}
```

#### 验证H3: Kafka消息发送

```java
@Test
@DisplayName("验证假设H3: 发送Kafka消息")
void verifyH3_SendsKafkaMessage() {
    // Arrange
    ArgumentCaptor<OrderCreatedEvent> eventCaptor =
        ArgumentCaptor.forClass(OrderCreatedEvent.class);

    // Act
    Order order = orderService.createOrder("test-item", 1);

    // Assert
    verify(kafkaTemplate).send(
        eq("order-created"),
        eventCaptor.capture()
    );

    OrderCreatedEvent event = eventCaptor.getValue();
    assertEquals(order.getId(), event.getOrderId(),
        "假设H3验证通过：确实发送了Kafka消息");
}
```

### 阶段3: 运行验证测试

```bash
# 自动编译和运行验证测试
$ ./mvnw test -Dtest=VerificationTests

Running verifyH1_OrderCreationDeductsInventory...
✓ PASSED (假设H1: 正确)

Running verifyH2_TransactionalConsistency...
✗ FAILED (假设H2: 错误！)
  Expected: 100
  Actual: 99
  库存被扣减但订单未创建，事务不一致

Running verifyH3_SendsKafkaMessage...
✓ PASSED (假设H3: 正确)

Summary:
  2/3 假设验证通过
  1/3 假设验证失败 ← 需要重新分析
```

### 阶段4: 基于验证结果修正分析

```json
{
  "verified_findings": [
    {
      "id": "FINDING-001",
      "type": "critical-bug",
      "title": "订单创建存在事务一致性问题",
      "status": "verified",  // ← 通过测试验证！
      "verification_test": "VerificationTests.verifyH2_TransactionalConsistency",
      "test_result": "FAILED - 发现真实问题",

      "description": "订单创建和库存扣减不在同一事务中",

      "evidence": {
        "code_location": "OrderService.java:157",
        "test_output": "库存被扣减(99)但订单未创建，违反一致性",
        "root_cause": "inventoryService.deductStock()有独立的@Transactional"
      },

      "impact": "高危 - 可能导致库存不准确，财务损失",

      "reproduction_steps": [
        "1. 调用orderService.createOrder()",
        "2. 在save订单后抛出异常",
        "3. 观察库存已扣减但订单未创建"
      ]
    }
  ],

  "unverified_hypotheses": [
    // H2被推翻，重新分析后发现真相
  ],

  "false_positives": []  // 验证测试未通过的"问题"
}
```

---

## 完整的Spec设计

```yaml
# code-review-with-verification.spec.yaml

version: 2.0
name: "verified-code-review"

# 新增：验证配置
verification:
  enabled: true
  test_framework: "junit5"  # 根据项目自动检测
  test_output_dir: ".code-review/verification-tests"

  # 验证策略
  strategy:
    # 所有业务逻辑假设都要验证
    verify_business_logic: true

    # 安全问题用exploit测试验证
    verify_security_issues: true

    # 性能问题用benchmark验证
    verify_performance_issues: true

    # 只输出验证通过的发现
    output_only_verified: true

analysis_plan:

  # ... Layer 0-3 保持不变 ...

  # Layer 4: 业务逻辑分析（带验证）
  - name: "verified-business-logic-analysis"
    description: "分析核心业务流程，并通过测试验证"

    dependencies:
      - "02-api-endpoints.md"
      - "03-database-schema.md"

    focus_on:
      - service: "OrderService"
        methods: ["createOrder", "cancelOrder"]
        reason: "订单是核心业务，必须精确分析"

    # 分析过程
    analysis_process:
      # 步骤1: 读取代码，生成假设
      - step: "generate-hypotheses"
        instruction: |
          阅读OrderService.createOrder()方法，生成关于其行为的假设。

          对于每个假设，给出：
          1. 具体的声明（claim）
          2. 代码证据（evidence）
          3. 置信度（confidence: 0-1）
          4. 是否需要验证（verification_needed: boolean）

          重点关注：
          - 事务边界
          - 数据一致性
          - 异常处理
          - 外部调用（数据库、消息队列、其他服务）

      # 步骤2: 为需要验证的假设生成测试
      - step: "generate-verification-tests"
        instruction: |
          为每个需要验证的假设生成JUnit测试代码。

          测试要求：
          1. 可以直接编译运行
          2. 使用项目现有的测试框架（JUnit5 + Mockito）
          3. 包含详细的断言和错误信息
          4. 测试类名：VerificationTests_OrderService.java

          测试类型：
          - 行为验证：方法是否真的做了它应该做的事
          - 边界验证：异常情况下的行为
          - 一致性验证：事务、分布式一致性

      # 步骤3: 运行验证测试
      - step: "run-verification-tests"
        command: "./mvnw test -Dtest=VerificationTests_*"
        capture_output: true

      # 步骤4: 分析测试结果
      - step: "analyze-test-results"
        instruction: |
          根据测试结果，将假设分类：

          1. 验证通过的假设 → 作为可信的发现输出
          2. 验证失败的假设 → 说明发现了真实的bug！
          3. 测试本身失败 → 重新生成测试或人工介入

          特别注意：
          - 如果假设"代码应该这样工作"验证失败 → 说明有bug
          - 生成详细的问题报告，包含测试证据

    # 输出格式（GitHub Issue风格）
    output:
      file: "04-verified-business-logic.md"
      format: "github-issues"
      template: |
        # 业务逻辑分析报告（已验证）

        **分析方法**: OrderService.createOrder()
        **验证测试**: {{verification_test_count}} 个
        **发现问题**: {{verified_issues_count}} 个

        ---

        {{#each verified_issues}}
        ## 🔴 Issue #{{issue_number}}: {{title}}

        **状态**: ✓ 已验证
        **严重程度**: {{severity}}
        **位置**: `{{file}}:{{line}}`

        ### 问题描述
        {{description}}

        ### 证据

        **源代码**:
        ```java
        {{code_snippet}}
        ```

        **验证测试** ({{test_status}}):
        ```java
        {{verification_test}}
        ```

        **测试输出**:
        ```
        {{test_output}}
        ```

        ### 影响分析
        {{impact}}

        ### 复现步骤
        {{reproduction_steps}}

        ### 建议修复
        {{suggested_fix}}

        ### 相关代码
        {{#each related_files}}
        - `{{file}}:{{line}}` - {{description}}
        {{/each}}

        ---

        [👍 确认] [🔧 生成修复] [❌ 误报] [💬 评论]

        {{/each}}

        ---

        ## 📊 分析统计

        - **验证通过的假设**: {{verified_hypotheses_count}}
        - **发现的真实问题**: {{real_issues_count}}
        - **误报（测试未通过）**: {{false_positives_count}}

  # Layer 5: 安全问题验证
  - name: "verified-security-analysis"
    description: "检测安全漏洞，并通过exploit测试验证"

    analysis_process:
      # 步骤1: 静态分析扫描可疑代码
      - step: "static-scan"
        rules:
          - pattern: ".*PreparedStatement.*"
            check: "参数化查询"
          - pattern: "password|secret|apiKey"
            check: "硬编码密钥"

      # 步骤2: 为可疑代码生成exploit测试
      - step: "generate-exploit-tests"
        instruction: |
          为每个可疑的安全问题生成exploit测试。

          例如：怀疑SQL注入 → 生成注入测试
          例如：怀疑XSS → 生成XSS payload测试
          例如：怀疑权限绕过 → 生成权限测试

      # 步骤3: 在沙箱环境运行exploit
      - step: "run-exploit-tests"
        sandbox: true  # 安全沙箱
        command: "./mvnw test -Dtest=SecurityExploitTests_*"

      # 步骤4: 只报告验证成功的漏洞
      - step: "output-verified-vulnerabilities"
        instruction: |
          只输出exploit测试成功的漏洞（真实漏洞）。

          输出格式：
          - CVE级别的详细报告
          - 包含exploit代码
          - 包含修复建议
          - 包含影响评估

    output:
      file: "05-verified-security-issues.md"
      format: "security-advisory"

  # Layer 6: 性能问题验证
  - name: "verified-performance-analysis"
    description: "检测性能问题，并通过benchmark验证"

    analysis_process:
      # 步骤1: 静态分析发现可疑代码
      - step: "detect-performance-antipatterns"
        patterns:
          - "N+1查询"
          - "循环中的数据库查询"
          - "大事务"
          - "缺失索引"

      # 步骤2: 生成benchmark测试
      - step: "generate-benchmark"
        framework: "jmh"  # Java Microbenchmark Harness
        instruction: |
          为怀疑的性能问题生成benchmark。

          例如：怀疑N+1查询
          ```java
          @Benchmark
          public void benchmarkGetOrders() {
            // 测试实际查询次数
            queryCounter.reset();
            orderService.getOrdersWithItems(100);

            // 如果是N+1，会有101次查询
            assertTrue(queryCounter.getCount() <= 2,
              "发现N+1问题：查询了" + queryCounter.getCount() + "次");
          }
          ```

      # 步骤3: 运行benchmark
      - step: "run-benchmark"
        command: "./mvnw test -Dtest=PerformanceBenchmark_*"
        measure:
          - "execution_time"
          - "query_count"
          - "memory_usage"

      # 步骤4: 只报告有性能数据支撑的问题
      - step: "output-verified-performance-issues"
        threshold:
          query_count: "> 10"
          execution_time: "> 1000ms"

---

## 验证驱动 vs 传统方案对比

### 传统方案

```
AI: "OrderService.createOrder可能有事务一致性问题"
用户: "真的吗？让我看看代码..."
用户: "看了半天，好像是有问题，也可能没问题..."
用户: "算了，先记下来，回头再说"
结果: 问题被忽略，生产出bug ❌
```

### 验证驱动方案

```
AI: "怀疑OrderService.createOrder有事务一致性问题"
AI: "生成验证测试..."
AI: "运行测试... ✗ FAILED"
AI: "测试证实：库存被扣但订单未创建"
AI: "输出验证通过的Issue报告"

用户: 看到Issue #1，有测试证据，立即修复 ✓
```

---

## 输出示例

```markdown
# 🔴 Issue #1: 订单创建存在事务一致性缺陷

**状态**: ✓ 已通过测试验证
**严重程度**: 🔴 Critical
**位置**: `OrderService.java:156-168`
**发现时间**: 2025-01-15 14:23:00
**验证测试**: `VerificationTests.verifyTransactionalConsistency()`

---

## 问题描述

订单创建流程中，库存扣减(`inventoryService.deductStock`)和订单持久化(`orderRepository.save`)不在同一个事务中，导致以下问题：

**场景**: 如果在订单保存后、Kafka消息发送前抛出异常
- ✗ 库存已被扣减
- ✗ 订单未被创建
- ⚠️ 数据不一致

---

## 证据

### 源代码
```java
// OrderService.java:156
@Transactional  // ← 这个事务边界有问题
public Order createOrder(String itemId, int quantity) {

    // 调用其他服务的方法（独立事务）
    inventoryService.deductStock(itemId, quantity); // ← Line 157

    // 创建订单（当前事务）
    Order order = new Order(itemId, quantity);
    orderRepository.save(order);  // ← Line 161

    // 如果这里抛异常，库存已扣但订单未创建
    kafkaTemplate.send("order-created", new OrderCreatedEvent(order)); // ← Line 164

    return order;
}
```

```java
// InventoryService.java:92
@Transactional  // ← 独立的事务！
public void deductStock(String itemId, int quantity) {
    Inventory inventory = inventoryRepository.findByItemId(itemId);
    inventory.setQuantity(inventory.getQuantity() - quantity);
    inventoryRepository.save(inventory);
}  // ← 这里事务就提交了，无法回滚
```

### 验证测试 (✗ FAILED - 证实问题)

```java
@Test
@DisplayName("验证事务一致性")
void verifyTransactionalConsistency() {
    // 准备
    String itemId = "test-item-123";
    inventoryService.addStock(itemId, 100);  // 初始库存100

    // 模拟Kafka发送失败
    doThrow(new RuntimeException("Kafka unavailable"))
        .when(kafkaTemplate).send(anyString(), any());

    // 执行（预期抛异常）
    assertThrows(RuntimeException.class, () -> {
        orderService.createOrder(itemId, 1);
    });

    // 验证数据一致性
    Inventory inventory = inventoryService.getInventory(itemId);
    long orderCount = orderRepository.countByItemId(itemId);

    // ✗ 断言失败
    assertEquals(100, inventory.getQuantity(),
        "预期库存未变(100)，实际已扣减(99)");
    assertEquals(0, orderCount,
        "预期无订单(0)，实际为0 ✓");

    // 结论：库存被扣但订单未创建，数据不一致！
}
```

### 测试输出
```
verifyTransactionalConsistency() FAILED

org.opentest4j.AssertionFailedError:
  预期库存未变(100)，实际已扣减(99) ==>
  expected: <100> but was: <99>

  at OrderServiceVerificationTests.verifyTransactionalConsistency(...)

  库存: 99 (预期100)
  订单数: 0 (预期0)

  ✗ 事务不一致：库存已扣减但订单未创建
```

---

## 影响分析

**业务影响**:
- 💰 库存数据不准确，可能导致超卖或少卖
- 📊 订单统计不准确
- 😡 用户体验差（扣了库存但订单未创建）

**财务影响**:
- 假设每天1000个订单，异常率1%
- 每天10个订单出现库存不一致
- 月损失：10 × 30 = 300个库存单位的差异

**技术影响**:
- 需要定期人工对账
- 需要补偿机制修复数据

---

## 复现步骤

1. 准备环境：
   ```bash
   docker-compose up -d  # 启动MySQL、Kafka
   ./mvnw spring-boot:run
   ```

2. 执行复现测试：
   ```bash
   ./mvnw test -Dtest=VerificationTests#verifyTransactionalConsistency
   ```

3. 观察结果：
   - 查看数据库：`SELECT * FROM inventory WHERE item_id='test-item-123'`
   - 库存变为99（应为100）
   - 查看订单表：无订单记录

---

## 建议修复

### 方案1: 使用分布式事务（推荐）

```java
// OrderService.java
@GlobalTransactional  // Seata分布式事务
public Order createOrder(String itemId, int quantity) {
    inventoryService.deductStock(itemId, quantity);

    Order order = new Order(itemId, quantity);
    orderRepository.save(order);

    kafkaTemplate.send("order-created", new OrderCreatedEvent(order));

    return order;
    // 所有操作要么全部成功，要么全部回滚
}
```

**优点**: 强一致性
**缺点**: 性能开销，需要引入Seata

### 方案2: 本地消息表 + 最终一致性

```java
@Transactional
public Order createOrder(String itemId, int quantity) {
    // 1. 在同一事务中：扣库存 + 创建订单 + 写消息表
    inventoryRepository.deductStock(itemId, quantity);  // 改为直接SQL

    Order order = new Order(itemId, quantity);
    orderRepository.save(order);

    // 写本地消息表（同一事务）
    OutboxMessage msg = new OutboxMessage("order-created", order);
    outboxRepository.save(msg);

    // 2. 异步任务扫描消息表，发送到Kafka
    return order;
}
```

**优点**: 无需分布式事务，性能好
**缺点**: 最终一致性，需要额外的消息表

### 方案3: 调整事务边界（简单）

```java
@Transactional
public Order createOrder(String itemId, int quantity) {
    // 在同一事务中扣库存（不调用其他服务）
    Inventory inventory = inventoryRepository.findByItemId(itemId);
    inventory.deductQuantity(quantity);
    inventoryRepository.save(inventory);

    Order order = new Order(itemId, quantity);
    orderRepository.save(order);

    // Kafka发送失败不影响事务（异步补偿）
    return order;
}

// 事务提交后发送Kafka
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void sendKafkaMessage(OrderCreatedEvent event) {
    kafkaTemplate.send("order-created", event);
}
```

**优点**: 改动最小
**缺点**: 需要将InventoryService逻辑内联

---

## 相关代码

- `OrderService.java:156` - 问题源头（事务边界）
- `InventoryService.java:92` - 独立事务的库存扣减
- `OrderRepository.java:23` - 订单持久化
- `application.yml:34` - 事务管理器配置

---

## 验证测试代码

完整测试代码已生成：`.code-review/verification-tests/OrderServiceVerificationTests.java`

运行命令：
```bash
./mvnw test -Dtest=OrderServiceVerificationTests
```

---

[👍 确认问题] [🔧 自动生成修复PR] [❌ 标记为误报] [💬 添加评论] [📋 导出为Jira Issue]
```

---

## 总结

### 关键改进

1. **假设 → 验证 → 输出** 的闭环
   - 不盲目相信AI分析
   - 所有关键发现都通过测试验证
   - 只输出验证通过的结果

2. **自动生成验证测试**
   - 行为测试：验证代码是否真的做了它应该做的
   - Exploit测试：验证安全漏洞是否真实存在
   - Benchmark测试：验证性能问题是否真实

3. **GitHub Issue风格输出**
   - 简洁、重点突出
   - 包含测试证据（可信度高）
   - 可互动（确认、修复、评论）
   - 包含复现步骤

4. **精细化分析**
   - 对关键业务逻辑：逐行分析 + 测试验证
   - 对普通代码：签名提取 + 静态分析
   - 智能区分重点和非重点

### 与spec-kit的一致性

| spec-kit | 代码审查工具 |
|----------|------------|
| 生成代码 → 运行测试 → 验证 | 分析代码 → 生成测试 → 验证 |
| 测试不通过 → 重新生成代码 | 假设未验证 → 重新分析 |
| 输出通过测试的代码 | 输出通过验证的Issue |
| 文档驱动开发 | 文档驱动分析 |

都是**验证驱动**的闭环系统！
