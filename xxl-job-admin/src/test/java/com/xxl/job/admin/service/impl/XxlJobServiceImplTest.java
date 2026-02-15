package com.xxl.job.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.xxl.job.admin.model.XxlJobInfo;

/**
 * Unit tests for {@link XxlJobServiceImpl} private helper methods.
 *
 * <p>Tests the superTaskId sanitization logic that prevents self-references, invalid values, and
 * ensures compatibility with the database NOT NULL DEFAULT 0 constraint.
 */
class XxlJobServiceImplTest {

    private XxlJobServiceImpl service;
    private Method sanitizeMethod;

    @BeforeEach
    void setUp() throws Exception {
        service = new XxlJobServiceImpl();
        sanitizeMethod =
                XxlJobServiceImpl.class.getDeclaredMethod(
                        "sanitizeSuperTaskId", Integer.class, int.class);
        sanitizeMethod.setAccessible(true);
    }

    private int invokeSanitize(Integer superTaskId, int jobId) throws Exception {
        return (int) sanitizeMethod.invoke(service, superTaskId, jobId);
    }

    // ==================== sanitizeSuperTaskId Tests ====================

    @Test
    void sanitize_nullInput_returnsZero() throws Exception {
        assertThat(invokeSanitize(null, 5)).isZero();
    }

    @Test
    void sanitize_zero_returnsZero() throws Exception {
        assertThat(invokeSanitize(0, 5)).isZero();
    }

    @Test
    void sanitize_negative_returnsZero() throws Exception {
        assertThat(invokeSanitize(-1, 5)).isZero();
    }

    @Test
    void sanitize_selfReference_returnsZero() throws Exception {
        assertThat(invokeSanitize(5, 5)).isZero();
    }

    @Test
    void sanitize_validId_returnsId() throws Exception {
        assertThat(invokeSanitize(10, 5)).isEqualTo(10);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0", // zero with zero jobId (add flow)
        "-1, 0", // negative with zero jobId
        ",  0", // null with zero jobId
        "99, 0" // valid with zero jobId (add flow, no self-ref possible)
    })
    void sanitize_addFlow_zeroJobId(Integer superTaskId, int expected) throws Exception {
        // During add(), jobId is 0 (not yet assigned)
        int result = invokeSanitize(superTaskId, 0);
        if (superTaskId != null && superTaskId == 99) {
            assertThat(result).isEqualTo(99);
        } else {
            assertThat(result).isEqualTo(expected);
        }
    }

    // ==================== updateJobFields superTaskId Tests ====================

    @Test
    void updateJobFields_clearsSuperTaskId_whenNull() throws Exception {
        Method updateMethod =
                XxlJobServiceImpl.class.getDeclaredMethod(
                        "updateJobFields", XxlJobInfo.class, XxlJobInfo.class, long.class);
        updateMethod.setAccessible(true);

        XxlJobInfo existing = createJobInfo(5, 10); // has superTaskId=10
        XxlJobInfo newInfo = createJobInfo(5, null); // clearing superTaskId

        updateMethod.invoke(service, existing, newInfo, 0L);

        // DB column is NOT NULL DEFAULT 0, so sanitized null → 0
        assertThat(existing.getSuperTaskId()).isEqualTo(0);
    }

    @Test
    void updateJobFields_clearsSelfReference() throws Exception {
        Method updateMethod =
                XxlJobServiceImpl.class.getDeclaredMethod(
                        "updateJobFields", XxlJobInfo.class, XxlJobInfo.class, long.class);
        updateMethod.setAccessible(true);

        XxlJobInfo existing = createJobInfo(5, 10);
        XxlJobInfo newInfo = createJobInfo(5, 5); // self-reference

        updateMethod.invoke(service, existing, newInfo, 0L);

        assertThat(existing.getSuperTaskId()).isEqualTo(0);
    }

    @Test
    void updateJobFields_keepsValidSuperTaskId() throws Exception {
        Method updateMethod =
                XxlJobServiceImpl.class.getDeclaredMethod(
                        "updateJobFields", XxlJobInfo.class, XxlJobInfo.class, long.class);
        updateMethod.setAccessible(true);

        XxlJobInfo existing = createJobInfo(5, 0);
        XxlJobInfo newInfo = createJobInfo(5, 10); // setting valid superTaskId

        updateMethod.invoke(service, existing, newInfo, 0L);

        assertThat(existing.getSuperTaskId()).isEqualTo(10);
    }

    private XxlJobInfo createJobInfo(int id, Integer superTaskId) {
        XxlJobInfo job = new XxlJobInfo();
        job.setId(id);
        job.setSuperTaskId(superTaskId);
        job.setJobDesc("test");
        job.setAuthor("test");
        job.setScheduleType("CRON");
        job.setScheduleConf("* * * * * ?");
        job.setMisfireStrategy("DO_NOTHING");
        job.setExecutorRouteStrategy("FIRST");
        job.setExecutorHandler("handler");
        job.setExecutorParam("");
        job.setExecutorBlockStrategy("SERIAL_EXECUTION");
        job.setChildJobId("");
        return job;
    }
}
