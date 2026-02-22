package com.abyss.orth.admin.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;
import com.abyss.orth.core.openapi.AdminBiz;
import com.abyss.orth.core.openapi.model.CallbackRequest;
import com.abyss.orth.core.openapi.model.RegistryRequest;
import com.xxl.tool.response.Response;

/**
 * Admin business logic implementation for Orth OpenAPI.
 *
 * <p>Implements the AdminBiz interface to handle executor callbacks and registry operations. This
 * service is exposed via RPC to allow executors to:
 *
 * <ul>
 *   <li>Report job execution results (callback)
 *   <li>Register executor instances (registry)
 *   <li>Deregister executor instances (registryRemove)
 * </ul>
 *
 * <p>All operations are delegated to appropriate helper threads in the bootstrap for async
 * processing.
 *
 * @author xuxueli 2017-07-27 21:54:20
 */
@Service
public class AdminBizImpl implements AdminBiz {

    /**
     * Processes job execution callbacks from executors.
     *
     * <p>Executors call this method to report job execution results (success, failure, or in
     * progress). Callbacks are queued for async processing by JobCompleteHelper.
     *
     * @param callbackRequestList list of callback requests from executor
     * @return success response if queued successfully
     */
    @Override
    public Response<String> callback(List<CallbackRequest> callbackRequestList) {
        return OrthAdminBootstrap.getInstance()
                .getJobCompleteHelper()
                .callback(callbackRequestList);
    }

    /**
     * Registers executor instance for job scheduling.
     *
     * <p>Executors call this method to register themselves with the admin. Registration includes
     * executor address and group information. Heartbeats must be sent every 30 seconds to maintain
     * registration.
     *
     * @param registryRequest executor registration information
     * @return success response if registered successfully
     */
    @Override
    public Response<String> registry(RegistryRequest registryRequest) {
        return OrthAdminBootstrap.getInstance().getJobRegistryHelper().registry(registryRequest);
    }

    /**
     * Removes executor instance registration.
     *
     * <p>Executors call this method during graceful shutdown to deregister themselves from the
     * admin. This prevents the admin from routing jobs to stopped executors.
     *
     * @param registryRequest executor deregistration information
     * @return success response if deregistered successfully
     */
    @Override
    public Response<String> registryRemove(RegistryRequest registryRequest) {
        return OrthAdminBootstrap.getInstance()
                .getJobRegistryHelper()
                .registryRemove(registryRequest);
    }
}
