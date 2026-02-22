package com.abyss.orth.core.openapi.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.abyss.orth.core.AbstractUnitTest;
import com.abyss.orth.core.constant.ExecutorBlockStrategyEnum;
import com.abyss.orth.core.context.OrthJobHelper;
import com.abyss.orth.core.executor.OrthJobExecutor;
import com.abyss.orth.core.glue.GlueTypeEnum;
import com.abyss.orth.core.handler.IJobHandler;
import com.abyss.orth.core.log.OrthJobFileAppender;
import com.abyss.orth.core.openapi.ExecutorBiz;
import com.abyss.orth.core.openapi.model.IdleBeatRequest;
import com.abyss.orth.core.openapi.model.KillRequest;
import com.abyss.orth.core.openapi.model.LogRequest;
import com.abyss.orth.core.openapi.model.LogResult;
import com.abyss.orth.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Unit tests for {@link ExecutorBizImpl}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Beat/idleBeat endpoints
 *   <li>BEAN glue type execution (new handler, handler not found, handler update)
 *   <li>GLUE_GROOVY execution (first run, code update)
 *   <li>GLUE_SCRIPT execution (Shell, Python, NodeJS, PHP)
 *   <li>Block strategy application (SERIAL, DISCARD_LATER, COVER_EARLY)
 *   <li>Kill job (running, non-existent)
 *   <li>Log retrieval
 *   <li>Concurrent same job triggers
 * </ul>
 */
class ExecutorBizImplTest extends AbstractUnitTest {

    private ExecutorBiz executorBiz;
    private TestJobHandler testJobHandler;
    private static final int TEST_JOB_ID = 200;
    private static final String TEST_HANDLER_NAME = "testHandler";

    @BeforeEach
    public void setUp() {
        super.setUp();
        executorBiz = new ExecutorBizImpl();

        // Initialize log path
        try {
            OrthJobFileAppender.initLogPath("/tmp/orth-test");
        } catch (Exception e) {
            // Log path initialization failure is not critical
        }

        // Register test handler
        testJobHandler = new TestJobHandler();
        OrthJobExecutor.registryJobHandler(TEST_HANDLER_NAME, testJobHandler);
    }

    @AfterEach
    public void tearDown() {
        // Clean up job threads
        OrthJobExecutor.removeJobThread(TEST_JOB_ID, "test cleanup");
        super.tearDown();
    }

    @Test
    void testBeat_shouldReturnSuccess() {
        // When
        Response<String> response = executorBiz.beat();

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testIdleBeat_whenJobNotRunning_shouldReturnSuccess() {
        // Given
        IdleBeatRequest request = new IdleBeatRequest();
        request.setJobId(TEST_JOB_ID);

        // When
        Response<String> response = executorBiz.idleBeat(request);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testIdleBeat_whenJobRunning_shouldReturnFail() throws Exception {
        // Given
        IdleBeatRequest idleBeatRequest = new IdleBeatRequest();
        idleBeatRequest.setJobId(TEST_JOB_ID);

        // Start a job that will keep running
        testJobHandler.setExecutionTime(5000); // Run for 5 seconds
        TriggerRequest triggerRequest = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        executorBiz.run(triggerRequest);

        // Wait for job to start
        Thread.sleep(500);

        // When
        Response<String> response = executorBiz.idleBeat(idleBeatRequest);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMsg()).contains("running");
    }

    @Test
    void testRun_beanGlueType_withNewHandler_shouldExecute() throws Exception {
        // Given
        TriggerRequest request = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);

        // When
        Response<String> response = executorBiz.run(request);

        // Then
        assertThat(response.isSuccess()).isTrue();

        // Wait for execution
        await().atMost(5, TimeUnit.SECONDS).until(() -> testJobHandler.getExecutionCount() > 0);
        assertThat(testJobHandler.getExecutionCount()).isEqualTo(1);
    }

    @Test
    void testRun_beanGlueType_handlerNotFound_shouldReturnFail() {
        // Given
        TriggerRequest request = createBeanTriggerRequest(TEST_JOB_ID, "nonExistentHandler");

        // When
        Response<String> response = executorBiz.run(request);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMsg()).contains("not found");
    }

    @Test
    void testRun_beanGlueType_handlerUpdate_shouldKillOldThread() throws Exception {
        // Given - first execution with original handler
        TriggerRequest request1 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        testJobHandler.setExecutionTime(3000); // Long running
        executorBiz.run(request1);

        // Wait for job thread to be created
        Thread.sleep(500);

        // When - second execution with different handler (simulated by re-registering)
        TestJobHandler newHandler = new TestJobHandler();
        OrthJobExecutor.registryJobHandler(TEST_HANDLER_NAME, newHandler);
        TriggerRequest request2 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        Response<String> response = executorBiz.run(request2);

        // Then - old thread should be killed and new one created
        assertThat(response.isSuccess()).isTrue();

        // Wait and verify new handler is used
        await().atMost(5, TimeUnit.SECONDS).until(() -> newHandler.getExecutionCount() > 0);
        assertThat(newHandler.getExecutionCount()).isGreaterThan(0);
    }

    @Test
    void testRun_groovyGlueType_firstRun_shouldExecute() throws Exception {
        // Given
        String groovyCode =
                "import com.abyss.orth.core.handler.IJobHandler\n"
                        + "import com.abyss.orth.core.context.OrthJobHelper\n"
                        + "class TestGroovyHandler extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {\n"
                        + "        OrthJobHelper.handleSuccess('Groovy executed')\n"
                        + "    }\n"
                        + "}";

        TriggerRequest request = createGroovyTriggerRequest(TEST_JOB_ID, groovyCode);

        // When
        Response<String> response = executorBiz.run(request);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testRun_groovyGlueType_codeUpdate_shouldReloadHandler() throws Exception {
        // Given - first execution
        String groovyCode1 =
                "import com.abyss.orth.core.handler.IJobHandler\n"
                        + "import com.abyss.orth.core.context.OrthJobHelper\n"
                        + "class TestGroovyHandler1 extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {\n"
                        + "        OrthJobHelper.handleSuccess('Version 1')\n"
                        + "    }\n"
                        + "}";

        TriggerRequest request1 = createGroovyTriggerRequest(TEST_JOB_ID, groovyCode1);
        request1.setGlueUpdatetime(System.currentTimeMillis());
        executorBiz.run(request1);

        Thread.sleep(500);

        // When - second execution with updated code
        String groovyCode2 =
                "import com.abyss.orth.core.handler.IJobHandler\n"
                        + "import com.abyss.orth.core.context.OrthJobHelper\n"
                        + "class TestGroovyHandler2 extends IJobHandler {\n"
                        + "    @Override\n"
                        + "    void execute() throws Exception {\n"
                        + "        OrthJobHelper.handleSuccess('Version 2')\n"
                        + "    }\n"
                        + "}";

        TriggerRequest request2 = createGroovyTriggerRequest(TEST_JOB_ID, groovyCode2);
        request2.setGlueUpdatetime(System.currentTimeMillis() + 1000);
        Response<String> response = executorBiz.run(request2);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testRun_groovyGlueType_compilationError_shouldReturnFail() {
        // Given
        String invalidGroovyCode = "this is not valid groovy code {{{";

        TriggerRequest request = createGroovyTriggerRequest(TEST_JOB_ID, invalidGroovyCode);

        // When
        Response<String> response = executorBiz.run(request);

        // Then
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void testRun_shellGlueType_shouldExecute() {
        // Given
        String shellScript = "echo 'Hello from shell'";
        TriggerRequest request = createScriptTriggerRequest(TEST_JOB_ID, shellScript, "GLUE_SHELL");

        // When
        Response<String> response = executorBiz.run(request);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testRun_pythonGlueType_shouldExecute() {
        // Given
        String pythonScript = "print('Hello from Python')";
        TriggerRequest request =
                createScriptTriggerRequest(TEST_JOB_ID, pythonScript, "GLUE_PYTHON");

        // When
        Response<String> response = executorBiz.run(request);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testRun_invalidGlueType_shouldReturnFail() {
        // Given
        TriggerRequest request = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request.setGlueType("INVALID_TYPE");

        // When
        Response<String> response = executorBiz.run(request);

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMsg()).contains("not valid");
    }

    @Test
    void testRun_blockStrategySerial_shouldQueueTriggers() throws Exception {
        // Given
        testJobHandler.setExecutionTime(2000); // 2 second execution
        TriggerRequest request1 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request1.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        request1.setLogId(1L);

        TriggerRequest request2 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request2.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        request2.setLogId(2L);

        // When
        Response<String> response1 = executorBiz.run(request1);
        Thread.sleep(100); // Ensure first job starts
        Response<String> response2 = executorBiz.run(request2);

        // Then - both should be queued
        assertThat(response1.isSuccess()).isTrue();
        assertThat(response2.isSuccess()).isTrue();

        // Wait for both to execute
        await().atMost(10, TimeUnit.SECONDS).until(() -> testJobHandler.getExecutionCount() == 2);
    }

    @Test
    void testRun_blockStrategyDiscardLater_shouldDiscardWhenRunning() throws Exception {
        // Given
        testJobHandler.setExecutionTime(2000); // 2 second execution
        TriggerRequest request1 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request1.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.DISCARD_LATER.name());
        request1.setLogId(1L);

        TriggerRequest request2 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request2.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.DISCARD_LATER.name());
        request2.setLogId(2L);

        // When
        Response<String> response1 = executorBiz.run(request1);
        Thread.sleep(500); // Ensure first job is running
        Response<String> response2 = executorBiz.run(request2);

        // Then - second should be discarded
        assertThat(response1.isSuccess()).isTrue();
        assertThat(response2.isSuccess()).isFalse();
        assertThat(response2.getMsg()).contains("Discard Later");
    }

    @Test
    void testRun_blockStrategyCoverEarly_shouldKillOldThread() throws Exception {
        // Given
        testJobHandler.setExecutionTime(5000); // 5 second execution
        TriggerRequest request1 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request1.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.COVER_EARLY.name());
        request1.setLogId(1L);

        TriggerRequest request2 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request2.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.COVER_EARLY.name());
        request2.setLogId(2L);

        // When
        Response<String> response1 = executorBiz.run(request1);
        Thread.sleep(500); // Ensure first job is running
        Response<String> response2 = executorBiz.run(request2);

        // Then - both should succeed, old thread killed
        assertThat(response1.isSuccess()).isTrue();
        assertThat(response2.isSuccess()).isTrue();
    }

    @Test
    void testKill_runningJob_shouldKill() throws Exception {
        // Given
        testJobHandler.setExecutionTime(5000); // Long running job
        TriggerRequest triggerRequest = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        executorBiz.run(triggerRequest);

        // Wait for job to start
        Thread.sleep(500);

        KillRequest killRequest = new KillRequest();
        killRequest.setJobId(TEST_JOB_ID);

        // When
        Response<String> response = executorBiz.kill(killRequest);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testKill_nonExistentJob_shouldReturnSuccess() {
        // Given
        KillRequest killRequest = new KillRequest();
        killRequest.setJobId(99999); // Non-existent job

        // When
        Response<String> response = executorBiz.kill(killRequest);

        // Then
        assertThat(response.isSuccess()).isTrue();
        // Note: Returns success even if job doesn't exist (idempotent)
    }

    @Test
    void testLog_shouldReturnLogResult() {
        // Given
        LogRequest logRequest = new LogRequest();
        logRequest.setLogDateTim(System.currentTimeMillis());
        logRequest.setLogId(1L);
        logRequest.setFromLineNum(0);

        // When
        Response<LogResult> response = executorBiz.log(logRequest);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
    }

    @Test
    void testConcurrentRun_sameJob_shouldHandleCorrectly() throws Exception {
        // Given
        testJobHandler.setExecutionTime(100); // Quick execution

        // When - multiple concurrent triggers
        int concurrentCount = 5;
        for (int i = 0; i < concurrentCount; i++) {
            TriggerRequest request = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
            request.setLogId(i + 1L);
            executorBiz.run(request);
        }

        // Then - all should be queued and executed
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> testJobHandler.getExecutionCount() == concurrentCount);
    }

    // -------------------- Concurrent Block Strategy Tests --------------------

    @Test
    void testRun_blockStrategyConcurrent_shouldQueueTrigger() throws Exception {
        // Given
        testJobHandler.setExecutionTime(2000);
        TriggerRequest request1 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request1.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.CONCURRENT.name());
        request1.setExecutorConcurrency(2);
        request1.setLogId(1L);

        TriggerRequest request2 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request2.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.CONCURRENT.name());
        request2.setExecutorConcurrency(2);
        request2.setLogId(2L);

        // When
        Response<String> response1 = executorBiz.run(request1);
        Thread.sleep(100);
        Response<String> response2 = executorBiz.run(request2);

        // Then - both should succeed (queued for concurrent execution)
        assertThat(response1.isSuccess()).isTrue();
        assertThat(response2.isSuccess()).isTrue();

        // Wait for both to execute
        await().atMost(10, TimeUnit.SECONDS).until(() -> testJobHandler.getExecutionCount() == 2);
    }

    @Test
    void testRun_concurrencyChanged_shouldRecreateThread() throws Exception {
        // Given - first trigger with concurrency=1
        TriggerRequest request1 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request1.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.CONCURRENT.name());
        request1.setExecutorConcurrency(1);
        request1.setLogId(1L);
        executorBiz.run(request1);

        Thread.sleep(500);

        // When - second trigger with concurrency=3 (changed)
        TriggerRequest request2 = createBeanTriggerRequest(TEST_JOB_ID, TEST_HANDLER_NAME);
        request2.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.CONCURRENT.name());
        request2.setExecutorConcurrency(3);
        request2.setLogId(2L);
        Response<String> response = executorBiz.run(request2);

        // Then - should succeed (old thread recreated with new concurrency)
        assertThat(response.isSuccess()).isTrue();

        // Verify the new thread has the updated concurrency
        await().atMost(5, TimeUnit.SECONDS)
                .until(
                        () -> {
                            var thread = OrthJobExecutor.loadJobThread(TEST_JOB_ID);
                            return thread != null && thread.getConcurrency() == 3;
                        });
    }

    // Helper methods

    private TriggerRequest createBeanTriggerRequest(int jobId, String handlerName) {
        TriggerRequest request = new TriggerRequest();
        request.setJobId(jobId);
        request.setExecutorHandler(handlerName);
        request.setExecutorParams("");
        request.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        request.setExecutorTimeout(0);
        request.setLogId(System.currentTimeMillis());
        request.setLogDateTime(System.currentTimeMillis());
        request.setGlueType(GlueTypeEnum.BEAN.name());
        request.setGlueSource("");
        request.setGlueUpdatetime(System.currentTimeMillis());
        request.setBroadcastIndex(0);
        request.setBroadcastTotal(1);
        return request;
    }

    private TriggerRequest createGroovyTriggerRequest(int jobId, String groovyCode) {
        TriggerRequest request = new TriggerRequest();
        request.setJobId(jobId);
        request.setExecutorHandler("");
        request.setExecutorParams("");
        request.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        request.setExecutorTimeout(0);
        request.setLogId(System.currentTimeMillis());
        request.setLogDateTime(System.currentTimeMillis());
        request.setGlueType(GlueTypeEnum.GLUE_GROOVY.name());
        request.setGlueSource(groovyCode);
        request.setGlueUpdatetime(System.currentTimeMillis());
        request.setBroadcastIndex(0);
        request.setBroadcastTotal(1);
        return request;
    }

    private TriggerRequest createScriptTriggerRequest(int jobId, String script, String glueType) {
        TriggerRequest request = new TriggerRequest();
        request.setJobId(jobId);
        request.setExecutorHandler("");
        request.setExecutorParams("");
        request.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        request.setExecutorTimeout(0);
        request.setLogId(System.currentTimeMillis());
        request.setLogDateTime(System.currentTimeMillis());
        request.setGlueType(glueType);
        request.setGlueSource(script);
        request.setGlueUpdatetime(System.currentTimeMillis());
        request.setBroadcastIndex(0);
        request.setBroadcastTotal(1);
        return request;
    }

    /** Test job handler for testing purposes */
    static class TestJobHandler extends IJobHandler {
        private final AtomicInteger executionCount = new AtomicInteger(0);
        private long executionTime = 100; // Default 100ms

        @Override
        public void execute() throws Exception {
            executionCount.incrementAndGet();
            OrthJobHelper.handleSuccess("Test execution " + executionCount.get());
            if (executionTime > 0) {
                Thread.sleep(executionTime);
            }
        }

        public int getExecutionCount() {
            return executionCount.get();
        }

        public void setExecutionTime(long executionTime) {
            this.executionTime = executionTime;
        }
    }
}
