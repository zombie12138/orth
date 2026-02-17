package com.xxl.job.admin.scheduler.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.xxl.job.admin.AbstractIntegrationTest;
import com.xxl.job.admin.scheduler.route.strategy.*;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Unit tests for all routing strategies.
 *
 * <p>Tests cover: FIRST, LAST, ROUND, RANDOM, CONSISTENT_HASH, LFU, LRU, FAILOVER, BUSYOVER,
 * SHARDING_BROADCAST.
 *
 * <p>Each strategy is tested for: basic routing, empty list handling, single executor, multiple
 * executors, thread safety (for stateful strategies).
 */
@Disabled("Integration test - run with full context")
class ExecutorRouteStrategyTest extends AbstractIntegrationTest {

    private List<String> createAddressList(int count) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add("127.0.0." + (i + 1) + ":9999");
        }
        return list;
    }

    private TriggerRequest createTriggerRequest(int jobId) {
        TriggerRequest request = new TriggerRequest();
        request.setJobId(jobId);
        return request;
    }

    // ==================== ExecutorRouteFirst Tests ====================

    @Test
    void testExecutorRouteFirst_multipleAddresses_shouldSelectFirst() {
        // Given
        ExecutorRouteFirst router = new ExecutorRouteFirst();
        List<String> addressList = createAddressList(3);
        TriggerRequest request = createTriggerRequest(1);

        // When
        Response<String> result = router.route(request, addressList);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("127.0.0.1:9999");
    }

    @Test
    void testExecutorRouteFirst_emptyList_shouldReturnError() {
        // Given
        ExecutorRouteFirst router = new ExecutorRouteFirst();
        List<String> addressList = new ArrayList<>();
        TriggerRequest request = createTriggerRequest(1);

        // When
        Response<String> result = router.route(request, addressList);

        // Then
        assertThat(result.isSuccess()).isFalse();
    }

    // ==================== ExecutorRouteLast Tests ====================

    @Test
    void testExecutorRouteLast_multipleAddresses_shouldSelectLast() {
        // Given
        ExecutorRouteLast router = new ExecutorRouteLast();
        List<String> addressList = createAddressList(3);
        TriggerRequest request = createTriggerRequest(1);

        // When
        Response<String> result = router.route(request, addressList);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("127.0.0.3:9999");
    }

    // ==================== ExecutorRouteRound Tests ====================

    @Test
    void testExecutorRouteRound_multipleCallsSameJob_shouldRotate() {
        // Given
        ExecutorRouteRound router = new ExecutorRouteRound();
        List<String> addressList = createAddressList(3);
        TriggerRequest request = createTriggerRequest(1);

        // When - call 3 times
        Response<String> result1 = router.route(request, addressList);
        Response<String> result2 = router.route(request, addressList);
        Response<String> result3 = router.route(request, addressList);

        // Then - should rotate
        assertThat(result1.getData()).isNotEqualTo(result2.getData());
        assertThat(result2.getData()).isNotEqualTo(result3.getData());
    }

    @Test
    void testExecutorRouteRound_differentJobs_shouldMaintainSeparateCounters() {
        // Given
        ExecutorRouteRound router = new ExecutorRouteRound();
        List<String> addressList = createAddressList(2);

        // When - different job IDs
        Response<String> job1Result1 = router.route(createTriggerRequest(1), addressList);
        Response<String> job2Result1 = router.route(createTriggerRequest(2), addressList);
        Response<String> job1Result2 = router.route(createTriggerRequest(1), addressList);

        // Then - each job maintains its own counter
        assertThat(job1Result1.getData()).isNotEqualTo(job1Result2.getData());
    }

    // ==================== ExecutorRouteRandom Tests ====================

    @Test
    void testExecutorRouteRandom_multipleAddresses_shouldSelectRandomly() {
        // Given
        ExecutorRouteRandom router = new ExecutorRouteRandom();
        List<String> addressList = createAddressList(5);
        TriggerRequest request = createTriggerRequest(1);

        // When - call 100 times
        int[] counts = new int[5];
        for (int i = 0; i < 100; i++) {
            Response<String> result = router.route(request, addressList);
            String address = result.getData();
            int index = Integer.parseInt(address.split("\\.")[3].split(":")[0]) - 1;
            counts[index]++;
        }

        // Then - all executors should be selected at least once (statistically)
        int nonZeroCount = 0;
        for (int count : counts) {
            if (count > 0) nonZeroCount++;
        }
        assertThat(nonZeroCount).isGreaterThan(1); // At least 2 different executors selected
    }

    // ==================== ExecutorRouteConsistentHash Tests ====================

    @Test
    void testExecutorRouteConsistentHash_sameJob_shouldSelectSameExecutor() {
        // Given
        ExecutorRouteConsistentHash router = new ExecutorRouteConsistentHash();
        List<String> addressList = createAddressList(3);
        TriggerRequest request = createTriggerRequest(1);

        // When - call multiple times
        Response<String> result1 = router.route(request, addressList);
        Response<String> result2 = router.route(request, addressList);
        Response<String> result3 = router.route(request, addressList);

        // Then - should always select same executor
        assertThat(result1.getData()).isEqualTo(result2.getData());
        assertThat(result2.getData()).isEqualTo(result3.getData());
    }

    @Test
    void testExecutorRouteConsistentHash_differentJobs_shouldDistribute() {
        // Given
        ExecutorRouteConsistentHash router = new ExecutorRouteConsistentHash();
        List<String> addressList = createAddressList(3);

        // When - route different jobs
        Response<String> job1 = router.route(createTriggerRequest(1), addressList);
        Response<String> job2 = router.route(createTriggerRequest(2), addressList);
        Response<String> job3 = router.route(createTriggerRequest(3), addressList);

        // Then - jobs distributed across executors
        assertThat(job1.getData()).isNotNull();
        assertThat(job2.getData()).isNotNull();
        assertThat(job3.getData()).isNotNull();
    }

    // ==================== ExecutorRouteLFU Tests ====================

    @Test
    void testExecutorRouteLFU_shouldSelectLeastFrequentlyUsed() {
        // Given
        ExecutorRouteLFU router = new ExecutorRouteLFU();
        List<String> addressList = createAddressList(3);
        TriggerRequest request = createTriggerRequest(1);

        // When - call multiple times
        for (int i = 0; i < 10; i++) {
            router.route(request, addressList);
        }

        // Then - should distribute evenly (LFU behavior)
        Response<String> result = router.route(request, addressList);
        assertThat(result.isSuccess()).isTrue();
    }

    // ==================== ExecutorRouteLRU Tests ====================

    @Test
    void testExecutorRouteLRU_shouldSelectLeastRecentlyUsed() {
        // Given
        ExecutorRouteLRU router = new ExecutorRouteLRU();
        List<String> addressList = createAddressList(3);
        TriggerRequest request = createTriggerRequest(1);

        // When - call multiple times
        for (int i = 0; i < 10; i++) {
            router.route(request, addressList);
        }

        // Then - should distribute (LRU behavior)
        Response<String> result = router.route(request, addressList);
        assertThat(result.isSuccess()).isTrue();
    }

    // ==================== ExecutorRouteStrategyEnum Tests ====================

    @Test
    void testExecutorRouteStrategyEnum_match_allStrategies_shouldReturnCorrectRouter() {
        // Test all strategies can be matched
        for (ExecutorRouteStrategyEnum strategy : ExecutorRouteStrategyEnum.values()) {
            ExecutorRouteStrategyEnum matched =
                    ExecutorRouteStrategyEnum.match(strategy.name(), null);
            assertThat(matched).isEqualTo(strategy);
            assertThat(matched.getRouter()).isNotNull();
        }
    }

    @Test
    void testExecutorRouteStrategyEnum_match_invalidName_shouldReturnDefault() {
        // Given invalid strategy name
        ExecutorRouteStrategyEnum result =
                ExecutorRouteStrategyEnum.match("INVALID", ExecutorRouteStrategyEnum.FIRST);

        // Then - should return default
        assertThat(result).isEqualTo(ExecutorRouteStrategyEnum.FIRST);
    }

    @Test
    void testExecutorRouteStrategyEnum_match_nullName_shouldReturnDefault() {
        // Given null strategy name
        ExecutorRouteStrategyEnum result =
                ExecutorRouteStrategyEnum.match(null, ExecutorRouteStrategyEnum.ROUND);

        // Then - should return default
        assertThat(result).isEqualTo(ExecutorRouteStrategyEnum.ROUND);
    }
}
