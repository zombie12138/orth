package com.abyss.orth.core.handler.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MethodJobHandler}.
 *
 * <p>Covers: method execution with/without parameters, init/destroy lifecycle, error handling.
 */
class MethodJobHandlerTest {

    // ==================== Test Job Classes ====================

    public static class SimpleJob {
        public boolean executed = false;
        public boolean initialized = false;
        public boolean destroyed = false;

        public void execute() {
            executed = true;
        }

        public void init() {
            initialized = true;
        }

        public void destroy() {
            destroyed = true;
        }
    }

    public static class JobWithParams {
        public Object[] receivedParams = null;

        public void execute(String param1, Integer param2) {
            receivedParams = new Object[] {param1, param2};
        }
    }

    public static class JobWithException {
        public void execute() throws Exception {
            throw new RuntimeException("Execution failed");
        }

        public void init() throws Exception {
            throw new RuntimeException("Init failed");
        }

        public void destroy() throws Exception {
            throw new RuntimeException("Destroy failed");
        }
    }

    // ==================== Execute Tests ====================

    @Test
    void testExecute_withNoParams_shouldInvokeMethod() throws Exception {
        // Given
        SimpleJob job = new SimpleJob();
        Method executeMethod = SimpleJob.class.getMethod("execute");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, null);

        // When
        handler.execute();

        // Then
        assertThat(job.executed).isTrue();
    }

    @Test
    void testExecute_withParams_shouldInvokeWithNullParams() throws Exception {
        // Given
        JobWithParams job = new JobWithParams();
        Method executeMethod =
                JobWithParams.class.getMethod("execute", String.class, Integer.class);
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, null);

        // When
        handler.execute();

        // Then - method is invoked with null parameters
        assertThat(job.receivedParams).isNotNull();
        assertThat(job.receivedParams).hasSize(2);
        assertThat(job.receivedParams[0]).isNull();
        assertThat(job.receivedParams[1]).isNull();
    }

    @Test
    void testExecute_withException_shouldPropagateException() throws Exception {
        // Given
        JobWithException job = new JobWithException();
        Method executeMethod = JobWithException.class.getMethod("execute");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, null);

        // When & Then - InvocationTargetException wraps the RuntimeException
        assertThatThrownBy(() -> handler.execute())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("Execution failed");
    }

    // ==================== Init Tests ====================

    @Test
    void testInit_withInitMethod_shouldInvokeInit() throws Exception {
        // Given
        SimpleJob job = new SimpleJob();
        Method executeMethod = SimpleJob.class.getMethod("execute");
        Method initMethod = SimpleJob.class.getMethod("init");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, initMethod, null);

        // When
        handler.init();

        // Then
        assertThat(job.initialized).isTrue();
    }

    @Test
    void testInit_withNullInitMethod_shouldDoNothing() throws Exception {
        // Given
        SimpleJob job = new SimpleJob();
        Method executeMethod = SimpleJob.class.getMethod("execute");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, null);

        // When
        handler.init();

        // Then - no exception, init not called
        assertThat(job.initialized).isFalse();
    }

    @Test
    void testInit_withException_shouldPropagateException() throws Exception {
        // Given
        JobWithException job = new JobWithException();
        Method executeMethod = JobWithException.class.getMethod("execute");
        Method initMethod = JobWithException.class.getMethod("init");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, initMethod, null);

        // When & Then - InvocationTargetException wraps the RuntimeException
        assertThatThrownBy(() -> handler.init())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("Init failed");
    }

    // ==================== Destroy Tests ====================

    @Test
    void testDestroy_withDestroyMethod_shouldInvokeDestroy() throws Exception {
        // Given
        SimpleJob job = new SimpleJob();
        Method executeMethod = SimpleJob.class.getMethod("execute");
        Method destroyMethod = SimpleJob.class.getMethod("destroy");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, destroyMethod);

        // When
        handler.destroy();

        // Then
        assertThat(job.destroyed).isTrue();
    }

    @Test
    void testDestroy_withNullDestroyMethod_shouldDoNothing() throws Exception {
        // Given
        SimpleJob job = new SimpleJob();
        Method executeMethod = SimpleJob.class.getMethod("execute");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, null);

        // When
        handler.destroy();

        // Then - no exception, destroy not called
        assertThat(job.destroyed).isFalse();
    }

    @Test
    void testDestroy_withException_shouldPropagateException() throws Exception {
        // Given
        JobWithException job = new JobWithException();
        Method executeMethod = JobWithException.class.getMethod("execute");
        Method destroyMethod = JobWithException.class.getMethod("destroy");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, destroyMethod);

        // When & Then - InvocationTargetException wraps the RuntimeException
        assertThatThrownBy(() -> handler.destroy())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("Destroy failed");
    }

    // ==================== ToString Test ====================

    @Test
    void testToString_shouldIncludeTargetAndMethod() throws Exception {
        // Given
        SimpleJob job = new SimpleJob();
        Method executeMethod = SimpleJob.class.getMethod("execute");
        MethodJobHandler handler = new MethodJobHandler(job, executeMethod, null, null);

        // When
        String result = handler.toString();

        // Then
        assertThat(result).contains("SimpleJob");
        assertThat(result).contains("execute");
    }

    // ==================== Full Lifecycle Test ====================

    @Test
    void testFullLifecycle_shouldWorkCorrectly() throws Exception {
        // Given
        SimpleJob job = new SimpleJob();
        Method executeMethod = SimpleJob.class.getMethod("execute");
        Method initMethod = SimpleJob.class.getMethod("init");
        Method destroyMethod = SimpleJob.class.getMethod("destroy");
        MethodJobHandler handler =
                new MethodJobHandler(job, executeMethod, initMethod, destroyMethod);

        // When - full lifecycle
        handler.init();
        handler.execute();
        handler.destroy();

        // Then
        assertThat(job.initialized).isTrue();
        assertThat(job.executed).isTrue();
        assertThat(job.destroyed).isTrue();
    }
}
