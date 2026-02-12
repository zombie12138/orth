package com.xxl.job.admin.scheduler.route.strategy;

import java.util.List;

import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.admin.scheduler.route.ExecutorRouter;
import com.xxl.job.admin.util.I18nUtil;
import com.xxl.job.core.openapi.ExecutorBiz;
import com.xxl.job.core.openapi.model.IdleBeatRequest;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Busy-over routing strategy for the Orth scheduler.
 *
 * <p>This strategy routes jobs to idle executors, skipping those currently running the same job. It
 * actively checks executor availability using idle-beat requests before selection, preventing job
 * queue buildup on busy executors.
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Iterate through executor address list in order
 *   <li>Send idle-beat request for the specific job ID
 *   <li>Return the first executor that reports idle status
 *   <li>If all executors are busy, return failure
 * </ol>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Long-running jobs where concurrent execution on same executor is undesirable
 *   <li>Resource-intensive jobs requiring dedicated executor capacity
 *   <li>Scenarios where job queueing should be avoided
 * </ul>
 *
 * <p><b>Performance consideration:</b> This strategy performs synchronous idle-beat checks on the
 * trigger path, adding latency. It's best used for jobs where avoiding busy executors is more
 * important than routing speed.
 *
 * <p>The response message includes detailed diagnostics of all idle-beat attempts, useful for
 * understanding executor load patterns and troubleshooting availability issues.
 *
 * @author xuxueli 2017-03-10
 */
public class ExecutorRouteBusyover extends ExecutorRouter {

    private static final String IDLE_BEAT_SEPARATOR = "<br><br>";
    private static final String LINE_BREAK = "<br>";

    /**
     * Routes to the first idle executor for the specified job.
     *
     * <p>This method probes each executor with an idle-beat request specific to the job ID. The
     * first executor reporting idle status is selected. All idle-beat results are accumulated in
     * the response message for diagnostic purposes.
     *
     * @param triggerParam the trigger request containing the job ID
     * @param addressList the available executor addresses
     * @return the first idle executor, or failure if all executors are busy
     */
    @Override
    public Response<String> route(TriggerRequest triggerParam, List<String> addressList) {
        var diagnostics = new StringBuilder();
        var jobId = triggerParam.getJobId();

        for (var address : addressList) {
            // Check if executor is idle for this specific job
            Response<String> idleBeatResult = checkExecutorIdle(address, jobId);

            // Append diagnostic information
            appendDiagnostics(diagnostics, address, idleBeatResult);

            // Return immediately if executor is idle
            if (idleBeatResult.isSuccess()) {
                idleBeatResult.setMsg(diagnostics.toString());
                idleBeatResult.setData(address);
                return idleBeatResult;
            }
        }

        // All executors are busy
        return Response.ofFail(diagnostics.toString());
    }

    /**
     * Sends an idle-beat request to check if an executor is available for the job.
     *
     * @param address the executor address
     * @param jobId the job identifier
     * @return the idle-beat response indicating availability
     */
    private Response<String> checkExecutorIdle(String address, int jobId) {
        try {
            ExecutorBiz executorBiz = XxlJobAdminBootstrap.getExecutorBiz(address);
            var idleBeatRequest = new IdleBeatRequest(jobId);
            return executorBiz.idleBeat(idleBeatRequest);
        } catch (Exception e) {
            logger.error(
                    "Idle-beat check failed for executor {} job {}: {}",
                    address,
                    jobId,
                    e.getMessage(),
                    e);
            return Response.ofFail(e.toString());
        }
    }

    /**
     * Appends idle-beat diagnostic information to the result message.
     *
     * @param diagnostics the string builder accumulating diagnostics
     * @param address the executor address
     * @param idleBeatResult the idle-beat response
     */
    private void appendDiagnostics(
            StringBuilder diagnostics, String address, Response<String> idleBeatResult) {

        if (diagnostics.length() > 0) {
            diagnostics.append(IDLE_BEAT_SEPARATOR);
        }

        diagnostics
                .append(I18nUtil.getString("jobconf_idleBeat"))
                .append(":")
                .append(LINE_BREAK)
                .append("address:")
                .append(address)
                .append(LINE_BREAK)
                .append("code:")
                .append(idleBeatResult.getCode())
                .append(LINE_BREAK)
                .append("msg:")
                .append(idleBeatResult.getMsg());
    }
}
