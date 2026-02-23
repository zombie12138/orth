package com.abyss.orth.core.thread;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abyss.orth.core.constant.Const;
import com.abyss.orth.core.constant.RegistType;
import com.abyss.orth.core.executor.OrthJobExecutor;
import com.abyss.orth.core.openapi.AdminBiz;
import com.abyss.orth.core.openapi.model.RegistryRequest;
import com.xxl.tool.response.Response;

/**
 * Background thread for executor registration with admin scheduler.
 *
 * <p>Sends periodic heartbeat registrations (every 30 seconds) to all admin endpoints. On shutdown,
 * sends deregistration request to remove executor from service discovery.
 */
public class ExecutorRegistryThread {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorRegistryThread.class);
    private static final ExecutorRegistryThread instance = new ExecutorRegistryThread();

    public static ExecutorRegistryThread getInstance() {
        return instance;
    }

    private ScheduledExecutorService heartbeatScheduler;
    private String appname;
    private String address;

    /**
     * Starts the executor registry heartbeat scheduler.
     *
     * @param appname executor application name (e.g., "orth-executor-1")
     * @param address executor address (e.g., "http://192.168.1.100:9999/")
     */
    public void start(String appname, String address) {
        // Validate parameters
        if (appname == null || appname.trim().isEmpty()) {
            logger.warn("Registry thread not started: appname is null or empty");
            return;
        }
        if (OrthJobExecutor.getAdminBizList() == null) {
            logger.warn("Registry thread not started: admin addresses not configured");
            return;
        }

        this.appname = appname;
        this.address = address;

        // Start heartbeat scheduler
        heartbeatScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "orth-registry-thread");
                            t.setDaemon(true);
                            return t;
                        });
        heartbeatScheduler.scheduleWithFixedDelay(
                safeRunnable("registry-heartbeat", this::sendHeartbeat),
                0,
                Const.BEAT_TIMEOUT,
                TimeUnit.SECONDS);
    }

    /** Sends a single heartbeat registration to admin. */
    private void sendHeartbeat() {
        sendRegistration(appname, address);
    }

    /** Sends registration heartbeat to all admin endpoints. */
    private void sendRegistration(String appname, String address) {
        RegistryRequest request = new RegistryRequest(RegistType.EXECUTOR.name(), appname, address);

        for (AdminBiz adminBiz : OrthJobExecutor.getAdminBizList()) {
            try {
                Response<String> response = adminBiz.registry(request);
                if (response != null && response.isSuccess()) {
                    logger.debug("Registry heartbeat success: {}", request);
                    break; // Success - no need to try other admins
                } else {
                    logger.info("Registry heartbeat failed: {}, response: {}", request, response);
                }
            } catch (Throwable e) {
                logger.info("Registry heartbeat error: {}", request, e);
            }
        }
    }

    /** Sends deregistration request to all admin endpoints. */
    private void sendDeregistration(String appname, String address) {
        RegistryRequest request = new RegistryRequest(RegistType.EXECUTOR.name(), appname, address);

        for (AdminBiz adminBiz : OrthJobExecutor.getAdminBizList()) {
            try {
                Response<String> response = adminBiz.registryRemove(request);
                if (response != null && response.isSuccess()) {
                    logger.info("Deregistration success: {}", request);
                    break; // Success - no need to try other admins
                } else {
                    logger.info("Deregistration failed: {}, response: {}", request, response);
                }
            } catch (Throwable e) {
                logger.info("Deregistration error: {}", request, e);
            }
        }
    }

    /**
     * Stops the registry heartbeat scheduler and sends deregistration.
     *
     * <p>Shutdown sequence: stop scheduler first to prevent new heartbeats, then synchronously send
     * deregistration so admin removes this executor from service discovery.
     */
    public void toStop() {
        if (heartbeatScheduler == null) {
            return;
        }

        // Stop scheduler first
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Then deregister synchronously
        sendDeregistration(appname, address);
        logger.info("Orth registry thread stopped");
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
}
