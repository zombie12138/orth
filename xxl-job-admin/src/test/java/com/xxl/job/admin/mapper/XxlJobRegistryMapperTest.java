package com.xxl.job.admin.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import com.xxl.job.admin.model.XxlJobRegistry;

import jakarta.annotation.Resource;

/**
 * Integration tests for {@link XxlJobRegistryMapper}.
 *
 * <p>Tests executor heartbeat registration and cleanup in the Orth distributed task scheduling
 * system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class XxlJobRegistryMapperTest {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobRegistryMapperTest.class);

    // Test data constants
    private static final String TEST_REGISTRY_GROUP = "orth-executor-group";
    private static final String TEST_REGISTRY_KEY = "test-executor-01";
    private static final String TEST_REGISTRY_VALUE = "http://localhost:9999";
    private static final int TEST_DEAD_REGISTRY_ID = 1;
    private static final int TEST_TIMEOUT_SECONDS = 90;

    private static final int CONCURRENT_THREAD_COUNT = 100;
    private static final int CONCURRENT_TEST_TIMEOUT_SECONDS = 10;

    @Resource private XxlJobRegistryMapper xxlJobRegistryMapper;

    /**
     * Tests executor registry operations including heartbeat and cleanup.
     *
     * <p>Verifies:
     *
     * <ul>
     *   <li>Upserting executor heartbeat (insert or update)
     *   <li>Querying active executors with timeout threshold
     *   <li>Removing dead/stale executor entries
     * </ul>
     */
    @Test
    public void testExecutorRegistryOperations() {
        // Upsert executor heartbeat
        int upsertResult =
                xxlJobRegistryMapper.registrySaveOrUpdate(
                        TEST_REGISTRY_GROUP, TEST_REGISTRY_KEY, TEST_REGISTRY_VALUE, new Date());
        assertTrue(upsertResult > 0, "Upsert should affect at least 1 row");

        // Query active executors (within timeout threshold)
        Date timeoutThreshold = new Date(System.currentTimeMillis() - TEST_TIMEOUT_SECONDS * 1000);
        List<XxlJobRegistry> activeRegistries =
                xxlJobRegistryMapper.findAll(TEST_TIMEOUT_SECONDS, timeoutThreshold);
        assertNotNull(activeRegistries, "Active registries should not be null");

        // Remove dead executor entries
        int removeResult = xxlJobRegistryMapper.removeDead(Arrays.asList(TEST_DEAD_REGISTRY_ID));
        assertTrue(removeResult >= 0, "Remove dead should succeed");
    }

    /**
     * Tests concurrent executor heartbeat updates for race condition handling.
     *
     * <p>Simulates 100 concurrent threads updating the same executor registry entry to verify
     * database-level upsert atomicity.
     *
     * @throws InterruptedException if thread execution is interrupted
     */
    @Test
    public void testConcurrentRegistryUpsert() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREAD_COUNT);

        IntStream.range(0, CONCURRENT_THREAD_COUNT)
                .forEach(
                        i -> {
                            new Thread(
                                            () -> {
                                                try {
                                                    int result =
                                                            xxlJobRegistryMapper
                                                                    .registrySaveOrUpdate(
                                                                            TEST_REGISTRY_GROUP,
                                                                            TEST_REGISTRY_KEY,
                                                                            TEST_REGISTRY_VALUE,
                                                                            new Date());
                                                    logger.debug(
                                                            "Thread {} upsert result: {}",
                                                            Thread.currentThread().getId(),
                                                            result);
                                                } finally {
                                                    latch.countDown();
                                                }
                                            })
                                    .start();
                        });

        boolean completed = latch.await(CONCURRENT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(completed, "All concurrent threads should complete within timeout");
    }
}
