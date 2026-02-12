package com.xxl.job.admin.scheduler.route.strategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import com.xxl.job.admin.scheduler.route.ExecutorRouter;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Consistent hash routing strategy for the Orth scheduler.
 *
 * <p>This strategy uses consistent hashing to provide stable executor assignment for each job. The
 * same job will always route to the same executor (unless executors are added/removed), providing
 * executor affinity.
 *
 * <p>Algorithm overview:
 *
 * <ol>
 *   <li>Create a virtual node ring by hashing each executor address multiple times (100 virtual
 *       nodes per executor)
 *   <li>Hash the job ID to a position on the ring
 *   <li>Route to the first virtual node clockwise from the job's position
 * </ol>
 *
 * <p>Benefits of virtual nodes:
 *
 * <ul>
 *   <li>More uniform distribution of jobs across executors
 *   <li>Minimized rebalancing when executors are added or removed
 *   <li>Better load balance than simple hash modulo
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Jobs that maintain local state or caches (executor affinity beneficial)
 *   <li>Workloads where consistent routing reduces overhead
 *   <li>Scenarios requiring minimal job migration during executor scaling
 * </ul>
 *
 * <p>This implementation uses MD5 hashing for distribution on a 2^32 ring space.
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteConsistentHash extends ExecutorRouter {

    private static final int VIRTUAL_NODE_COUNT = 100;
    private static final long HASH_RING_MAX = 0xFFFFFFFFL;

    /**
     * Computes MD5 hash for a key, mapped to 2^32 ring space.
     *
     * <p>Uses MD5 digest truncated to 32 bits for hash ring positioning. This provides better
     * distribution than Java's default {@code hashCode()}.
     *
     * @param key the string to hash
     * @return hash value in range [0, 2^32)
     */
    private static long computeHash(String key) {
        try {
            var md5 = MessageDigest.getInstance("MD5");
            var keyBytes = key.getBytes(StandardCharsets.UTF_8);
            var digest = md5.digest(keyBytes);

            // Extract 32-bit hash from first 4 bytes of digest
            long hashCode =
                    ((long) (digest[3] & 0xFF) << 24)
                            | ((long) (digest[2] & 0xFF) << 16)
                            | ((long) (digest[1] & 0xFF) << 8)
                            | (digest[0] & 0xFF);

            return hashCode & HASH_RING_MAX;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Routes a job to an executor using consistent hashing.
     *
     * <p>Algorithm:
     *
     * <ol>
     *   <li>Build hash ring with virtual nodes for each executor
     *   <li>Hash the job ID to find its position
     *   <li>Find the first virtual node clockwise from that position
     *   <li>Return the executor owning that virtual node
     * </ol>
     *
     * @param jobId the job identifier
     * @param addressList the available executor addresses
     * @return the selected executor address
     */
    public String selectExecutorByHash(int jobId, List<String> addressList) {
        // Build consistent hash ring with virtual nodes
        var hashRing = new TreeMap<Long, String>();

        for (var address : addressList) {
            // Create virtual nodes to improve distribution
            for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
                var virtualNodeKey = "SHARD-" + address + "-NODE-" + i;
                var virtualNodeHash = computeHash(virtualNodeKey);
                hashRing.put(virtualNodeHash, address);
            }
        }

        // Hash job ID to find position on ring
        var jobHash = computeHash(String.valueOf(jobId));

        // Find first node clockwise from job position (tailMap returns >= jobHash)
        SortedMap<Long, String> clockwiseNodes = hashRing.tailMap(jobHash);

        if (!clockwiseNodes.isEmpty()) {
            return clockwiseNodes.get(clockwiseNodes.firstKey());
        }

        // Wrap around to beginning of ring
        return hashRing.firstEntry().getValue();
    }

    /**
     * Routes to an executor using consistent hashing based on job ID.
     *
     * @param triggerParam the trigger request containing the job ID
     * @param addressList the available executor addresses
     * @return the selected executor address
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        var address = selectExecutorByHash(triggerParam.getJobId(), addressList);
        return Response.ofSuccess(address);
    }
}
