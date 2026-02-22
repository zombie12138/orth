package com.abyss.orth.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.abyss.orth.admin.model.JobLogGlue;

/**
 * MyBatis mapper for GLUE code version history operations.
 *
 * <p>Manages version control for dynamically edited job code (GLUE mode) enabling auditing and
 * rollback capabilities.
 */
@Mapper
public interface JobLogGlueMapper {

    /** Save new GLUE code version. */
    int save(JobLogGlue orthJobLogGlue);

    /** Find all GLUE versions for a specific job. */
    List<JobLogGlue> findByJobId(@Param("jobId") int jobId);

    /** Remove old GLUE versions, keeping only the most recent ones up to the limit. */
    int removeOld(@Param("jobId") int jobId, @Param("limit") int limit);

    /** Delete all GLUE versions for a specific job. */
    int deleteByJobId(@Param("jobId") int jobId);
}
