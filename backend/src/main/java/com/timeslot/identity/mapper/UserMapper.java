/**
 * 文件职责：定义 身份与用户 的 MyBatis 数据访问接口，执行查询、插入和更新 SQL。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 */
package com.timeslot.identity.mapper;

import com.timeslot.identity.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {
    String SELECT_WITH_DEPARTMENT = """
            SELECT u.id, u.username, u.password, u.real_name, u.email, u.phone, u.role, u.status,
                   u.department_id, u.credit_score, u.restricted_until, d.dept_name AS department_name
            FROM sys_user u
            LEFT JOIN department d ON u.department_id = d.id
            """;

    @Select(SELECT_WITH_DEPARTMENT + """
            WHERE u.username = #{username}
            """)
    User findByUsername(@Param("username") String username);

    @Select(SELECT_WITH_DEPARTMENT + """
            WHERE u.id = #{id}
            """)
    User findById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT u.id, u.username, u.password, u.real_name, u.email, u.phone, u.role, u.status,
                   u.department_id, u.credit_score, u.restricted_until, d.dept_name AS department_name
            FROM sys_user u
            LEFT JOIN department d ON u.department_id = d.id
            <where>
                <if test="keyword != null and keyword != ''">
                    AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                         OR u.real_name LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="status != null">
                    AND u.status = #{status}
                </if>
            </where>
            ORDER BY u.id
            </script>
            """)
    List<User> search(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select("""
            SELECT COUNT(*) FROM sys_user WHERE username = #{username}
            """)
    int countByUsername(@Param("username") String username);

    /** 按角色查询活跃（status=1）用户 ID，用于把通知广播给某一角色的全部成员。 */
    @Select("""
            SELECT id FROM sys_user WHERE role = #{role} AND status = 1
            """)
    List<Long> findIdsByRole(@Param("role") String role);

    @Insert("""
            INSERT INTO sys_user (username, password, real_name, email, phone, role, status,
                                  department_id, credit_score, restricted_until)
            VALUES (#{username}, #{password}, #{realName}, #{email}, #{phone}, #{role}, #{status},
                    #{departmentId}, #{creditScore}, #{restrictedUntil})
            """)
    int insert(@Param("username") String username, @Param("password") String password,
               @Param("realName") String realName, @Param("email") String email,
               @Param("phone") String phone, @Param("role") String role, @Param("status") Integer status,
               @Param("departmentId") Long departmentId, @Param("creditScore") Integer creditScore,
               @Param("restrictedUntil") LocalDateTime restrictedUntil);

    @Update("""
            <script>
            UPDATE sys_user
            <set>
                <if test="realName != null">real_name = #{realName},</if>
                <if test="email != null">email = #{email},</if>
                <if test="phone != null">phone = #{phone},</if>
                <if test="departmentId != null">department_id = #{departmentId},</if>
                <if test="role != null">role = #{role},</if>
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateProfile(@Param("id") Long id, @Param("realName") String realName, @Param("email") String email,
                      @Param("phone") String phone, @Param("departmentId") Long departmentId,
                      @Param("role") String role);

    @Update("""
            UPDATE sys_user SET status = #{status} WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("""
            UPDATE sys_user SET password = #{password} WHERE id = #{id}
            """)
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    @Update("""
            UPDATE sys_user SET credit_score = #{creditScore} WHERE id = #{id}
            """)
    int updateCreditScore(@Param("id") Long id, @Param("creditScore") Integer creditScore);

    @Update("""
            UPDATE sys_user SET restricted_until = #{restrictedUntil} WHERE id = #{id}
            """)
    int updateRestrictedUntil(@Param("id") Long id, @Param("restrictedUntil") LocalDateTime restrictedUntil);
}
