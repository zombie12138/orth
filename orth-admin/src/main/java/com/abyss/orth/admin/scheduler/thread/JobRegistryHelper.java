package com.abyss.orth.admin.scheduler.thread;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.admin.model.JobGroup;
import com.abyss.orth.admin.model.JobRegistry;
import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;
import com.abyss.orth.core.constant.Const;
import com.abyss.orth.core.constant.RegistType;
import com.abyss.orth.core.openapi.model.RegistryRequest;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.response.Response;

/**
 * Job registry instance helper for executor heartbeat processing.
 *
 * <p>Manages executor registration and auto-discovery through heartbeat mechanism:
 *
 * <ul>
 *   <li><b>Registration Pool</b>: Processes async registry/remove requests from executors
 *   <li><b>Monitor Thread</b>: Runs every 30 seconds to clean stale entries and update group
 *       addresses
 * </ul>
 *
 * <p><b>Heartbeat Mechanism</b>:
 *
 * <ul>
 *   <li>Executors send heartbeat every 30 seconds (BEAT_TIMEOUT)
 *   <li>Entries older than 90 seconds (DEAD_TIMEOUT) are considered dead
 *   <li>Group address cache is refreshed from live executor registrations
 * </ul>
 *
 * @author xuxueli 2016-10-02 19:10:24
 */
public class JobRegistryHelper {
    private static final Logger logger = LoggerFactory.getLogger(JobRegistryHelper.class);

    // Thread pool configuration
    private static final int REGISTRY_CORE_POOL_SIZE = 2;
    private static final int REGISTRY_MAX_POOL_SIZE = 10;
    private static final long REGISTRY_KEEP_ALIVE_SECONDS = 30L;
    private static final int REGISTRY_QUEUE_SIZE = 2000;

    private ThreadPoolExecutor registryOrRemoveThreadPool = null;
    private ScheduledExecutorService monitorScheduler;

    /**
     * Starts the registry helper with thread pool and monitor scheduler.
     *
     * <p>Initializes:
     *
     * <ul>
     *   <li>Registry thread pool for async registration/removal requests
     *   <li>Scheduled executor for cleaning stale entries and refreshing group addresses
     * </ul>
     */
    public void start() {

        // for registry or remove
        registryOrRemoveThreadPool =
                new ThreadPoolExecutor(
                        REGISTRY_CORE_POOL_SIZE,
                        REGISTRY_MAX_POOL_SIZE,
                        REGISTRY_KEEP_ALIVE_SECONDS,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(REGISTRY_QUEUE_SIZE),
                        r ->
                                new Thread(
                                        r,
                                        "orth, admin JobRegistryMonitorHelper-registryOrRemoveThreadPool-"
                                                + r.hashCode()),
                        (r, executor) -> {
                            r.run();
                            logger.warn(
                                    ">>>>>>>>>>> orth, registry or remove too fast, match threadpool rejected handler(run now).");
                        });

        // for monitor
        monitorScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t =
                                    new Thread(
                                            r,
                                            "orth-admin-JobRegistryMonitorHelper-registryMonitorThread");
                            t.setDaemon(true);
                            return t;
                        });
        monitorScheduler.scheduleWithFixedDelay(
                safeRunnable("registry-monitor", this::processRegistryMonitor),
                0,
                Const.BEAT_TIMEOUT,
                TimeUnit.SECONDS);
    }

    /**
     * Processes one registry monitor cycle: cleans stale entries and refreshes group addresses.
     *
     * <p>This fixes the original tight-spin bug where an empty groupList caused `continue` to skip
     * the sleep, resulting in a busy loop. Now uses `return` to exit back to the scheduler.
     */
    private void processRegistryMonitor() {
        // auto registry group
        List<JobGroup> groupList =
                OrthAdminBootstrap.getInstance().getJobGroupMapper().findByAddressType(0);
        if (groupList == null || groupList.isEmpty()) {
            return;
        }

        // remove dead address (admin/executor)
        List<Integer> ids =
                OrthAdminBootstrap.getInstance()
                        .getJobRegistryMapper()
                        .findDead(Const.DEAD_TIMEOUT, new Date());
        if (ids != null && !ids.isEmpty()) {
            OrthAdminBootstrap.getInstance().getJobRegistryMapper().removeDead(ids);
        }

        // fresh online address (admin/executor)
        List<JobRegistry> list =
                OrthAdminBootstrap.getInstance()
                        .getJobRegistryMapper()
                        .findAll(Const.DEAD_TIMEOUT, new Date());
        if (list == null) {
            return;
        }

        Map<String, List<String>> appAddressMap =
                list.stream()
                        .filter(item -> RegistType.EXECUTOR.name().equals(item.getRegistryGroup()))
                        .collect(
                                Collectors.groupingBy(
                                        JobRegistry::getRegistryKey,
                                        Collectors.mapping(
                                                JobRegistry::getRegistryValue,
                                                Collectors.toList())));

        // fresh group address
        for (JobGroup group : groupList) {
            List<String> registryList = appAddressMap.get(group.getAppname());
            String addressListStr = null;
            if (registryList != null && !registryList.isEmpty()) {
                Collections.sort(registryList);
                addressListStr = String.join(",", registryList);
            }
            group.setAddressList(addressListStr);
            group.setUpdateTime(new Date());

            OrthAdminBootstrap.getInstance().getJobGroupMapper().update(group);
        }
    }

    /**
     * Stops the registry helper gracefully.
     *
     * <p>Shuts down the registry thread pool and monitor scheduler.
     */
    public void stop() {
        // stop registryOrRemoveThreadPool
        registryOrRemoveThreadPool.shutdownNow();

        // stop monitor scheduler
        monitorScheduler.shutdown();
        try {
            if (!monitorScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info(">>>>>>>>>>> orth, job registry monitor thread stop");
    }

    /**
     * Wraps a runnable to catch and log exceptions, preventing {@link ScheduledExecutorService}
     * from silently cancelling future executions on uncaught exceptions.
     */
    private static Runnable safeRunnable(String taskName, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable e) {
                logger.error("Scheduled task '{}' threw exception", taskName, e);
            }
        };
    }

    // ---------------------- tool ----------------------

    /**
     * Registers executor heartbeat asynchronously.
     *
     * @param registryParam registration request with group, key (appname), and value (address)
     * @return success response
     */
    public Response<String> registry(RegistryRequest registryParam) {

        // valid
        if (StringTool.isBlank(registryParam.getRegistryGroup())
                || StringTool.isBlank(registryParam.getRegistryKey())
                || StringTool.isBlank(registryParam.getRegistryValue())) {
            return Response.ofFail("Illegal Argument.");
        }

        // async execute
        registryOrRemoveThreadPool.execute(
                () -> {
                    // 0-fail; 1-save suc; 2-update suc;
                    int ret =
                            OrthAdminBootstrap.getInstance()
                                    .getJobRegistryMapper()
                                    .registrySaveOrUpdate(
                                            registryParam.getRegistryGroup(),
                                            registryParam.getRegistryKey(),
                                            registryParam.getRegistryValue(),
                                            new Date());
                    if (ret == 1) {
                        // fresh (add)
                        freshGroupRegistryInfo(registryParam);
                    }
                });

        return Response.ofSuccess();
    }

    /**
     * Removes executor registration asynchronously.
     *
     * @param registryParam registration request with group, key (appname), and value (address)
     * @return success response
     */
    public Response<String> registryRemove(RegistryRequest registryParam) {

        // valid
        if (StringTool.isBlank(registryParam.getRegistryGroup())
                || StringTool.isBlank(registryParam.getRegistryKey())
                || StringTool.isBlank(registryParam.getRegistryValue())) {
            return Response.ofFail("Illegal Argument.");
        }

        // async execute
        registryOrRemoveThreadPool.execute(
                () -> {
                    int ret =
                            OrthAdminBootstrap.getInstance()
                                    .getJobRegistryMapper()
                                    .registryDelete(
                                            registryParam.getRegistryGroup(),
                                            registryParam.getRegistryKey(),
                                            registryParam.getRegistryValue());
                    if (ret > 0) {
                        // fresh (delete)
                        freshGroupRegistryInfo(registryParam);
                    }
                });

        return Response.ofSuccess();
    }

    /**
     * Refreshes group registry information after registration changes.
     *
     * <p>Placeholder method for future optimization - currently disabled to prevent affecting core
     * tables during high-frequency registration updates.
     *
     * @param registryParam the registration request that triggered the refresh
     */
    private void freshGroupRegistryInfo(RegistryRequest registryParam) {
        // Under consideration, prevent affecting core tables
    }
}
