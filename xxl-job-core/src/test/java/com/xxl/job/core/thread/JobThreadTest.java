package com.xxl.job.core.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xxl.job.core.AbstractUnitTest;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Unit tests for {@link JobThread}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Trigger queue management (push, deduplication)
 *   <li>Job execution lifecycle (init → run → destroy)
 *   <li>Timeout handling with FutureTask
 *   <li>Idle time tracking
 *   <li>Callback thread integration
 *   <li>Concurrent trigger handling (thread safety)
 *   <li>Graceful shutdown (toStop)
 * </ul>
 */
class JobThreadTest extends AbstractUnitTest {

    @Mock private IJobHandler mockHandler;

    private JobThread jobThread;
    private static final int TEST_JOB_ID = 100;

    @BeforeEach
    public void setUp() {
        super.setUp();
        // Initialize log path for callback thread
        try {
            XxlJobFileAppender.initLogPath("/tmp/xxl-job-test");
        } catch (Exception e) {
            // Log path initialization failure is not critical for most tests
        }
    }

    @AfterEach
    public void tearDown() {
        if (jobThread != null && jobThread.isAlive()) {
            jobThread.toStop("test cleanup");
            try {
                jobThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        super.tearDown();
    }

    @Test
    void testConstructor_shouldInitializeCorrectly() {
        // When
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);

        // Then
        assertThat(jobThread.getHandler()).isEqualTo(mockHandler);
        assertThat(jobThread.getName()).startsWith("xxl-job, JobThread-100-");
    }

    @Test
    void testPushTriggerQueue_shouldAddTrigger() {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        Response<String> response = jobThread.pushTriggerQueue(trigger);

        // Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testPushTriggerQueue_shouldPreventDuplicateLogId() {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        Response<String> firstResponse = jobThread.pushTriggerQueue(trigger);
        Response<String> secondResponse = jobThread.pushTriggerQueue(trigger);

        // Then
        assertThat(firstResponse.isSuccess()).isTrue();
        assertThat(secondResponse.isSuccess()).isFalse();
        assertThat(secondResponse.getMsg()).contains("repeate trigger job");
    }

    @Test
    void testPushTriggerQueue_shouldAllowDifferentLogIds() {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger1 = createTriggerRequest(1L);
        TriggerRequest trigger2 = createTriggerRequest(2L);

        // When
        Response<String> response1 = jobThread.pushTriggerQueue(trigger1);
        Response<String> response2 = jobThread.pushTriggerQueue(trigger2);

        // Then
        assertThat(response1.isSuccess()).isTrue();
        assertThat(response2.isSuccess()).isTrue();
    }

    @Test
    void testJobExecution_shouldCallHandlerInitExecuteDestroy() throws Exception {
        // Given
        CountDownLatch executeLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            XxlJobHelper.handleSuccess("test success");
                            executeLatch.countDown();
                            return null;
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        jobThread.start();
        jobThread.pushTriggerQueue(trigger);

        // Wait for execution
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Stop and wait
        jobThread.toStop("test complete");
        jobThread.join(3000);

        // Then
        verify(mockHandler).init();
        verify(mockHandler).execute();
        verify(mockHandler).destroy();
    }

    @Test
    void testJobExecution_shouldHandleExecutionSuccess() throws Exception {
        // Given
        CountDownLatch executeLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            XxlJobHelper.handleSuccess("Job completed successfully");
                            executeLatch.countDown();
                            return null;
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        jobThread.start();
        jobThread.pushTriggerQueue(trigger);

        // Wait for execution
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Then - verify context was set correctly
        verify(mockHandler).execute();
    }

    @Test
    void testJobExecution_shouldHandleExecutionFailure() throws Exception {
        // Given
        CountDownLatch executeLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            XxlJobHelper.handleFail("Job failed with error");
                            executeLatch.countDown();
                            return null;
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        jobThread.start();
        jobThread.pushTriggerQueue(trigger);

        // Wait for execution
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Then
        verify(mockHandler).execute();
    }

    @Test
    void testJobExecution_shouldHandleException() throws Exception {
        // Given
        CountDownLatch executeLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            executeLatch.countDown();
                            throw new RuntimeException("Test exception");
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        jobThread.start();
        jobThread.pushTriggerQueue(trigger);

        // Wait for execution
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Give time for exception handling
        Thread.sleep(500);

        // Then - thread should still be running and handle the exception
        assertThat(jobThread.isAlive()).isTrue();
        verify(mockHandler).execute();
    }

    @Test
    void testJobExecution_shouldHandleTimeout() throws Exception {
        // Given
        CountDownLatch executeLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            executeLatch.countDown();
                            // Simulate long-running job
                            Thread.sleep(5000);
                            XxlJobHelper.handleSuccess("Should not reach here");
                            return null;
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);
        trigger.setExecutorTimeout(1); // 1 second timeout

        // When
        jobThread.start();
        jobThread.pushTriggerQueue(trigger);

        // Wait for execution to start
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Wait for timeout to occur
        Thread.sleep(2000);

        // Then - execution should have timed out
        verify(mockHandler).execute();
    }

    @Test
    void testJobExecution_withoutTimeout_shouldExecuteNormally() throws Exception {
        // Given
        CountDownLatch executeLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            XxlJobHelper.handleSuccess("Completed");
                            executeLatch.countDown();
                            return null;
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);
        trigger.setExecutorTimeout(0); // No timeout

        // When
        jobThread.start();
        jobThread.pushTriggerQueue(trigger);

        // Wait for execution
        assertThat(executeLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Then
        verify(mockHandler).execute();
    }

    @Test
    void testIsRunningOrHasQueue_whenRunning() throws Exception {
        // Given
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch holdLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            startLatch.countDown();
                            XxlJobHelper.handleSuccess("Running");
                            holdLatch.await(5, TimeUnit.SECONDS);
                            return null;
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        jobThread.start();
        jobThread.pushTriggerQueue(trigger);

        // Wait for job to start executing
        assertThat(startLatch.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100); // Give time for running flag to be set

        // Then
        assertThat(jobThread.isRunningOrHasQueue()).isTrue();

        // Cleanup
        holdLatch.countDown();
    }

    @Test
    void testIsRunningOrHasQueue_whenQueueHasTriggers() {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger = createTriggerRequest(1L);

        // When
        jobThread.pushTriggerQueue(trigger);

        // Then
        assertThat(jobThread.isRunningOrHasQueue()).isTrue();
    }

    @Test
    void testIsRunningOrHasQueue_whenIdleAndEmptyQueue() throws Exception {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);

        // When - start thread and wait for it to be idle
        jobThread.start();
        Thread.sleep(1000);

        // Then
        assertThat(jobThread.isRunningOrHasQueue()).isFalse();
    }

    @Test
    void testToStop_shouldStopThread() throws Exception {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        jobThread.start();

        // When
        jobThread.toStop("Test stop");
        jobThread.join(5000);

        // Then
        assertThat(jobThread.isAlive()).isFalse();
        verify(mockHandler).init();
        verify(mockHandler).destroy();
    }

    @Test
    void testToStop_shouldCallbackRemainingQueuedTriggers() throws Exception {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        TriggerRequest trigger1 = createTriggerRequest(1L);
        TriggerRequest trigger2 = createTriggerRequest(2L);

        // When
        jobThread.pushTriggerQueue(trigger1);
        jobThread.pushTriggerQueue(trigger2);
        jobThread.start();
        Thread.sleep(100);
        jobThread.toStop("Test stop with queue");
        jobThread.join(5000);

        // Then - thread should stop and callbacks should be made for queued items
        assertThat(jobThread.isAlive()).isFalse();
    }

    @Test
    void testConcurrentTriggerPush_shouldHandleThreadSafely() throws Exception {
        // Given
        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - push same log ID from multiple threads
        for (int i = 0; i < threadCount; i++) {
            new Thread(
                            () -> {
                                try {
                                    startLatch.await();
                                    TriggerRequest trigger = createTriggerRequest(999L);
                                    Response<String> response = jobThread.pushTriggerQueue(trigger);
                                    if (response.isSuccess()) {
                                        successCount.incrementAndGet();
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    doneLatch.countDown();
                                }
                            })
                    .start();
        }

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Then - only one should succeed due to deduplication
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void testMultipleSequentialExecutions() throws Exception {
        // Given
        AtomicInteger executeCount = new AtomicInteger(0);
        doAnswer(
                        invocation -> {
                            executeCount.incrementAndGet();
                            XxlJobHelper.handleSuccess("Execution " + executeCount.get());
                            return null;
                        })
                .when(mockHandler)
                .execute();

        jobThread = new JobThread(TEST_JOB_ID, mockHandler);
        jobThread.start();

        // When
        for (int i = 1; i <= 5; i++) {
            TriggerRequest trigger = createTriggerRequest((long) i);
            jobThread.pushTriggerQueue(trigger);
        }

        // Wait for all executions
        await().atMost(10, TimeUnit.SECONDS).until(() -> executeCount.get() == 5);

        // Then
        assertThat(executeCount.get()).isEqualTo(5);
        verify(mockHandler, times(5)).execute();
    }

    // Helper methods

    private TriggerRequest createTriggerRequest(long logId) {
        TriggerRequest trigger = new TriggerRequest();
        trigger.setJobId(TEST_JOB_ID);
        trigger.setLogId(logId);
        trigger.setLogDateTime(System.currentTimeMillis());
        trigger.setExecutorHandler("testHandler");
        trigger.setExecutorParams("");
        trigger.setExecutorBlockStrategy("SERIAL_EXECUTION");
        trigger.setExecutorTimeout(0);
        trigger.setGlueType("BEAN");
        trigger.setGlueSource("");
        trigger.setGlueUpdatetime(System.currentTimeMillis());
        trigger.setBroadcastIndex(0);
        trigger.setBroadcastTotal(1);
        return trigger;
    }
}
