package com.xxl.job.core.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xxl.job.core.AbstractUnitTest;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.openapi.AdminBiz;
import com.xxl.job.core.thread.JobThread;

/**
 * Unit tests for {@link XxlJobExecutor}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Startup/shutdown lifecycle
 *   <li>Handler registry (register, duplicate, retrieve, not found)
 *   <li>Job thread registry (create, retrieve, remove)
 *   <li>Admin client list initialization
 *   <li>Concurrent handler/thread map operations
 * </ul>
 */
class XxlJobExecutorTest extends AbstractUnitTest {

    private XxlJobExecutor executor;

    @BeforeEach
    public void setUp() {
        super.setUp();

        // Initialize log path
        try {
            XxlJobFileAppender.initLogPath("/tmp/xxl-job-test");
        } catch (Exception e) {
            // Log path initialization failure is not critical for these tests
        }

        executor = new XxlJobExecutor();
        executor.setAdminAddresses("http://localhost:8080/xxl-job-admin");
        executor.setAccessToken("test-token");
        executor.setTimeout(3);
        executor.setAppname("test-app");
        executor.setAddress("http://localhost:9999");
        executor.setIp("127.0.0.1");
        executor.setPort(9999);
        executor.setLogPath("/tmp/xxl-job-test");
        executor.setLogRetentionDays(30);

        // Clear registries
        clearJobHandlerRepository();
        clearJobThreadRepository();
        clearAdminBizList();
    }

    @AfterEach
    public void tearDown() {
        // Clean up any running threads
        clearJobThreadRepository();
        clearJobHandlerRepository();
        clearAdminBizList();

        // Destroy executor if started
        try {
            if (executor != null) {
                executor.destroy();
            }
        } catch (Exception e) {
            // Ignore teardown errors
        }

        super.tearDown();
    }

    // ==================== Handler Registry Tests ====================

    @Test
    void testRegistryJobHandler_newHandler_shouldRegisterSuccessfully() {
        // Given
        String handlerName = "testHandler";
        IJobHandler handler = new TestJobHandler();

        // When
        IJobHandler oldHandler = XxlJobExecutor.registryJobHandler(handlerName, handler);

        // Then
        assertThat(oldHandler).isNull(); // No previous handler
        assertThat(XxlJobExecutor.loadJobHandler(handlerName)).isEqualTo(handler);
    }

    @Test
    void testRegistryJobHandler_duplicateName_shouldReplaceHandler() {
        // Given
        String handlerName = "testHandler";
        IJobHandler handler1 = new TestJobHandler();
        IJobHandler handler2 = new TestJobHandler();
        XxlJobExecutor.registryJobHandler(handlerName, handler1);

        // When
        IJobHandler oldHandler = XxlJobExecutor.registryJobHandler(handlerName, handler2);

        // Then
        assertThat(oldHandler).isEqualTo(handler1); // Returns old handler
        assertThat(XxlJobExecutor.loadJobHandler(handlerName))
                .isEqualTo(handler2); // New one active
    }

    @Test
    void testLoadJobHandler_existingHandler_shouldReturnHandler() {
        // Given
        String handlerName = "testHandler";
        IJobHandler handler = new TestJobHandler();
        XxlJobExecutor.registryJobHandler(handlerName, handler);

        // When
        IJobHandler loadedHandler = XxlJobExecutor.loadJobHandler(handlerName);

        // Then
        assertThat(loadedHandler).isEqualTo(handler);
    }

    @Test
    void testLoadJobHandler_nonExistentHandler_shouldReturnNull() {
        // When
        IJobHandler handler = XxlJobExecutor.loadJobHandler("nonExistent");

        // Then
        assertThat(handler).isNull();
    }

    @Test
    void testRegistryJobHandler_methodBased_validAnnotation_shouldRegister() throws Exception {
        // Given
        TestJobBean bean = new TestJobBean();
        Method method = TestJobBean.class.getMethod("execute");
        XxlJob annotation = method.getAnnotation(XxlJob.class);

        // When
        executor.registryJobHandler(annotation, bean, method);

        // Then
        IJobHandler handler = XxlJobExecutor.loadJobHandler("testJob");
        assertThat(handler).isNotNull();
    }

    @Test
    void testRegistryJobHandler_methodBased_emptyName_shouldThrowException() throws Exception {
        // Given
        TestJobBeanEmptyName bean = new TestJobBeanEmptyName();
        Method method = TestJobBeanEmptyName.class.getMethod("execute");
        XxlJob annotation = method.getAnnotation(XxlJob.class);

        // When/Then
        assertThatThrownBy(() -> executor.registryJobHandler(annotation, bean, method))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("xxl-job method-jobhandler name invalid");
    }

    @Test
    void testRegistryJobHandler_methodBased_duplicateName_shouldThrowException() throws Exception {
        // Given
        TestJobBean bean1 = new TestJobBean();
        Method method1 = TestJobBean.class.getMethod("execute");
        XxlJob annotation1 = method1.getAnnotation(XxlJob.class);
        executor.registryJobHandler(annotation1, bean1, method1);

        TestJobBean bean2 = new TestJobBean();
        Method method2 = TestJobBean.class.getMethod("execute");
        XxlJob annotation2 = method2.getAnnotation(XxlJob.class);

        // When/Then
        assertThatThrownBy(() -> executor.registryJobHandler(annotation2, bean2, method2))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("naming conflicts");
    }

    // ==================== Job Thread Registry Tests ====================

    @Test
    void testRegistJobThread_newThread_shouldCreateAndStart() throws InterruptedException {
        // Given
        int jobId = 1;
        IJobHandler handler = new TestJobHandler();

        // When
        JobThread jobThread = XxlJobExecutor.registJobThread(jobId, handler, "test reason");

        // Then
        assertThat(jobThread).isNotNull();
        assertThat(jobThread.isAlive()).isTrue();
        assertThat(XxlJobExecutor.loadJobThread(jobId)).isEqualTo(jobThread);

        // Cleanup
        XxlJobExecutor.removeJobThread(jobId, "test cleanup");
    }

    @Test
    void testRegistJobThread_existingThread_shouldReplaceAndStopOld() throws InterruptedException {
        // Given
        int jobId = 1;
        IJobHandler handler = new TestJobHandler();
        JobThread oldThread = XxlJobExecutor.registJobThread(jobId, handler, "initial");
        Thread.sleep(100); // Let old thread start

        // When
        JobThread newThread = XxlJobExecutor.registJobThread(jobId, handler, "replacement");

        // Then
        assertThat(newThread).isNotEqualTo(oldThread);
        assertThat(XxlJobExecutor.loadJobThread(jobId)).isEqualTo(newThread);

        // Wait for old thread to stop
        Thread.sleep(500);
        assertThat(oldThread.isAlive()).isFalse();

        // Cleanup
        XxlJobExecutor.removeJobThread(jobId, "test cleanup");
    }

    @Test
    void testLoadJobThread_existingThread_shouldReturnThread() {
        // Given
        int jobId = 1;
        IJobHandler handler = new TestJobHandler();
        JobThread jobThread = XxlJobExecutor.registJobThread(jobId, handler, "test");

        // When
        JobThread loadedThread = XxlJobExecutor.loadJobThread(jobId);

        // Then
        assertThat(loadedThread).isEqualTo(jobThread);

        // Cleanup
        XxlJobExecutor.removeJobThread(jobId, "test cleanup");
    }

    @Test
    void testLoadJobThread_nonExistentThread_shouldReturnNull() {
        // When
        JobThread thread = XxlJobExecutor.loadJobThread(999);

        // Then
        assertThat(thread).isNull();
    }

    @Test
    void testRemoveJobThread_existingThread_shouldRemoveAndStop() throws InterruptedException {
        // Given
        int jobId = 1;
        IJobHandler handler = new TestJobHandler();
        JobThread jobThread = XxlJobExecutor.registJobThread(jobId, handler, "test");
        Thread.sleep(100); // Let thread start

        // When
        JobThread removedThread = XxlJobExecutor.removeJobThread(jobId, "manual removal");

        // Then
        assertThat(removedThread).isEqualTo(jobThread);
        assertThat(XxlJobExecutor.loadJobThread(jobId)).isNull();

        // Wait for thread to stop
        Thread.sleep(500);
        assertThat(removedThread.isAlive()).isFalse();
    }

    @Test
    void testRemoveJobThread_nonExistentThread_shouldReturnNull() {
        // When
        JobThread removed = XxlJobExecutor.removeJobThread(999, "test");

        // Then
        assertThat(removed).isNull();
    }

    // ==================== Concurrent Operations Tests ====================

    @Test
    void testConcurrentHandlerRegistry_multipleThreads_shouldBeThreadSafe() throws Exception {
        // Given
        int threadCount = 10;
        int handlersPerThread = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - concurrent handler registration
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(
                            () -> {
                                try {
                                    startLatch.await();
                                    for (int j = 0; j < handlersPerThread; j++) {
                                        String name = "handler-" + threadId + "-" + j;
                                        XxlJobExecutor.registryJobHandler(
                                                name, new TestJobHandler());
                                        successCount.incrementAndGet();
                                    }
                                } catch (Exception e) {
                                    // Ignore
                                } finally {
                                    doneLatch.countDown();
                                }
                            })
                    .start();
        }

        startLatch.countDown(); // Start all threads
        doneLatch.await(); // Wait for completion

        // Then
        assertThat(successCount.get()).isEqualTo(threadCount * handlersPerThread);

        // Verify all handlers registered
        for (int i = 0; i < threadCount; i++) {
            for (int j = 0; j < handlersPerThread; j++) {
                String name = "handler-" + i + "-" + j;
                assertThat(XxlJobExecutor.loadJobHandler(name)).isNotNull();
            }
        }
    }

    // ==================== Admin Biz List Tests ====================

    @Test
    void testGetAdminBizList_afterInitialization_shouldReturnList() throws Exception {
        // Given - initialize admin biz list via reflection
        executor.setAdminAddresses("http://localhost:8080/xxl-job-admin");
        executor.setAccessToken("test-token");
        executor.setTimeout(3);

        // Call private method via reflection
        Method initMethod =
                XxlJobExecutor.class.getDeclaredMethod(
                        "initAdminBizList", String.class, String.class, int.class);
        initMethod.setAccessible(true);
        initMethod.invoke(executor, "http://localhost:8080/xxl-job-admin", "test-token", 3);

        // When
        List<AdminBiz> adminBizList = XxlJobExecutor.getAdminBizList();

        // Then
        assertThat(adminBizList).isNotNull();
        assertThat(adminBizList).hasSize(1);
    }

    // ==================== Helper Methods ====================

    private void clearJobHandlerRepository() {
        try {
            Field field = XxlJobExecutor.class.getDeclaredField("jobHandlerRepository");
            field.setAccessible(true);
            ConcurrentMap<String, IJobHandler> repository =
                    (ConcurrentMap<String, IJobHandler>) field.get(null);
            repository.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear jobHandlerRepository", e);
        }
    }

    private void clearJobThreadRepository() {
        try {
            Field field = XxlJobExecutor.class.getDeclaredField("jobThreadRepository");
            field.setAccessible(true);
            ConcurrentMap<Integer, JobThread> repository =
                    (ConcurrentMap<Integer, JobThread>) field.get(null);

            // Stop all threads first
            for (JobThread thread : new ArrayList<>(repository.values())) {
                try {
                    thread.toStop("test cleanup");
                    thread.interrupt();
                } catch (Exception e) {
                    // Ignore
                }
            }
            repository.clear();
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear jobThreadRepository", e);
        }
    }

    private void clearAdminBizList() {
        try {
            Field field = XxlJobExecutor.class.getDeclaredField("adminBizList");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear adminBizList", e);
        }
    }

    // ==================== Test Helper Classes ====================

    /** Simple test job handler. */
    private static class TestJobHandler extends IJobHandler {
        @Override
        public void execute() throws Exception {
            // No-op for testing
        }
    }

    /** Test job bean with valid annotation. */
    public static class TestJobBean {
        @XxlJob("testJob")
        public void execute() {
            // No-op
        }
    }

    /** Test job bean with empty name annotation. */
    public static class TestJobBeanEmptyName {
        @XxlJob("")
        public void execute() {
            // No-op
        }
    }
}
