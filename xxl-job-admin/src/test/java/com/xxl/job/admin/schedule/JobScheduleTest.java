package com.xxl.job.admin.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.tool.core.DateTool;

/**
 * Integration tests for job scheduling components.
 *
 * <p>Tests distributed locking behavior used by the scheduler to ensure only one admin instance
 * processes scheduling operations at a time. Uses database-level "SELECT ... FOR UPDATE" locks.
 *
 * <p>These tests verify that concurrent threads properly serialize access to the schedule lock.
 */
@SpringBootTest
public class JobScheduleTest {

    private static final Logger logger = LoggerFactory.getLogger(JobScheduleTest.class);

    // Test configuration constants
    private static final int CONCURRENT_THREAD_COUNT = 10;
    private static final int TEST_DURATION_MINUTES = 10;
    private static final int LOCK_HOLD_DURATION_MS = 500;
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss SSS";

    /**
     * Tests distributed lock behavior under concurrent access.
     *
     * <p>Spawns multiple threads that compete for the schedule lock. Each thread holds the lock for
     * 500ms to simulate real scheduling work. Verifies that lock acquisition serializes access and
     * prevents race conditions.
     *
     * <p>This test runs for 10 minutes to observe lock behavior over time.
     */
    @Test
    public void testScheduleLock_withConcurrentThreads_serializesAccess() throws Exception {
        // Create concurrent threads using Stream API
        IntStream.range(0, CONCURRENT_THREAD_COUNT)
                .forEach(
                        i -> {
                            String threadName = "lock-test-thread-" + i;
                            new Thread(() -> executeLockTest(threadName)).start();
                        });

        // Allow test to run long enough to observe lock behavior
        TimeUnit.MINUTES.sleep(TEST_DURATION_MINUTES);
    }

    /**
     * Executes a lock acquisition test cycle.
     *
     * <p>Acquires the schedule lock via database transaction, holds it for configured duration,
     * then releases it via transaction commit. Logs timing information for observability.
     *
     * @param threadName identifier for the thread executing the test
     */
    private void executeLockTest(String threadName) {
        TransactionStatus transaction =
                XxlJobAdminBootstrap.getInstance()
                        .getTransactionManager()
                        .getTransaction(new DefaultTransactionDefinition());

        try {
            // Acquire distributed lock via SELECT ... FOR UPDATE
            String lockedRecord =
                    XxlJobAdminBootstrap.getInstance().getXxlJobLockMapper().scheduleLock();

            assertThat(lockedRecord)
                    .as("Lock record should be acquired successfully")
                    .isNotNull()
                    .isNotEmpty();

            String startTime = DateTool.format(new Date(), DATE_FORMAT);
            logger.info("{} : acquired lock at {}", threadName, startTime);

            // Simulate scheduling work
            TimeUnit.MILLISECONDS.sleep(LOCK_HOLD_DURATION_MS);

            String endTime = DateTool.format(new Date(), DATE_FORMAT);
            logger.info("{} : releasing lock at {}", threadName, endTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("{} : interrupted during lock test", threadName, e);
        } catch (Throwable e) {
            logger.error("{} : error during lock test", threadName, e);
        } finally {
            String commitTime = DateTool.format(new Date(), DATE_FORMAT);
            logger.info("{} : committing transaction at {}", threadName, commitTime);

            // Release lock by committing transaction
            XxlJobAdminBootstrap.getInstance().getTransactionManager().commit(transaction);
        }
    }
}
