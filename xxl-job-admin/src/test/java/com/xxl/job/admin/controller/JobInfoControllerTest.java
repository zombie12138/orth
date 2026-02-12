package com.xxl.job.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.xxl.sso.core.constant.Const;

import jakarta.servlet.http.Cookie;

/**
 * Integration tests for JobInfoController endpoints.
 *
 * <p>Tests job management operations including job listing and pagination. All tests run with
 * authenticated session using admin credentials.
 */
public class JobInfoControllerTest extends AbstractSpringMvcTest {

    private static final Logger logger = LoggerFactory.getLogger(JobInfoControllerTest.class);

    // Test credentials
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "123456";

    // Test data constants
    private static final String DEFAULT_JOB_GROUP = "1";
    private static final String ALL_TRIGGER_STATUS = "-1";

    private Cookie sessionCookie;

    /**
     * Authenticates as admin user before each test.
     *
     * <p>Performs login via /auth/doLogin and extracts session cookie for subsequent requests.
     */
    @BeforeEach
    public void login_withAdminCredentials_storesSessionCookie() throws Exception {
        MvcResult loginResult =
                mockMvc.perform(
                                post("/auth/doLogin")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("userName", TEST_USERNAME)
                                        .param("password", TEST_PASSWORD))
                        .andExpect(status().isOk())
                        .andReturn();

        sessionCookie = loginResult.getResponse().getCookie(Const.XXL_SSO_TOKEN);
        assertThat(sessionCookie)
                .as("Session cookie should be present after successful login")
                .isNotNull();
    }

    /**
     * Tests job list pagination endpoint with default filters.
     *
     * <p>Verifies that /jobinfo/pageList returns successfully when querying for all jobs in the
     * default job group with any trigger status.
     */
    @Test
    public void testPageList_withDefaultFilters_returnsSuccessfully() throws Exception {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("jobGroup", DEFAULT_JOB_GROUP);
        parameters.add("triggerStatus", ALL_TRIGGER_STATUS);

        MvcResult result =
                mockMvc.perform(
                                post("/jobinfo/pageList")
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .params(parameters)
                                        .cookie(sessionCookie))
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
