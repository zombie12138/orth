package com.xxl.job.admin.scheduler.thread;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.job.admin.scheduler.config.XxlJobAdminBootstrap;
import com.xxl.job.admin.scheduler.trigger.TriggerTypeEnum;

/**
 * Job trigger thread pool helper with adaptive fast/slow pool routing.
 *
 * <p>Manages two thread pools for job triggering:
 *
 * <ul>
 *   <li><b>Fast Pool</b>: Default pool for normal jobs (200 threads, 2000 queue)
 *   <li><b>Slow Pool</b>: Dedicated pool for long-running jobs (100 threads, 5000 queue)
 * </ul>
 *
 * <p><b>Adaptive Routing Algorithm</b>:
 *
 * <ul>
 *   <li>Tracks job timeout counts (execution > 500ms) per minute
 *   <li>Jobs with 10+ timeouts in 1 minute are routed to slow pool
 *   <li>Prevents slow jobs from blocking fast jobs
 * </ul>
 *
 * @author xuxueli 2018-07-03 21:08:07
 */
public class JobTriggerPoolHelper {
    private static final Logger logger = LoggerFactory.getLogger(JobTriggerPoolHelper.class);

    // Thread pool configuration constants
    private static final int CORE_POOL_SIZE = 10;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int FAST_POOL_QUEUE_SIZE = 2000;
    private static final int SLOW_POOL_QUEUE_SIZE = 5000;

    // Timeout tracking constants
    private static final int SLOW_POOL_TIMEOUT_THRESHOLD = 10; // 10 timeouts per minute
    private static final long TRIGGER_TIMEOUT_MS = 500; // 500ms threshold
    private static final long MS_TO_MIN = 60000; // Milliseconds to minutes conversion

    // fast/slow thread pool
    private ThreadPoolExecutor fastTriggerPool = null;
    private ThreadPoolExecutor slowTriggerPool = null;

    /**
     * Start the fast and slow trigger thread pools.
     *
     * <p>Pool configurations:
     *
     * <ul>
     *   <li>Fast pool: core=10, max=configurable (default 200), queue=2000
     *   <li>Slow pool: core=10, max=configurable (default 100), queue=5000
     * </ul>
     */
    public void start() {
        fastTriggerPool =
                new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        XxlJobAdminBootstrap.getInstance().getTriggerPoolFastMax(),
                        KEEP_ALIVE_SECONDS,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(FAST_POOL_QUEUE_SIZE),
                        r ->
                                new Thread(
                                        r,
                                        "orth, admin JobTriggerPoolHelper-fastTriggerPool-"
                                                + r.hashCode()),
                        (r, executor) ->
                                logger.error(
                                        ">>>>>>>>>>> orth, admin JobTriggerPoolHelper-fastTriggerPool execute too fast, Runnable={}",
                                        r.toString()));

        slowTriggerPool =
                new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        XxlJobAdminBootstrap.getInstance().getTriggerPoolSlowMax(),
                        KEEP_ALIVE_SECONDS,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(SLOW_POOL_QUEUE_SIZE),
                        r ->
                                new Thread(
                                        r,
                                        "orth, admin JobTriggerPoolHelper-slowTriggerPool-"
                                                + r.hashCode()),
                        (r, executor) ->
                                logger.error(
                                        ">>>>>>>>>>> orth, admin JobTriggerPoolHelper-slowTriggerPool execute too fast, Runnable={}",
                                        r.toString()));
    }

    /**
     * Stop both trigger thread pools immediately.
     *
     * <p>Uses shutdownNow() to attempt to stop all actively executing tasks.
     */
    public void stop() {
        fastTriggerPool.shutdownNow();
        slowTriggerPool.shutdownNow();
        logger.info(">>>>>>>>> orth trigger thread pool shutdown success.");
    }

    // job timeout count tracking (per minute)
    private volatile long currentMinute = System.currentTimeMillis() / MS_TO_MIN;
    private volatile ConcurrentMap<Integer, AtomicInteger> jobTimeoutCountMap =
            new ConcurrentHashMap<>();

    // ---------------------- tool ----------------------

    /**
     * Trigger a job execution with adaptive pool routing.
     *
     * <p>Jobs are routed to fast or slow pool based on recent timeout history. Jobs with 10+
     * timeouts (>500ms) in the current minute are sent to the slow pool to prevent blocking fast
     * jobs.
     *
     * @param jobId job ID
     * @param triggerType trigger type (CRON, MANUAL, API, etc.)
     * @param failRetryCount retry count (>=0: use this value, <0: use job config)
     * @param executorShardingParam sharding parameters for distributed execution
     * @param executorParam execution parameters (null: use job param, not null: override job param)
     * @param addressList executor address list (null: auto-discover from group)
     * @param scheduleTime theoretical schedule time in milliseconds (null for manual/API triggers)
     */
    public void trigger(
            final int jobId,
            final TriggerTypeEnum triggerType,
            final int failRetryCount,
            final String executorShardingParam,
            final String executorParam,
            final String addressList,
            final Long scheduleTime) {

        // choose thread pool based on recent timeout history
        ThreadPoolExecutor selectedTriggerPool = fastTriggerPool;
        AtomicInteger jobTimeoutCount = jobTimeoutCountMap.get(jobId);
        if (jobTimeoutCount != null && jobTimeoutCount.get() > SLOW_POOL_TIMEOUT_THRESHOLD) {
            selectedTriggerPool = slowTriggerPool;
        }

        // trigger execution in selected pool
        selectedTriggerPool.execute(
                new Runnable() {
                    @Override
                    public void run() {

                        long start = System.currentTimeMillis();

                        try {
                            // execute trigger
                            XxlJobAdminBootstrap.getInstance()
                                    .getJobTrigger()
                                    .trigger(
                                            jobId,
                                            triggerType,
                                            failRetryCount,
                                            executorShardingParam,
                                            executorParam,
                                            addressList,
                                            scheduleTime);
                        } catch (Throwable e) {
                            logger.error(e.getMessage(), e);
                        } finally {

                            // reset timeout count map every minute
                            long nowMinute = System.currentTimeMillis() / MS_TO_MIN;
                            if (currentMinute != nowMinute) {
                                currentMinute = nowMinute;
                                jobTimeoutCountMap.clear();
                            }

                            // increment timeout count if execution exceeded threshold
                            long cost = System.currentTimeMillis() - start;
                            if (cost > TRIGGER_TIMEOUT_MS) {
                                AtomicInteger timeoutCount =
                                        jobTimeoutCountMap.putIfAbsent(jobId, new AtomicInteger(1));
                                if (timeoutCount != null) {
                                    timeoutCount.incrementAndGet();
                                }
                            }
                        }
                    }

                    @Override
                    public String toString() {
                        return "Job Runnable, jobId:" + jobId;
                    }
                });
    }
}
