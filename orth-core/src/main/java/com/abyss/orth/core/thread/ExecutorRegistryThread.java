package com.abyss.orth.core.thread;

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

    private Thread registryThread;
    private volatile boolean toStop = false;

    /**
     * Starts the executor registry thread.
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

        // Start registry thread
        registryThread = new Thread(() -> runRegistryLoop(appname, address));
        registryThread.setDaemon(true);
        registryThread.setName("orth-registry-thread");
        registryThread.start();
    }

    /** Main registry loop: sends periodic heartbeats and deregisters on shutdown. */
    private void runRegistryLoop(String appname, String address) {
        // Periodic registration heartbeat
        while (!toStop) {
            try {
                sendRegistration(appname, address);
            } catch (Throwable e) {
                if (!toStop) {
                    logger.error("Registry heartbeat error", e);
                }
            }

            // Sleep between heartbeats
            try {
                if (!toStop) {
                    TimeUnit.SECONDS.sleep(Const.BEAT_TIMEOUT);
                }
            } catch (InterruptedException e) {
                if (!toStop) {
                    logger.warn("Registry sleep interrupted: {}", e.getMessage());
                }
                Thread.currentThread().interrupt();
            }
        }

        // Deregister on shutdown
        sendDeregistration(appname, address);

        logger.info("Orth registry thread stopped");
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
                if (!toStop) {
                    logger.info("Deregistration error: {}", request, e);
                }
            }
        }
    }

    /** Stops the registry thread gracefully. */
    public void toStop() {
        toStop = true;

        if (registryThread != null) {
            registryThread.interrupt();
            try {
                registryThread.join();
            } catch (InterruptedException e) {
                logger.error("Registry thread join interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
