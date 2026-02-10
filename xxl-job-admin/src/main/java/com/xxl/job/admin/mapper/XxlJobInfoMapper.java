package com.xxl.job.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xxl.job.admin.model.XxlJobInfo;

/**
 * job info
 *
 * @author xuxueli 2016-1-12 18:03:45
 */
@Mapper
public interface XxlJobInfoMapper {

    public List<XxlJobInfo> pageList(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("jobGroup") int jobGroup,
            @Param("triggerStatus") int triggerStatus,
            @Param("jobDesc") String jobDesc,
            @Param("executorHandler") String executorHandler,
            @Param("author") String author,
            @Param("superTaskName") String superTaskName);

    public int pageListCount(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("jobGroup") int jobGroup,
            @Param("triggerStatus") int triggerStatus,
            @Param("jobDesc") String jobDesc,
            @Param("executorHandler") String executorHandler,
            @Param("author") String author,
            @Param("superTaskName") String superTaskName);

    public int save(XxlJobInfo info);

    public XxlJobInfo loadById(@Param("id") int id);

    public int update(XxlJobInfo xxlJobInfo);

    public int delete(@Param("id") long id);

    public List<XxlJobInfo> getJobsByGroup(@Param("jobGroup") int jobGroup);

    public int findAllCount();

    /**
     * find schedule job, limit "trigger_status = 1"
     *
     * @param maxNextTime
     * @param pagesize
     * @return
     */
    public List<XxlJobInfo> scheduleJobQuery(
            @Param("maxNextTime") long maxNextTime, @Param("pagesize") int pagesize);

    /**
     * update schedule job
     *
     * <p>1、can only update "trigger_status = 1", Avoid stopping tasks from being opened 2、valid
     * "triggerStatus gte 0", filter illegal state
     *
     * @param xxlJobInfo
     * @return
     */
    public int scheduleUpdate(XxlJobInfo xxlJobInfo);

    /**
     * find all SubTasks by SuperTask ID
     *
     * @param superTaskId SuperTask ID
     * @return list of SubTasks
     */
    public List<XxlJobInfo> findBySuperTaskId(@Param("superTaskId") int superTaskId);

    /**
     * count SubTasks by SuperTask ID
     *
     * @param superTaskId SuperTask ID
     * @return count of SubTasks
     */
    public int countBySuperTaskId(@Param("superTaskId") int superTaskId);

    /**
     * Search jobs by ID or description (for SuperTask autocomplete)
     *
     * @param jobGroup job group ID
     * @param query search query (matches job ID or description)
     * @return list of matching jobs (max 20)
     */
    public List<XxlJobInfo> searchByIdOrDesc(
            @Param("jobGroup") int jobGroup, @Param("query") String query);
}
