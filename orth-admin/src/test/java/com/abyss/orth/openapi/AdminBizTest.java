package com.abyss.orth.openapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.core.constant.Const;
import com.abyss.orth.core.constant.RegistType;
import com.abyss.orth.core.context.OrthJobContext;
import com.abyss.orth.core.openapi.AdminBiz;
import com.abyss.orth.core.openapi.model.CallbackRequest;
import com.abyss.orth.core.openapi.model.RegistryRequest;
import com.xxl.tool.http.HttpTool;
import com.xxl.tool.response.Response;

/**
 * Integration tests for {@link AdminBiz} OpenAPI client.
 *
 * <p>Tests verify that the HTTP RPC client correctly communicates with the Orth admin server's
 * OpenAPI endpoints. The admin API handles executor callbacks, service discovery registration, and
 * job lifecycle management.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li>Executor task execution callbacks with result codes
 *   <li>Executor registration for service discovery
 *   <li>Executor de-registration (graceful shutdown)
 *   <li>Job management operations (add/update/remove/start/stop)
 * </ul>
 *
 * <p><b>Prerequisites:</b>
 *
 * <ul>
 *   <li>Orth admin server running at {@value #ADMIN_BASE_URL}
 *   <li>Access token configured: {@value #ACCESS_TOKEN}
 *   <li>Executor group configured to accept test registrations
 * </ul>
 *
 * <p><b>Note:</b> These are integration tests requiring a live admin server. They are typically
 * disabled in CI/CD pipelines and executed manually for API validation.
 *
 * @author orth (Abyss Project)
 * @since 3.3.0
 */
public class AdminBizTest {
    private static final Logger logger = LoggerFactory.getLogger(AdminBizTest.class);

    // Connection configuration
    private static final String ADMIN_BASE_URL = "http://127.0.0.1:8080/orth-admin";
    private static final String ACCESS_TOKEN = "default_token";
    private static final int TIMEOUT_MS = 3000;

    // Test data constants
    private static final long TEST_LOG_ID = 1L;
    private static final String TEST_EXECUTOR_APP_NAME = "orth-executor-example";
    private static final String TEST_EXECUTOR_ADDRESS = "127.0.0.1:9999";

    private AdminBiz adminBizClient;

    /**
     * Initializes the AdminBiz HTTP RPC client before each test.
     *
     * <p>Creates a proxy client with configured timeout and authentication headers.
     */
    @BeforeEach
    public void setUp() {
        adminBizClient = buildClient();
    }

    /**
     * Builds an {@link AdminBiz} HTTP RPC proxy client.
     *
     * @return configured AdminBiz client instance
     */
    private AdminBiz buildClient() {
        String apiUrl = ADMIN_BASE_URL + "/api";

        return HttpTool.createClient()
                .url(apiUrl)
                .timeout(TIMEOUT_MS)
                .header(Const.ORTH_ACCESS_TOKEN, ACCESS_TOKEN)
                .proxy(AdminBiz.class);
    }

    /**
     * Tests executor callback reporting for successful task execution.
     *
     * <p>Validates that the admin server correctly processes executor callbacks containing task
     * execution results. The callback includes log ID and handle code (success/failure).
     *
     * @throws Exception if HTTP communication fails
     */
    @Test
    public void testCallback_withSuccessCode_shouldReturnSuccess() throws Exception {
        // Arrange
        CallbackRequest callbackRequest = new CallbackRequest();
        callbackRequest.setLogId(TEST_LOG_ID);
        callbackRequest.setHandleCode(OrthJobContext.HANDLE_CODE_SUCCESS);

        List<CallbackRequest> callbackList = Arrays.asList(callbackRequest);

        // Act
        Response<String> response = adminBizClient.callback(callbackList);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.isSuccess(), "Callback should succeed");

        logger.info("Callback response: {}", response);
    }

    /**
     * Tests executor registration for service discovery.
     *
     * <p>Validates that executors can register themselves with the admin server, enabling the
     * scheduler to discover and route jobs to them. Registration includes registry type (EXECUTOR),
     * app name, and network address.
     *
     * @throws Exception if HTTP communication fails
     */
    @Test
    public void testRegistry_withValidExecutorInfo_shouldReturnSuccess() throws Exception {
        // Arrange
        RegistryRequest registryRequest =
                new RegistryRequest(
                        RegistType.EXECUTOR.name(), TEST_EXECUTOR_APP_NAME, TEST_EXECUTOR_ADDRESS);

        // Act
        Response<String> response = adminBizClient.registry(registryRequest);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.isSuccess(), "Registry should succeed");

        logger.info("Registry response: {}", response);
    }

    /**
     * Tests executor de-registration (graceful shutdown).
     *
     * <p>Validates that executors can remove themselves from the admin server's registry during
     * graceful shutdown. This prevents the scheduler from routing new jobs to stopped executors.
     *
     * @throws Exception if HTTP communication fails
     */
    @Test
    public void testRegistryRemove_withValidExecutorInfo_shouldReturnSuccess() throws Exception {
        // Arrange
        RegistryRequest registryRequest =
                new RegistryRequest(
                        RegistType.EXECUTOR.name(), TEST_EXECUTOR_APP_NAME, TEST_EXECUTOR_ADDRESS);

        // Act
        Response<String> response = adminBizClient.registryRemove(registryRequest);

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.isSuccess(), "Registry removal should succeed");

        logger.info("Registry removal response: {}", response);
    }

    /**
     * Tests job lifecycle management operations.
     *
     * <p><b>TODO:</b> Implement comprehensive tests for:
     *
     * <ul>
     *   <li>Job creation (jobAdd)
     *   <li>Job configuration updates (jobUpdate)
     *   <li>Job deletion (jobRemove)
     *   <li>Job activation (jobStart)
     *   <li>Job deactivation (jobStop)
     * </ul>
     *
     * <p>These operations are currently exposed via web controllers but may be added to the OpenAPI
     * in future versions for programmatic job management.
     *
     * @throws Exception if HTTP communication fails
     */
    @Test
    public void testJobManagement_shouldSupportFullLifecycle() throws Exception {
        // TODO: Implement tests for jobAdd, jobUpdate, jobRemove, jobStart, jobStop
        logger.warn(
                "Job management tests not yet implemented - these operations may require "
                        + "additional OpenAPI endpoints");
    }
}
