package com.xxl.job.admin.scheduler.type.strategy;

import java.util.Date;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.job.admin.scheduler.type.ScheduleType;

public class NoneScheduleType extends ScheduleType {

    @Override
    public Date generateNextTriggerTime(XxlJobInfo jobInfo, Date fromTime) throws Exception {
        // generate none trigger-time
        return null;
    }
}
