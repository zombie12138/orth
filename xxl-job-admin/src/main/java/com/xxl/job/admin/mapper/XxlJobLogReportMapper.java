package com.xxl.job.admin.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xxl.job.admin.model.XxlJobLogReport;

/**
 * MyBatis mapper for job log report operations.
 *
 * <p>Provides aggregated statistics and metrics for job execution monitoring and dashboard
 * displays.
 */
@Mapper
public interface XxlJobLogReportMapper {

    /** Insert or update daily log report statistics (upsert). */
    int saveOrUpdate(XxlJobLogReport xxlJobLogReport);

    /** Query log reports for a date range. */
    List<XxlJobLogReport> queryLogReport(
            @Param("triggerDayFrom") Date triggerDayFrom, @Param("triggerDayTo") Date triggerDayTo);

    /** Query total aggregated statistics across all days. */
    XxlJobLogReport queryLogReportTotal();
}
