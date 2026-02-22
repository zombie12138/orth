package com.abyss.orth.admin.scheduler.route;

import java.util.Arrays;

import com.abyss.orth.admin.scheduler.route.strategy.*;
import com.abyss.orth.admin.util.I18nUtil;

/**
 * Defines executor routing strategies for the Orth scheduler.
 *
 * <p>When a job is triggered, the scheduler must select which executor(s) to run it on. This enum
 * provides multiple routing strategies to support different load distribution, failover, and
 * partitioning requirements.
 *
 * <p>Available strategies:
 *
 * <ul>
 *   <li><b>FIRST</b>: Always route to the first executor in the address list
 *   <li><b>LAST</b>: Always route to the last executor in the address list
 *   <li><b>ROUND</b>: Round-robin distribution across all executors
 *   <li><b>RANDOM</b>: Random selection for each trigger
 *   <li><b>CONSISTENT_HASH</b>: Consistent hashing by job ID for sticky routing
 *   <li><b>LEAST_FREQUENTLY_USED</b>: Route to executor with lowest cumulative usage count
 *   <li><b>LEAST_RECENTLY_USED</b>: Route to executor with oldest last-used timestamp
 *   <li><b>FAILOVER</b>: Automatic failover to next executor on heartbeat failure
 *   <li><b>BUSYOVER</b>: Route to idle executors, skipping busy ones
 *   <li><b>SHARDING_BROADCAST</b>: Execute on all executors with shard parameters
 * </ul>
 *
 * <p>Strategy selection guidelines:
 *
 * <ul>
 *   <li>Use ROUND or RANDOM for stateless jobs requiring balanced load distribution
 *   <li>Use CONSISTENT_HASH when jobs need executor affinity (e.g., local caching)
 *   <li>Use FAILOVER for critical jobs requiring high availability
 *   <li>Use BUSYOVER to avoid executor overload
 *   <li>Use SHARDING_BROADCAST for parallel batch processing with data partitioning
 * </ul>
 *
 * @author xuxueli 2017-03-10
 */
public enum ExecutorRouteStrategyEnum {
    /** Always select the first executor */
    FIRST(I18nUtil.getString("jobconf_route_first"), new ExecutorRouteFirst()),

    /** Always select the last executor */
    LAST(I18nUtil.getString("jobconf_route_last"), new ExecutorRouteLast()),

    /** Round-robin distribution across executors */
    ROUND(I18nUtil.getString("jobconf_route_round"), new ExecutorRouteRound()),

    /** Random executor selection */
    RANDOM(I18nUtil.getString("jobconf_route_random"), new ExecutorRouteRandom()),

    /** Consistent hash routing by job ID */
    CONSISTENT_HASH(
            I18nUtil.getString("jobconf_route_consistenthash"), new ExecutorRouteConsistentHash()),

    /** Route to least frequently used executor */
    LEAST_FREQUENTLY_USED(I18nUtil.getString("jobconf_route_lfu"), new ExecutorRouteLFU()),

    /** Route to least recently used executor */
    LEAST_RECENTLY_USED(I18nUtil.getString("jobconf_route_lru"), new ExecutorRouteLRU()),

    /** Auto-failover to healthy executors */
    FAILOVER(I18nUtil.getString("jobconf_route_failover"), new ExecutorRouteFailover()),

    /** Route to idle executors only */
    BUSYOVER(I18nUtil.getString("jobconf_route_busyover"), new ExecutorRouteBusyover()),

    /** Broadcast to all executors with sharding parameters */
    SHARDING_BROADCAST(I18nUtil.getString("jobconf_route_shard"), null);

    private final String title;
    private final ExecutorRouter router;

    ExecutorRouteStrategyEnum(String title, ExecutorRouter router) {
        this.title = title;
        this.router = router;
    }

    /**
     * Returns the internationalized display title for this routing strategy.
     *
     * @return the localized display name
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the router implementation for this strategy.
     *
     * <p>Note: SHARDING_BROADCAST returns null because it's handled specially by the scheduler
     * (broadcast to all executors rather than selecting one).
     *
     * @return the executor router, or null for SHARDING_BROADCAST
     */
    public ExecutorRouter getRouter() {
        return router;
    }

    /**
     * Finds a routing strategy by its enum name, with fallback to a default.
     *
     * <p>This method is used during job configuration parsing to safely resolve routing strategy
     * settings.
     *
     * @param name the enum constant name (e.g., "ROUND", "FAILOVER")
     * @param defaultItem the fallback value if name is null or not found
     * @return the matching routing strategy, or defaultItem if not found
     */
    public static ExecutorRouteStrategyEnum match(
            String name, ExecutorRouteStrategyEnum defaultItem) {
        if (name == null) {
            return defaultItem;
        }

        return Arrays.stream(values())
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElse(defaultItem);
    }
}
