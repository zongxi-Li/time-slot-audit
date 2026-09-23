/**
 * 文件职责：用户违规和信用变动记录的 MyBatis 访问接口。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 * 方法：findByUserId/findLatestByType 查询违规历史或最新指定类型记录；insert 写入违规及信用变化记录。
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
    /** 查询违规记录时复用的字段清单，并关联操作人姓名。 */
    String SELECT_WITH_OPERATOR = """
            SELECT v.id, v.user_id, v.violation_type, v.credit_change, v.reason, v.operator_id, v.created_at,
                   u.real_name AS operator_name
            FROM user_violation v
            LEFT JOIN sys_user u ON v.operator_id = u.id
            """;

    /** 查询用户的全部违规记录，按发生时间从新到旧排列。 */
    @Select(SELECT_WITH_OPERATOR + """
            WHERE v.user_id = #{userId}
            ORDER BY v.created_at DESC, v.id DESC
            """)
    List<UserViolation> findByUserId(@Param("userId") Long userId);

    /** 查询用户某类违规的最近一条记录，用于资格或限制判断。 */
    @Select(SELECT_WITH_OPERATOR + """
            WHERE v.user_id = #{userId} AND v.violation_type = #{type}
            ORDER BY v.created_at DESC, v.id DESC
            LIMIT 1
            """)
    UserViolation findLatestByType(@Param("userId") Long userId, @Param("type") String type);

    /** 新增一条用户违规记录，保存扣分原因和操作人。 */
    @Insert("""
            INSERT INTO user_violation (user_id, violation_type, credit_change, reason, operator_id)
            VALUES (#{userId}, #{violationType}, #{creditChange}, #{reason}, #{operatorId})
            """)
    int insert(@Param("userId") Long userId, @Param("violationType") String violationType,
               @Param("creditChange") Integer creditChange, @Param("reason") String reason,
               @Param("operatorId") Long operatorId);
}
