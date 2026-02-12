package com.xxl.job.admin.scheduler.route.strategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.xxl.job.admin.scheduler.route.ExecutorRouter;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Least Recently Used (LRU) routing strategy for the Orth scheduler.
 *
 * <p>This strategy routes jobs to the executor that was least recently used. Each job maintains an
 * access-ordered map of executors, automatically tracking usage recency through map access
 * patterns.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Maintain per-job LRU map (access-ordered LinkedHashMap)
 *   <li>On each trigger, select the eldest (least recently used) executor
 *   <li>Accessing the executor moves it to the end of the order
 *   <li>Periodically clear maps to prevent unbounded growth
 * </ol>
 *
 * <p>Load distribution characteristics:
 *
 * <ul>
 *   <li>Fair distribution based on recent usage, not cumulative counts
 *   <li>Self-balancing: naturally rotates through executors
 *   <li>Adaptive to usage patterns over time
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Jobs requiring balanced distribution based on recent activity
 *   <li>Workloads where recent load matters more than cumulative history
 *   <li>Scenarios where executor usage should rotate regularly
 * </ul>
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Uses LinkedHashMap with accessOrder=true for automatic LRU tracking
 *   <li>Entire cache cleared every 24 hours to prevent memory growth
 *   <li>Dynamically handles executor additions/removals
 *   <li>Thread-safe through synchronized access to per-job maps
 * </ul>
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteLRU extends ExecutorRouter {

    private static final long CACHE_CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final int LRU_MAP_INITIAL_CAPACITY = 16;
    private static final float LRU_MAP_LOAD_FACTOR = 0.75f;
    private static final boolean LRU_MAP_ACCESS_ORDER = true;

    /**
     * Per-job LRU map: jobId → LinkedHashMap(address → address)
     *
     * <p>LinkedHashMap configured with accessOrder=true maintains insertion/access order,
     * automatically tracking least recently used entries.
     */
    private static final ConcurrentMap<Integer, LinkedHashMap<String, String>> jobLRUMap =
            new ConcurrentHashMap<>();

    private static volatile long cacheValidUntil = 0;

    /**
     * Routes to the least recently used executor for the given job.
     *
     * @param jobId the job identifier
     * @param addressList the available executor addresses
     * @return the executor address least recently used
     */
    public String selectLeastRecentlyUsed(int jobId, List<String> addressList) {
        // Periodic cache cleanup to prevent unbounded memory growth
        cleanupCacheIfNeeded();

        // Get or create LRU map for this job
        var lruMap =
                jobLRUMap.computeIfAbsent(
                        jobId,
                        id ->
                                new LinkedHashMap<>(
                                        LRU_MAP_INITIAL_CAPACITY,
                                        LRU_MAP_LOAD_FACTOR,
                                        LRU_MAP_ACCESS_ORDER));

        // Synchronize on the LRU map to ensure thread-safe updates
        synchronized (lruMap) {
            // Add new executors
            for (var address : addressList) {
                if (!lruMap.containsKey(address)) {
                    lruMap.put(address, address);
                }
            }

            // Remove stale executors no longer in the address list
            lruMap.keySet().removeIf(address -> !addressList.contains(address));

            // Get least recently used (first) entry
            var eldestKey = lruMap.entrySet().iterator().next().getKey();

            // Access the entry to move it to the end (most recently used)
            return lruMap.get(eldestKey);
        }
    }

    /** Clears the LRU cache if the cleanup interval has elapsed. */
    private void cleanupCacheIfNeeded() {
        var currentTime = System.currentTimeMillis();
        if (currentTime > cacheValidUntil) {
            jobLRUMap.clear();
            cacheValidUntil = currentTime + CACHE_CLEANUP_INTERVAL_MS;
        }
    }

    /**
     * Routes to the least recently used executor.
     *
     * @param triggerParam the trigger request containing the job ID
     * @param addressList the available executor addresses
     * @return the selected executor address
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        var address = selectLeastRecentlyUsed(triggerParam.getJobId(), addressList);
        return Response.ofSuccess(address);
    }
}
