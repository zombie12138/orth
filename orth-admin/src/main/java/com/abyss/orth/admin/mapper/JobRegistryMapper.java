package com.abyss.orth.admin.mapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.abyss.orth.admin.model.JobRegistry;

/**
 * MyBatis mapper for executor registry operations.
 *
 * <p>Manages executor heartbeat registrations for service discovery and health monitoring with
 * automatic cleanup of stale entries.
 */
@Mapper
public interface JobRegistryMapper {

    /** Find dead (timed-out) registry entries. */
    List<Integer> findDead(@Param("timeout") int timeout, @Param("nowTime") Date nowTime);

    /** Remove dead registry entries by IDs. */
    int removeDead(@Param("ids") List<Integer> ids);

    /** Find all active registry entries (not timed out). */
    List<JobRegistry> findAll(@Param("timeout") int timeout, @Param("nowTime") Date nowTime);

    /**
     * Insert or update executor registry entry (upsert).
     *
     * <p>Creates new entry if not exists, updates timestamp if exists.
     */
    int registrySaveOrUpdate(
            @Param("registryGroup") String registryGroup,
            @Param("registryKey") String registryKey,
            @Param("registryValue") String registryValue,
            @Param("updateTime") Date updateTime);

    /** Delete specific registry entry. */
    int registryDelete(
            @Param("registryGroup") String registryGroup,
            @Param("registryKey") String registryKey,
            @Param("registryValue") String registryValue);
}
