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
    @Select("""
            SELECT id, dept_name, description, created_at
            FROM department
            ORDER BY id
            """)
    List<Department> findAll();

    @Select("""
            SELECT id, dept_name, description, created_at
            FROM department
            WHERE id = #{id}
            """)
    Department findById(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*) FROM department WHERE dept_name = #{deptName} AND id != #{excludeId}
            """)
    int countByNameExcluding(@Param("deptName") String deptName, @Param("excludeId") Long excludeId);

    @Select("""
            SELECT id, dept_name, description, created_at
            FROM department
            WHERE dept_name = #{deptName}
            """)
    Department findByDeptName(@Param("deptName") String deptName);

    @Insert("""
            INSERT INTO department (dept_name, description) VALUES (#{deptName}, #{description})
            """)
    int insert(@Param("deptName") String deptName, @Param("description") String description);

    @Update("""
            UPDATE department SET dept_name = #{deptName}, description = #{description} WHERE id = #{id}
            """)
    int update(@Param("id") Long id, @Param("deptName") String deptName, @Param("description") String description);
}
