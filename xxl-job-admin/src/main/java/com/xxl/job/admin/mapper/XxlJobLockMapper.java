package com.xxl.job.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis mapper for distributed lock operations.
 *
 * <p>Provides database-level pessimistic locking using SELECT FOR UPDATE to ensure single-scheduler
 * execution in clustered deployments.
 */
@Mapper
public interface XxlJobLockMapper {

    /**
     * Acquire schedule lock using SELECT FOR UPDATE.
     *
     * <p>Blocks until lock is available. Lock is automatically released at transaction commit.
     *
     * @return lock name (always "schedule_lock")
     */
    String scheduleLock();
}
