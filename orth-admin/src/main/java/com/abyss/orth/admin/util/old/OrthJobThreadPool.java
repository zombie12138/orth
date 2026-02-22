// package com.abyss.orth.admin.core.quartz;
//
// import org.quartz.SchedulerConfigException;
// import org.quartz.spi.ThreadPool;
//
/// **
// * Single-thread pool implementation for Quartz async trigger execution.
// *
// * @author xuxueli 2019-03-06
// * @deprecated This Quartz thread pool is deprecated. Use the dual-pool architecture in
// *             {@link com.abyss.orth.admin.scheduler.thread.JobTriggerPoolHelper} instead.
// *             Migration: Replace with JobTriggerPoolHelper which provides:
// *             - Fast pool: 200 threads + 2000 queue for quick jobs
// *             - Slow pool: 100 threads + 5000 queue for long-running jobs
// *             - Adaptive routing: Jobs with 10+ timeouts (>500ms) in 1 minute move to slow pool
// *             Orth uses a sophisticated dual-pool design to prevent head-of-line blocking
// *             and ensure high-priority jobs aren't delayed by slow executions.
// */
// @Deprecated
// public class OrthJobThreadPool implements ThreadPool {
//
//    @Override
//    public boolean runInThread(Runnable runnable) {
//
//        // async run
//        runnable.run();
//        return true;
//
//        //return false;
//    }
//
//    @Override
//    public int blockForAvailableThreads() {
//        return 1;
//    }
//
//    @Override
//    public void initialize() throws SchedulerConfigException {
//
//    }
//
//    @Override
//    public void shutdown(boolean waitForJobsToComplete) {
//
//    }
//
//    @Override
//    public int getPoolSize() {
//        return 1;
//    }
//
//    @Override
//    public void setInstanceId(String schedInstId) {
//
//    }
//
//    @Override
//    public void setInstanceName(String schedName) {
//
//    }
//
//    // support
//    public void setThreadCount(int count) {
//        //
//    }
//
// }
