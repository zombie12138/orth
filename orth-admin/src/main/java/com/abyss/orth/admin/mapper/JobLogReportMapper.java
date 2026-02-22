package com.abyss.orth.admin.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.abyss.orth.admin.model.JobLogReport;

/**
 * MyBatis mapper for job log report operations.
 *
 * <p>Provides aggregated statistics and metrics for job execution monitoring and dashboard
 * displays.
 */
@Mapper
public interface JobLogReportMapper {

    /** Insert or update daily log report statistics (upsert). */
    int saveOrUpdate(JobLogReport orthJobLogReport);

    /** Query log reports for a date range. */
    List<JobLogReport> queryLogReport(
            @Param("triggerDayFrom") Date triggerDayFrom, @Param("triggerDayTo") Date triggerDayTo);

    /** Query total aggregated statistics across all days. */
    JobLogReport queryLogReportTotal();
}
