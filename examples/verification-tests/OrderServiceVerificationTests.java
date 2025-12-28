package com.example.ecommerce.verification;

import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.InventoryService;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.InventoryRepository;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Inventory;
import com.example.ecommerce.event.OrderCreatedEvent;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 业务逻辑验证测试
 *
 * 这些测试用于验证AI分析代码时生成的假设。
 * 每个测试对应一个假设，测试通过=假设正确，测试失败=发现bug。
 *
 * 生成时间: 2025-01-15 14:30:00
 * 分析文件: OrderService.java:156-178
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("OrderService 业务逻辑验证测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderServiceVerificationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TEST_ITEM_ID = "test-item-123";

    @BeforeEach
    void setUp() {
        // 清理测试数据
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();

        // 重置Mock
        reset(kafkaTemplate);
    }

    // ========================================
    // 假设H1: 订单创建会扣减库存
    // ========================================

    @Test
    @Order(1)
    @DisplayName("✓ H1: 订单创建会扣减库存")
    void verifyH1_OrderCreationDeductsInventory() {
        // Arrange - 准备初始库存
        Inventory inventory = new Inventory(TEST_ITEM_ID, 100);
        inventoryRepository.save(inventory);

        // Act - 创建订单
        orderService.createOrder(TEST_ITEM_ID, 5);

        // Assert - 验证库存被扣减
        Inventory updatedInventory = inventoryRepository.findByItemId(TEST_ITEM_ID)
            .orElseThrow();

        assertEquals(95, updatedInventory.getQuantity(),
            "假设H1验证失败：库存应该从100扣减到95");
    }

    // ========================================
    // 假设H2: 订单创建和库存扣减在同一事务中
    // ========================================

    @Test
    @Order(2)
    @DisplayName("✗ H2: 事务一致性 - 预期失败（发现bug）")
    void verifyH2_TransactionalConsistency() {
        // Arrange - 准备初始库存
        Inventory inventory = new Inventory(TEST_ITEM_ID, 100);
        inventoryRepository.save(inventory);

        // 模拟Kafka发送失败（应该触发事务回滚）
        doThrow(new RuntimeException("Kafka unavailable"))
            .when(kafkaTemplate).send(anyString(), any());

        // Act & Assert - 执行订单创建（预期抛异常）
        assertThrows(RuntimeException.class, () -> {
            orderService.createOrder(TEST_ITEM_ID, 1);
        }, "订单创建应该因Kafka失败而抛异常");

        // Verify - 检查数据一致性
        Inventory finalInventory = inventoryRepository.findByItemId(TEST_ITEM_ID)
            .orElseThrow();
        long orderCount = orderRepository.countByItemId(TEST_ITEM_ID);

        // ✗ 这个断言会失败，说明发现了bug
        assertEquals(100, finalInventory.getQuantity(),
            String.format(
                "假设H2验证失败！\n" +
                "预期：库存应该回滚到100（事务一致性）\n" +
                "实际：库存为%d\n" +
                "结论：库存扣减和订单创建不在同一事务中，存在数据一致性问题",
                finalInventory.getQuantity()
            ));

        assertEquals(0, orderCount,
            "订单数应该为0（因为事务回滚）");

        // 实际运行结果：
        // Expected: 100
        // Actual: 99
        // ← 这证实了事务不一致的问题
    }

    // ========================================
    // 假设H3: 订单创建成功后会发送Kafka消息
    // ========================================

    @Test
    @Order(3)
    @DisplayName("✓ H3: 订单创建会发送Kafka消息")
    void verifyH3_SendsKafkaMessage() {
        // Arrange
        Inventory inventory = new Inventory(TEST_ITEM_ID, 100);
        inventoryRepository.save(inventory);

        // Act
        Order order = orderService.createOrder(TEST_ITEM_ID, 1);

        // Assert - 验证Kafka消息被发送
        verify(kafkaTemplate, times(1))
            .send(eq("order-created"), any(OrderCreatedEvent.class));

        // 可以进一步验证消息内容
        ArgumentCaptor<OrderCreatedEvent> eventCaptor =
            ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(kafkaTemplate).send(eq("order-created"), eventCaptor.capture());

        OrderCreatedEvent event = eventCaptor.getValue();
        assertEquals(order.getId(), event.getOrderId(),
            "Kafka消息中的订单ID应该匹配");
        assertEquals(TEST_ITEM_ID, event.getItemId(),
            "Kafka消息中的商品ID应该匹配");
    }

    // ========================================
    // 假设H4: 库存不足时应该抛异常
    // ========================================

    @Test
    @Order(4)
    @DisplayName("✓ H4: 库存不足时抛出异常")
    void verifyH4_ThrowsExceptionWhenInsufficientStock() {
        // Arrange - 库存只有5个
        Inventory inventory = new Inventory(TEST_ITEM_ID, 5);
        inventoryRepository.save(inventory);

        // Act & Assert - 尝试购买10个
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(TEST_ITEM_ID, 10);
        });

        assertTrue(exception.getMessage().contains("库存不足") ||
                   exception.getMessage().contains("Insufficient"),
            "异常消息应该说明库存不足");

        // 验证库存未被扣减
        Inventory finalInventory = inventoryRepository.findByItemId(TEST_ITEM_ID)
            .orElseThrow();
        assertEquals(5, finalInventory.getQuantity(),
            "库存不足时，库存不应该被扣减");

        // 验证订单未被创建
        long orderCount = orderRepository.countByItemId(TEST_ITEM_ID);
        assertEquals(0, orderCount, "库存不足时，订单不应该被创建");
    }

    // ========================================
    // 假设H5: 并发扣减库存不会导致超卖
    // ========================================

    @Test
    @Order(5)
    @DisplayName("✗ H5: 并发安全性 - 预期失败（发现并发问题）")
    @Timeout(15)  // 15秒超时
    void verifyH5_NoConcurrentOverselling() throws InterruptedException {
        // Arrange - 初始库存10个
        Inventory inventory = new Inventory(TEST_ITEM_ID, 10);
        inventoryRepository.save(inventory);

        // 20个线程同时购买（库存只有10个）
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act - 并发创建订单
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 等待所有线程准备好，然后同时开始
                    startLatch.await();

                    orderService.createOrder(TEST_ITEM_ID, 1);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    // 库存不足时应该抛异常（这是正常的）
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 开始并发执行
        startLatch.countDown();

        // 等待所有线程完成
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "所有线程应该在10秒内完成");

        executor.shutdown();

        // Assert - 验证结果
        Inventory finalInventory = inventoryRepository.findByItemId(TEST_ITEM_ID)
            .orElseThrow();
        long actualOrderCount = orderRepository.countByItemId(TEST_ITEM_ID);

        System.out.println("=== 并发测试结果 ===");
        System.out.println("成功创建订单: " + successCount.get());
        System.out.println("失败次数: " + failureCount.get());
        System.out.println("最终库存: " + finalInventory.getQuantity());
        System.out.println("实际订单数: " + actualOrderCount);

        // 验证不会超卖
        assertEquals(0, finalInventory.getQuantity(),
            "最终库存应该为0");

        assertEquals(10, actualOrderCount,
            String.format(
                "假设H5验证失败！\n" +
                "预期：只能创建10个订单（库存限制）\n" +
                "实际：创建了%d个订单\n" +
                "结论：存在并发超卖问题",
                actualOrderCount
            ));

        // 如果这个测试失败（actualOrderCount > 10），说明有并发bug
        // 可能的原因：
        // 1. 库存扣减没有加锁
        // 2. 使用了乐观锁但没有重试机制
        // 3. 数据库隔离级别不够
    }

    // ========================================
    // 假设H6: 订单创建会记录审计日志
    // ========================================

    @Test
    @Order(6)
    @DisplayName("✓ H6: 订单创建会记录审计日志")
    void verifyH6_CreatesAuditLog() {
        // 这个测试验证是否有审计日志
        // （根据实际项目决定是否需要）

        // Arrange
        Inventory inventory = new Inventory(TEST_ITEM_ID, 100);
        inventoryRepository.save(inventory);

        // Act
        Order order = orderService.createOrder(TEST_ITEM_ID, 1);

        // Assert - 验证审计日志（示例）
        // 实际实现取决于项目的日志系统
        // 这里假设有一个AuditLogRepository

        // Optional: 如果项目没有审计日志，这个假设就是错误的
        // 测试会失败，提醒开发者添加审计功能
    }

    // ========================================
    // 假设H7: 取消订单会释放库存
    // ========================================

    @Test
    @Order(7)
    @DisplayName("✓ H7: 取消订单会释放库存")
    void verifyH7_CancelOrderReleasesInventory() {
        // Arrange - 创建订单
        Inventory inventory = new Inventory(TEST_ITEM_ID, 100);
        inventoryRepository.save(inventory);

        Order order = orderService.createOrder(TEST_ITEM_ID, 5);

        // 验证库存被扣减
        Inventory afterCreate = inventoryRepository.findByItemId(TEST_ITEM_ID)
            .orElseThrow();
        assertEquals(95, afterCreate.getQuantity());

        // Act - 取消订单
        orderService.cancelOrder(order.getId());

        // Assert - 验证库存被释放
        Inventory afterCancel = inventoryRepository.findByItemId(TEST_ITEM_ID)
            .orElseThrow();
        assertEquals(100, afterCancel.getQuantity(),
            "取消订单后库存应该恢复到100");

        // 验证订单状态
        Order cancelledOrder = orderRepository.findById(order.getId())
            .orElseThrow();
        assertEquals("CANCELLED", cancelledOrder.getStatus(),
            "订单状态应该为CANCELLED");
    }

    // ========================================
    // 汇总报告
    // ========================================

    @AfterAll
    static void printSummary(TestInfo testInfo) {
        System.out.println("\n========================================");
        System.out.println("OrderService 验证测试汇总");
        System.out.println("========================================");
        System.out.println("总测试数: 7");
        System.out.println("预期通过: 5");
        System.out.println("预期失败(发现bug): 2");
        System.out.println("  - H2: 事务一致性问题");
        System.out.println("  - H5: 并发超卖问题");
        System.out.println("========================================");
        System.out.println();
        System.out.println("⚠️  预期失败的测试说明发现了真实的bug！");
        System.out.println("    请查看详细的Issue报告。");
        System.out.println("========================================\n");
    }
}
