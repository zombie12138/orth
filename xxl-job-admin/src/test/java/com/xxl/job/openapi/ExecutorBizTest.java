package com.xxl.job.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.constant.Const;
import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.openapi.ExecutorBiz;
import com.xxl.job.core.openapi.model.IdleBeatRequest;
import com.xxl.job.core.openapi.model.KillRequest;
import com.xxl.job.core.openapi.model.LogRequest;
import com.xxl.job.core.openapi.model.LogResult;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.http.HttpTool;
import com.xxl.tool.response.Response;

/**
 * Integration tests for {@link ExecutorBiz} OpenAPI client.
 *
 * <p>Tests verify that the HTTP RPC client correctly communicates with Orth executor's embedded
 * Netty server endpoints. The executor API handles heartbeats, idle checks, job triggers, kill
 * signals, and log retrieval.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li>Heartbeat validation (connectivity check)
 *   <li>Idle beat check (job thread availability)
 *   <li>Job trigger execution with various configurations
 *   <li>Job termination (kill signals)
 *   <li>Log file retrieval (streaming execution logs)
 * </ul>
 *
 * <p><b>Prerequisites:</b>
 *
 * <ul>
 *   <li>Orth executor running at {@value #EXECUTOR_BASE_URL}
 *   <li>Access token configured: {@value #ACCESS_TOKEN}
 *   <li>Test job handler registered: {@value #TEST_JOB_HANDLER}
 * </ul>
 *
 * <p><b>Note:</b> These are integration tests requiring a live executor server. They are typically
 * disabled in CI/CD pipelines and executed manually for API validation.
 *
 * @author orth (Abyss Project)
 * @since 3.3.0
 */
public class ExecutorBizTest {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorBizTest.class);

    // Connection configuration
    private static final String EXECUTOR_BASE_URL = "http://127.0.0.1:9999/";
    private static final String ACCESS_TOKEN = "default_token";
    private static final int TIMEOUT_MS = 3000;

    // Test data constants
    private static final int TEST_JOB_ID = 1;
    private static final int IDLE_JOB_ID = 0;
    private static final String TEST_JOB_HANDLER = "demoJobHandler";
    private static final long TEST_LOG_ID = 1L;
    private static final int LOG_FROM_LINE = 0;

    // Expected response codes
    private static final int SUCCESS_CODE = 200;
    private static final int ERROR_CODE = 500;
    private static final String IDLE_BEAT_BUSY_MESSAGE =
            "job thread is running or has trigger queue.";

    private ExecutorBiz executorBizClient;

    /**
     * Initializes the ExecutorBiz HTTP RPC client before each test.
     *
     * <p>Creates a proxy client with configured timeout and authentication headers.
     */
    @BeforeEach
    public void setUp() {
        executorBizClient = buildClient();
    }

    /**
     * Builds an {@link ExecutorBiz} HTTP RPC proxy client.
     *
     * @return configured ExecutorBiz client instance
     */
    private ExecutorBiz buildClient() {
        return HttpTool.createClient()
                .url(EXECUTOR_BASE_URL)
                .timeout(TIMEOUT_MS)
                .header(Const.ORTH_ACCESS_TOKEN, ACCESS_TOKEN)
                .proxy(ExecutorBiz.class);
    }

    /**
     * Tests executor heartbeat endpoint for connectivity validation.
     *
     * <p>Validates that the executor's embedded Netty server responds to heartbeat requests. The
     * admin server periodically calls this endpoint to verify executor availability.
     *
     * @throws Exception if HTTP communication fails
     */
    @Test
    public void testBeat_shouldReturnSuccessResponse() throws Exception {
        // Act
        Response<String> response = executorBizClient.beat();

        // Assert
        assertNotNull(response, "Response should not be null");
        assertNull(response.getData(), "Beat response data should be null");
        assertEquals(SUCCESS_CODE, response.getCode(), "Response code should indicate success");
        assertNull(response.getMsg(), "Beat response message should be null");

        logger.info("Heartbeat response: {}", response);
    }

    /**
     * Tests idle beat check for job thread availability.
     *
     * <p>Validates that the executor correctly reports whether a job thread is idle or busy. This
     * is used by routing strategies (e.g., BUSYOVER) to avoid sending jobs to overloaded executors.
     *
     * <p><b>Note:</b> This test expects the job to be busy (running or queued), which is typical
     * when an executor has active jobs.
     */
    @Test
    public void testIdleBeat_withBusyJob_shouldReturnBusyStatus() {
        // Arrange
        IdleBeatRequest request = new IdleBeatRequest(IDLE_JOB_ID);

        // Act
        Response<String> response = executorBizClient.idleBeat(request);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertNull(response.getData(), "Idle beat response data should be null");
        assertEquals(ERROR_CODE, response.getCode(), "Response code should indicate job is busy");
        assertEquals(
                IDLE_BEAT_BUSY_MESSAGE,
                response.getMsg(),
                "Response message should indicate busy status");

        logger.info("Idle beat response: {}", response);
    }

    /**
     * Tests job trigger execution with comprehensive configuration.
     *
     * <p>Validates that the executor correctly receives and processes trigger requests from the
     * admin server. The trigger includes job ID, handler name, execution parameters, blocking
     * strategy, GLUE configuration, and log metadata.
     */
    @Test
    public void testRun_withValidTriggerRequest_shouldReturnResponse() {
        // Arrange
        TriggerRequest triggerRequest = new TriggerRequest();
        triggerRequest.setJobId(TEST_JOB_ID);
        triggerRequest.setExecutorHandler(TEST_JOB_HANDLER);
        triggerRequest.setExecutorParams(null);
        triggerRequest.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.COVER_EARLY.name());
        triggerRequest.setGlueType(GlueTypeEnum.BEAN.name());
        triggerRequest.setGlueSource(null);
        triggerRequest.setGlueUpdatetime(System.currentTimeMillis());
        triggerRequest.setLogId(TEST_LOG_ID);
        triggerRequest.setLogDateTime(System.currentTimeMillis());

        // Act
        Response<String> response = executorBizClient.run(triggerRequest);

        // Assert
        assertNotNull(response, "Response should not be null");

        logger.info("Trigger response: {}", response);
    }

    /**
     * Tests job termination (kill signal) delivery.
     *
     * <p>Validates that the executor correctly processes kill requests from the admin server. The
     * executor should interrupt the running job thread and clean up resources.
     */
    @Test
    public void testKill_withValidJobId_shouldReturnSuccessResponse() {
        // Arrange
        KillRequest killRequest = new KillRequest(IDLE_JOB_ID);

        // Act
        Response<String> response = executorBizClient.kill(killRequest);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertNull(response.getData(), "Kill response data should be null");
        assertEquals(SUCCESS_CODE, response.getCode(), "Response code should indicate success");
        assertNull(response.getMsg(), "Kill response message should be null");

        logger.info("Kill response: {}", response);
    }

    /**
     * Tests execution log retrieval from executor's log files.
     *
     * <p>Validates that the executor correctly streams log file contents to the admin server. The
     * request specifies log ID, date/time, and starting line number for incremental log fetching.
     */
    @Test
    public void testLog_withValidLogRequest_shouldReturnLogResult() {
        // Arrange
        long logDateTime = 0L;
        LogRequest logRequest = new LogRequest(logDateTime, TEST_LOG_ID, LOG_FROM_LINE);

        // Act
        Response<LogResult> response = executorBizClient.log(logRequest);

        // Assert
        assertNotNull(response, "Response should not be null");

        logger.info("Log response: {}", response);
    }
}
