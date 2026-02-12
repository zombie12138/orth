package com.xxl.job.admin.scheduler.route.strategy;

import java.util.List;

import com.xxl.job.admin.scheduler.route.ExecutorRouter;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Fixed first-executor routing strategy for the Orth scheduler.
 *
 * <p>This strategy always routes job executions to the first executor in the address list. The
 * executor list is typically ordered by registration time or alphabetically by address.
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Primary/secondary executor configurations where jobs should prefer the primary
 *   <li>Testing or debugging scenarios where consistent routing is desired
 *   <li>Jobs that maintain local state and require executor affinity
 * </ul>
 *
 * <p><b>Note:</b> This strategy provides no load distribution. All jobs will execute on the same
 * executor unless it becomes unavailable (removed from the address list).
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteFirst extends ExecutorRouter {

    /**
     * Routes to the first executor in the address list.
     *
     * <p>Assumes the address list is non-empty (enforced by caller).
     *
     * @param triggerParam the trigger request (unused by this strategy)
     * @param addressList the available executor addresses
     * @return the first executor address
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        return Response.ofSuccess(addressList.get(0));
    }
}
