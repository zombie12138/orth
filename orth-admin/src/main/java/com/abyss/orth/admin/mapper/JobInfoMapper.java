package com.abyss.orth.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.abyss.orth.admin.model.JobInfo;

/**
 * MyBatis mapper for job configuration operations.
 *
 * <p>Core mapper for managing job definitions, scheduling state, and SuperTask relationships.
 * Provides specialized queries for time-ring scheduling algorithm.
 */
@Mapper
public interface JobInfoMapper {

    /** Query paginated job list with optional filters. */
    List<JobInfo> pageList(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("jobGroup") int jobGroup,
            @Param("triggerStatus") int triggerStatus,
            @Param("jobDesc") String jobDesc,
            @Param("executorHandler") String executorHandler,
            @Param("author") String author,
            @Param("superTaskName") String superTaskName);

    /** Count total jobs matching pageList query criteria. */
    int pageListCount(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("jobGroup") int jobGroup,
            @Param("triggerStatus") int triggerStatus,
            @Param("jobDesc") String jobDesc,
            @Param("executorHandler") String executorHandler,
            @Param("author") String author,
            @Param("superTaskName") String superTaskName);

    /** Create new job. */
    int save(JobInfo info);

    /** Load job by ID. */
    JobInfo loadById(@Param("id") int id);

    /** Update job configuration. */
    int update(JobInfo orthJobInfo);

    /** Delete job by ID. */
    int delete(@Param("id") long id);

    /** Get all jobs in a specific executor group. */
    List<JobInfo> getJobsByGroup(@Param("jobGroup") int jobGroup);

    /** Count total jobs in the system. */
    int findAllCount();

    /**
     * Query jobs due for scheduling (time-ring pre-read).
     *
     * <p>Only returns jobs with triggerStatus=1 (STARTED) and triggerNextTime &lt;= maxNextTime.
     * Used by JobScheduleHelper to populate the time-ring buffer.
     *
     * @param maxNextTime maximum next trigger time (current time + pre-read window)
     * @param pagesize batch size for pre-read
     * @return list of jobs ready to schedule
     */
    List<JobInfo> scheduleJobQuery(
            @Param("maxNextTime") long maxNextTime, @Param("pagesize") int pagesize);

    /**
     * Update job trigger state after scheduling (with optimistic locking).
     *
     * <p>Requirements: 1) Only updates jobs with triggerStatus=1 (prevents stopped jobs from being
     * rescheduled) 2) Validates triggerStatus &gt;= 0 (filters illegal states)
     *
     * @param orthJobInfo job with updated trigger times
     * @return 1 if updated successfully, 0 if state conflict
     */
    int scheduleUpdate(JobInfo orthJobInfo);

    /**
     * Find all SubTasks linked to a SuperTask template.
     *
     * @param superTaskId SuperTask template ID
     * @return list of SubTask instances
     */
    List<JobInfo> findBySuperTaskId(@Param("superTaskId") int superTaskId);

    /**
     * Count SubTasks linked to a SuperTask template.
     *
     * @param superTaskId SuperTask template ID
     * @return count of SubTask instances
     */
    int countBySuperTaskId(@Param("superTaskId") int superTaskId);

    /**
     * Search jobs by ID or description (for SuperTask autocomplete).
     *
     * @param jobGroup job group ID
     * @param query search query (matches job ID or description)
     * @return list of matching jobs (max 20)
     */
    List<JobInfo> searchByIdOrDesc(@Param("jobGroup") int jobGroup, @Param("query") String query);

    /**
     * Search jobs by ID or description across multiple groups.
     *
     * <p>When permittedGroupIds is empty, searches all groups (admin). Otherwise restricts to the
     * given group IDs.
     *
     * @param permittedGroupIds list of permitted group IDs (empty for admin)
     * @param query search query (matches job ID or description)
     * @return list of matching jobs (max 20)
     */
    List<JobInfo> searchByIdOrDescMultiGroup(
            @Param("permittedGroupIds") List<Integer> permittedGroupIds,
            @Param("query") String query);
}
