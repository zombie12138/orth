package com.xxl.job.admin.scheduler.route;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Abstract strategy for routing job executions to executors in the Orth scheduler.
 *
 * <p>When a job is triggered, the scheduler must select which executor instance(s) to send the
 * request to. This class defines the routing strategy interface, with concrete implementations
 * providing different load distribution algorithms.
 *
 * <p>Routing decision inputs:
 *
 * <ul>
 *   <li>Trigger parameters (job ID, execution parameters, shard info)
 *   <li>Available executor addresses (from service discovery)
 *   <li>Strategy-specific state (round-robin counters, usage statistics, etc.)
 * </ul>
 *
 * <p>Routing strategies registered in {@link ExecutorRouteStrategyEnum} include:
 *
 * <ul>
 *   <li><b>Load distribution:</b> ROUND, RANDOM, CONSISTENT_HASH, LFU, LRU
 *   <li><b>High availability:</b> FAILOVER, BUSYOVER
 *   <li><b>Fixed selection:</b> FIRST, LAST
 *   <li><b>Special:</b> SHARDING_BROADCAST (handled separately, not via this interface)
 * </ul>
 *
 * <p>Implementation guidelines:
 *
 * <ul>
 *   <li>Handle empty address lists gracefully (return error response)
 *   <li>Ensure thread-safety; routers are shared across multiple threads
 *   <li>Minimize routing overhead; this is called on the hot path
 *   <li>Log routing decisions at DEBUG level for troubleshooting
 * </ul>
 *
 * @author xuxueli 2017-03-10
 * @see ExecutorRouteStrategyEnum
 */
public abstract class ExecutorRouter {
    protected static final Logger logger = LoggerFactory.getLogger(ExecutorRouter.class);

    /**
     * Selects an executor address to route the trigger request to.
     *
     * <p>This method implements the routing algorithm to choose one executor from the available
     * address list. The selection criteria vary by strategy (round-robin, random, least-used,
     * etc.).
     *
     * <p>Response format:
     *
     * <ul>
     *   <li><b>Success:</b> {@code Response.ofSuccess(address)} where address is the selected
     *       executor endpoint
     *   <li><b>Failure:</b> {@code Response.ofFail(message)} if routing fails (e.g., no available
     *       executors)
     * </ul>
     *
     * @param triggerParam the trigger request containing job ID, parameters, and execution context
     * @param addressList the list of available executor addresses from service discovery
     * @return a Response containing the selected executor address, or an error if routing fails
     */
    public abstract Response<String> route(TriggerRequest triggerParam, List<String> addressList);
}
