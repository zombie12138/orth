package com.xxl.job.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.xxl.tool.gson.GsonTool;

/**
 * Integration tests for Job REST API endpoints.
 *
 * <p>Tests job management operations including job listing and pagination. All tests run with JWT
 * authentication using admin credentials.
 */
public class JobInfoControllerTest extends AbstractSpringMvcTest {

    private static final Logger logger = LoggerFactory.getLogger(JobInfoControllerTest.class);

    // Test credentials
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "123456";

    // Test data constants
    private static final String DEFAULT_JOB_GROUP = "1";
    private static final String ALL_TRIGGER_STATUS = "-1";

    private String accessToken;

    /**
     * Authenticates as admin user before each test.
     *
     * <p>Performs login via /api/v1/auth/login and extracts JWT token for subsequent requests.
     */
    @BeforeEach
    public void login_withAdminCredentials_storesAccessToken() throws Exception {
        String loginJson =
                "{\"username\":\"" + TEST_USERNAME + "\",\"password\":\"" + TEST_PASSWORD + "\"}";

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson))
                        .andExpect(status().isOk())
                        .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        assertThat(responseBody)
                .as("Login response should contain accessToken")
                .contains("accessToken");

        // Extract access token from response
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> response =
                GsonTool.fromJson(responseBody, java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
        accessToken = (String) data.get("accessToken");
        assertThat(accessToken)
                .as("Access token should be present after successful login")
                .isNotNull();
    }

    /**
     * Tests job list pagination endpoint with default filters.
     *
     * <p>Verifies that /api/v1/jobs returns successfully when querying for all jobs in the default
     * job group with any trigger status.
     */
    @Test
    public void testPageList_withDefaultFilters_returnsSuccessfully() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get("/api/v1/jobs")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .param("jobGroup", DEFAULT_JOB_GROUP)
                                        .param("triggerStatus", ALL_TRIGGER_STATUS)
                                        .param("jobDesc", "")
                                        .param("executorHandler", "")
                                        .param("author", ""))
                        .andExpect(status().isOk())
                        .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertThat(responseContent)
                .as("Response should not be empty")
                .isNotEmpty()
                .as("Response should be valid JSON")
                .startsWith("{");

        logger.info("Job page list response: {}", responseContent);
    }
}
