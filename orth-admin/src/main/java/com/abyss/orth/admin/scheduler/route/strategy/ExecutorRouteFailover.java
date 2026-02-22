package com.abyss.orth.admin.scheduler.route.strategy;

import java.util.List;

import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;
import com.abyss.orth.admin.scheduler.route.ExecutorRouter;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.core.openapi.ExecutorBiz;
import com.abyss.orth.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Failover routing strategy for the Orth scheduler.
 *
 * <p>This strategy provides high-availability routing by actively checking executor health before
 * selection. It sends heartbeat requests to executors in sequence and routes to the first healthy
 * one.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Iterate through executor address list in order
 *   <li>Send heartbeat (beat) request to each executor
 *   <li>Return the first executor that responds successfully
 *   <li>If all executors fail, return failure with diagnostic information
 * </ol>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Critical jobs requiring high availability
 *   <li>Environments with unreliable executors or network conditions
 *   <li>Jobs that must avoid executing on unhealthy executors
 * </ul>
 *
 * <p><b>Performance consideration:</b> This strategy performs synchronous heartbeat checks on the
 * trigger path, adding latency. It's best used for critical, low-frequency jobs rather than
 * high-throughput scenarios.
 *
 * <p>The response message includes detailed diagnostics of all heartbeat attempts, useful for
 * troubleshooting connectivity or executor health issues.
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteFailover extends ExecutorRouter {

    private static final String HEARTBEAT_SEPARATOR = "<br><br>";
    private static final String LINE_BREAK = "<br>";

    /**
     * Routes to the first healthy executor based on heartbeat checks.
     *
     * <p>This method probes each executor with a heartbeat request. The first executor to respond
     * successfully is selected. All heartbeat results are accumulated in the response message for
     * diagnostic purposes.
     *
     * @param triggerParam the trigger request (unused by this strategy)
     * @param addressList the available executor addresses
     * @return the first healthy executor, or failure if all executors are unavailable
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        var diagnostics = new StringBuilder();

        for (var address : addressList) {
            // Send heartbeat to check executor health
            Response<String> beatResult = sendHeartbeat(address);

            // Append diagnostic information
            appendDiagnostics(diagnostics, address, beatResult);

            // Return immediately if executor is healthy
            if (beatResult.isSuccess()) {
                beatResult.setMsg(diagnostics.toString());
                beatResult.setData(address);
                return beatResult;
            }
        }

        // All executors failed health check
        return Response.ofFail(diagnostics.toString());
    }

    /**
     * Sends a heartbeat request to an executor to check health.
     *
     * @param address the executor address
     * @return the heartbeat response
     */
    private Response<String> sendHeartbeat(String address) {
        try {
            ExecutorBiz executorBiz = OrthAdminBootstrap.getExecutorBiz(address);
            return executorBiz.beat();
        } catch (Exception e) {
            logger.error("Heartbeat failed for executor {}: {}", address, e.getMessage(), e);
            return Response.ofFail(e.getMessage());
        }
    }

    /**
     * Appends heartbeat diagnostic information to the result message.
     *
     * @param diagnostics the string builder accumulating diagnostics
     * @param address the executor address
     * @param beatResult the heartbeat response
     */
    private void appendDiagnostics(
            StringBuilder diagnostics, String address, Response<String> beatResult) {

        if (diagnostics.length() > 0) {
            diagnostics.append(HEARTBEAT_SEPARATOR);
        }

        diagnostics
                .append(I18nUtil.getString("jobconf_beat"))
                .append(":")
                .append(LINE_BREAK)
                .append("address:")
                .append(address)
                .append(LINE_BREAK)
                .append("code:")
                .append(beatResult.getCode())
                .append(LINE_BREAK)
                .append("msg:")
                .append(beatResult.getMsg());
    }
}
