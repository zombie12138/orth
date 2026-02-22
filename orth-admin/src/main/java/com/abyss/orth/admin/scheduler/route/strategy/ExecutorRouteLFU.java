package com.abyss.orth.admin.scheduler.route.strategy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import com.abyss.orth.admin.scheduler.route.ExecutorRouter;
import com.abyss.orth.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Least Frequently Used (LFU) routing strategy for the Orth scheduler.
 *
 * <p>This strategy routes jobs to the executor with the lowest usage frequency count. Each job
 * maintains independent usage counters for its executors, ensuring fair distribution based on
 * actual execution history.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Maintain per-job usage frequency map (address → count)
 *   <li>On each trigger, select executor with minimum count
 *   <li>Increment the selected executor's count
 *   <li>Periodically reset counters to prevent unbounded growth
 * </ol>
 *
 * <p>Load distribution characteristics:
 *
 * <ul>
 *   <li>Fair distribution based on cumulative usage, not recent usage
 *   <li>Self-balancing: naturally compensates for uneven load
 *   <li>Stable routing during normal operation
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Jobs requiring balanced distribution over long time periods
 *   <li>Workloads where cumulative fairness matters more than recent activity
 *   <li>Scenarios with heterogeneous executors requiring balanced usage
 * </ul>
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Counters initialized with random values to spread initial load
 *   <li>Counters reset at 1 million to prevent overflow
 *   <li>Entire cache cleared every 24 hours to prevent memory growth
 *   <li>Dynamically handles executor additions/removals
 * </ul>
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteLFU extends ExecutorRouter {

    private static final long CACHE_CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final int COUNTER_RESET_THRESHOLD = 1_000_000;

    /** Per-job frequency map: jobId → (executor address → usage count) */
    private static final ConcurrentMap<Integer, HashMap<String, Integer>> jobLfuMap =
            new ConcurrentHashMap<>();

    private static volatile long cacheValidUntil = 0;

    /**
     * Routes to the least frequently used executor for the given job.
     *
     * @param jobId the job identifier
     * @param addressList the available executor addresses
     * @return the executor address with lowest usage count
     */
    public String selectLeastFrequentlyUsed(int jobId, List<String> addressList) {
        // Periodic cache cleanup to prevent unbounded memory growth
        cleanupCacheIfNeeded();

        // Get or create frequency map for this job
        var frequencyMap = jobLfuMap.computeIfAbsent(jobId, id -> new HashMap<>());

        // Synchronize on the frequency map to ensure thread-safe updates
        synchronized (frequencyMap) {
            // Add new executors with random initial counts
            addNewExecutors(frequencyMap, addressList);

            // Remove executors no longer in the address list
            removeStaleExecutors(frequencyMap, addressList);

            // Find executor with minimum usage count
            var sortedEntries = new ArrayList<>(frequencyMap.entrySet());
            sortedEntries.sort(Map.Entry.comparingByValue());

            var leastUsedEntry = sortedEntries.get(0);

            // Increment usage count for selected executor
            leastUsedEntry.setValue(leastUsedEntry.getValue() + 1);

            return leastUsedEntry.getKey();
        }
    }

    /** Clears the frequency cache if the cleanup interval has elapsed. */
    private void cleanupCacheIfNeeded() {
        var currentTime = System.currentTimeMillis();
        if (currentTime > cacheValidUntil) {
            jobLfuMap.clear();
            cacheValidUntil = currentTime + CACHE_CLEANUP_INTERVAL_MS;
        }
    }

    /** Adds new executors to the frequency map with randomized initial counts. */
    private void addNewExecutors(HashMap<String, Integer> frequencyMap, List<String> addressList) {
        for (var address : addressList) {
            var currentCount = frequencyMap.get(address);

            // Initialize new executors or reset if count exceeded threshold
            if (currentCount == null || currentCount > COUNTER_RESET_THRESHOLD) {
                // Random initial value to spread initial load
                var initialCount = ThreadLocalRandom.current().nextInt(addressList.size());
                frequencyMap.put(address, initialCount);
            }
        }
    }

    /** Removes executors that are no longer in the address list. */
    private void removeStaleExecutors(
            HashMap<String, Integer> frequencyMap, List<String> addressList) {
        frequencyMap.keySet().removeIf(address -> !addressList.contains(address));
    }

    /**
     * Routes to the least frequently used executor.
     *
     * @param triggerParam the trigger request containing the job ID
     * @param addressList the available executor addresses
     * @return the selected executor address
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        var address = selectLeastFrequentlyUsed(triggerParam.getJobId(), addressList);
        return Response.ofSuccess(address);
    }
}
