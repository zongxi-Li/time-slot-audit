/**
 * 文件职责：部门数据的 MyBatis 查询和写入接口。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 * 方法：findAll/findById/findByDeptName 查询部门；countByNameExcluding 检查名称是否重复；insert/update 新增或修改部门。
*/

package com.timeslot.identity.mapper;

import com.timeslot.identity.domain.Department;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DepartmentMapper {
    /** 查询全部部门，按主键顺序排列。 */
    @Select("""
            SELECT id, dept_name, description, created_at
            FROM department
            ORDER BY id
            """)
    List<Department> findAll();

    /** 根据部门 ID 查询单个部门。 */
    @Select("""
            SELECT id, dept_name, description, created_at
            FROM department
            WHERE id = #{id}
            """)
    Department findById(@Param("id") Long id);

    /** 检查除指定部门外是否已有同名部门，供重命名时校验唯一性。 */
    @Select("""
            SELECT COUNT(*) FROM department WHERE dept_name = #{deptName} AND id != #{excludeId}
            """)
    int countByNameExcluding(@Param("deptName") String deptName, @Param("excludeId") Long excludeId);

    /** 根据部门名称查询部门，用于创建时检查重名。 */
    @Select("""
            SELECT id, dept_name, description, created_at
            FROM department
            WHERE dept_name = #{deptName}
            """)
    Department findByDeptName(@Param("deptName") String deptName);

    /** 新增部门并保存名称和描述。 */
    @Insert("""
            INSERT INTO department (dept_name, description) VALUES (#{deptName}, #{description})
            """)
    int insert(@Param("deptName") String deptName, @Param("description") String description);

    /** 更新指定部门的名称和描述。 */
    @Update("""
            UPDATE department SET dept_name = #{deptName}, description = #{description} WHERE id = #{id}
            """)
    int update(@Param("id") Long id, @Param("deptName") String deptName, @Param("description") String description);
}
