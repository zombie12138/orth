package com.xxl.job.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xxl.job.admin.model.XxlJobLogGlue;

/**
 * MyBatis mapper for GLUE code version history operations.
 *
 * <p>Manages version control for dynamically edited job code (GLUE mode) enabling auditing and
 * rollback capabilities.
 */
@Mapper
public interface XxlJobLogGlueMapper {

    /** Save new GLUE code version. */
    int save(XxlJobLogGlue xxlJobLogGlue);

    /** Find all GLUE versions for a specific job. */
    List<XxlJobLogGlue> findByJobId(@Param("jobId") int jobId);

    /** Remove old GLUE versions, keeping only the most recent ones up to the limit. */
    int removeOld(@Param("jobId") int jobId, @Param("limit") int limit);

    /** Delete all GLUE versions for a specific job. */
    int deleteByJobId(@Param("jobId") int jobId);
}
