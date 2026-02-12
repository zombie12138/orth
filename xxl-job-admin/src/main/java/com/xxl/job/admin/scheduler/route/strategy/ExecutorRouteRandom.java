package com.xxl.job.admin.scheduler.route.strategy;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.xxl.job.admin.scheduler.route.ExecutorRouter;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Random executor routing strategy for the Orth scheduler.
 *
 * <p>This strategy selects a random executor from the available address list for each trigger. Each
 * execution is independent and has equal probability of routing to any available executor.
 *
 * <p>Load distribution characteristics:
 *
 * <ul>
 *   <li>Statistically uniform distribution over many executions
 *   <li>No guaranteed fairness for small sample sizes
 *   <li>No executor affinity or stickiness
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Stateless jobs where executor selection doesn't matter
 *   <li>Simple load balancing without round-robin state management
 *   <li>Testing scenarios requiring varied executor selection
 * </ul>
 *
 * <p>This implementation uses {@link ThreadLocalRandom} for better performance in concurrent
 * scenarios compared to shared {@link java.util.Random} instances.
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteRandom extends ExecutorRouter {

    /**
     * Routes to a randomly selected executor.
     *
     * <p>Uses thread-local random number generation for optimal concurrency performance.
     *
     * @param triggerParam the trigger request (unused by this strategy)
     * @param addressList the available executor addresses
     * @return a randomly selected executor address
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        var randomIndex = ThreadLocalRandom.current().nextInt(addressList.size());
        var address = addressList.get(randomIndex);
        return Response.ofSuccess(address);
    }
}
