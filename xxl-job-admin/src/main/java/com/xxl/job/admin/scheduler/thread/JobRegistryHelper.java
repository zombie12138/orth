package com.xxl.job.admin.scheduler.thread;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.admin.model.XxlJobGroup;
import com.xxl.job.admin.model.XxlJobRegistry;
import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.core.constant.Const;
import com.xxl.job.core.constant.RegistType;
import com.xxl.job.core.openapi.model.RegistryRequest;
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
    private Thread registryMonitorThread;
    private volatile boolean toStop = false;

    /**
     * Starts the registry helper with thread pool and monitor thread.
     *
     * <p>Initializes:
     *
     * <ul>
     *   <li>Registry thread pool for async registration/removal requests
     *   <li>Monitor thread for cleaning stale entries and refreshing group addresses
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
        registryMonitorThread =
                new Thread(
                        () -> {
                            while (!toStop) {
                                try {
                                    // auto registry group
                                    List<XxlJobGroup> groupList =
                                            XxlJobAdminBootstrap.getInstance()
                                                    .getXxlJobGroupMapper()
                                                    .findByAddressType(0);
                                    if (groupList == null || groupList.isEmpty()) {
                                        continue;
                                    }

                                    // remove dead address (admin/executor)
                                    List<Integer> ids =
                                            XxlJobAdminBootstrap.getInstance()
                                                    .getXxlJobRegistryMapper()
                                                    .findDead(Const.DEAD_TIMEOUT, new Date());
                                    if (ids != null && !ids.isEmpty()) {
                                        XxlJobAdminBootstrap.getInstance()
                                                .getXxlJobRegistryMapper()
                                                .removeDead(ids);
                                    }

                                    // fresh online address (admin/executor)
                                    List<XxlJobRegistry> list =
                                            XxlJobAdminBootstrap.getInstance()
                                                    .getXxlJobRegistryMapper()
                                                    .findAll(Const.DEAD_TIMEOUT, new Date());
                                    if (list == null) {
                                        continue;
                                    }

                                    Map<String, List<String>> appAddressMap =
                                            list.stream()
                                                    .filter(
                                                            item ->
                                                                    RegistType.EXECUTOR
                                                                            .name()
                                                                            .equals(
                                                                                    item
                                                                                            .getRegistryGroup()))
                                                    .collect(
                                                            Collectors.groupingBy(
                                                                    XxlJobRegistry::getRegistryKey,
                                                                    Collectors.mapping(
                                                                            XxlJobRegistry
                                                                                    ::getRegistryValue,
                                                                            Collectors.toList())));

                                    // fresh group address
                                    for (XxlJobGroup group : groupList) {
                                        List<String> registryList =
                                                appAddressMap.get(group.getAppname());
                                        String addressListStr = null;
                                        if (registryList != null && !registryList.isEmpty()) {
                                            Collections.sort(registryList);
                                            addressListStr = String.join(",", registryList);
                                        }
                                        group.setAddressList(addressListStr);
                                        group.setUpdateTime(new Date());

                                        XxlJobAdminBootstrap.getInstance()
                                                .getXxlJobGroupMapper()
                                                .update(group);
                                    }
                                } catch (Throwable e) {
                                    if (!toStop) {
                                        logger.error(
                                                ">>>>>>>>>>> orth, job registry monitor thread error:{}",
                                                e);
                                    }
                                }
                                try {
                                    TimeUnit.SECONDS.sleep(Const.BEAT_TIMEOUT);
                                } catch (Throwable e) {
                                    if (!toStop) {
                                        logger.error(
                                                ">>>>>>>>>>> orth, job registry monitor thread error:{}",
                                                e);
                                    }
                                }
                            }
                            logger.info(">>>>>>>>>>> orth, job registry monitor thread stop");
                        });
        registryMonitorThread.setDaemon(true);
        registryMonitorThread.setName("orth, admin JobRegistryMonitorHelper-registryMonitorThread");
        registryMonitorThread.start();
    }

    /**
     * Stops the registry helper gracefully.
     *
     * <p>Shuts down the registry thread pool and interrupts the monitor thread, waiting for it to
     * finish.
     */
    public void stop() {
        toStop = true;

        // stop registryOrRemoveThreadPool
        registryOrRemoveThreadPool.shutdownNow();

        // stop monitor (interrupt and wait)
        registryMonitorThread.interrupt();
        try {
            registryMonitorThread.join();
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
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
                            XxlJobAdminBootstrap.getInstance()
                                    .getXxlJobRegistryMapper()
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
                            XxlJobAdminBootstrap.getInstance()
                                    .getXxlJobRegistryMapper()
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
