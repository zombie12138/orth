package com.xxl.job.admin.scheduler.type.strategy;

import java.util.Date;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.scheduler.cron.CronExpression;
import com.xxl.job.admin.scheduler.type.ScheduleType;

/**
 * Cron-based scheduling strategy for the Orth scheduler.
 *
 * <p>This strategy uses standard Unix cron expressions to define job schedules.
 *
 * @author xuxueli 2020-10-29
 */
public class CronScheduleType extends ScheduleType {

    @Override
    public Date generateNextTriggerTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        return new CronExpression(jobInfo.getScheduleConf()).getNextValidTimeAfter(fromTime);
    }
}
