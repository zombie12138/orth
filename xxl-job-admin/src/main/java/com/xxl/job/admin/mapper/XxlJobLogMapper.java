package com.xxl.job.admin.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xxl.job.admin.model.XxlJobLog;

/**
 * MyBatis mapper for job execution log operations.
 *
 * <p>Provides CRUD operations and specialized queries for job execution logs including pagination,
 * filtering, alarm status management, and cleanup operations.
 */
@Mapper
public interface XxlJobLogMapper {

    /**
     * Query paginated job logs with filters.
     *
     * <p>If jobId is provided, filters by jobId; otherwise filters by jobGroup.
     *
     * @param offset pagination offset
     * @param pagesize page size
     * @param jobGroup executor group ID filter
     * @param jobId job ID filter (takes precedence over jobGroup if specified)
     * @param triggerTimeStart start time filter (inclusive)
     * @param triggerTimeEnd end time filter (inclusive)
     * @param logStatus execution status filter
     * @return list of job logs matching criteria
     */
    List<XxlJobLog> pageList(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("jobGroup") int jobGroup,
            @Param("jobId") int jobId,
            @Param("triggerTimeStart") Date triggerTimeStart,
            @Param("triggerTimeEnd") Date triggerTimeEnd,
            @Param("logStatus") int logStatus);

    /** Count total records matching pageList query criteria. */
    int pageListCount(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("jobGroup") int jobGroup,
            @Param("jobId") int jobId,
            @Param("triggerTimeStart") Date triggerTimeStart,
            @Param("triggerTimeEnd") Date triggerTimeEnd,
            @Param("logStatus") int logStatus);

    /** Load job log by ID. */
    XxlJobLog load(@Param("id") long id);

    /** Save new job log and return generated ID. */
    long save(XxlJobLog xxlJobLog);

    /** Update trigger information (time, code, message). */
    int updateTriggerInfo(XxlJobLog xxlJobLog);

    /** Update execution result information (time, code, message). */
    int updateHandleInfo(XxlJobLog xxlJobLog);

    /** Delete all logs for a specific job. */
    int delete(@Param("jobId") int jobId);

    /** Generate log statistics report for a date range. */
    Map<String, Object> findLogReport(@Param("from") Date from, @Param("to") Date to);

    /**
     * Find log IDs eligible for cleanup.
     *
     * @param jobGroup executor group ID filter
     * @param jobId job ID filter
     * @param clearBeforeTime delete logs before this time
     * @param clearBeforeNum keep at least this many recent logs
     * @param pagesize batch size limit
     * @return list of log IDs to delete
     */
    List<Long> findClearLogIds(
            @Param("jobGroup") int jobGroup,
            @Param("jobId") int jobId,
            @Param("clearBeforeTime") Date clearBeforeTime,
            @Param("clearBeforeNum") int clearBeforeNum,
            @Param("pagesize") int pagesize);

    /** Delete logs by ID list (batch deletion). */
    int clearLog(@Param("logIds") List<Long> logIds);

    /** Find failed job log IDs for alarm processing. */
    List<Long> findFailJobLogIds(@Param("pagesize") int pagesize);

    /**
     * Update alarm status with optimistic locking.
     *
     * @param logId log ID
     * @param oldAlarmStatus expected current status
     * @param newAlarmStatus new status to set
     * @return 1 if updated, 0 if status mismatch (already processed)
     */
    int updateAlarmStatus(
            @Param("logId") long logId,
            @Param("oldAlarmStatus") int oldAlarmStatus,
            @Param("newAlarmStatus") int newAlarmStatus);

    /** Find lost job log IDs (jobs that never reported back after being triggered). */
    List<Long> findLostJobIds(@Param("lostTime") Date lostTime);
}
