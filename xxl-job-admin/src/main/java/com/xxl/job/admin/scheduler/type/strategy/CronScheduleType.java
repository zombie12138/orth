package com.xxl.job.admin.scheduler.type.strategy;

import java.util.Date;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.scheduler.cron.CronExpression;
import com.xxl.job.admin.scheduler.type.ScheduleType;

public class CronScheduleType extends ScheduleType {

    @Override
    public Date generateNextTriggerTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        // generate next trigger time, with cron
        return new CronExpression(jobInfo.getScheduleConf()).getNextValidTimeAfter(fromTime);
    }
}
