// package com.xxl.job.admin.core.jobbean;
//
// import com.xxl.job.admin.core.thread.JobTriggerPoolHelper;
// import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
// import org.quartz.JobExecutionContext;
// import org.quartz.JobExecutionException;
// import org.quartz.JobKey;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.scheduling.quartz.QuartzJobBean;
//
/// **
// * Quartz job bean for triggering remote HTTP executors.
// * Note: "@DisallowConcurrentExecution" disabled - thread pool size should be configured
// * appropriately for concurrent execution.
// *
// * @author xuxueli 2015-12-17 18:20:34
// * @deprecated This Quartz-based job bean is deprecated. Use the time-ring scheduler in
// *             {@link com.xxl.job.admin.scheduler.thread.JobScheduleHelper} instead.
// *             Migration: Remove Quartz dependency. Job scheduling is now handled by
// *             JobScheduleHelper with a 60-slot time ring and pre-read window.
// *             Triggering is managed by {@link
// com.xxl.job.admin.scheduler.thread.JobTriggerPoolHelper}.
// *             Orth replaced Quartz with a custom time-ring algorithm for better performance
// *             and control in high-throughput scenarios.
// */
// @Deprecated
//// @DisallowConcurrentExecution
// public class RemoteHttpJobBean extends QuartzJobBean {
//	private static Logger logger = LoggerFactory.getLogger(RemoteHttpJobBean.class);
//
//	@Override
//	protected void executeInternal(JobExecutionContext context)
//			throws JobExecutionException {
//
//		// load jobId
//		JobKey jobKey = context.getTrigger().getJobKey();
//		Integer jobId = Integer.valueOf(jobKey.getName());
//
//
//	}
//
// }
