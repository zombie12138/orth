package com.abyss.orth.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.abyss.orth.admin.model.JobGroup;

/**
 * MyBatis mapper for executor group operations.
 *
 * <p>Manages executor groups and their address configurations for job routing and service
 * discovery.
 */
@Mapper
public interface JobGroupMapper {

    /** Find all executor groups. */
    List<JobGroup> findAll();

    /** Find executor groups by address type (AUTO or MANUAL). */
    List<JobGroup> findByAddressType(@Param("addressType") int addressType);

    /** Create new executor group. */
    int save(JobGroup orthJobGroup);

    /** Update executor group. */
    int update(JobGroup orthJobGroup);

    /** Delete executor group by ID. */
    int remove(@Param("id") int id);

    /** Load executor group by ID. */
    JobGroup load(@Param("id") int id);

    /** Query paginated executor groups with optional filters. */
    List<JobGroup> pageList(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("appname") String appname,
            @Param("title") String title);

    /** Count total executor groups matching pageList query criteria. */
    int pageListCount(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("appname") String appname,
            @Param("title") String title);
}
