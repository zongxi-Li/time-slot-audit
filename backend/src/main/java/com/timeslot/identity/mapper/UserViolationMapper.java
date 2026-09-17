/**
 * 文件职责：定义 身份与用户 的 MyBatis 数据访问接口，执行查询、插入和更新 SQL。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 */
package com.timeslot.identity.mapper;

import com.timeslot.identity.domain.UserViolation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserViolationMapper {
    String SELECT_WITH_OPERATOR = """
            SELECT v.id, v.user_id, v.violation_type, v.credit_change, v.reason, v.operator_id, v.created_at,
                   u.real_name AS operator_name
            FROM user_violation v
            LEFT JOIN sys_user u ON v.operator_id = u.id
            """;

    @Select(SELECT_WITH_OPERATOR + """
            WHERE v.user_id = #{userId}
            ORDER BY v.created_at DESC, v.id DESC
            """)
    List<UserViolation> findByUserId(@Param("userId") Long userId);

    @Select(SELECT_WITH_OPERATOR + """
            WHERE v.user_id = #{userId} AND v.violation_type = #{type}
            ORDER BY v.created_at DESC, v.id DESC
            LIMIT 1
            """)
    UserViolation findLatestByType(@Param("userId") Long userId, @Param("type") String type);

    @Insert("""
            INSERT INTO user_violation (user_id, violation_type, credit_change, reason, operator_id)
            VALUES (#{userId}, #{violationType}, #{creditChange}, #{reason}, #{operatorId})
            """)
    int insert(@Param("userId") Long userId, @Param("violationType") String violationType,
               @Param("creditChange") Integer creditChange, @Param("reason") String reason,
               @Param("operatorId") Long operatorId);
}
