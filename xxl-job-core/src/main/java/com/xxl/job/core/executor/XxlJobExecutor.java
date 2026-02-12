package com.xxl.job.core.executor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.core.constant.Const;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.openapi.AdminBiz;
import com.xxl.job.core.server.EmbedServer;
import com.xxl.job.core.thread.JobLogFileCleanThread;
import com.xxl.job.core.thread.JobThread;
import com.xxl.job.core.thread.TriggerCallbackThread;
import com.xxl.tool.core.StringTool;
import com.xxl.tool.http.HttpTool;
import com.xxl.tool.http.IPTool;

/**
 * Base executor framework for Orth job scheduling.
 *
 * <p>Manages the lifecycle of the executor: starts embedded Netty server, initializes admin
 * clients, handles job registration, and maintains job thread pool. Subclasses should implement
 * bean scanning for job handler discovery.
 */
public class XxlJobExecutor {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobExecutor.class);

    // ---------------------- Configuration Fields ----------------------
    private String adminAddresses;
    private String accessToken;
    private int timeout;
    private String appname;
    private String address;
    private String ip;
    private int port;
    private String logPath;
    private int logRetentionDays;

    public void setAdminAddresses(String adminAddresses) {
        this.adminAddresses = adminAddresses;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    // ---------------------- Lifecycle Management ----------------------

    /**
     * Starts the executor framework.
     *
     * <p>Initializes: 1. Log file system 2. Admin RPC clients 3. Log cleanup thread 4. Callback
     * thread 5. Embedded Netty server for receiving triggers
     *
     * @throws Exception if initialization fails
     */
    public void start() throws Exception {
        // Initialize log file system
        XxlJobFileAppender.initLogPath(logPath);

        // Initialize admin RPC clients
        initAdminBizList(adminAddresses, accessToken, timeout);

        // Start log cleanup background thread
        JobLogFileCleanThread.getInstance().start(logRetentionDays);

        // Start callback background thread
        TriggerCallbackThread.getInstance().start();

        // Start embedded Netty server
        initEmbedServer(address, ip, port, appname, accessToken);
    }

    /**
     * Destroys the executor framework.
     *
     * <p>Gracefully shuts down: 1. Embedded Netty server 2. All running job threads 3. Background
     * threads (log cleanup, callback) 4. Handler registries
     */
    public void destroy() {
        // Stop embedded Netty server
        stopEmbedServer();

        // Stop all running job threads
        if (!jobThreadRepository.isEmpty()) {
            for (Map.Entry<Integer, JobThread> entry : jobThreadRepository.entrySet()) {
                JobThread jobThread =
                        removeJobThread(entry.getKey(), "Executor shutdown - terminating job");
                // Wait for job thread to push result to callback queue
                if (jobThread != null) {
                    try {
                        jobThread.join();
                    } catch (InterruptedException e) {
                        logger.error(
                                "Orth job thread join interrupted during shutdown, jobId: {}",
                                entry.getKey(),
                                e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
            jobThreadRepository.clear();
        }
        jobHandlerRepository.clear();

        // Stop background threads
        JobLogFileCleanThread.getInstance().toStop();
        TriggerCallbackThread.getInstance().toStop();
    }

    // ---------------------- admin-client (rpc invoker) ----------------------
    private static List<AdminBiz> adminBizList;

    private void initAdminBizList(String adminAddresses, String accessToken, int timeout)
            throws Exception {
        if (StringTool.isNotBlank(adminAddresses)) {
            for (String address : adminAddresses.trim().split(",")) {
                if (StringTool.isNotBlank(address)) {

                    // valid
                    String finalAddress = address.trim();
                    finalAddress =
                            finalAddress.endsWith("/")
                                    ? (finalAddress + "api")
                                    : (finalAddress + "/api");
                    if (!(this.timeout >= 1 && this.timeout <= 10)) {
                        this.timeout = 3;
                    }

                    // Build admin RPC client
                    AdminBiz adminBiz =
                            HttpTool.createClient()
                                    .url(finalAddress)
                                    .timeout(timeout * 1000)
                                    .header(Const.ORTH_ACCESS_TOKEN, accessToken)
                                    .proxy(AdminBiz.class);

                    // Add to registry
                    if (adminBizList == null) {
                        adminBizList = new ArrayList<>();
                    }
                    adminBizList.add(adminBiz);
                }
            }
        }
    }

    public static List<AdminBiz> getAdminBizList() {
        return adminBizList;
    }

    // ---------------------- executor-server (rpc provider) ----------------------
    private EmbedServer embedServer = null;

    private void initEmbedServer(
            String address, String ip, int port, String appname, String accessToken)
            throws Exception {

        // Auto-detect IP and port if not configured
        port = port > 0 ? port : IPTool.getAvailablePort(9999);
        ip = StringTool.isNotBlank(ip) ? ip : IPTool.getIp();

        // Generate registry address (use ip:port if address not explicitly configured)
        if (StringTool.isBlank(address)) {
            String ipPortAddress = IPTool.toAddressString(ip, port);
            address = String.format("http://%s/", ipPortAddress);
        }

        // Validate access token for security
        if (StringTool.isBlank(accessToken)) {
            logger.warn(
                    "Orth accessToken is empty. To ensure system security, please set the accessToken.");
        }

        // start
        embedServer = new EmbedServer();
        embedServer.start(address, port, appname, accessToken);
    }

    private void stopEmbedServer() {
        // stop provider factory
        if (embedServer != null) {
            try {
                embedServer.stop();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    // ---------------------- Job Handler Repository ----------------------

    /** Registry of job handlers by name (handler name -> handler instance) */
    private static final ConcurrentMap<String, IJobHandler> jobHandlerRepository =
            new ConcurrentHashMap<>();

    /**
     * Loads a registered job handler by name.
     *
     * @param name handler name
     * @return handler instance, or null if not registered
     */
    public static IJobHandler loadJobHandler(String name) {
        return jobHandlerRepository.get(name);
    }

    /**
     * Registers a job handler with the specified name.
     *
     * @param name handler name (must be unique)
     * @param jobHandler handler instance
     * @return previous handler with the same name, or null
     */
    public static IJobHandler registryJobHandler(String name, IJobHandler jobHandler) {
        logger.info("Registered Orth job handler: name={}, handler={}", name, jobHandler);
        return jobHandlerRepository.put(name, jobHandler);
    }

    /**
     * Registers a method-based job handler from an @XxlJob annotated method.
     *
     * @param xxlJob the @XxlJob annotation
     * @param bean the bean instance containing the method
     * @param executeMethod the method to execute for this job
     * @throws RuntimeException if handler name is invalid or conflicts with existing handler
     */
    protected void registryJobHandler(XxlJob xxlJob, Object bean, Method executeMethod) {
        if (xxlJob == null) {
            return;
        }

        String name = xxlJob.value();
        Class<?> clazz = bean.getClass();
        String methodName = executeMethod.getName();

        // Validate handler name
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Orth job handler name cannot be empty for [%s#%s]",
                            clazz.getName(), methodName));
        }

        // Check for naming conflicts
        if (loadJobHandler(name) != null) {
            throw new IllegalStateException(
                    String.format(
                            "Orth job handler name conflict: '%s' is already registered", name));
        }

        executeMethod.setAccessible(true);

        // Resolve optional init/destroy lifecycle methods
        Method initMethod = resolveLifecycleMethod(clazz, xxlJob.init(), "init", methodName);
        Method destroyMethod =
                resolveLifecycleMethod(clazz, xxlJob.destroy(), "destroy", methodName);

        // Register the method handler
        registryJobHandler(
                name, new MethodJobHandler(bean, executeMethod, initMethod, destroyMethod));
    }

    /**
     * Resolves a lifecycle method (init or destroy) by name.
     *
     * @param clazz the class containing the method
     * @param methodName the method name from annotation
     * @param lifecycleType the lifecycle type ("init" or "destroy") for error messages
     * @param jobMethodName the job method name for error messages
     * @return the resolved method, or null if not specified
     * @throws IllegalArgumentException if the method is specified but not found
     */
    private Method resolveLifecycleMethod(
            Class<?> clazz, String methodName, String lifecycleType, String jobMethodName) {
        if (methodName.trim().isEmpty()) {
            return null;
        }

        try {
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Orth job %s method '%s' not found for [%s#%s]",
                            lifecycleType, methodName, clazz.getName(), jobMethodName));
        }
    }

    // ---------------------- Job Thread Repository ----------------------

    /** Registry of active job threads by job ID */
    private static final ConcurrentMap<Integer, JobThread> jobThreadRepository =
            new ConcurrentHashMap<>();

    /**
     * Registers a new job thread for the specified job.
     *
     * <p>If a thread already exists for this job, it will be stopped and replaced.
     *
     * @param jobId the job ID
     * @param handler the job handler to execute
     * @param removeOldReason reason for stopping old thread (if exists)
     * @return the newly registered job thread
     */
    public static JobThread registJobThread(
            int jobId, IJobHandler handler, String removeOldReason) {
        JobThread newJobThread = new JobThread(jobId, handler);
        newJobThread.start();
        logger.info("Registered Orth job thread: jobId={}, handler={}", jobId, handler);

        // Replace old thread if exists (ConcurrentHashMap.put returns previous value)
        JobThread oldJobThread = jobThreadRepository.put(jobId, newJobThread);
        if (oldJobThread != null) {
            oldJobThread.toStop(removeOldReason);
            oldJobThread.interrupt();
        }

        return newJobThread;
    }

    /**
     * Removes and stops the job thread for the specified job.
     *
     * @param jobId the job ID
     * @param removeOldReason reason for stopping the thread
     * @return the removed job thread, or null if not found
     */
    public static JobThread removeJobThread(int jobId, String removeOldReason) {
        JobThread jobThread = jobThreadRepository.remove(jobId);
        if (jobThread != null) {
            jobThread.toStop(removeOldReason);
            jobThread.interrupt();
        }
        return jobThread;
    }

    /**
     * Loads the active job thread for the specified job.
     *
     * @param jobId the job ID
     * @return the job thread, or null if not found
     */
    public static JobThread loadJobThread(int jobId) {
        return jobThreadRepository.get(jobId);
    }
}
