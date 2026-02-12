package com.xxl.job.core.openapi;

import java.util.List;

import com.xxl.job.core.openapi.model.CallbackRequest;
import com.xxl.job.core.openapi.model.RegistryRequest;
import com.xxl.tool.response.Response;

/**
 * Admin RPC interface for executor-to-admin communication.
 *
 * <p>Executors use this interface to: 1. Send job execution result callbacks 2. Register/deregister
 * with the admin scheduler for service discovery
 *
 * <p>Implementations are created via HTTP proxy (see {@link
 * com.xxl.job.core.executor.XxlJobExecutor#initAdminBizList}).
 */
public interface AdminBiz {

    // ---------------------- Callback ----------------------

    /**
     * Sends job execution result callbacks to admin.
     *
     * <p>Executors batch multiple callbacks and send them periodically via {@link
     * com.xxl.job.core.thread.TriggerCallbackThread}.
     *
     * @param callbackRequestList batch of execution results
     * @return success response, or error if admin rejects callbacks
     */
    Response<String> callback(List<CallbackRequest> callbackRequestList);

    // ---------------------- Registry ----------------------

    /**
     * Registers executor with admin for service discovery.
     *
     * <p>Sent as a heartbeat every 30 seconds by {@link
     * com.xxl.job.core.thread.ExecutorRegistryThread}. Admin considers executors dead after 90
     * seconds without heartbeat.
     *
     * @param registryRequest executor registration info (type, appname, address)
     * @return success response, or error if registration rejected
     */
    Response<String> registry(RegistryRequest registryRequest);

    /**
     * Deregisters executor from admin service discovery.
     *
     * <p>Sent on executor shutdown to immediately remove from available executor pool.
     *
     * @param registryRequest executor registration info (type, appname, address)
     * @return success response, or error if deregistration rejected
     */
    Response<String> registryRemove(RegistryRequest registryRequest);
}
