package com.xxl.job.admin.scheduler.route.strategy;

import java.util.List;

import com.xxl.job.admin.scheduler.route.ExecutorRouter;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Fixed last-executor routing strategy for the Orth scheduler.
 *
 * <p>This strategy always routes job executions to the last executor in the address list. The
 * executor list is typically ordered by registration time or alphabetically by address.
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Preferring most recently registered executors (if list is ordered by registration time)
 *   <li>Testing or debugging scenarios where consistent routing to a specific executor is desired
 *   <li>Blue/green deployment patterns where new executors are added at the end
 * </ul>
 *
 * <p><b>Note:</b> This strategy provides no load distribution. All jobs will execute on the same
 * executor unless it becomes unavailable (removed from the address list).
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteLast extends ExecutorRouter {

    /**
     * Routes to the last executor in the address list.
     *
     * <p>Assumes the address list is non-empty (enforced by caller).
     *
     * @param triggerParam the trigger request (unused by this strategy)
     * @param addressList the available executor addresses
     * @return the last executor address
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        return Response.ofSuccess(addressList.get(addressList.size() - 1));
    }
}
