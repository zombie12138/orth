package com.xxl.job.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xxl.job.admin.model.XxlJobGroup;

/**
 * MyBatis mapper for executor group operations.
 *
 * <p>Manages executor groups and their address configurations for job routing and service
 * discovery.
 */
@Mapper
public interface XxlJobGroupMapper {

    /** Find all executor groups. */
    List<XxlJobGroup> findAll();

    /** Find executor groups by address type (AUTO or MANUAL). */
    List<XxlJobGroup> findByAddressType(@Param("addressType") int addressType);

    /** Create new executor group. */
    int save(XxlJobGroup xxlJobGroup);

    /** Update executor group. */
    int update(XxlJobGroup xxlJobGroup);

    /** Delete executor group by ID. */
    int remove(@Param("id") int id);

    /** Load executor group by ID. */
    XxlJobGroup load(@Param("id") int id);

    /** Query paginated executor groups with optional filters. */
    List<XxlJobGroup> pageList(
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
