package com.xxl.job.admin.scheduler.type.strategy;

import java.util.Date;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.scheduler.type.ScheduleType;

public class FixRateScheduleType extends ScheduleType {

    @Override
    public Date generateNextTriggerTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        // generate next trigger time, fix rate delay
        return new Date(fromTime.getTime() + Long.parseLong(jobInfo.getScheduleConf()) * 1000L);
    }
}
