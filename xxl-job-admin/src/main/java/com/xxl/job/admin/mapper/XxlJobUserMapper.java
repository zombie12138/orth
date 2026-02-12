package com.xxl.job.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.xxl.job.admin.model.XxlJobUser;

/**
 * MyBatis mapper for user account operations.
 *
 * <p>Handles user authentication, authorization, and profile management with role-based access
 * control.
 */
@Mapper
public interface XxlJobUserMapper {

    /** Query paginated user list with optional filters. */
    List<XxlJobUser> pageList(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("username") String username,
            @Param("role") int role);

    /** Count total users matching pageList query criteria. */
    int pageListCount(
            @Param("offset") int offset,
            @Param("pagesize") int pagesize,
            @Param("username") String username,
            @Param("role") int role);

    /** Load user by username (for login authentication). */
    XxlJobUser loadByUserName(@Param("username") String username);

    /** Load user by ID. */
    XxlJobUser loadById(@Param("id") int id);

    /** Create new user. */
    int save(XxlJobUser xxlJobUser);

    /** Update user information. */
    int update(XxlJobUser xxlJobUser);

    /** Delete user by ID. */
    int delete(@Param("id") int id);

    /** Update user session token. */
    int updateToken(@Param("id") int id, @Param("token") String token);
}
