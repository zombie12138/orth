package com.xxl.job.admin.scheduler.alarm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.model.XxlJobLog;
import com.xxl.tool.core.CollectionTool;
import com.xxl.tool.core.MapTool;

/**
 * Central alarm coordinator for the Orth scheduler.
 *
 * <p>This component manages multiple alarm notification channels and invokes them when job failures
 * occur. It auto-discovers all {@link JobAlarm} bean implementations in the Spring context and
 * coordinates their execution.
 *
 * <p>Alarm delivery model:
 *
 * <ul>
 *   <li>Multiple alarm channels can be configured simultaneously (email, webhook, SMS, etc.)
 *   <li>All channels are invoked in sequence for each failure
 *   <li>Failure of one channel doesn't prevent others from executing
 *   <li>Overall success requires all channels to succeed (AND logic)
 * </ul>
 *
 * <p>This class is thread-safe and designed to be called from the alarm monitoring background
 * thread ({@code JobFailAlarmMonitorHelper}).
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>Spring initializes the component and injects ApplicationContext
 *   <li>{@code afterPropertiesSet()} discovers all JobAlarm beans
 *   <li>{@code alarm()} is called by monitoring thread on each failure
 * </ol>
 *
 * @author xuxueli 2017-07-13
 */
@Component
public class JobAlarmer implements ApplicationContextAware, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(JobAlarmer.class);

    private ApplicationContext applicationContext;
    private List<JobAlarm> jobAlarmList;

    /**
     * Receives the Spring ApplicationContext for bean discovery.
     *
     * <p>Called automatically by Spring during component initialization.
     *
     * @param applicationContext the Spring application context
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Discovers and registers all JobAlarm implementations.
     *
     * <p>Called automatically by Spring after properties are set. Scans the context for all beans
     * implementing the {@link JobAlarm} interface and registers them for alarm delivery.
     *
     * <p>If no alarm beans are found, the list remains null and no alarms will be sent.
     *
     * @throws Exception if bean discovery fails
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, JobAlarm> alarmBeans = applicationContext.getBeansOfType(JobAlarm.class);

        if (MapTool.isNotEmpty(alarmBeans)) {
            jobAlarmList = new ArrayList<>(alarmBeans.values());
            logger.info(
                    "Orth scheduler initialized {} alarm channel(s): {}",
                    jobAlarmList.size(),
                    alarmBeans.keySet());
        } else {
            logger.warn("No alarm channels configured; failures will not generate notifications");
        }
    }

    /**
     * Sends alarm notifications through all configured channels.
     *
     * <p>This method is the primary entry point called by the alarm monitoring thread when a job
     * failure is detected. It invokes all registered alarm implementations in sequence.
     *
     * <p>Failure handling:
     *
     * <ul>
     *   <li>Each alarm channel is invoked independently
     *   <li>Exceptions are caught and logged; they don't prevent other channels from executing
     *   <li>Overall result is true only if ALL channels succeed
     * </ul>
     *
     * <p>If no alarm channels are configured, returns false (no-op).
     *
     * @param info the job configuration including alarm recipients
     * @param jobLog the execution log containing failure details
     * @return true if all alarm channels succeeded, false if any failed or no channels configured
     */
    public boolean alarm(XxlJobInfo info, XxlJobLog jobLog) {
        // Guard clause: no alarm channels configured
        if (CollectionTool.isEmpty(jobAlarmList)) {
            return false;
        }

        boolean allSucceeded = true;

        // Invoke all alarm channels
        for (var alarm : jobAlarmList) {
            try {
                boolean channelSucceeded = alarm.doAlarm(info, jobLog);
                if (!channelSucceeded) {
                    allSucceeded = false;
                    logger.warn(
                            "Alarm channel {} failed for job {} log {}",
                            alarm.getClass().getSimpleName(),
                            info.getId(),
                            jobLog.getId());
                }
            } catch (Exception e) {
                allSucceeded = false;
                logger.error(
                        "Alarm channel {} threw exception for job {} log {}: {}",
                        alarm.getClass().getSimpleName(),
                        info.getId(),
                        jobLog.getId(),
                        e.getMessage(),
                        e);
            }
        }

        return allSucceeded;
    }
}
