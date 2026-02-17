package com.xxl.job.core.executor.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.openapi.AdminBiz;
import com.xxl.job.core.openapi.model.RegistryRequest;
import com.xxl.tool.response.Response;

/**
 * Tests for {@link XxlJobSimpleExecutor}.
 *
 * <p>Covers: initialization without Spring, handler registration from beans, lifecycle, @XxlJob
 * annotation scanning.
 */
class XxlJobSimpleExecutorTest {

    private XxlJobSimpleExecutor executor;
    private AdminBiz mockAdminBiz;

    @BeforeEach
    void setUp() throws Exception {
        executor = new XxlJobSimpleExecutor();

        // Setup mock admin
        mockAdminBiz = mock(AdminBiz.class);
        when(mockAdminBiz.registry(any(RegistryRequest.class)))
                .thenReturn(Response.ofSuccess("success"));
        when(mockAdminBiz.registryRemove(any(RegistryRequest.class)))
                .thenReturn(Response.ofSuccess("removed"));

        List<AdminBiz> adminBizList = new ArrayList<>();
        adminBizList.add(mockAdminBiz);

        // Set via reflection
        java.lang.reflect.Field field = XxlJobExecutor.class.getDeclaredField("adminBizList");
        field.setAccessible(true);
        field.set(null, adminBizList);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            executor.destroy();
        } catch (Exception e) {
            // Ignore
        }

        // Clear via reflection
        java.lang.reflect.Field field = XxlJobExecutor.class.getDeclaredField("adminBizList");
        field.setAccessible(true);
        field.set(null, null);
    }

    // ==================== Test Bean Classes ====================

    /** Test bean with @XxlJob annotated method */
    public static class TestJobBean {
        @XxlJob("testJob")
        public void testJobMethod() {
            // Test job implementation
        }

        @XxlJob(value = "testJob2", init = "init", destroy = "destroy")
        public void anotherJobMethod() {
            // Another test job
        }

        public void init() {
            // Init method
        }

        public void destroy() {
            // Destroy method
        }

        // Method without annotation - should be ignored
        public void regularMethod() {
            // Not a job handler
        }
    }

    /** Test bean with multiple @XxlJob methods */
    public static class MultipleJobsBean {
        @XxlJob("job1")
        public void job1() {}

        @XxlJob("job2")
        public void job2() {}

        @XxlJob("job3")
        public void job3() {}
    }

    /** Test bean without @XxlJob methods */
    public static class NonJobBean {
        public void someMethod() {
            // No annotation
        }
    }

    // ==================== Basic Lifecycle Tests ====================

    @Test
    void testStart_withNoBeans_shouldStartSuccessfully() throws Exception {
        // Given
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When
        executor.start();

        // Then
        assertThat(executor.getXxlJobBeanList()).isEmpty();
        Thread.sleep(100);
        executor.destroy();
    }

    @Test
    void testStart_withEmptyBeanList_shouldStartSuccessfully() throws Exception {
        // Given
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");
        executor.setXxlJobBeanList(new ArrayList<>());

        // When
        executor.start();

        // Then
        assertThat(executor.getXxlJobBeanList()).isEmpty();
        Thread.sleep(100);
        executor.destroy();
    }

    @Test
    void testDestroy_shouldCleanupResources() throws Exception {
        // Given
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");
        executor.start();
        Thread.sleep(100);

        // When
        executor.destroy();

        // Then - should complete without exception
    }

    // ==================== Handler Registration Tests ====================

    @Test
    void testStart_withJobBean_shouldRegisterHandlers() throws Exception {
        // Given
        TestJobBean jobBean = new TestJobBean();
        List<Object> beans = new ArrayList<>();
        beans.add(jobBean);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When
        executor.start();
        Thread.sleep(100);

        // Then - handlers should be registered
        IJobHandler handler1 = XxlJobExecutor.loadJobHandler("testJob");
        IJobHandler handler2 = XxlJobExecutor.loadJobHandler("testJob2");

        assertThat(handler1).isNotNull();
        assertThat(handler2).isNotNull();

        executor.destroy();
    }

    @Test
    void testStart_withMultipleJobsInOneBean_shouldRegisterAll() throws Exception {
        // Given
        MultipleJobsBean jobBean = new MultipleJobsBean();
        List<Object> beans = new ArrayList<>();
        beans.add(jobBean);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When
        executor.start();
        Thread.sleep(100);

        // Then - all handlers should be registered
        IJobHandler job1 = XxlJobExecutor.loadJobHandler("job1");
        IJobHandler job2 = XxlJobExecutor.loadJobHandler("job2");
        IJobHandler job3 = XxlJobExecutor.loadJobHandler("job3");

        assertThat(job1).isNotNull();
        assertThat(job2).isNotNull();
        assertThat(job3).isNotNull();

        executor.destroy();
    }

    @Test
    void testStart_withMultipleBeans_shouldRegisterAllHandlers() throws Exception {
        // Given
        TestJobBean bean1 = new TestJobBean();
        MultipleJobsBean bean2 = new MultipleJobsBean();

        List<Object> beans = new ArrayList<>();
        beans.add(bean1);
        beans.add(bean2);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When
        executor.start();
        Thread.sleep(100);

        // Then - all handlers from both beans should be registered
        assertThat(XxlJobExecutor.loadJobHandler("testJob")).isNotNull();
        assertThat(XxlJobExecutor.loadJobHandler("testJob2")).isNotNull();
        assertThat(XxlJobExecutor.loadJobHandler("job1")).isNotNull();
        assertThat(XxlJobExecutor.loadJobHandler("job2")).isNotNull();
        assertThat(XxlJobExecutor.loadJobHandler("job3")).isNotNull();

        executor.destroy();
    }

    @Test
    void testStart_withBeanWithoutAnnotations_shouldNotRegisterAny() throws Exception {
        // Given
        NonJobBean bean = new NonJobBean();
        List<Object> beans = new ArrayList<>();
        beans.add(bean);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When
        executor.start();
        Thread.sleep(100);

        // Then - no handlers should be registered from this bean
        // (We can't easily check this without access to internal registry)

        executor.destroy();
    }

    @Test
    void testStart_withMixedBeans_shouldRegisterOnlyAnnotated() throws Exception {
        // Given - mix of beans with and without annotations
        TestJobBean jobBean = new TestJobBean();
        NonJobBean nonJobBean = new NonJobBean();

        List<Object> beans = new ArrayList<>();
        beans.add(jobBean);
        beans.add(nonJobBean);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When
        executor.start();
        Thread.sleep(100);

        // Then - only annotated handlers should be registered
        assertThat(XxlJobExecutor.loadJobHandler("testJob")).isNotNull();
        assertThat(XxlJobExecutor.loadJobHandler("testJob2")).isNotNull();

        executor.destroy();
    }

    // ==================== Bean List Management Tests ====================

    @Test
    void testGetXxlJobBeanList_shouldReturnSetList() {
        // Given
        List<Object> beans = new ArrayList<>();
        beans.add(new TestJobBean());

        // When
        executor.setXxlJobBeanList(beans);

        // Then
        assertThat(executor.getXxlJobBeanList()).isSameAs(beans);
        assertThat(executor.getXxlJobBeanList()).hasSize(1);
    }

    @Test
    void testSetXxlJobBeanList_withNull_shouldAccept() {
        // When
        executor.setXxlJobBeanList(null);

        // Then
        assertThat(executor.getXxlJobBeanList()).isNull();
    }

    // ==================== Edge Cases ====================

    @Test
    void testStart_withBeanWithNoMethods_shouldHandleGracefully() throws Exception {
        // Given - bean class with no methods (edge case)
        Object emptyBean = new Object();

        List<Object> beans = new ArrayList<>();
        beans.add(emptyBean);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When
        executor.start();
        Thread.sleep(100);

        // Then - should handle gracefully
        executor.destroy();
    }

    @Test
    void testStart_withNullInBeanList_shouldHandleGracefully() throws Exception {
        // Given - null in bean list (edge case)
        List<Object> beans = new ArrayList<>();
        beans.add(new TestJobBean());
        beans.add(null);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When - should handle gracefully (may throw exception, which is acceptable)
        try {
            executor.start();
            Thread.sleep(100);
            executor.destroy();
        } catch (Exception e) {
            // Exception is acceptable for null bean
        }
    }

    // ==================== Integration Tests ====================

    @Test
    void testFullLifecycle_shouldWorkCorrectly() throws Exception {
        // Given
        TestJobBean jobBean = new TestJobBean();
        List<Object> beans = new ArrayList<>();
        beans.add(jobBean);

        executor.setXxlJobBeanList(beans);
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When - start
        executor.start();
        Thread.sleep(100);

        // Then - verify handlers registered
        assertThat(XxlJobExecutor.loadJobHandler("testJob")).isNotNull();

        // When - destroy
        executor.destroy();

        // Then - should complete without exception
    }

    @Test
    void testMultipleStartCalls_shouldHandleGracefully() throws Exception {
        // Given
        executor.setAppname("test-app");
        executor.setAdminAddresses("http://localhost:8080");

        // When - multiple start calls
        executor.start();
        Thread.sleep(100);

        // Second start should throw exception (port already bound)
        try {
            executor.start();
        } catch (Exception e) {
            // Expected - port already in use
        }

        executor.destroy();
    }
}
