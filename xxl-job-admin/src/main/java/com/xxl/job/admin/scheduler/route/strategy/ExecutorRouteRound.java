package com.xxl.job.admin.scheduler.route.strategy;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.xxl.job.admin.scheduler.route.ExecutorRouter;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Round-robin executor routing strategy for the Orth scheduler.
 *
 * <p>This strategy distributes job executions evenly across all available executors using a
 * round-robin algorithm. Each job maintains its own counter to track the next executor to use.
 *
 * <p>Load distribution characteristics:
 *
 * <ul>
 *   <li>Guaranteed fair distribution across executors over time
 *   <li>Per-job routing state (different jobs use independent counters)
 *   <li>Deterministic and predictable routing sequence
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Stateless jobs requiring balanced load distribution
 *   <li>Scenarios where executor affinity is not required
 *   <li>Workloads where predictable rotation is desired
 * </ul>
 *
 * <p>Implementation details:
 *
 * <ul>
 *   <li>Maintains a counter per job ID in a concurrent map
 *   <li>Initializes counters with random offset to avoid thundering herd on startup
 *   <li>Resets counters when exceeding 1 million to prevent overflow
 *   <li>Periodically clears entire cache (every 24 hours) to prevent unbounded memory growth
 * </ul>
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteRound extends ExecutorRouter {

    private static final long CACHE_CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final int COUNTER_RESET_THRESHOLD = 1_000_000;
    private static final int INITIAL_RANDOM_BOUND = 100;

    private static final ConcurrentMap<Integer, AtomicInteger> routeCountEachJob =
            new ConcurrentHashMap<>();
    private static volatile long cacheValidUntil = 0;

    /**
     * Gets and increments the round-robin counter for the specified job.
     *
     * <p>This method handles:
     *
     * <ul>
     *   <li>Periodic cache cleanup to prevent memory growth
     *   <li>Counter initialization with random offset
     *   <li>Counter reset when exceeding threshold
     * </ul>
     *
     * @param jobId the job identifier
     * @return the current counter value for routing
     */
    private static int getAndIncrementCounter(int jobId) {
        var currentTime = System.currentTimeMillis();

        // Periodic cache cleanup to prevent unbounded growth
        if (currentTime > cacheValidUntil) {
            routeCountEachJob.clear();
            cacheValidUntil = currentTime + CACHE_CLEANUP_INTERVAL_MS;
        }

        // Get or initialize counter for this job
        var counter =
                routeCountEachJob.computeIfAbsent(
                        jobId,
                        id -> {
                            // Initialize with random offset to spread initial load
                            var initialValue =
                                    ThreadLocalRandom.current().nextInt(INITIAL_RANDOM_BOUND);
                            return new AtomicInteger(initialValue);
                        });

        // Reset counter if it exceeds threshold to prevent overflow
        var currentValue = counter.get();
        if (currentValue > COUNTER_RESET_THRESHOLD) {
            counter.set(0);
            currentValue = 0;
        }

        // Increment and return
        return counter.getAndIncrement();
    }

    /**
     * Routes to the next executor in round-robin order.
     *
     * <p>Uses modulo arithmetic to map the counter to an executor index.
     *
     * @param triggerParam the trigger request containing the job ID
     * @param addressList the available executor addresses
     * @return the selected executor address
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        var counter = getAndIncrementCounter(triggerParam.getJobId());
        var index = counter % addressList.size();
        var address = addressList.get(index);
        return Response.ofSuccess(address);
    }
}
