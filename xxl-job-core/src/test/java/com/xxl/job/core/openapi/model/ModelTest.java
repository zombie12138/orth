package com.xxl.job.core.openapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.xxl.job.core.constant.ExecutorBlockStrategyEnum;

/**
 * Tests for model/POJO classes.
 *
 * <p>Tests getters/setters, toString, and basic functionality of request/response models.
 */
class ModelTest {

    // ==================== TriggerRequest Tests ====================

    @Test
    void testTriggerRequest_gettersAndSetters() {
        // Given
        TriggerRequest request = new TriggerRequest();

        // When
        request.setJobId(123);
        request.setExecutorHandler("testHandler");
        request.setExecutorParams("param1=value1");
        request.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        request.setExecutorTimeout(30);
        request.setLogId(456L);
        request.setLogDateTime(789L);
        request.setGlueType("BEAN");
        request.setGlueSource("source code");
        request.setGlueUpdatetime(1234567890L);
        request.setBroadcastIndex(0);
        request.setBroadcastTotal(5);
        request.setScheduleTime(9876543210L);

        // Then
        assertThat(request.getJobId()).isEqualTo(123);
        assertThat(request.getExecutorHandler()).isEqualTo("testHandler");
        assertThat(request.getExecutorParams()).isEqualTo("param1=value1");
        assertThat(request.getExecutorBlockStrategy())
                .isEqualTo(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        assertThat(request.getExecutorTimeout()).isEqualTo(30);
        assertThat(request.getLogId()).isEqualTo(456L);
        assertThat(request.getLogDateTime()).isEqualTo(789L);
        assertThat(request.getGlueType()).isEqualTo("BEAN");
        assertThat(request.getGlueSource()).isEqualTo("source code");
        assertThat(request.getGlueUpdatetime()).isEqualTo(1234567890L);
        assertThat(request.getBroadcastIndex()).isEqualTo(0);
        assertThat(request.getBroadcastTotal()).isEqualTo(5);
        assertThat(request.getScheduleTime()).isEqualTo(9876543210L);
    }

    @Test
    void testTriggerRequest_toString() {
        // Given
        TriggerRequest request = new TriggerRequest();
        request.setJobId(123);
        request.setExecutorHandler("testHandler");

        // When
        String str = request.toString();

        // Then
        assertThat(str).contains("123");
        assertThat(str).contains("testHandler");
    }

    // ==================== RegistryRequest Tests ====================

    @Test
    void testRegistryRequest_constructor() {
        // When
        RegistryRequest request = new RegistryRequest("EXECUTOR", "app-name", "127.0.0.1:9999");

        // Then
        assertThat(request.getRegistryGroup()).isEqualTo("EXECUTOR");
        assertThat(request.getRegistryKey()).isEqualTo("app-name");
        assertThat(request.getRegistryValue()).isEqualTo("127.0.0.1:9999");
    }

    @Test
    void testRegistryRequest_gettersAndSetters() {
        // Given
        RegistryRequest request = new RegistryRequest();

        // When
        request.setRegistryGroup("EXECUTOR");
        request.setRegistryKey("test-app");
        request.setRegistryValue("192.168.1.1:8888");

        // Then
        assertThat(request.getRegistryGroup()).isEqualTo("EXECUTOR");
        assertThat(request.getRegistryKey()).isEqualTo("test-app");
        assertThat(request.getRegistryValue()).isEqualTo("192.168.1.1:8888");
    }

    @Test
    void testRegistryRequest_toString() {
        // Given
        RegistryRequest request = new RegistryRequest("EXECUTOR", "app", "127.0.0.1:9999");

        // When
        String str = request.toString();

        // Then
        assertThat(str).contains("EXECUTOR");
        assertThat(str).contains("app");
        assertThat(str).contains("127.0.0.1:9999");
    }

    // ==================== LogRequest Tests ====================

    @Test
    void testLogRequest_constructor() {
        // When
        LogRequest request = new LogRequest(123L, 456L, 100);

        // Then
        assertThat(request.getLogDateTim()).isEqualTo(123L);
        assertThat(request.getLogId()).isEqualTo(456L);
        assertThat(request.getFromLineNum()).isEqualTo(100);
    }

    @Test
    void testLogRequest_gettersAndSetters() {
        // Given
        LogRequest request = new LogRequest();

        // When
        request.setLogDateTim(789L);
        request.setLogId(999L);
        request.setFromLineNum(100);

        // Then
        assertThat(request.getLogDateTim()).isEqualTo(789L);
        assertThat(request.getLogId()).isEqualTo(999L);
        assertThat(request.getFromLineNum()).isEqualTo(100);
    }

    // ==================== KillRequest Tests ====================

    @Test
    void testKillRequest_constructor() {
        // When
        KillRequest request = new KillRequest(123);

        // Then
        assertThat(request.getJobId()).isEqualTo(123);
    }

    @Test
    void testKillRequest_gettersAndSetters() {
        // Given
        KillRequest request = new KillRequest();

        // When
        request.setJobId(456);

        // Then
        assertThat(request.getJobId()).isEqualTo(456);
    }

    // ==================== IdleBeatRequest Tests ====================

    @Test
    void testIdleBeatRequest_constructor() {
        // When
        IdleBeatRequest request = new IdleBeatRequest(789);

        // Then
        assertThat(request.getJobId()).isEqualTo(789);
    }

    @Test
    void testIdleBeatRequest_gettersAndSetters() {
        // Given
        IdleBeatRequest request = new IdleBeatRequest();

        // When
        request.setJobId(999);

        // Then
        assertThat(request.getJobId()).isEqualTo(999);
    }

    // ==================== CallbackRequest Tests ====================

    @Test
    void testCallbackRequest_constructor() {
        // When
        CallbackRequest request = new CallbackRequest(123L, 456L, 200, "Success");

        // Then
        assertThat(request.getLogId()).isEqualTo(123L);
        assertThat(request.getLogDateTim()).isEqualTo(456L);
        assertThat(request.getHandleCode()).isEqualTo(200);
        assertThat(request.getHandleMsg()).isEqualTo("Success");
    }

    @Test
    void testCallbackRequest_gettersAndSetters() {
        // Given
        CallbackRequest request = new CallbackRequest();

        // When
        request.setLogId(123L);
        request.setLogDateTim(789L);
        request.setHandleCode(200);
        request.setHandleMsg("Success");

        // Then
        assertThat(request.getLogId()).isEqualTo(123L);
        assertThat(request.getLogDateTim()).isEqualTo(789L);
        assertThat(request.getHandleCode()).isEqualTo(200);
        assertThat(request.getHandleMsg()).isEqualTo("Success");
    }

    @Test
    void testCallbackRequest_toString() {
        // Given
        CallbackRequest request = new CallbackRequest();
        request.setLogId(123L);
        request.setHandleCode(200);

        // When
        String str = request.toString();

        // Then
        assertThat(str).contains("123");
        assertThat(str).contains("200");
    }

    // ==================== LogResult Tests ====================

    @Test
    void testLogResult_constructor() {
        // When
        LogResult result = new LogResult(100, 200, "log content", true);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(100);
        assertThat(result.getToLineNum()).isEqualTo(200);
        assertThat(result.getLogContent()).isEqualTo("log content");
        assertThat(result.isEnd()).isTrue();
    }

    @Test
    void testLogResult_gettersAndSetters() {
        // Given
        LogResult result = new LogResult();

        // When
        result.setFromLineNum(50);
        result.setToLineNum(150);
        result.setLogContent("test log");
        result.setEnd(false);

        // Then
        assertThat(result.getFromLineNum()).isEqualTo(50);
        assertThat(result.getToLineNum()).isEqualTo(150);
        assertThat(result.getLogContent()).isEqualTo("test log");
        assertThat(result.isEnd()).isFalse();
    }
}
